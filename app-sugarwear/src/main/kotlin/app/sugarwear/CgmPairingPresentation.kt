package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus

internal const val CGM_PAIRING_SUCCESS_DURATION_MS = 3_000L

internal fun hasUsableCollectorSession(
    reading: CgmReading?,
    sensorId: String?,
): Boolean = reading != null && reading.status == CgmReadingStatus.VALID && reading.sensorId == sensorId

internal fun hasCgmSensorAssociation(state: CgmPersistedState): Boolean =
    state.sensor != null &&
        (
            !state.sensor?.deviceAddress.isNullOrBlank() ||
                state.authenticationState == app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED ||
                hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
        )

@Suppress("UNUSED_PARAMETER")
internal fun isCgmPairingAttemptActive(
    state: CgmPersistedState,
    nowEpochMs: Long,
): Boolean = state.collectorEnabled && state.pairingAttemptId != null && state.lastError?.recoverable != false

internal fun pairingSuccessRemainingMs(
    deadlineEpochMs: Long,
    nowEpochMs: Long,
): Long = (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)

internal enum class CgmPairingScreenStep { ENTER_CODE, CONNECTING, CONNECTED }

@Suppress("UNUSED_PARAMETER")
internal fun initialCgmPairingScreenStep(
    state: CgmPersistedState,
    restored: CgmPairingScreenStep? = null,
): CgmPairingScreenStep? {
    if (restored == CgmPairingScreenStep.ENTER_CODE) return restored
    if (restored == CgmPairingScreenStep.CONNECTING) return restored
    return restored?.takeIf { it == CgmPairingScreenStep.CONNECTED }
}

internal fun advanceCgmPairingScreen(
    current: CgmPairingScreenStep,
    state: CgmPersistedState,
): CgmPairingScreenStep =
    if (
        current == CgmPairingScreenStep.CONNECTING &&
        !state.sensor?.deviceAddress.isNullOrBlank() &&
        hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
    ) {
        CgmPairingScreenStep.CONNECTED
    } else {
        current
    }

internal fun canStartCgmPairing(
    step: CgmPairingScreenStep?,
    inFlight: Boolean,
    code: String,
): Boolean = step == CgmPairingScreenStep.ENTER_CODE && !inFlight && code.length == 4 && code.all(Char::isDigit)

internal fun shouldScheduleCgmPairingCompletion(
    step: CgmPairingScreenStep?,
    alreadyScheduled: Boolean,
): Boolean = step == CgmPairingScreenStep.CONNECTED && !alreadyScheduled

@Suppress("UNUSED_PARAMETER")
internal fun isTerminalCgmPairingFailure(
    state: CgmPersistedState,
    pairingStartedAtEpochMs: Long,
    nowEpochMs: Long,
): Boolean {
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= pairingStartedAtEpochMs }
    return error?.code == "G7-AUTH-204" || error?.recoverable == false
}
