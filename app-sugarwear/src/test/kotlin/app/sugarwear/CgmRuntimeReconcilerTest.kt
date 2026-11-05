package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CollectorDiagnosticAttempt
import app.sugarlicious.sensor.protocol.CollectorDiagnosticStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmRuntimeReconcilerTest {
    private val now = 23L * 60L * 60_000L + 41L * 60_000L
    private val configuredState =
        CgmPersistedState(
            sensor = CgmSensor("sensor", "session", deviceAddress = "AA:BB:CC:DD:EE:FF"),
            collectorEnabled = true,
        )

    @Test fun `live bounded discovery survives two minutes without an advertisement`() {
        val started = now - 3 * 60_000L
        val deadline = started + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS
        val state = configuredState.copy(connectionState = CgmConnectionState.SCANNING, protocolState = CgmProtocolState.SCANNING, activeAttemptId = 11L, scanStartedAtEpochMs = started, scanTimeoutAtEpochMs = deadline)
        val attempt = CollectorDiagnosticAttempt(attemptId = 11L, startedAtEpochMs = started, lastProgressAtEpochMs = started, currentStage = CollectorDiagnosticStage.SCANNING, deadlineEpochMs = deadline + 120_000L)
        assertEquals(CgmRuntimeHealth.HEALTHY_ACTIVE, assessCgmRuntime(state, attempt, now + 290_000L, true, true, now).health)
    }

    @Test fun `scan budget never hides dead processes expired scans or stalled connections`() {
        val started = now - 3 * 60_000L
        val deadline = started + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS
        val state = configuredState.copy(connectionState = CgmConnectionState.SCANNING, protocolState = CgmProtocolState.SCANNING, activeAttemptId = 11L, scanStartedAtEpochMs = started, scanTimeoutAtEpochMs = deadline)
        val attempt = CollectorDiagnosticAttempt(attemptId = 11L, startedAtEpochMs = started, lastProgressAtEpochMs = started, currentStage = CollectorDiagnosticStage.SCANNING, deadlineEpochMs = deadline + 120_000L)
        val invalidCases =
            listOf(
                state to false,
                state.copy(scanTimeoutAtEpochMs = now) to true,
                state.copy(scanTimeoutAtEpochMs = deadline + 1L) to true,
                state.copy(scanTimeoutAtEpochMs = null) to true,
                state.copy(activeAttemptId = 12L) to true,
                state.copy(connectionState = CgmConnectionState.CONNECTING, protocolState = CgmProtocolState.CONNECTING) to true,
            )
        for ((candidate, live) in invalidCases) {
            val result = assessCgmRuntime(candidate, attempt, now + 290_000L, live, true, now)
            assertEquals(CgmRuntimeHealth.STALE_ATTEMPT, result.health)
            assertTrue(result.cleanupRequired)
        }
        assertEquals(CgmRuntimeHealth.STALE_ATTEMPT, assessCgmRuntime(state, attempt.copy(deadlineEpochMs = now), now + 290_000L, true, true, now).health)
        assertEquals(CgmRuntimeHealth.RECOVERY_INVARIANT_BROKEN, assessCgmRuntime(state, attempt, null, true, true, now).health)
    }

    @Test fun `276 minute outage state is stale and must be cleaned`() {
        val scanStarted = 18L * 60L * 60_000L + 20L * 60_000L
        val state =
            configuredState.copy(
                connectionState = CgmConnectionState.CONNECTING,
                protocolState = CgmProtocolState.SCANNING,
                activeAttemptId = 1_214L,
                scanStartedAtEpochMs = scanStarted,
                nextReconnectEpochMs = 19L * 60L * 60_000L + 15L * 60_000L,
            )
        val attempt =
            CollectorDiagnosticAttempt(
                attemptId = 1_214L,
                startedAtEpochMs = scanStarted,
                lastProgressAtEpochMs = scanStarted,
                currentStage = CollectorDiagnosticStage.SCANNING,
                deadlineEpochMs = scanStarted + 3L * 60_000L,
            )

        val result = assessCgmRuntime(state, attempt, null, false, true, now)

        assertEquals(CgmRuntimeHealth.STALE_ATTEMPT, result.health)
        assertTrue(result.cleanupRequired)
        assertEquals(now - scanStarted, result.attemptAgeMs)
    }

    @Test fun `process death never treats persisted scan as live`() {
        val started = now - 30_000L
        val attempt =
            CollectorDiagnosticAttempt(
                attemptId = 4L,
                startedAtEpochMs = started,
                lastProgressAtEpochMs = started,
                currentStage = CollectorDiagnosticStage.SCANNING,
                deadlineEpochMs = now + 120_000L,
            )
        val state =
            configuredState.copy(
                connectionState = CgmConnectionState.SCANNING,
                protocolState = CgmProtocolState.SCANNING,
                activeAttemptId = 4L,
            )

        val result = assessCgmRuntime(state, attempt, now + 290_000L, false, true, now)

        assertEquals(CgmRuntimeHealth.ORPHANED_STATE, result.health)
        assertTrue(result.cleanupRequired)
    }

    @Test fun `enabled collector without cycle or alarm breaks recovery invariant`() {
        assertEquals(
            CgmRuntimeHealth.RECOVERY_REQUIRED,
            assessCgmRuntime(configuredState, null, null, false, true, now).health,
        )
    }

    @Test fun `an armed alarm remains valid during its final thirty seconds`() {
        val attempt = CollectorDiagnosticAttempt(attemptId = 8L, startedAtEpochMs = now - 1_000L, lastProgressAtEpochMs = now, deadlineEpochMs = now + 120_000L)
        for (lead in listOf(1L, 10_000L, CGM_RUNTIME_RECONNECT_TOLERANCE_MS)) {
            assertEquals(CgmRuntimeHealth.HEALTHY_ARMED, assessCgmRuntime(configuredState, null, now + lead, false, true, now).health)
            assertEquals(CgmRuntimeHealth.HEALTHY_ACTIVE, assessCgmRuntime(configuredState.copy(activeAttemptId = 8L), attempt, now + lead, true, true, now).health)
        }
    }

    @Test fun `past reconnect is detected instead of being awaited`() {
        assertEquals(
            CgmRuntimeHealth.MISSED_RECONNECT,
            assessCgmRuntime(
                configuredState.copy(nextReconnectEpochMs = now - 4L * 60L * 60_000L),
                null,
                null,
                false,
                true,
                now,
            ).health,
        )
    }

    @Test fun `future timestamp without armed alarm evidence is repaired`() {
        assertEquals(
            CgmRuntimeHealth.RECOVERY_REQUIRED,
            assessCgmRuntime(
                configuredState.copy(nextReconnectEpochMs = now + 290_000L),
                null,
                null,
                false,
                true,
                now,
            ).health,
        )
    }

    @Test fun `valid live cycle still requires a future safety alarm`() {
        val attempt =
            CollectorDiagnosticAttempt(
                attemptId = 8L,
                startedAtEpochMs = now - 10_000L,
                lastProgressAtEpochMs = now - 1_000L,
                deadlineEpochMs = now + 120_000L,
            )
        assertEquals(
            CgmRuntimeHealth.RECOVERY_INVARIANT_BROKEN,
            assessCgmRuntime(configuredState.copy(activeAttemptId = 8L), attempt, null, true, true, now).health,
        )
        assertEquals(
            CgmRuntimeHealth.HEALTHY_ACTIVE,
            assessCgmRuntime(configuredState.copy(activeAttemptId = 8L), attempt, now + 290_000L, true, true, now).health,
        )
    }

    @Test fun `simultaneous automatic triggers coalesce to one cycle`() {
        assertTrue(shouldCoalesceCgmCollectorTrigger(automatic = true, activeCycle = true))
        assertEquals(false, shouldCoalesceCgmCollectorTrigger(automatic = true, activeCycle = false))
        assertEquals(false, shouldCoalesceCgmCollectorTrigger(automatic = false, activeCycle = true))
    }
}
