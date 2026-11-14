package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationSpecsTest {
    @Test
    fun `visual scaling keeps independent value and trend controls`() {
        val scaled = GlucoseVisualSpec.twoByTwoWidgetReference().scaled(200, 70)
        assertEquals(GlucoseTrendSizing.REFERENCE_GLUCOSE_TEXT_SP * 2f, scaled.glucoseTextSize)
        assertEquals(GlucoseTrendSizing.REFERENCE_TREND_HEIGHT_DP * .7f, scaled.trendHeight)
        assertTrue(scaled.baselineAligned)
    }

    @Test
    fun `graph defaults encode sensor floor and two reading confirmation`() {
        val spec =
            GraphSpec(
                time = GraphTimePolicy(3L * 60L * 60_000L),
                axis = GraphAxisSpec(),
                range = GraphRangeSpec(80, 160),
                dots = GraphDotSpec(7f, 1f),
                showPredictions = false,
                showTreatments = false,
            )
        assertEquals(40, spec.range.minimumMgDl)
        assertEquals(2, spec.range.confirmationCount)
        assertTrue(spec.time.anchorAtLatestCgm)
    }
}
