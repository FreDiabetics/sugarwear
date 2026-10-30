package app.aapswear.g7watch

import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState

internal data class G7PairingProgress(
    val message: String,
    val animate: Boolean,
)

internal fun g7PairingProgress(
    state: G7PersistedState,
    startedAtEpochMs: Long,
    preparing: Boolean = false,
    awaitingSystemAccess: Boolean = false,
    systemAccessFailure: String? = null,
): G7PairingProgress {
    systemAccessFailure?.let { return G7PairingProgress(it, false) }
    if (preparing) return G7PairingProgress("Verbindung wird vorbereitet", true)
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= startedAtEpochMs }
    if (error?.recoverable == false) return G7PairingProgress(error.safeMessage, false)
    if (!state.collectorEnabled) return G7PairingProgress("Sensorsuche pausiert", false)
    if (awaitingSystemAccess) return G7PairingProgress("Android-Bluetooth-Freigabe wird benötigt", true)
    val phase = g7ProtocolPhaseName(state.protocolState)
    val radioError = error?.takeIf { it.code.startsWith("G7-GATT-") || it.code.startsWith("G7-BLE-") }
    val message = listOfNotNull(phase, radioError?.safeMessage?.substringBefore(" · scan=")).distinct().joinToString("\n")
    val waiting = state.protocolState in setOf(G7ProtocolState.RECOVERING, G7ProtocolState.WAITING_FOR_NEXT_READING, G7ProtocolState.IDLE, G7ProtocolState.DISCONNECTED, G7ProtocolState.ERROR)
    return G7PairingProgress(message, state.activeAttemptId != null && !waiting)
}
