package app.aapswear.g7watch

import app.aapswear.g7.G7PersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

internal const val G7_PAIRING_TIMEOUT_ERROR_CODE = "G7-PAIRING-TIMEOUT"

internal fun g7InitialPairingDeadline(state: G7PersistedState): Long? =
    listOfNotNull(state.pairingDeadlineEpochMs, state.pairingStartedAtEpochMs?.plus(G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS)).minOrNull()

private fun remainingInitialPairingMs(state: G7PersistedState, nowEpochMs: Long): Long? =
    if (hasEstablishedG7SensorSession(state)) null else g7InitialPairingDeadline(state)?.minus(nowEpochMs) ?: 0L

/** Check at authentication/key commit too: a delayed timer must not accept late callbacks. */
internal fun requireG7InitialPairingBudget(state: G7PersistedState, nowEpochMs: Long) {
    if (!state.collectorEnabled) throw CancellationException("Collector paused")
    if (remainingInitialPairingMs(state, nowEpochMs)?.let { it <= 0L } == true) {
        throw G7BleException(G7_PAIRING_TIMEOUT_ERROR_CODE, "Verbindungszeitfenster abgelaufen", false)
    }
}

internal suspend fun <T> withinG7InitialPairingBudget(
    state: G7PersistedState,
    nowEpochMs: Long,
    block: suspend () -> T,
): T {
    if (!state.collectorEnabled) throw CancellationException("Collector paused")
    val remaining = remainingInitialPairingMs(state, nowEpochMs)
    return if (remaining == null) coroutineScope { block() } else withTimeout(remaining) { block() }
}
