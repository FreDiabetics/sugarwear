package app.sugarwear

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CgmSettingsNavigationState(
    val category: String? = null,
    val aboutExpanded: Boolean = false,
)

@dagger.hilt.android.lifecycle.HiltViewModel
internal class CgmSettingsNavigationViewModel
    @javax.inject.Inject
    constructor() : ViewModel() {
        private val mutableState = MutableStateFlow(CgmSettingsNavigationState())
        val state = mutableState.asStateFlow()

        fun selectCategory(category: String?) {
            mutableState.value = state.value.copy(category = category)
        }

        fun toggleAbout() {
            mutableState.value = state.value.copy(aboutExpanded = !state.value.aboutExpanded)
        }

        fun back(): Boolean {
            if (state.value.category == null) return false
            selectCategory(null)
            return true
        }
    }

internal data class CgmSetupState(
    val step: Int = 0,
    val awaitingSystem: Boolean = false,
    val complete: Boolean = false,
)

@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = CgmSetupViewModel.Factory::class)
internal class CgmSetupViewModel
    @dagger.assisted.AssistedInject
    constructor(
        @dagger.assisted.Assisted private val completeSetup: () -> Unit,
    ) : ViewModel() {
        @dagger.assisted.AssistedFactory
        interface Factory {
            fun create(completeSetup: () -> Unit): CgmSetupViewModel
        }

        private val mutableState = MutableStateFlow(CgmSetupState())
        val state = mutableState.asStateFlow()

        fun granted(state: CgmSystemAccessSnapshot, alarm: CgmAlarmSystemSnapshot): Boolean =
            when (this.state.value.step) {
                0 -> state.notificationsGranted
                1 -> state.exactAlarmsGranted
                2 -> state.bluetoothGranted
                3 -> state.batteryUnrestricted && state.autoRevokeStatus != CgmBackgroundRestrictionStatus.RESTRICTED
                else -> true
            }

        fun explanation(alarm: CgmAlarmSystemSnapshot): String =
            when (this.state.value.step) {
                0 -> "Für Sensorstatus und Glukosealarme."
                1 -> "Für zeitgenaue Sensorfenster, Wiederverbindung und Alarmwiederholung."
                2 -> "Für die direkte Verbindung mit dem Sensor."
                3 -> "App-Inaktivitätsbeschränkungen und Akkuoptimierungen werden getrennt geprüft."
                else -> ""
            }

        fun restore(step: Int, awaiting: Boolean) {
            mutableState.value = state.value.copy(step = step.coerceIn(0, 3), awaitingSystem = awaiting)
        }

        fun beginSystemRequest(): Boolean {
            if (state.value.awaitingSystem) return false
            mutableState.value = state.value.copy(awaitingSystem = true)
            return true
        }

        fun systemReturned() {
            mutableState.value = state.value.copy(awaitingSystem = false)
        }

        fun advance(): Boolean {
            if (state.value.step < 3) {
                mutableState.value = state.value.copy(step = state.value.step + 1)
                return false
            }
            if (!state.value.complete) {
                completeSetup()
                mutableState.value = state.value.copy(complete = true)
            }
            return true
        }
    }

@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = CgmPermissionRequestViewModel.Factory::class)
internal class CgmPermissionRequestViewModel
    @dagger.assisted.AssistedInject
    constructor(
        @dagger.assisted.Assisted private val recordRequests: (List<String>) -> Unit,
    ) : ViewModel() {
        @dagger.assisted.AssistedFactory
        interface Factory {
            fun create(recordRequests: (List<String>) -> Unit): CgmPermissionRequestViewModel
        }

        private var requestInFlight = false

        fun systemReturned() {
            requestInFlight = false
        }

        fun shouldRequest(missing: List<String>, requestedBefore: Set<String>, rationale: Set<String>): Boolean {
            if (requestInFlight || missing.isEmpty() || missing.any { it in requestedBefore && it !in rationale }) return false
            requestInFlight = true
            recordRequests(missing)
            return true
        }
    }
