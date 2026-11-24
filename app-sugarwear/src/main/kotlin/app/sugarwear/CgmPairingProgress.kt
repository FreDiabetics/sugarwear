package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState

internal data class CgmPairingProgress(
    val message: String,
    val animate: Boolean,
)

internal fun cgmOtherSensorsLabel(count: Int): String? =
    count.takeIf { it > 0 }?.let { "Weitere Sensoren erkannt: $it" }

internal fun cgmPairingProgress(
    state: CgmPersistedState,
    startedAtEpochMs: Long,
    preparing: Boolean = false,
    awaitingSystemAccess: Boolean = false,
    systemAccessFailure: String? = null,
): CgmPairingProgress {
    systemAccessFailure?.let { return CgmPairingProgress(it, false) }
    if (preparing) return CgmPairingProgress("Verbindung wird vorbereitet", true)
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= startedAtEpochMs }
    if (error?.recoverable == false) return CgmPairingProgress(error.safeMessage, false)
    if (!state.collectorEnabled) return CgmPairingProgress("Sensorsuche pausiert", false)
    if (awaitingSystemAccess) return CgmPairingProgress("Android-Bluetooth-Freigabe wird benötigt", true)
    val recoverableRadioRetry =
        error?.recoverable == true &&
            (error.code.startsWith("G7-GATT-") || error.code.startsWith("G7-BLE-")) &&
            state.protocolState == CgmProtocolState.RECOVERING
    val phase =
        if (recoverableRadioRetry) {
            "Sensor suchen"
        } else {
            cgmProtocolPhaseName(state.protocolState)
        }
    val message = phase
    val waiting =
        state.protocolState in
            setOf(
                CgmProtocolState.RECOVERING,
                CgmProtocolState.WAITING_FOR_NEXT_READING,
                CgmProtocolState.IDLE,
                CgmProtocolState.DISCONNECTED,
                CgmProtocolState.ERROR,
            )
    return CgmPairingProgress(
        message,
        state.activeAttemptId != null && (!waiting || recoverableRadioRetry),
    )
}
