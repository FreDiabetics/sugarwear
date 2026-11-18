package app.sugarwear

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.IOException

internal enum class CgmOverviewError { STORAGE_UNAVAILABLE }

internal data class CgmOverviewSnapshot(
    val persisted: CgmPersistedState,
    val credentialsPresent: Boolean,
    val graphReadings: List<CgmReading>,
    val transition: CgmUiSensorTransition?,
    val activeHistoryIdentity: Pair<String, String>? = null,
    val pendingAlarms: List<SugarWearAlarmInstance> = emptyList(),
    val bluetoothPermission: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val lastValidReading: CgmReading? = null,
    val pairingCode: String? = null,
)

internal data class CgmOverviewState(
    val snapshot: CgmOverviewSnapshot? = null,
    val now: Long = 0L,
    val error: CgmOverviewError? = null,
) {
    val failed: Boolean get() = error != null
}

/** Cold: registrations and all database/credential reads exist only for a visible subscriber. */
internal fun interface CgmOverviewRepository {
    fun observe(): Flow<CgmOverviewSnapshot>
}

internal class AndroidCgmOverviewRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CgmOverviewRepository {
    private val app = context.applicationContext

    override fun observe(): Flow<CgmOverviewSnapshot> =
        callbackFlow {
            val observer =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            val radioReceiver =
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: android.content.Intent) {
                        trySend(Unit)
                    }
                }
            app.registerReceiver(radioReceiver, android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED), Context.RECEIVER_EXPORTED)
            val preferences =
                listOf("g7_collector_state", CGM_CREDENTIAL_PREFERENCES, "g7_ui_sensor_transition", "g7_cgm_alarm_state")
                    .map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
            app.contentResolver.registerContentObserver(CgmReadingProvider.CONTENT_URI, true, observer)
            preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
            trySend(Unit)
            awaitClose {
                app.contentResolver.unregisterContentObserver(observer)
                app.unregisterReceiver(radioReceiver)
                preferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
            }
        }.conflate()
            .map {
                val persisted = CgmSensorStateStore(app).read()
                val bluetoothPermission = CgmSystemAccess.missingBluetoothPermissions(app).isEmpty()
                val bluetoothEnabled = if (app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) app.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true else false
                val transition = CgmCollectorUiTransitionStore.read(app)
                if (transition?.action in setOf(CgmUiSensorAction.RESUME, CgmUiSensorAction.PAIR) && transition != null && !transition.completed && cgmHasResumedLiveValue(persisted, transition, System.currentTimeMillis())) CgmCollectorUiTransitionStore.completeResume(app, transition)
                val credentials = CgmCredentialStore(app).read()
                CgmOverviewSnapshot(
                    persisted = persisted,
                    credentialsPresent = credentials != null,
                    pairingCode = credentials?.pairingCode,
                    graphReadings = CgmReadingDatabase(app).use { it.getGraphHistory(System.currentTimeMillis()) },
                    lastValidReading =
                        CgmReadingDatabase(app).use { database -> persisted.sensor?.let { sensor -> database.getLatestValidBefore(sensor.sensorId, sensor.sessionId ?: persisted.lastReading?.sessionId ?: sensor.sensorId, System.currentTimeMillis() + 1L) } }
                            ?: persisted.lastReading?.takeIf { it.status == CgmReadingStatus.VALID && cgmReadingBelongsToSession(persisted, it) },
                    transition = CgmCollectorUiTransitionStore.read(app),
                    activeHistoryIdentity = persisted.sensor?.let { CgmHistoryIdentityStore(app).canonical(it.sensorId, it.sessionId ?: it.sensorId) },
                    pendingAlarms = CgmAlarmCoordinator.pendingNotices(app),
                    bluetoothPermission = bluetoothPermission,
                    bluetoothEnabled = bluetoothEnabled,
                )
            }.distinctUntilChanged()
            .flowOn(io)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@dagger.hilt.android.lifecycle.HiltViewModel
internal class CollectorViewModel(
    repository: CgmOverviewRepository,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    @javax.inject.Inject
    constructor(repository: CgmOverviewRepository) : this(repository, scope = null)

    private val retryRevision = MutableStateFlow(0L)

    fun retry() {
        retryRevision.value += 1
    }

    // scan retains the last canonical snapshot if IO fails; time never rewrites measurements.
    private val updates: Flow<CgmOverviewState> =
        retryRevision
            .flatMapLatest {
                repository
                    .observe()
                    .map<CgmOverviewSnapshot, CgmOverviewState> { CgmOverviewState(it, clock()) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (error !is IOException && error !is android.database.SQLException && error !is SecurityException) throw error
                        java.util.logging.Logger
                            .getLogger("G7Overview")
                            .warning("UI storage observation failed: ${error.javaClass.simpleName}")
                        emit(CgmOverviewState(now = clock(), error = CgmOverviewError.STORAGE_UNAVAILABLE))
                    }
            }.scan(CgmOverviewState()) { previous, next ->
                if (next.failed) next.copy(snapshot = previous.snapshot) else next
            }.flatMapLatest { loaded ->
                flow {
                    var current = loaded.copy(now = clock())
                    emit(current)
                    while (!current.failed &&
                        current.snapshot?.let { snapshot ->
                            snapshot.persisted.sensor != null ||
                                collectorAttemptPresentation(snapshot.persisted, snapshot.transition, current.now)?.expiresAt != null
                        } == true
                    ) {
                        delay(nextCgmOverviewRefresh(current.snapshot, current.now))
                        current = current.copy(now = clock())
                        emit(current)
                    }
                }
            }
    val state: StateFlow<CgmOverviewState> =
        updates.stateIn(
            scope ?: viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            CgmOverviewState(),
        )
}

/** Visible age boundaries are relative to measured time; attempts expire at their exact deadline. */
internal fun nextCgmOverviewRefresh(snapshot: CgmOverviewSnapshot, now: Long): Long {
    val measured = snapshot.persisted.lastReading?.timestampEpochMs
    val minute =
        measured?.let { if (now < it) it else now + 60_000L - (now - it) % 60_000L }
            ?: (now + 60_000L - now % 60_000L)
    val pauseExpiry =
        snapshot.transition
            ?.takeIf { !snapshot.persisted.collectorEnabled && it.action == CgmUiSensorAction.PAUSE }
            ?.let { it.startedAt + CGM_PAUSED_VALUE_DURATION_MS }
            ?.takeIf { it > now }
    val expiry = listOfNotNull(collectorAttemptPresentation(snapshot.persisted, snapshot.transition, now)?.expiresAt, pauseExpiry).minOrNull()
    return (minOf(minute, expiry ?: Long.MAX_VALUE) - now).coerceAtLeast(1L)
}
