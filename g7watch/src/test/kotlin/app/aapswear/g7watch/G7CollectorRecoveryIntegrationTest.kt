package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7FailureClass
import app.aapswear.g7.G7GapRecoveryState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7RecoveryStage
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SensorAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7CollectorRecoveryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context
            .getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        G7SensorStateStore(context).save(G7PersistedState(sensor = G7Sensor("sensor", "session"), collectorEnabled = true))
    }

    @Test fun `restoring half a second before scan lead never skips the known next sensor window`() {
        val now = System.currentTimeMillis()
        val expected = now + 30_500L
        G7ReconnectStrategyStore.write(context, G7ReconnectStrategy.BOUNDED_SCAN)
        try {
            val cycle =
                G7ReconnectAlarmScheduler.rearmScheduledCycle(
                    context,
                    CollectorCycleTiming(expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 30_000L),
                    G7SensorStateStore(context).read(),
                )
            assertEquals(expected, cycle.expectedReadingEpoch)
            assertTrue(requireNotNull(cycle.requestedReconnectEpoch) >= now + 1_000L)
            assertTrue(requireNotNull(cycle.requestedReconnectEpoch) < expected)
        } finally {
            G7ReconnectStrategyStore.write(context, G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT)
        }
    }

    @Test fun `real hardware failure sequence self heals and closes every available gap`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gaps =
            listOf(300_000L, 600_000L, 900_000L).map { expected ->
                ledger.create(expected, expected - 10_000L).also {
                    ledger.markFinal(it.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
                }
            }
        var health = G7CollectorHealth()
        health = G7CollectorReliability.failed(health, G7FailureClass.DIRECT_GATT_133, false, false, 310_000L)
        health = G7CollectorReliability.failed(health, G7FailureClass.DIRECT_NO_CALLBACK, false, false, 610_000L)
        repeat(
            6,
        ) { health = G7CollectorReliability.failed(health, G7FailureClass.DIRECT_NO_CALLBACK, false, true, 910_000L + it * 300_000L) }
        assertEquals(G7SensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR, health.sensorAvailability)
        assertEquals(G7RecoveryStage.RECOVERY_ESCALATED, health.recoveryStage)

        val rehydrated =
            G7CollectorReliability.rehydrate(
                G7PersistedState(sensor = G7Sensor("sensor", "session"), collectorEnabled = true, health = health),
                3_000_000L,
            )
        health = G7CollectorReliability.succeeded(rehydrated.health, 3_000_000L, 3_001_000L)
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

        assertEquals(G7RecoveryStage.NORMAL, health.recoveryStage)
        assertEquals(0, health.consecutiveFailures)
        assertNull(ledger.oldestOpenGap("sensor", "session"))
        assertEquals(3, ledger.snapshot().count { it.gapRecoveryState == G7GapRecoveryState.RECOVERED })
    }

    @Test fun `seeded 24h 48h and seven day simulations always recover on next possible contact`() {
        listOf(24, 48, 7 * 24).forEach { hours ->
            var state = G7PersistedState(sensor = G7Sensor("sensor", "session"), collectorEnabled = true)
            repeat(hours * 12) { slot ->
                val now = slot * 300_000L
                state =
                    if (slot % 29 == 28) {
                        state.copy(health = G7CollectorReliability.succeeded(state.health, now, now + 1_000L))
                    } else {
                        val failure =
                            when (slot % 5) {
                                0 -> G7FailureClass.DIRECT_GATT_133
                                1 -> G7FailureClass.PROCESS_INTERRUPTED
                                else -> G7FailureClass.DIRECT_NO_CALLBACK
                            }
                        state.copy(health = G7CollectorReliability.failed(state.health, failure, false, slot % 3 != 0, now))
                    }
                if (slot % 53 == 0) state = G7CollectorReliability.rehydrate(state, now)
                if (slot % 29 == 28) {
                    assertEquals(G7RecoveryStage.NORMAL, state.health.recoveryStage)
                    assertEquals(0, state.health.consecutiveFailures)
                }
            }
        }
    }
}
