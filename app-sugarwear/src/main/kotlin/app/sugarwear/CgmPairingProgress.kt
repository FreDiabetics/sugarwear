package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState

internal data class CgmPairingProgress(
    val message: String,
    val animate: Boolean,
)

internal fun cgmPairingProgress(
    state: CgmPersistedState,
    startedAtEpochMs: Long,
    preparing: Boolean = false,
    awaitingSystemAccess: Boolean = false,
    systemAccessFailure: String? = null,
    overviewPairing: Boolean = false,
): CgmPairingProgress {
    systemAccessFailure?.let { return CgmPairingProgress(it, false) }
    if (preparing) return CgmPairingProgress(if (overviewPairing) "Suche Sensor" else "Verbindung wird vorbereitet", true)
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= startedAtEpochMs }
    if (error?.recoverable == false) return CgmPairingProgress(error.safeMessage, false)
    if (!state.collectorEnabled) return CgmPairingProgress("Sensorsuche pausiert", false)
    if (awaitingSystemAccess) return CgmPairingProgress(if (overviewPairing) "Kopplungsanfrage" else "Android-Bluetooth-Freigabe wird benötigt", true)
    val recoverableRadioRetry =
        error?.recoverable == true &&
            (error.code.startsWith("G7-GATT-") || error.code.startsWith("G7-BLE-")) &&
            state.protocolState == CgmProtocolState.RECOVERING
    val phase =
        when {
            overviewPairing -> when (state.protocolState) {
                CgmProtocolState.AUTHENTICATION_START,
                CgmProtocolState.AUTHENTICATION_ROUND_1,
                CgmProtocolState.AUTHENTICATION_ROUND_2,
                CgmProtocolState.AUTHENTICATION_ROUND_3,
                CgmProtocolState.CHALLENGE,
                CgmProtocolState.CERTIFICATE_EXCHANGE,
                CgmProtocolState.KEY_EXCHANGE,
                CgmProtocolState.AUTHENTICATING,
                -> "Authentifiziere"
                CgmProtocolState.BONDING -> "Kopplungsanfrage"
                CgmProtocolState.AUTHENTICATED,
                CgmProtocolState.REQUESTING_GLUCOSE,
                CgmProtocolState.RECEIVING_GLUCOSE,
                CgmProtocolState.BACKFILL,
                CgmProtocolState.WAITING_FOR_NEXT_READING,
                -> "Verbinde"
                else -> "Suche Sensor"
            }
            recoverableRadioRetry -> "Sensor suchen"
            else -> cgmProtocolPhaseName(state.protocolState)
        }
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
        phase,
        state.activeAttemptId != null && (!waiting || recoverableRadioRetry),
    )
}
