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
                0 -> state.bluetoothGranted
                1 -> state.notificationsGranted
                2 -> state.batteryUnrestricted
                3 -> state.exactAlarmsGranted
                4 -> state.dndAccessGranted && alarm.dndAllowsAlarmAudio == true
                5 -> state.alarmChannels.all { it.importance != null && it.importance > 0 && it.bypassDnd }
                else -> true
            }

        fun explanation(alarm: CgmAlarmSystemSnapshot): String =
            when (this.state.value.step) {
                0 -> "Geräte in der Nähe für den direkten Sensorempfang. Der Sensor wird erst über Sensor verbinden eingerichtet."
                1 -> "Erlaubt Glukosealarme und den laufenden Collectorstatus."
                2 -> "Die Akkuausnahme schützt den zeitkritischen Sensorempfang vor Doze-Einschränkungen."
                3 -> "Erlaubt zeitgenaue Sensorfenster und Alarmwiederholungen."
                4 -> "Der Systemzugriff erlaubt Kanal-Ausnahmen. Eigene Alarmtöne benötigen zusätzlich die Systemausnahme Alarme im Ruhemodus; Kanal-Ausnahmen allein reichen dafür nicht. Aktueller Tonstatus: ${cgmAlarmAudioPolicyLabel(alarm)}."
                5 -> "Alle SugarWear-Alarmkanäle gemeinsam prüfen. Ihre Ausnahme betrifft die Meldungen. Eigene Töne verwenden die System-Alarmausgabe und benötigen deren Freigabe im Ruhemodus."
                else -> "Standard ist die System-Alarmlautstärke. Unter Alarme lässt sich optional eine eigene Lautstärke wählen und jeder eigene SugarWear-Ton testen. Lautstärke 0 bleibt ohne Überschreibung stumm."
            }

        fun restore(step: Int, awaiting: Boolean) {
            mutableState.value = state.value.copy(step = step.coerceIn(0, 6), awaitingSystem = awaiting)
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
            if (state.value.step < 6) {
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
