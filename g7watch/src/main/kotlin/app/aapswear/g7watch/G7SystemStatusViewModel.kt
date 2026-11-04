package app.aapswear.g7watch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.CollectorHardwareMetrics
import app.aapswear.g7.G7PersistedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class G7SensorIdentityUi(
    val pairingCode: String?,
    val gtin: String?,
    val sensorSerial: String?,
)

internal data class G7SystemStatusSnapshot(
    val palette: G7AppearancePalette,
    val sensor: G7PersistedState,
    val credentials: G7SensorIdentityUi?,
    val attempt: CollectorDiagnosticAttempt?,
    val cycle: CollectorCycleTiming?,
    val metrics: CollectorHardwareMetrics,
    val reconnectStrategy: String,
    val access: G7SystemAccessSnapshot,
    val serviceRunning: Boolean,
)

internal data class G7SystemStatusUiState(
    val snapshot: G7SystemStatusSnapshot? = null,
    val showPairingCode: Boolean = false,
    val section: String? = null,
    val hardwareExpanded: Boolean = false,
    val diagnosticsExpanded: Boolean = false,
    val loading: Boolean = true,
    val error: G7SettingsIoError? = null,
)

internal class G7SystemStatusRepository(
    private val app: Context,
) {
    fun snapshot(revealCode: Boolean): G7SystemStatusSnapshot {
        val diagnostics = G7CollectorDiagnosticStore(app)
        val attempt = diagnostics.snapshot().firstOrNull()
        return G7SystemStatusSnapshot(
            G7AppearanceStore(app).load(),
            G7SensorStateStore(app).read(),
            G7CredentialStore(app).read()?.let { G7SensorIdentityUi(if (revealCode) it.pairingCode else null, it.gtin, it.sensorSerial) },
            attempt,
            attempt?.cycle ?: diagnostics.pendingScheduledCycle(),
            G7ExpectedWindowLedger(app).metrics(),
            G7ReconnectStrategyStore.read(app).name,
            G7SystemAccess.snapshot(app),
            G7CollectorService.isServiceRunning(),
        )
    }

    fun reconcile() = G7RuntimeReconciler.reconcile(app, G7RuntimeEntryPoint.SYSTEM_STATUS)

    fun recordUnlink() = G7CollectorUiTransitionStore.record(app, G7UiSensorAction.UNLINK)

    fun setCollectorEnabled(enabled: Boolean) {
        G7CollectorUiTransitionStore.record(app, if (enabled) G7UiSensorAction.RESUME else G7UiSensorAction.PAUSE)
        if (enabled) G7CollectorService.start(app) else G7CollectorService.stop(app)
    }
}

internal class G7SystemStatusViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repository = G7SystemStatusRepository(app)
    private val mutableState = MutableStateFlow(G7SystemStatusUiState())
    val state = mutableState.asStateFlow()
    private var reconciled = false
    private val worker =
        G7SettingsWorker(viewModelScope, read = {
            if (!reconciled) {
                repository.reconcile()
                reconciled = true
            }
            repository.snapshot(state.value.showPairingCode)
        })

    init {
        viewModelScope.launch {
            worker.state.collect { loaded ->
                val revealed = state.value.showPairingCode
                val snapshot =
                    loaded.snapshot?.let {
                        if (revealed) it else it.copy(credentials = it.credentials?.copy(pairingCode = null))
                    }
                mutableState.value = state.value.copy(snapshot = snapshot, loading = loaded.loading, error = loaded.error)
            }
        }
    }

    fun refresh() {
        worker.submit()
    }

    fun section(value: String?) {
        mutableState.value = state.value.copy(section = value)
    }

    fun showCode(value: Boolean) {
        val old = state.value
        mutableState.value = old.copy(showPairingCode = value, snapshot = if (value) old.snapshot else old.snapshot?.let { it.copy(credentials = it.credentials?.copy(pairingCode = null)) })
        worker.submit()
    }

    fun expandHardware(value: Boolean) {
        mutableState.value = state.value.copy(hardwareExpanded = value)
    }

    fun expandDiagnostics(value: Boolean) {
        mutableState.value = state.value.copy(diagnosticsExpanded = value)
    }

    fun recordUnlink(completed: () -> Unit) {
        worker.submit({ repository.recordUnlink() }, completed)
    }

    fun pause() {
        worker.submit({ repository.setCollectorEnabled(false) }, refreshDelayMs = 350L)
    }

    fun resumeCollector() {
        worker.submit({ repository.setCollectorEnabled(true) }, refreshDelayMs = 350L)
    }
}
