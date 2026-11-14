package app.sugarlicious.model
import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessPolicyTest {
    @Test fun boundaries() {
        val now = 1_000_000L
        assertEquals(Freshness.NO_DATA, FreshnessPolicy.classify(null, now))
        assertEquals(
            Freshness.CURRENT,
            FreshnessPolicy.classify(
                now - 360_000,
                now,
            ),
        )
        assertEquals(Freshness.DELAYED, FreshnessPolicy.classify(now - 360_001, now))
        assertEquals(
            Freshness.STALE,
            FreshnessPolicy.classify(
                now - 720_001,
                now,
            ),
        )
        assertEquals(Freshness.SIGNAL_LOSS, FreshnessPolicy.classify(now - 16 * 60_000L, now))
    }

    @Test fun implausibleFutureMeasurementIsNotCurrent() {
        assertEquals(Freshness.NO_DATA, FreshnessPolicy.classify(400_001L, 100_000L))
    }
}
