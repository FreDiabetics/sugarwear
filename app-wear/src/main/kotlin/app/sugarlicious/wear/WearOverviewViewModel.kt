package app.sugarlicious.wear

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.complications.DirectSensorReadingResolver
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal enum class WearOverviewError { STORAGE_UNAVAILABLE, PHONE_REFRESH_FAILED, PREFERENCES_SAVE_FAILED }

internal data class WearOverviewSnapshot(
    val therapy: TherapyDisplayState? = null,
    val preferences: WearDisplayPreferences = WearDisplayPreferences(),
    val error: WearOverviewError? = null,
)

internal data class WearOverviewUiState(
    val snapshot: WearOverviewSnapshot = WearOverviewSnapshot(),
    val nowEpochMs: Long = 0,
    val connectedNodes: Int = 0,
    val refreshing: Boolean = false,
    val refreshAttempted: Boolean = false,
    val error: WearOverviewError? = null,
) {
    val displayTherapy: TherapyDisplayState?
        get() =
            snapshot.therapy?.let { canonical ->
                if (snapshot.error == null) canonical else canonical.copy(glucose = canonical.glucose?.copy(quality = app.sugarlicious.model.CgmQuality.INVALID))
            }
}

/** Adapter only: persistence, source policy and transport remain in their existing owners. */
internal interface WearOverviewRepository {
    fun observe(): Flow<WearOverviewSnapshot>

    suspend fun resolve(snapshot: WearOverviewSnapshot, now: Long): WearOverviewSnapshot

    suspend fun refresh(): Int

    suspend fun advanceGraphPeriod()

    fun retry()
}

/** Recovery waits for an explicit user retry; it never starts background polling. */
internal fun recoverWearOverviewSnapshots(
    retryRevision: kotlinx.coroutines.flow.StateFlow<Long>,
    source: () -> Flow<WearOverviewSnapshot>,
): Flow<WearOverviewSnapshot> =
    flow {
        var last = WearOverviewSnapshot()
        while (true) {
            val revision = retryRevision.value
            var retry = false
            source()
                .catch { error ->
                    if (error !is IOException) throw error
                    java.util.logging.Logger
                        .getLogger("WearOverview")
                        .warning("UI storage unavailable: ${error.javaClass.simpleName}")
                    emit(last.copy(error = WearOverviewError.STORAGE_UNAVAILABLE))
                    retryRevision.first { it != revision }
                    retry = true
                }.collect {
                    last = it
                    emit(it)
                }
            if (!retry) return@flow
        }
    }

internal class AndroidWearOverviewRepository(
    context: Context,
) : WearOverviewRepository {
    private val context = context.applicationContext
    private val retryRevision = MutableStateFlow(0L)

    override fun retry() {
        retryRevision.value += 1
    }

    private fun preferences(): Flow<WearDisplayPreferences> =
        callbackFlow {
            val prefs = context.getSharedPreferences(WearDisplayPreferences.PREFS, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Unit)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.map { WearDisplayPreferences.read(context) }.distinctUntilChanged()

    override fun observe(): Flow<WearOverviewSnapshot> =
        recoverWearOverviewSnapshots(retryRevision) {
            combine(
                TherapyStateStore(context).state,
                preferences(),
                WearCanonicalStateEvents.updates.onStart { emit(Unit) },
            ) { phone, preferences, _ ->
                WearOverviewSnapshot(
                    therapy = phone,
                    preferences = preferences,
                )
            }.distinctUntilChanged()
        }.flowOn(Dispatchers.IO)

    override suspend fun resolve(snapshot: WearOverviewSnapshot, now: Long): WearOverviewSnapshot =
        withContext(Dispatchers.IO) {
            if (snapshot.error != null) return@withContext snapshot
            snapshot.copy(
                therapy = DirectSensorReadingResolver.resolve(context, snapshot.therapy, now, snapshot.preferences.dataSource),
            )
        }

    override suspend fun refresh(): Int = withContext(Dispatchers.IO) { requestLatestState(context) }

    override suspend fun advanceGraphPeriod() =
        withContext(Dispatchers.IO) {
            val current = WearDisplayPreferences.read(context)
            WearDisplayPreferences.saveLocal(context, current.copy(graphHours = nextWearGraphHours(current.graphHours)))
        }
}

@dagger.hilt.android.lifecycle.HiltViewModel
internal class WearOverviewViewModel(
    private val repository: WearOverviewRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {
    @javax.inject.Inject
    constructor(repository: WearOverviewRepository) : this(repository, clock = System::currentTimeMillis)

    private val workScope = scope ?: viewModelScope

    private data class Actions(
        val connectedNodes: Int = 0,
        val refreshing: Boolean = false,
        val attempted: Boolean = false,
        val error: WearOverviewError? = null,
        val retryRevision: Long = 0L,
    )

    private val actions = MutableStateFlow(Actions())
    private var failedResolution: Pair<WearOverviewSnapshot, Long>? = null

    private suspend fun resolve(snapshot: WearOverviewSnapshot, now: Long, revision: Long): WearOverviewSnapshot {
        if (failedResolution == (snapshot to revision)) return snapshot.copy(error = WearOverviewError.STORAGE_UNAVAILABLE)
        return try {
            repository.resolve(snapshot, now).also { failedResolution = null }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error !is IOException && error !is android.database.SQLException && error !is SecurityException) throw error
            failedResolution = snapshot to revision
            java.util.logging.Logger
                .getLogger("WearOverview")
                .warning("UI resolution unavailable: ${error.javaClass.simpleName}")
            snapshot.copy(error = WearOverviewError.STORAGE_UNAVAILABLE)
        }
    }

    private val visibleClock =
        flow {
            while (true) {
                emit(clock())
                delay(VISIBLE_CLOCK_INTERVAL_MS)
            }
        }
    val uiState =
        combine(repository.observe().onStart { failedResolution = null }, visibleClock, actions) { snapshot, now, action ->
            val resolved = resolve(snapshot, now, action.retryRevision)
            WearOverviewUiState(resolved, now, action.connectedNodes, action.refreshing, action.attempted, action.error ?: resolved.error)
        }.stateIn(workScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), WearOverviewUiState())

    fun refresh() {
        if (actions.value.refreshing) return
        repository.retry()
        actions.value = actions.value.copy(refreshing = true, attempted = true, error = null, retryRevision = actions.value.retryRevision + 1)
        workScope.launch {
            try {
                actions.value = actions.value.copy(connectedNodes = repository.refresh(), refreshing = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is IOException && error !is ApiException && error !is SecurityException) throw error
                java.util.logging.Logger
                    .getLogger("WearOverview")
                    .warning("Phone refresh failed: ${error.javaClass.simpleName}")
                actions.value = actions.value.copy(refreshing = false, error = WearOverviewError.PHONE_REFRESH_FAILED)
            }
        }
    }

    fun advanceGraphPeriod() {
        workScope.launch {
            try {
                repository.advanceGraphPeriod()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is IOException && error !is SecurityException) throw error
                java.util.logging.Logger
                    .getLogger("WearOverview")
                    .warning("Graph preference save failed: ${error.javaClass.simpleName}")
                actions.value = actions.value.copy(error = WearOverviewError.PREFERENCES_SAVE_FAILED)
            }
        }
    }

    private companion object {
        const val VISIBLE_CLOCK_INTERVAL_MS = 30_000L
    }
}
