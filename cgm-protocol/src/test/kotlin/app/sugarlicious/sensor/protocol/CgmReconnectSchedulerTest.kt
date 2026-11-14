package app.sugarlicious.sensor.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class CgmReconnectSchedulerTest {
    @Test fun `GATT 133 before first reading retries shortly without exponential backoff`() {
        val now = 1_000_000L
        val plan = CgmReconnectScheduler.afterGatt133(now, null)

        assertEquals(now + 15_000L, plan.nextReconnectEpochMs)
        assertEquals(15_000L, plan.delayMs)
        assertEquals(0, plan.retryCount)
    }

    @Test fun `GATT 133 after a reading advances to the next five minute connection window`() {
        val reading = 1_000_000L
        val now = 1_280_000L
        val plan = CgmReconnectScheduler.afterGatt133(now, reading)

        assertEquals(1_570_000L, plan.nextReconnectEpochMs)
        assertEquals(290_000L, plan.delayMs)
        assertEquals(0, plan.retryCount)
    }

    @Test fun `missed advertisement window aligns to next five minute connection window`() {
        val reading = 1_000_000L
        val missAt = 1_360_000L
        val plan = CgmReconnectScheduler.afterExpectedWindowMiss(missAt, reading)

        assertEquals(1_570_000L, plan.nextReconnectEpochMs)
        assertEquals(210_000L, plan.delayMs)
        assertEquals(0, plan.retryCount)
    }

    @Test fun `GATT 133 never inherits generic retry count`() {
        val plan = CgmReconnectScheduler.afterGatt133(10_000_000L, 9_000_000L)

        assertEquals(0, plan.retryCount)
        assert(plan.delayMs <= CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS)
    }

    @Test fun `long outage advances old cadence to exactly one future sensor slot`() {
        val lastReading = 19L * 60L * 60_000L + 5L * 60_000L
        val now = 23L * 60L * 60_000L + 41L * 60_000L

        val plan = CgmReconnectScheduler.afterExpectedWindowMiss(now, lastReading)

        assertEquals(23L * 60L * 60_000L + 44L * 60_000L + 30_000L, plan.nextReconnectEpochMs)
        assert(plan.nextReconnectEpochMs > now)
        assert(plan.delayMs <= CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS)
    }
}
