package app.sugarlicious.sensor.protocol

import kotlin.math.min

data class CgmReconnectPlan(
    val nextReconnectEpochMs: Long,
    val retryCount: Int,
    val delayMs: Long,
)

object CgmReconnectScheduler {
    const val EXPECTED_READING_INTERVAL_MS = 5 * 60_000L
    const val PRECONNECT_LEAD_MS = 30_000L
    private const val MAX_BACKOFF_MS = 15 * 60_000L
    private const val GATT_133_INITIAL_RETRY_MS = 15_000L
    private const val MIN_WINDOW_LEAD_MS = 1_000L

    fun afterReading(readingEpochMs: Long): CgmReconnectPlan =
        CgmReconnectPlan(
            readingEpochMs + EXPECTED_READING_INTERVAL_MS - PRECONNECT_LEAD_MS,
            0,
            EXPECTED_READING_INTERVAL_MS - PRECONNECT_LEAD_MS,
        )

    fun afterFailure(
        nowEpochMs: Long,
        retryCount: Int,
    ): CgmReconnectPlan {
        val count = (retryCount + 1).coerceAtMost(10)
        val delay = min(15_000L * (1L shl count.coerceAtMost(6)), MAX_BACKOFF_MS)
        return CgmReconnectPlan(nowEpochMs + delay, count, delay)
    }

    /**
     * GATT 133 and a missed advertisement window are transient BLE/cadence failures, not an
     * authentication/session failure. After bounded in-window work is exhausted, wait for the next
     * expected Manufacturer advertising window instead of spinning generic exponential retries between
     * the sensor's five-minute transmit opportunities.
     */
    fun afterExpectedWindowMiss(
        nowEpochMs: Long,
        lastReadingEpochMs: Long?,
    ): CgmReconnectPlan {
        if (lastReadingEpochMs == null) {
            return CgmReconnectPlan(nowEpochMs + GATT_133_INITIAL_RETRY_MS, 0, GATT_133_INITIAL_RETRY_MS)
        }
        val firstWindow = lastReadingEpochMs + EXPECTED_READING_INTERVAL_MS - PRECONNECT_LEAD_MS
        val minimumTrigger = nowEpochMs + MIN_WINDOW_LEAD_MS
        val trigger =
            if (firstWindow > minimumTrigger) {
                firstWindow
            } else {
                val elapsed = minimumTrigger - firstWindow
                val cycles = elapsed / EXPECTED_READING_INTERVAL_MS + 1L
                firstWindow + cycles * EXPECTED_READING_INTERVAL_MS
            }
        return CgmReconnectPlan(trigger, 0, trigger - nowEpochMs)
    }

    fun afterGatt133(
        nowEpochMs: Long,
        lastReadingEpochMs: Long?,
    ): CgmReconnectPlan = afterExpectedWindowMiss(nowEpochMs, lastReadingEpochMs)
}

object CgmRecoveryChain {
    val steps = CgmRecoveryStep.entries

    fun stepForFailure(failureCount: Int): CgmRecoveryStep = steps[failureCount.coerceIn(0, steps.lastIndex)]
}

class CgmSessionManager(
    initial: CgmPersistedState = CgmPersistedState(),
) {
    var state: CgmPersistedState = initial
        private set

    /**
     * Stores a newly entered sensor without enabling BLE collection. Sensor setup and collector
     * activation are deliberately separate user actions so merely entering/syncing a code never
     * starts an active scan.
     */
    fun prepareInitialSetup(sensor: CgmSensor): CgmPersistedState =
        transition(
            resetForSensor(sensor).copy(
                collectorEnabled = false,
                connectionState = CgmConnectionState.DISCONNECTED,
                protocolState = CgmProtocolState.IDLE,
                sessionState = CgmSessionState.UNINITIALIZED,
            ),
        )

    fun beginInitialSetup(sensor: CgmSensor): CgmPersistedState =
        transition(
            resetForSensor(sensor).copy(
                collectorEnabled = true,
                connectionState = CgmConnectionState.SCANNING,
                protocolState = CgmProtocolState.SCANNING,
                sessionState = CgmSessionState.INITIAL_SETUP,
            ),
        )

    /** Enables an already configured collector only after an explicit user/source-selection start. */
    fun startCollector(): CgmPersistedState {
        if (state.sensor == null) return state
        if (state.collectorEnabled) return state
        return transition(
            state.copy(
                collectorEnabled = true,
                collectorOwner = CollectorOwner.WATCH,
                connectionState = CgmConnectionState.DISCONNECTED,
                protocolState = CgmProtocolState.IDLE,
                sessionState = if (state.lastReading == null) CgmSessionState.INITIAL_SETUP else CgmSessionState.READY_FOR_RECONNECT,
                nextReconnectEpochMs = null,
                retryCount = 0,
                lastError = null,
            ),
        )
    }

    fun authenticationStarted(reconnect: Boolean): CgmPersistedState =
        transition(
            state.copy(
                sessionState = if (reconnect) CgmSessionState.REAUTHENTICATING else CgmSessionState.AUTHENTICATING,
                authenticationState = CgmAuthenticationState.AUTHENTICATING,
                protocolState = CgmProtocolState.AUTHENTICATING,
            ),
        )

    fun authenticationSucceeded(): CgmPersistedState =
        transition(
            state.copy(sessionState = CgmSessionState.AUTHENTICATED, authenticationState = CgmAuthenticationState.AUTHENTICATED),
        )

    /**
     * A packet may arrive now while describing an older sensor measurement. Only a fresh valid
     * packet advances the displayable current reading and clears the fresh-slot cadence. Aged valid
     * packets remain diagnostic/history data and schedule the next attempt from the prior fresh
     * cadence rather than pretending the missed current slot succeeded.
     */
    fun readingReceived(
        reading: CgmReading,
        fresh: Boolean = true,
        nowEpochMs: Long = reading.receivedAtEpochMs,
    ): CgmPersistedState {
        val acceptedFresh = fresh && reading.status == CgmReadingStatus.VALID
        val plan =
            if (acceptedFresh) {
                CgmReconnectScheduler.afterReading(reading.timestampEpochMs)
            } else {
                CgmReconnectScheduler.afterExpectedWindowMiss(nowEpochMs, state.lastReading?.timestampEpochMs)
            }
        return transition(
            state.copy(
                lastReading = if (acceptedFresh) reading else state.lastReading,
                sessionState = CgmSessionState.WAITING_FOR_NEXT_READING,
                protocolState = CgmProtocolState.RECEIVING_GLUCOSE,
                nextReconnectEpochMs = plan.nextReconnectEpochMs,
                retryCount = if (acceptedFresh) 0 else state.retryCount,
                lastError = if (acceptedFresh) null else state.lastError,
            ),
        )
    }

    fun failure(error: CgmCollectorError): CgmPersistedState {
        if (!error.recoverable) {
            return transition(
                state.copy(
                    sessionState = CgmSessionState.USER_INTERVENTION_REQUIRED,
                    nextReconnectEpochMs = null,
                    lastError = error,
                ),
            )
        }
        if (error.code in EXPECTED_WINDOW_ERROR_CODES) {
            val plan = CgmReconnectScheduler.afterExpectedWindowMiss(error.occurredAtEpochMs, state.lastReading?.timestampEpochMs)
            return transition(
                state.copy(
                    sessionState = CgmSessionState.RECOVERING,
                    connectionState = CgmConnectionState.DISCONNECTED,
                    protocolState = CgmProtocolState.RECOVERING,
                    retryCount = 0,
                    nextReconnectEpochMs = plan.nextReconnectEpochMs,
                    lastError = error,
                ),
            )
        }
        val step = CgmRecoveryChain.stepForFailure(state.retryCount)
        val plan = CgmReconnectScheduler.afterFailure(error.occurredAtEpochMs, state.retryCount)
        val session =
            when (step) {
                CgmRecoveryStep.SESSION_REAUTH -> CgmSessionState.REAUTHENTICATING
                CgmRecoveryStep.SESSION_RESET -> CgmSessionState.REQUIRES_FULL_HANDSHAKE
                CgmRecoveryStep.REBOND -> CgmSessionState.REQUIRES_REBOND
                CgmRecoveryStep.FULL_HANDSHAKE -> CgmSessionState.REQUIRES_FULL_HANDSHAKE
                CgmRecoveryStep.USER_INTERVENTION_REQUIRED -> CgmSessionState.USER_INTERVENTION_REQUIRED
                else -> CgmSessionState.RECOVERING
            }
        return transition(
            state.copy(
                sessionState = session,
                authenticationState = if (error.code.startsWith("AUTH")) CgmAuthenticationState.FAILED else state.authenticationState,
                retryCount = plan.retryCount,
                nextReconnectEpochMs = plan.nextReconnectEpochMs,
                lastError = error,
            ),
        )
    }

    fun stop(): CgmPersistedState =
        transition(
            state.copy(
                collectorEnabled = false,
                sessionState = CgmSessionState.UNINITIALIZED,
                connectionState = CgmConnectionState.DISCONNECTED,
                protocolState = CgmProtocolState.IDLE,
                nextReconnectEpochMs = null,
                retryCount = 0,
                activeAttemptId = null,
                scanStartedAtEpochMs = null,
                scanTimeoutAtEpochMs = null,
                pairingAttemptId = null,
                pairingStartedAtEpochMs = null,
                pairingDeadlineEpochMs = null,
            ),
        )

    private fun resetForSensor(sensor: CgmSensor): CgmPersistedState =
        state.copy(
            sensor = sensor,
            collectorOwner = CollectorOwner.WATCH,
            authenticationState = CgmAuthenticationState.REQUIRED,
            lastReading = null,
            lastSuccessfulConnectionEpochMs = null,
            nextReconnectEpochMs = null,
            retryCount = 0,
            lastError = null,
        )

    private fun transition(next: CgmPersistedState): CgmPersistedState = next.also { state = it }

    private companion object {
        val EXPECTED_WINDOW_ERROR_CODES =
            setOf(
                "G7-GATT-133",
                "G7-GATT-215",
                "G7-BLE-107",
                "G7-BLE-111",
                "G7-BLE-FALLBACK-107",
            )
    }
}

class CollectorOwnershipManager(
    initial: CollectorOwner = CollectorOwner.UNKNOWN,
) {
    var owner: CollectorOwner = initial
        private set

    fun requestWatch(): CollectorOwner = CollectorOwner.TRANSITION_TO_WATCH.also { owner = it }

    fun confirmWatchAfterValidReading(): CollectorOwner = CollectorOwner.WATCH.also { owner = it }

    fun requestPhone(): CollectorOwner = CollectorOwner.TRANSITION_TO_PHONE.also { owner = it }

    fun confirmPhone(): CollectorOwner = CollectorOwner.PHONE.also { owner = it }

    fun phoneDisconnected(): CollectorOwner = owner
}
