package app.aapswear.g7watch

import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7CollectorRuntimeState
import app.aapswear.g7.G7FailureClass
import app.aapswear.g7.G7RecoveryStage
import app.aapswear.g7.G7SensorAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7CollectorReliabilityTest {
    @Test fun `repeated no callback with working radio escalates to possible other collector`() {
        var health = G7CollectorHealth()
        repeat(3) { index ->
            health =
                G7CollectorReliability.failed(
                    health,
                    G7FailureClass.DIRECT_NO_CALLBACK,
                    sensorAdvertisementSeen = false,
                    foreignAdvertisementsSeen = true,
                    now = index * 300_000L,
                )
        }
        assertEquals(G7SensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR, health.sensorAvailability)
        assertEquals(G7RecoveryStage.WAIT_NEXT_SENSOR_WINDOW, health.recoveryStage)
    }

    @Test fun `long outage reduces scans but every state retains a timed retry`() {
        var health = G7CollectorHealth()
        val scanDecisions = mutableListOf<Boolean>()
        repeat(48 * 12) { slot ->
            health =
                G7CollectorReliability.failed(
                    health,
                    G7FailureClass.DIRECT_NO_CALLBACK,
                    sensorAdvertisementSeen = false,
                    foreignAdvertisementsSeen = true,
                    now = slot * 300_000L,
                )
            scanDecisions += G7CollectorReliability.shouldRunPresenceScan(health)
        }
        assertEquals(G7CollectorRuntimeState.DEGRADED, health.runtime)
        assertEquals(G7RecoveryStage.RECOVERY_ESCALATED, health.recoveryStage)
        assertTrue(scanDecisions.take(6).any { it })
        assertTrue(scanDecisions.drop(6).any { it })
        assertTrue(scanDecisions.drop(6).any { !it })
    }

    @Test fun `first technically successful contact resets all recovery history`() {
        var health = G7CollectorHealth()
        repeat(38) { health = G7CollectorReliability.failed(health, G7FailureClass.DIRECT_NO_CALLBACK, false, true, it * 300_000L) }

        health = G7CollectorReliability.succeeded(health, 12_000_000L, 12_001_000L)

        assertEquals(0, health.consecutiveFailures)
        assertEquals(G7RecoveryStage.NORMAL, health.recoveryStage)
        assertEquals(G7SensorAvailability.AVAILABLE, health.sensorAvailability)
        assertTrue(G7CollectorReliability.shouldRunPresenceScan(health))
    }

    @Test fun `escalated scan schedule is bounded over seven simulated days`() {
        var health = G7CollectorHealth()
        var scans = 0
        repeat(7 * 24 * 12) { slot ->
            health = G7CollectorReliability.failed(health, G7FailureClass.DIRECT_NO_CALLBACK, false, true, slot * 300_000L)
            if (G7CollectorReliability.shouldRunPresenceScan(health)) scans++
        }
        assertTrue(scans > 0)
        assertTrue(scans < 7 * 24 * 12 / 2)
        assertFalse(health.consecutiveFailures == 0)
    }
}
