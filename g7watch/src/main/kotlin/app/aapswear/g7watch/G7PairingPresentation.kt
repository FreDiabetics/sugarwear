package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7PersistedState

internal const val G7_PAIRING_SUCCESS_DURATION_MS = 3_000L

internal fun hasUsableCollectorSession(
    reading: CgmReading?,
    sensorId: String?,
): Boolean = reading != null && reading.status == CgmReadingStatus.VALID && reading.sensorId == sensorId

internal fun hasG7SensorAssociation(state: G7PersistedState): Boolean =
    state.sensor != null &&
        (
            !state.sensor?.deviceAddress.isNullOrBlank() ||
                state.authenticationState == app.aapswear.g7.G7AuthenticationState.AUTHENTICATED ||
                hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
        )

@Suppress("UNUSED_PARAMETER")
internal fun isG7PairingAttemptActive(
    state: G7PersistedState,
    nowEpochMs: Long,
): Boolean = state.collectorEnabled && state.pairingAttemptId != null && state.lastError?.recoverable != false

internal fun pairingSuccessRemainingMs(
    deadlineEpochMs: Long,
    nowEpochMs: Long,
): Long = (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)

internal enum class G7PairingScreenStep { ENTER_CODE, CONNECTING, CONNECTED }

@Suppress("UNUSED_PARAMETER")
internal fun initialG7PairingScreenStep(
    state: G7PersistedState,
    restored: G7PairingScreenStep? = null,
): G7PairingScreenStep? {
    if (restored == G7PairingScreenStep.ENTER_CODE) return restored
    if (restored == G7PairingScreenStep.CONNECTING) return restored
    return restored?.takeIf { it == G7PairingScreenStep.CONNECTED }
}

internal fun advanceG7PairingScreen(
    current: G7PairingScreenStep,
    state: G7PersistedState,
): G7PairingScreenStep =
    if (
        current == G7PairingScreenStep.CONNECTING &&
        !state.sensor?.deviceAddress.isNullOrBlank() &&
        hasUsableCollectorSession(state.lastReading, state.sensor?.sensorId)
    ) {
        G7PairingScreenStep.CONNECTED
    } else {
        current
    }

internal fun canStartG7Pairing(
    step: G7PairingScreenStep?,
    inFlight: Boolean,
    code: String,
): Boolean = step == G7PairingScreenStep.ENTER_CODE && !inFlight && code.length == 4 && code.all(Char::isDigit)

internal fun shouldScheduleG7PairingCompletion(
    step: G7PairingScreenStep?,
    alreadyScheduled: Boolean,
): Boolean = step == G7PairingScreenStep.CONNECTED && !alreadyScheduled

@Suppress("UNUSED_PARAMETER")
internal fun isTerminalG7PairingFailure(
    state: G7PersistedState,
    pairingStartedAtEpochMs: Long,
    nowEpochMs: Long,
): Boolean {
    val error = state.lastError?.takeIf { it.occurredAtEpochMs >= pairingStartedAtEpochMs }
    return error?.code == "G7-AUTH-204" || error?.recoverable == false
}
