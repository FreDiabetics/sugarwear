package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

internal const val CGM_PAIRING_TIMEOUT_ERROR_CODE = "G7-PAIRING-TIMEOUT"

internal fun cgmInitialPairingDeadline(state: CgmPersistedState): Long? =
    listOfNotNull(state.pairingDeadlineEpochMs, state.pairingStartedAtEpochMs?.plus(CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS)).minOrNull()

private fun remainingInitialPairingMs(state: CgmPersistedState, nowEpochMs: Long): Long? =
    if (hasEstablishedCgmSensorSession(state)) null else cgmInitialPairingDeadline(state)?.minus(nowEpochMs) ?: 0L

/** Check at authentication/key commit too: a delayed timer must not accept late callbacks. */
internal fun requireCgmInitialPairingBudget(state: CgmPersistedState, nowEpochMs: Long) {
    if (!state.collectorEnabled) throw CancellationException("Collector paused")
    if (remainingInitialPairingMs(state, nowEpochMs)?.let { it <= 0L } == true) {
        throw CgmBleException(CGM_PAIRING_TIMEOUT_ERROR_CODE, "Verbindungszeitfenster abgelaufen", false)
    }
}

internal suspend fun <T> withinCgmInitialPairingBudget(
    state: CgmPersistedState,
    nowEpochMs: Long,
    block: suspend () -> T,
): T {
    if (!state.collectorEnabled) throw CancellationException("Collector paused")
    val remaining = remainingInitialPairingMs(state, nowEpochMs)
    return if (remaining == null) coroutineScope { block() } else withTimeout(remaining) { block() }
}
