package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmCollectorHealth
import app.sugarlicious.sensor.protocol.CgmFailureClass
import app.sugarlicious.sensor.protocol.CgmGapRecoveryState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmRecoveryStage
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorAvailability
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmCollectorRecoveryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context
            .getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmSensorStateStore(context).save(CgmPersistedState(sensor = CgmSensor("sensor", "session"), collectorEnabled = true))
    }

    @Test fun `restoring half a second before scan lead never skips the known next sensor window`() {
        val now = System.currentTimeMillis()
        val expected = now + 30_500L
        CgmReconnectStrategyStore.write(context, CgmReconnectStrategy.BOUNDED_SCAN)
        try {
            val cycle =
                CgmReconnectAlarmScheduler.rearmScheduledCycle(
                    context,
                    CollectorCycleTiming(expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 30_000L),
                    CgmSensorStateStore(context).read(),
                )
            assertEquals(expected, cycle.expectedReadingEpoch)
            assertTrue(requireNotNull(cycle.requestedReconnectEpoch) >= now + 1_000L)
            assertTrue(requireNotNull(cycle.requestedReconnectEpoch) < expected)
        } finally {
            CgmReconnectStrategyStore.write(context, CgmReconnectStrategy.KNOWN_ADDRESS_DIRECT)
        }
    }

    @Test fun `real hardware failure sequence self heals and closes every available gap`() {
        val ledger = CgmExpectedWindowLedger(context)
        val gaps =
            listOf(300_000L, 600_000L, 900_000L).map { expected ->
                ledger.create(expected, expected - 10_000L).also {
                    ledger.markFinal(it.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
                }
            }
        var health = CgmCollectorHealth()
        health = CgmCollectorReliability.failed(health, CgmFailureClass.DIRECT_GATT_133, false, false, 310_000L)
        health = CgmCollectorReliability.failed(health, CgmFailureClass.DIRECT_NO_CALLBACK, false, false, 610_000L)
        repeat(
            6,
        ) { health = CgmCollectorReliability.failed(health, CgmFailureClass.DIRECT_NO_CALLBACK, false, true, 910_000L + it * 300_000L) }
        assertEquals(CgmSensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR, health.sensorAvailability)
        assertEquals(CgmRecoveryStage.RECOVERY_ESCALATED, health.recoveryStage)

        val rehydrated =
            CgmCollectorReliability.rehydrate(
                CgmPersistedState(sensor = CgmSensor("sensor", "session"), collectorEnabled = true, health = health),
                3_000_000L,
            )
        health = CgmCollectorReliability.succeeded(rehydrated.health, 3_000_000L, 3_001_000L)
        ledger.markRecoveryRequestStarted("sensor", "session", 3_000_000L, 3_001_100L)
        ledger.markNextLiveAndBackfill(
            "sensor",
            "session",
            3_000_000L,
            3_001_000L,
            3_002_000L,
            3_001_100L,
            3_001_500L,
            gaps.map { it.expectedAt },
        )

        assertEquals(CgmRecoveryStage.NORMAL, health.recoveryStage)
        assertEquals(0, health.consecutiveFailures)
        assertNull(ledger.oldestOpenGap("sensor", "session"))
        assertEquals(3, ledger.snapshot().count { it.gapRecoveryState == CgmGapRecoveryState.RECOVERED })
    }

    @Test fun `seeded 24h 48h and seven day simulations always recover on next possible contact`() {
        listOf(24, 48, 7 * 24).forEach { hours ->
            var state = CgmPersistedState(sensor = CgmSensor("sensor", "session"), collectorEnabled = true)
            repeat(hours * 12) { slot ->
                val now = slot * 300_000L
                state =
                    if (slot % 29 == 28) {
                        state.copy(health = CgmCollectorReliability.succeeded(state.health, now, now + 1_000L))
                    } else {
                        val failure =
                            when (slot % 5) {
                                0 -> CgmFailureClass.DIRECT_GATT_133
                                1 -> CgmFailureClass.PROCESS_INTERRUPTED
                                else -> CgmFailureClass.DIRECT_NO_CALLBACK
                            }
                        state.copy(health = CgmCollectorReliability.failed(state.health, failure, false, slot % 3 != 0, now))
                    }
                if (slot % 53 == 0) state = CgmCollectorReliability.rehydrate(state, now)
                if (slot % 29 == 28) {
                    assertEquals(CgmRecoveryStage.NORMAL, state.health.recoveryStage)
                    assertEquals(0, state.health.consecutiveFailures)
                }
            }
        }
    }
}
