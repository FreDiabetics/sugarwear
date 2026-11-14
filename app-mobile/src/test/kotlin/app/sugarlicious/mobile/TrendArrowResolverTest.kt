package app.sugarlicious.mobile

import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendArrowResolverTest {
    @Test
    fun `Sugarlicious whole delta policy overrides source arrow`() {
        val result =
            TrendArrowResolver.resolve(
                Trend.SINGLE_UP,
                current(50.0).copy(deltaMgDl = -14.0),
                listOf(GlucoseSample(100.0, 0L), GlucoseSample(50.0, 5 * 60_000L)),
                "DoubleDown",
            )
        assertEquals(Trend.DOUBLE_DOWN, result)
    }

    @Test
    fun `calculates forty five up from five minute rate`() {
        val history =
            listOf(
                GlucoseSample(100.0, 0L),
                GlucoseSample(107.5, 5 * 60_000L),
            )
        assertEquals(
            Trend.FORTY_FIVE_UP,
            TrendArrowResolver.resolve(Trend.UNKNOWN, current(107.5), history),
        )
    }

    @Test
    fun `explicit direction is preferred over calculated fallback`() {
        val history =
            listOf(
                GlucoseSample(100.0, 0L),
                GlucoseSample(100.0, 5 * 60_000L),
            )
        assertEquals(
            Trend.DOUBLE_DOWN,
            TrendArrowResolver.resolve(
                Trend.UNKNOWN,
                current(100.0),
                history,
                "DoubleDown",
            ),
        )
    }

    @Test
    fun `fallback reports rate separately from delta`() {
        val resolution =
            TrendArrowResolver.resolveWithRate(
                Trend.UNKNOWN,
                current(92.0),
                listOf(GlucoseSample(100.0, 0L)),
            )

        assertEquals(Trend.FORTY_FIVE_DOWN, resolution.trend)
        assertEquals(-1.6, resolution.rateMgDlPerMinute ?: 0.0, 0.0)
    }

    private fun current(value: Double) =
        GlucoseState(value, GlucoseUnit.MG_DL, measuredAtEpochMs = 5 * 60_000L)
}
