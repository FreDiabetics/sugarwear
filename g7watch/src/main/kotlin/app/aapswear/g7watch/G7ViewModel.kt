package app.aapswear.g7watch

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aapswear.g7.CgmReading
import app.aapswear.g7.G7PersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.IOException

internal enum class G7OverviewError { STORAGE_UNAVAILABLE }

internal data class G7OverviewSnapshot(
    val persisted: G7PersistedState,
    val credentialsPresent: Boolean,
    val graphReadings: List<CgmReading>,
    val transition: G7UiSensorTransition?,
)

internal data class G7OverviewState(
    val snapshot: G7OverviewSnapshot? = null,
    val now: Long = 0L,
    val error: G7OverviewError? = null,
) {
    val failed: Boolean get() = error != null
}

/** Cold: registrations and all database/credential reads exist only for a visible subscriber. */
internal fun interface G7OverviewRepository {
    fun observe(): Flow<G7OverviewSnapshot>
}

internal class AndroidG7OverviewRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : G7OverviewRepository {
    private val app = context.applicationContext

    override fun observe(): Flow<G7OverviewSnapshot> =
        callbackFlow {
            val observer =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            val preferences =
                listOf("g7_collector_state", G7_CREDENTIAL_PREFERENCES, "g7_ui_sensor_transition")
                    .map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
            app.contentResolver.registerContentObserver(G7ReadingProvider.CONTENT_URI, true, observer)
            preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
            trySend(Unit)
            awaitClose {
                app.contentResolver.unregisterContentObserver(observer)
                preferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
            }
        }.conflate()
            .map {
                G7OverviewSnapshot(
                    persisted = G7SensorStateStore(app).read(),
                    credentialsPresent = G7CredentialStore(app).read() != null,
                    graphReadings = G7ReadingDatabase(app).use { it.query(limit = 300).toList() },
                    transition = G7CollectorUiTransitionStore.read(app),
                )
            }.distinctUntilChanged()
            .flowOn(io)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class G7ViewModel(
    repository: G7OverviewRepository,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val retryRevision = MutableStateFlow(0L)

    fun retry() {
        retryRevision.value += 1
    }

    // scan retains the last canonical snapshot if IO fails; time never rewrites measurements.
    private val updates: Flow<G7OverviewState> =
        retryRevision
            .flatMapLatest {
                repository
                    .observe()
                    .map<G7OverviewSnapshot, G7OverviewState> { G7OverviewState(it, clock()) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (error !is IOException && error !is android.database.SQLException && error !is SecurityException) throw error
                        java.util.logging.Logger
                            .getLogger("G7Overview")
                            .warning("UI storage observation failed: ${error.javaClass.simpleName}")
                        emit(G7OverviewState(now = clock(), error = G7OverviewError.STORAGE_UNAVAILABLE))
                    }
            }.scan(G7OverviewState()) { previous, next ->
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
                        delay(nextG7OverviewRefresh(current.snapshot, current.now))
                        current = current.copy(now = clock())
                        emit(current)
                    }
                }
            }
    val state: StateFlow<G7OverviewState> =
        updates.stateIn(
            scope ?: viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            G7OverviewState(),
        )
}

/** Visible age boundaries are relative to measured time; attempts expire at their exact deadline. */
internal fun nextG7OverviewRefresh(snapshot: G7OverviewSnapshot, now: Long): Long {
    val measured = snapshot.persisted.lastReading?.timestampEpochMs
    val minute =
        measured?.let { if (now < it) it else now + 60_000L - (now - it) % 60_000L }
            ?: (now + 60_000L - now % 60_000L)
    val expiry = collectorAttemptPresentation(snapshot.persisted, snapshot.transition, now)?.expiresAt
    return (minOf(minute, expiry ?: Long.MAX_VALUE) - now).coerceAtLeast(1L)
}
