package app.sugarlicious.sensor.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CgmLifecycleStateTest {
    private val now = 1_800_000_000_000L

    @Test fun `recoverable technical disconnect keeps user enable state`() {
        val manager =
            CgmSessionManager(
                CgmPersistedState(
                    sensor = CgmSensor("sensor"),
                    collectorEnabled = true,
                ),
            )

        val failed =
            manager.failure(
                CgmCollectorError(
                    code = "G7-BLE-111",
                    recoverable = true,
                    occurredAtEpochMs = now,
                    safeMessage = "Sensor temporarily unavailable",
                ),
            )

        assertTrue(failed.collectorEnabled)
    }

    @Test fun `non recoverable technical error still does not become user disable`() {
        val manager =
            CgmSessionManager(
                CgmPersistedState(
                    sensor = CgmSensor("sensor"),
                    collectorEnabled = true,
                ),
            )

        val failed =
            manager.failure(
                CgmCollectorError(
                    code = "G7-PERM-401",
                    recoverable = false,
                    occurredAtEpochMs = now,
                    safeMessage = "Permission missing",
                ),
            )

        assertTrue(failed.collectorEnabled)
    }

    @Test fun `only explicit user stop disables collector`() {
        val manager =
            CgmSessionManager(
                CgmPersistedState(
                    sensor = CgmSensor("sensor"),
                    collectorEnabled = true,
                ),
            )

        val stopped = manager.stop()
        assertFalse(stopped.collectorEnabled)
        assertNull(stopped.pairingAttemptId)
        assertNull(stopped.pairingStartedAtEpochMs)
        assertNull(stopped.pairingDeadlineEpochMs)
    }
}
