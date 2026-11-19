package app.sugarlicious.model

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ComplicationPresentationTest {
    @Test
    fun `typed provider ids remain unique and resolve to their presentation family`() {
        assertEquals(40, SugarliciousComplicationIds.all.size)
        assertEquals(40, SugarliciousComplicationIds.all.distinct().size)
        assertEquals(SugarliciousComplicationIds.GRAPH, SugarliciousComplicationIds.baseId(SugarliciousComplicationIds.GRAPH_ORBIT))
        assertEquals(
            SugarliciousComplicationIds.GLUCOSE_TREND,
            SugarliciousComplicationIds.baseId(SugarliciousComplicationIds.GLUCOSE_TREND_RANGED),
        )
    }

    private val now = 1_700_000_000_000L
    private val state =
        TherapyDisplayState(
            receivedAtEpochMs = now,
            sourceVersion = "4.0.0",
            glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, Trend.FORTY_FIVE_UP, now - 2 * 60_000L, 5.0),
            glucoseHistory =
                listOf(
                    GlucoseSample(80.0, now - 10 * 60_000L),
                    GlucoseSample(120.0, now - 5 * 60_000L),
                    GlucoseSample(200.0, now),
                ),
            insulin = InsulinState(totalIob = 1.2),
            carbs = CarbState(cobGrams = 15.0),
            basal = BasalState(currentUnitsPerHour = 0.7),
        )

    @Test fun `glucose trend keeps text and icon separate`() {
        val p = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.GLUCOSE_TREND, state, now)
        assertEquals("123", p.text)
        assertEquals(Trend.FORTY_FIVE_UP, p.trend)
        assertNull(p.title)
    }

    @Test fun `stale and signal loss retain the last glucose trend delta and age`() {
        val stale = state.copy(glucose = state.glucose!!.copy(measuredAtEpochMs = now - 20 * 60_000L))

        val trendOnly = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.TREND_ONLY, stale, now)
        assertEquals("", trendOnly.text)
        assertEquals(Trend.FORTY_FIVE_UP, trendOnly.trend)

        val ranged = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.GLUCOSE_TREND_RANGED, stale, now)
        assertEquals(Trend.FORTY_FIVE_UP, ranged.trend)
        assertFalse(ranged.text.contains('\u0336'))
        assertNull(ranged.title)

        val combined = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE, stale, now)
        assertFalse(combined.text.contains('\u0336'))
        assertEquals("+5 · 20m", combined.title)
        assertEquals(Trend.FORTY_FIVE_UP, combined.trend)
    }

    @Test fun `glucose delta uses title instead of concatenating`() {
        val p = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA, state, now)
        assertEquals("123", p.text)
        assertEquals("+5", p.title)
    }

    @Test fun `time delta keeps compact lines`() {
        val p = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.TIME_DELTA, state, now)
        assertEquals("+5", p.text)
        assertEquals("2m", p.title)
    }

    @Test fun `trend geometry matches mobile overview`() {
        val expected =
            mapOf(
                Trend.DOUBLE_UP to TrendVisualAsset.DOUBLE_UP,
                Trend.SINGLE_UP to TrendVisualAsset.UP,
                Trend.FORTY_FIVE_UP to TrendVisualAsset.FORTY_FIVE_UP,
                Trend.FLAT to TrendVisualAsset.FLAT,
                Trend.FORTY_FIVE_DOWN to TrendVisualAsset.FORTY_FIVE_DOWN,
                Trend.SINGLE_DOWN to TrendVisualAsset.DOWN,
                Trend.DOUBLE_DOWN to TrendVisualAsset.DOUBLE_DOWN,
            )
        expected.forEach { (trend, asset) -> assertEquals(asset, TrendVisuals.spec(trend)?.asset) }
        assertEquals(1f, TrendVisuals.spec(Trend.FORTY_FIVE_UP)!!.aspectRatio)
        assertEquals(TrendVisualAsset.DOUBLE_DOWN, TrendVisuals.spec(Trend.DOUBLE_DOWN)!!.asset)
        assertEquals(125f / 60f, TrendVisuals.spec(Trend.DOUBLE_DOWN)!!.aspectRatio)
        assertNull(TrendVisuals.spec(Trend.UNKNOWN))
    }

    @Test fun `combined therapy presents values without redundant visible labels`() {
        val p = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.IOB_COB_BASAL, state, now)
        assertEquals("0.70U/h", p.title)
        assertEquals("1.20U · 15g", p.text)
    }

    @Test fun `combined therapy never invents zero for missing values`() {
        val p =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB_BASAL,
                state.copy(insulin = null, carbs = null, basal = null),
                now,
            )
        assertEquals("—", p.title)
        assertEquals("— · —", p.text)
    }

    @Test fun `IOB COB maps IOB to title and COB to text`() {
        val p = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.IOB_COB, state, now)
        assertEquals("1.20U", p.title)
        assertEquals("15g", p.text)
        assertEquals("IOB 1.20U, COB 15g", p.contentDescription)
    }

    @Test fun `IOB COB preserves either available value without inventing zero`() {
        val iobOnly =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(carbs = null),
                now,
            )
        assertEquals("1.20U", iobOnly.title)
        assertEquals("—", iobOnly.text)

        val cobOnly =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(insulin = null),
                now,
            )
        assertEquals("—", cobOnly.title)
        assertEquals("15g", cobOnly.text)

        val neither =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(insulin = null, carbs = null),
                now,
            )
        assertEquals("—", neither.title)
        assertEquals("—", neither.text)
    }

    @Test fun `IOB COB rejects invalid numbers and describes freshness without discarding last state`() {
        val invalid =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(insulin = InsulinState(totalIob = Double.NaN), carbs = CarbState(cobGrams = Double.POSITIVE_INFINITY)),
                now,
            )
        assertEquals("—", invalid.title)
        assertEquals("—", invalid.text)

        val stale =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(glucose = state.glucose!!.copy(measuredAtEpochMs = now - 13 * 60_000L)),
                now,
            )
        assertEquals("1.20U", stale.title)
        assertEquals("15g", stale.text)
        assertEquals("veraltet, IOB 1.20U, COB 15g", stale.contentDescription)

        val error =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.IOB_COB,
                state.copy(glucose = state.glucose.copy(quality = CgmQuality.SENSOR_ERROR)),
                now,
            )
        assertEquals("1.20U", error.title)
        assertEquals("15g", error.text)
        assertEquals("Sensorfehler, IOB 1.20U, COB 15g", error.contentDescription)

        val noSource = ComplicationPresentationFormatter.format(SugarliciousComplicationIds.IOB_COB, null, now)
        assertEquals("—", noSource.title)
        assertEquals("—", noSource.text)
        assertEquals("keine Quelle, IOB —, COB —", noSource.contentDescription)
    }

    @Test fun `loop complication uses the shared app state instead of circle glyphs`() {
        val closed =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.LOOP,
                state.copy(loop = LoopState(status = "enacted")),
                now,
            )
        assertEquals("Closed", closed.text)
        assertEquals("Closed Loop", closed.contentDescription)

        val paused =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.LOOP,
                state.copy(loop = LoopState(status = "paused")),
                now,
            )
        assertEquals("Pausiert", paused.text)
        assertEquals("Loop pausiert", paused.contentDescription)
    }

    @Test fun `tir presentation uses configured central thresholds`() {
        val thresholds =
            CgmThresholds(
                veryHighMgDl = 240.0,
                highMgDl = 160.0,
                lowMgDl = 80.0,
                veryLowMgDl = 55.0,
            )
        val presentation =
            ComplicationPresentationFormatter.format(
                SugarliciousComplicationIds.TIR,
                state,
                now,
                thresholds,
            )

        assertEquals("67%", presentation.text)
        assertEquals("80–160", presentation.title)
    }

    @Test fun `date weekdays always use the requested German abbreviations`() {
        val expected = listOf("MON", "DIE", "MIT", "DON", "FRE", "SAM", "SON")
        assertEquals(expected, DayOfWeek.entries.map(ComplicationPresentationFormatter::germanWeekday))
    }
}
