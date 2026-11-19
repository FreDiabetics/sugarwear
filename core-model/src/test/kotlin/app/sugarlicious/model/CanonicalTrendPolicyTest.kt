package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanonicalTrendPolicyTest {
    @Test
    fun `glucose provenance distinguishes source trend from derived trend`() {
        val source =
            GlucoseState(
                valueMgDl = 120.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.SINGLE_UP,
                measuredAtEpochMs = 1_000L,
                trendOrigin = ValueProvenance.SOURCE,
            )
        val derived = source.copy(trendOrigin = ValueProvenance.DERIVED)

        assertEquals(ValueProvenance.SOURCE, source.trendOrigin)
        assertEquals(ValueProvenance.DERIVED, derived.trendOrigin)
    }

    @Test
    fun `all source directions normalize without ordinal mapping`() {
        val expected =
            mapOf(
                "DoubleUp" to Trend.DOUBLE_UP,
                "SingleUp" to Trend.SINGLE_UP,
                "FortyFiveUp" to Trend.FORTY_FIVE_UP,
                "Flat" to Trend.FLAT,
                "FortyFiveDown" to Trend.FORTY_FIVE_DOWN,
                "SingleDown" to Trend.SINGLE_DOWN,
                "DoubleDown" to Trend.DOUBLE_DOWN,
            )
        expected.forEach { (source, trend) -> assertEquals(trend, CanonicalTrendPolicy.fromDirection(source)) }
        assertEquals(Trend.UNKNOWN, CanonicalTrendPolicy.fromDirection("NONE"))
        assertEquals(Trend.UNKNOWN, CanonicalTrendPolicy.fromDirection(null))
    }

    @Test
    fun `Sugarlicious boundaries use rounded whole five minute deltas`() {
        val cases =
            mapOf(
                -14.0 to Trend.DOUBLE_DOWN,
                -13.0 to Trend.SINGLE_DOWN,
                -10.0 to Trend.SINGLE_DOWN,
                -9.0 to Trend.FORTY_FIVE_DOWN,
                -6.0 to Trend.FORTY_FIVE_DOWN,
                -5.0 to Trend.FLAT,
                0.0 to Trend.FLAT,
                5.0 to Trend.FLAT,
                6.0 to Trend.FORTY_FIVE_UP,
                9.0 to Trend.FORTY_FIVE_UP,
                10.0 to Trend.SINGLE_UP,
                13.0 to Trend.SINGLE_UP,
                14.0 to Trend.DOUBLE_UP,
            )
        cases.forEach { (delta, trend) ->
            assertEquals(trend, CanonicalTrendPolicy.fromFiveMinuteDelta(delta, TrendRateProfile.SUGARLICIOUS))
        }
    }

    @Test
    fun `Sugarlicious rounds raw deltas before classification while Manufacturer profile is unchanged`() {
        assertEquals(Trend.FLAT, CanonicalTrendPolicy.fromFiveMinuteDelta(5.49, TrendRateProfile.SUGARLICIOUS))
        assertEquals(Trend.FLAT, CanonicalTrendPolicy.fromFiveMinuteDelta(-5.49, TrendRateProfile.SUGARLICIOUS))
        assertEquals(Trend.FORTY_FIVE_UP, CanonicalTrendPolicy.fromRate(1.0, TrendRateProfile.DEXCOM_G7))
    }

    @Test
    fun `Manufacturer G7 boundaries match documented fifteen minute bands`() {
        val cases =
            mapOf(
                -3.0001 to Trend.DOUBLE_DOWN,
                -3.0 to Trend.SINGLE_DOWN,
                -2.0 to Trend.SINGLE_DOWN,
                -1.0 to Trend.FORTY_FIVE_DOWN,
                1.0 to Trend.FORTY_FIVE_UP,
                2.0 to Trend.SINGLE_UP,
                3.0 to Trend.SINGLE_UP,
            )
        cases.forEach { (rate, trend) ->
            assertEquals(trend, CanonicalTrendPolicy.fromRate(rate, TrendRateProfile.DEXCOM_G7))
        }
    }

    @Test
    fun `requested five minute deltas are monotone in both directions`() {
        val deltas = listOf(20.0, 15.0, 12.0, 8.0, 5.0, 3.0, 0.0, -3.0, -5.0, -6.0, -8.0, -12.0, -15.0, -20.0)
        val ranks =
            mapOf(
                Trend.DOUBLE_DOWN to -3,
                Trend.SINGLE_DOWN to -2,
                Trend.FORTY_FIVE_DOWN to -1,
                Trend.FLAT to 0,
                Trend.FORTY_FIVE_UP to 1,
                Trend.SINGLE_UP to 2,
                Trend.DOUBLE_UP to 3,
            )
        val classified = deltas.map { delta -> CanonicalTrendPolicy.fromRate(delta / 5.0, TrendRateProfile.SUGARLICIOUS) }
        classified.zipWithNext().forEach { (left, right) ->
            check(ranks.getValue(left) >= ranks.getValue(right)) { "$left must not rank below $right" }
        }
        assertEquals(Trend.FORTY_FIVE_DOWN, classified[deltas.indexOf(-6.0)])
        assertEquals(Trend.FORTY_FIVE_DOWN, classified[deltas.indexOf(-8.0)])
    }

    @Test
    fun `fallback uses measured interval and rejects incompatible evidence`() {
        val current = sample(100.0, 10, source = DataSourceId.ANDROID_APS, sensor = "s", session = "a")
        assertEquals(Trend.SINGLE_DOWN, CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 6, sensor = "s", session = "a")), TrendRateProfile.SUGARLICIOUS)?.trend)
        assertEquals(Trend.FORTY_FIVE_DOWN, CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 5, sensor = "s", session = "a")), TrendRateProfile.SUGARLICIOUS)?.trend)
        assertEquals(Trend.FORTY_FIVE_DOWN, CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 4, sensor = "s", session = "a")), TrendRateProfile.SUGARLICIOUS)?.trend)
        assertEquals(Trend.FLAT, CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 0, sensor = "s", session = "a")), TrendRateProfile.SUGARLICIOUS)?.trend)
        assertNull(CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 5, sensor = "other", session = "a")), TrendRateProfile.SUGARLICIOUS))
        assertNull(CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 5, sensor = "s", session = "other")), TrendRateProfile.SUGARLICIOUS))
        assertNull(CanonicalTrendPolicy.derive(current, listOf(sample(108.0, 11, sensor = "s", session = "a")), TrendRateProfile.SUGARLICIOUS))
    }

    @Test
    fun `debug diagnostic exposes semantic stages and selected asset`() {
        val text = TrendDiagnostics.format(142.0, -8.0, 5.0, -1.6, Trend.UNKNOWN, Trend.FORTY_FIVE_DOWN, DataSourceId.DEXCOM_G7_WATCH)

        check("delta=-8.0" in text)
        check("periodMin=5.0" in text)
        check("rateMgDlMin=-1.6" in text)
        check("canonical=FORTY_FIVE_DOWN" in text)
        check("asset=FORTY_FIVE_DOWN" in text)
    }

    @Test
    fun `backfill arrival order cannot replace measured time predecessor`() {
        val current = sample(100.0, 10).copy(receivedAtEpochMs = 20 * 60_000L)
        val properPrevious = sample(108.0, 5).copy(receivedAtEpochMs = 19 * 60_000L)
        val lateBackfill = sample(140.0, 3).copy(receivedAtEpochMs = 21 * 60_000L)

        val result = CanonicalTrendPolicy.derive(current, listOf(lateBackfill, properPrevious), TrendRateProfile.SUGARLICIOUS)

        assertEquals(5 * 60_000L, result?.previousMeasuredAtEpochMs)
        assertEquals(-1.6, result?.rateMgDlPerMinute ?: 0.0, 0.0)
    }

    private fun sample(
        value: Double,
        minute: Int,
        source: DataSourceId = DataSourceId.ANDROID_APS,
        sensor: String? = null,
        session: String? = null,
    ) = GlucoseSample(value, minute * 60_000L, source, sensorId = sensor, sessionId = session)
}
