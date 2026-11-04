package app.sugarwear

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
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
            val preferences =
                listOf("g7_collector_state", CGM_CREDENTIAL_PREFERENCES, "g7_ui_sensor_transition")
                    .map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
            app.contentResolver.registerContentObserver(CgmReadingProvider.CONTENT_URI, true, observer)
            preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
            trySend(Unit)
            awaitClose {
                app.contentResolver.unregisterContentObserver(observer)
                preferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
            }
        }.conflate()
            .map {
                CgmOverviewSnapshot(
                    persisted = CgmSensorStateStore(app).read(),
                    credentialsPresent = CgmCredentialStore(app).read() != null,
                    graphReadings = CgmReadingDatabase(app).use { it.query(limit = 300).toList() },
                    transition = CgmCollectorUiTransitionStore.read(app),
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
                            (snapshot.persisted.collectorEnabled && snapshot.persisted.sensor != null) ||
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
    val expiry = collectorAttemptPresentation(snapshot.persisted, snapshot.transition, now)?.expiresAt
    return (minOf(minute, expiry ?: Long.MAX_VALUE) - now).coerceAtLeast(1L)
}
