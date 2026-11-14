package app.sugarlicious.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.IOException

enum class DashboardObservationError { STORAGE_UNAVAILABLE }

/** Presentation input only: canonical resolution and background fan-out remain with the bridge. */
internal data class DashboardRepositoryState(
    val therapy: TherapyDisplayState?,
    val diagnostics: DiagnosticsSnapshot,
    val preferences: DashboardUiPreferences,
    val observationError: DashboardObservationError? = null,
)

private val emptyDiagnostics = DiagnosticsSnapshot(null, null, null, 0, 0, 0, 0, null, null)

data class DashboardUiState(
    val therapy: TherapyDisplayState? = null,
    val diagnostics: DiagnosticsSnapshot = emptyDiagnostics,
    val preferences: DashboardUiPreferences = DashboardUiPreferences(),
    val nowEpochMs: Long = 0L,
    val observationError: DashboardObservationError? = null,
)

internal class DashboardStateRepository(
    context: Context,
) {
    private val app = context.applicationContext
    private val diagnostics = app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    private val preferences = app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    val state: Flow<DashboardRepositoryState> =
        combine(
            TherapyStateStore(app).state.distinctUntilChanged().onEach {
                diagnostics.edit { putLong("mobileObservedAt", System.currentTimeMillis()) }
            },
            diagnostics.changes { it != "mobileObservedAt" },
            preferences.changes(),
        ) { therapy, _, _ ->
            DashboardRepositoryState(therapy, DiagnosticsSnapshot.read(diagnostics), DashboardUiPreferences.read(preferences))
        }.withDashboardStorageFailures { failure ->
            android.util.Log.e("DashboardState", "Canonical UI storage unavailable: ${failure.javaClass.simpleName}")
        }.flowOn(Dispatchers.IO)
}

/** Failure is a local presentation projection; canonical data and measurement time stay intact. */
internal fun Flow<DashboardRepositoryState>.withDashboardStorageFailures(
    report: (IOException) -> Unit,
): Flow<DashboardRepositoryState> =
    flow {
        var previous: DashboardRepositoryState? = null
        this@withDashboardStorageFailures
            .onEach { previous = it }
            .catch { failure ->
                if (failure !is IOException) throw failure
                report(failure)
                val last = previous ?: DashboardRepositoryState(null, emptyDiagnostics, DashboardUiPreferences())
                emit(
                    last.copy(
                        therapy = last.therapy?.let { it.copy(glucose = it.glucose?.copy(quality = app.sugarlicious.model.CgmQuality.INVALID)) },
                        observationError = DashboardObservationError.STORAGE_UNAVAILABLE,
                    ),
                )
            }.collect { emit(it) }
    }

private fun SharedPreferences.changes(include: (String?) -> Boolean = { true }): Flow<Unit> =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (include(key)) trySend(Unit) }
        registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

@dagger.hilt.android.lifecycle.HiltViewModel
internal class DashboardViewModel(
    repository: Flow<DashboardRepositoryState>,
    clock: Flow<Long>,
    scope: CoroutineScope? = null,
) : ViewModel() {
    @javax.inject.Inject
    constructor(dependencies: DashboardDependencies) : this(dependencies.repository.state, dependencies.clock)

    val uiState: StateFlow<DashboardUiState> =
        combine(repository, clock) { input, now ->
            DashboardUiState(input.therapy?.mobilePhoneSourcesOnly(), input.diagnostics, input.preferences, now, input.observationError)
        }.stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), DashboardUiState())
}
