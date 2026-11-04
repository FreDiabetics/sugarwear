package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorHealth
import app.sugarlicious.sensor.protocol.CgmCollectorRuntimeState
import app.sugarlicious.sensor.protocol.CgmFailureClass
import app.sugarlicious.sensor.protocol.CgmRecoveryStage
import app.sugarlicious.sensor.protocol.CgmSensorAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmCollectorReliabilityTest {
    @Test fun `repeated no callback with working radio escalates to possible other collector`() {
        var health = CgmCollectorHealth()
        repeat(3) { index ->
            health =
                CgmCollectorReliability.failed(
                    health,
                    CgmFailureClass.DIRECT_NO_CALLBACK,
                    sensorAdvertisementSeen = false,
                    foreignAdvertisementsSeen = true,
                    now = index * 300_000L,
                )
        }
        assertEquals(CgmSensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR, health.sensorAvailability)
        assertEquals(CgmRecoveryStage.WAIT_NEXT_SENSOR_WINDOW, health.recoveryStage)
    }

    @Test fun `long outage reduces scans but every state retains a timed retry`() {
        var health = CgmCollectorHealth()
        val scanDecisions = mutableListOf<Boolean>()
        repeat(48 * 12) { slot ->
            health =
                CgmCollectorReliability.failed(
                    health,
                    CgmFailureClass.DIRECT_NO_CALLBACK,
                    sensorAdvertisementSeen = false,
                    foreignAdvertisementsSeen = true,
                    now = slot * 300_000L,
                )
            scanDecisions += CgmCollectorReliability.shouldRunPresenceScan(health)
        }
        assertEquals(CgmCollectorRuntimeState.DEGRADED, health.runtime)
        assertEquals(CgmRecoveryStage.RECOVERY_ESCALATED, health.recoveryStage)
        assertTrue(scanDecisions.take(6).any { it })
        assertTrue(scanDecisions.drop(6).any { it })
        assertTrue(scanDecisions.drop(6).any { !it })
    }

    @Test fun `first technically successful contact resets all recovery history`() {
        var health = CgmCollectorHealth()
        repeat(38) { health = CgmCollectorReliability.failed(health, CgmFailureClass.DIRECT_NO_CALLBACK, false, true, it * 300_000L) }

        health = CgmCollectorReliability.succeeded(health, 12_000_000L, 12_001_000L)

        assertEquals(0, health.consecutiveFailures)
        assertEquals(CgmRecoveryStage.NORMAL, health.recoveryStage)
        assertEquals(CgmSensorAvailability.AVAILABLE, health.sensorAvailability)
        assertTrue(CgmCollectorReliability.shouldRunPresenceScan(health))
    }

    @Test fun `escalated scan schedule is bounded over seven simulated days`() {
        var health = CgmCollectorHealth()
        var scans = 0
        repeat(7 * 24 * 12) { slot ->
            health = CgmCollectorReliability.failed(health, CgmFailureClass.DIRECT_NO_CALLBACK, false, true, slot * 300_000L)
            if (CgmCollectorReliability.shouldRunPresenceScan(health)) scans++
        }
        assertTrue(scans > 0)
        assertTrue(scans < 7 * 24 * 12 / 2)
        assertFalse(health.consecutiveFailures == 0)
    }
}
