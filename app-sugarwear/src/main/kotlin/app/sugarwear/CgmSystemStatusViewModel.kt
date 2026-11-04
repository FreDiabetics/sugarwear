package app.sugarwear

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import app.sugarlicious.sensor.protocol.CollectorDiagnosticAttempt
import app.sugarlicious.sensor.protocol.CollectorHardwareMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CgmSensorIdentityUi(
    val pairingCode: String?,
    val gtin: String?,
    val sensorSerial: String?,
)

internal data class CgmSystemStatusSnapshot(
    val palette: CgmAppearancePalette,
    val sensor: CgmPersistedState,
    val credentials: CgmSensorIdentityUi?,
    val attempt: CollectorDiagnosticAttempt?,
    val cycle: CollectorCycleTiming?,
    val metrics: CollectorHardwareMetrics,
    val reconnectStrategy: String,
    val access: CgmSystemAccessSnapshot,
    val serviceRunning: Boolean,
)

internal data class CgmSystemStatusUiState(
    val snapshot: CgmSystemStatusSnapshot? = null,
    val showPairingCode: Boolean = false,
    val section: String? = null,
    val hardwareExpanded: Boolean = false,
    val diagnosticsExpanded: Boolean = false,
    val loading: Boolean = true,
    val error: CgmSettingsIoError? = null,
)

internal class CgmSystemStatusRepository(
    private val app: Context,
) {
    fun snapshot(revealCode: Boolean): CgmSystemStatusSnapshot {
        val diagnostics = CgmCollectorDiagnosticStore(app)
        val attempt = diagnostics.snapshot().firstOrNull()
        return CgmSystemStatusSnapshot(
            CgmAppearanceStore(app).load(),
            CgmSensorStateStore(app).read(),
            CgmCredentialStore(app).read()?.let { CgmSensorIdentityUi(if (revealCode) it.pairingCode else null, it.gtin, it.sensorSerial) },
            attempt,
            attempt?.cycle ?: diagnostics.pendingScheduledCycle(),
            CgmExpectedWindowLedger(app).metrics(),
            CgmReconnectStrategyStore.read(app).name,
            CgmSystemAccess.snapshot(app),
            CgmCollectorService.isServiceRunning(),
        )
    }

    fun reconcile() = CgmRuntimeReconciler.reconcile(app, CgmRuntimeEntryPoint.SYSTEM_STATUS)

    fun recordUnlink() = CgmCollectorUiTransitionStore.record(app, CgmUiSensorAction.UNLINK)

    fun setCollectorEnabled(enabled: Boolean) {
        CgmCollectorUiTransitionStore.record(app, if (enabled) CgmUiSensorAction.RESUME else CgmUiSensorAction.PAUSE)
        if (enabled) CgmCollectorService.start(app) else CgmCollectorService.stop(app)
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
internal class CgmSystemStatusViewModel
    @javax.inject.Inject
    constructor(
        app: Application,
        private val repository: CgmSystemStatusRepository = CgmSystemStatusRepository(app),
    ) : AndroidViewModel(app) {
        private val mutableState = MutableStateFlow(CgmSystemStatusUiState())
        val state = mutableState.asStateFlow()
        private var reconciled = false
        private val worker =
            CgmSettingsWorker(viewModelScope, read = {
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
