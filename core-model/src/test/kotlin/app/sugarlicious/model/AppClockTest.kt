package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppClockTest {
    private class FakeAppClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMs(): Long = now
    }

    @Test
    fun `fake clock advances deterministic freshness time`() {
        val clock = FakeAppClock(1_000L)
        assertEquals(1_000L, clock.nowEpochMs())
        clock.now += 15L * 60_000L
        assertEquals(901_000L, clock.nowEpochMs())
    }
}
