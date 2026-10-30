package app.aapswear.g7watch

import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorExpectedWindow
import app.aapswear.g7.CollectorWindowTerminalState
import app.aapswear.g7.DirectConnectResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class G7SensorWindowWatchdogTest {
    @After fun resetGattOwnership() = G7GattGenerationRegistry.resetForTest()

    @Test fun `10 35 success with missing 10 40 primary is recovered before 10 45`() {
        val tenForty = 10L * 60L * 60_000L + 40L * 60_000L
        val fired = tenForty + G7SensorWindowWatchdog.WINDOW_TOLERANCE_MS + 1L

        val decision =
            evaluateG7WindowWatchdog(
                expectedAt = tenForty,
                nowEpochMs = fired,
                primaryTriggeredAt = null,
                cycleStartedAt = null,
                readingReceivedAt = null,
                finalResult = null,
                activeCycle = false,
                plausibleFutureTriggerEpochMs = null,
            )

        assertTrue(decision.missed)
        assertTrue(fired < 10L * 60L * 60_000L + 45L * 60_000L)
        assertEquals("MISSED_SENSOR_WINDOW", decision.reason)
    }

    @Test fun `direct connect no callback timeout is hard bounded inside one sensor window`() {
        assertEquals(20_000L, G7_DIRECT_CONNECT_CALLBACK_TIMEOUT_MS)
        assertTrue(G7_DIRECT_CONNECT_CALLBACK_TIMEOUT_MS < 5L * 60_000L)
    }

    @Test fun `watchdog coalesces with a live cycle`() {
        val expected = 1_000_000L
        val decision =
            evaluateG7WindowWatchdog(
                expected,
                expected + 60_000L,
                null,
                null,
                null,
                null,
                true,
                null,
            )
        assertFalse(decision.missed)
        assertEquals("CYCLE_ACTIVE", decision.reason)
    }

    @Test fun `delivered primary without service cycle is recovered`() {
        val expected = 1_000_000L
        val decision =
            evaluateG7WindowWatchdog(
                expected,
                expected + 60_000L,
                expected - 10_000L,
                null,
                null,
                null,
                false,
                null,
            )
        assertTrue(decision.missed)
        assertEquals("SERVICE_START_FAILED", decision.reason)
    }

    @Test fun `process death after cycle start is not mistaken for a live cycle`() {
        val expected = 1_000_000L
        val decision =
            evaluateG7WindowWatchdog(
                expected,
                expected + 60_000L,
                expected - 10_000L,
                expected - 8_000L,
                null,
                null,
                false,
                null,
            )
        assertTrue(decision.missed)
        assertEquals("STALE_CYCLE_WITHOUT_RUNTIME", decision.reason)
    }

    @Test fun `completed window is never recovered twice`() {
        val expected = 1_000_000L
        val decision =
            evaluateG7WindowWatchdog(
                expected,
                expected + 60_000L,
                expected - 10_000L,
                expected - 8_000L,
                expected + 3_000L,
                CollectorCycleClassification.SUCCESS_FRESH,
                false,
                null,
            )
        assertFalse(decision.missed)
        assertEquals("WINDOW_COMPLETED", decision.reason)
    }

    @Test fun `new GATT generation invalidates callbacks owned by the previous generation`() {
        val first = G7GattGenerationRegistry.acquire(41L)
        val second = G7GattGenerationRegistry.acquire(41L)

        assertFalse(G7GattGenerationRegistry.isActive(first))
        assertTrue(G7GattGenerationRegistry.isActive(second))
        assertTrue(second.generation > first.generation)
        G7GattGenerationRegistry.invalidate(first)
        assertTrue(G7GattGenerationRegistry.isActive(second))
    }

    @Test fun `hardware metrics distinguish first retry 133 timeout and missed windows`() {
        val windows =
            listOf(
                CollectorExpectedWindow(
                    "one",
                    1_000L,
                    cycleStartedAt = 900L,
                    gattAttempts = 1,
                    gattResult = DirectConnectResult.SUCCESS,
                    readingReceivedAt = 1_100L,
                    finalResult = CollectorCycleClassification.SUCCESS_FRESH,
                ),
                CollectorExpectedWindow(
                    "two",
                    301_000L,
                    cycleStartedAt = 300_000L,
                    gattAttempts = 2,
                    gatt133Count = 1,
                    gattResult = DirectConnectResult.SUCCESS,
                    readingReceivedAt = 301_250L,
                    finalResult = CollectorCycleClassification.SUCCESS_FRESH,
                ),
                CollectorExpectedWindow(
                    "three",
                    601_000L,
                    noCallbackCount = 1,
                    finalResult = CollectorCycleClassification.MISSED_SENSOR_WINDOW,
                    recoveryRequired = true,
                ),
            )

        val metrics = calculateG7HardwareMetrics(windows)

        assertEquals(3, metrics.expectedWindows)
        assertEquals(2, metrics.successfulWindows)
        assertEquals(1, metrics.firstAttemptSuccess)
        assertEquals(1, metrics.retrySuccess)
        assertEquals(1, metrics.gatt133Count)
        assertEquals(1, metrics.noCallbackCount)
        assertEquals(1, metrics.missedWindows)
        assertEquals(300_150L, metrics.longestReadingGapMs)
    }

    @Test fun `hardware metrics exclude powered off watch windows from collector reliability`() {
        val windows =
            listOf(
                CollectorExpectedWindow(
                    "before",
                    1_000L,
                    bootId = "10",
                    cycleStartedAt = 900L,
                    readingReceivedAt = 1_100L,
                    finalResult = CollectorCycleClassification.SUCCESS_FRESH,
                ),
                CollectorExpectedWindow(
                    "off",
                    301_000L,
                    bootId = "11",
                    finalResult = CollectorCycleClassification.DEVICE_OFF_OR_REBOOT_GAP,
                    terminalState = CollectorWindowTerminalState.DEVICE_UNAVAILABLE,
                    recoveryRequired = true,
                ),
                CollectorExpectedWindow(
                    "after",
                    601_000L,
                    bootId = "11",
                    cycleStartedAt = 600_900L,
                    readingReceivedAt = 601_100L,
                    finalResult = CollectorCycleClassification.SUCCESS_FRESH,
                ),
            )

        val metrics = calculateG7HardwareMetrics(windows)

        assertEquals(2, metrics.expectedWindows)
        assertEquals(2, metrics.successfulWindows)
        assertEquals(0, metrics.missedWindows)
        assertEquals(1, metrics.deviceUnavailableWindows)
        assertEquals(100.0, metrics.availabilityPercent, 0.0)
        assertNull(metrics.longestReadingGapMs)
        assertEquals(0, metrics.silentWindows)
    }
}
