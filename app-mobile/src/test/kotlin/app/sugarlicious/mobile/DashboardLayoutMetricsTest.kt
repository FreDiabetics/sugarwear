package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutMetricsTest {
    @Test
    fun `large phone profile keeps overview dense enough for one screen`() {
        val metrics = DashboardLayoutMetrics.forScreenHeight(960)
        val estimatedOverviewContentDp =
            metrics.summaryTileHeight +
                (metrics.glucoseChartHeight + 62) +
                (metrics.metabolicChartHeight + 38) +
                (metrics.statTileHeight + 6) +
                70 +
                20

        assertTrue(estimatedOverviewContentDp <= 818)
        assertEquals(94, metrics.summaryTileHeight)
    }

    @Test
    fun `shorter displays receive progressively smaller graph budgets`() {
        val tall = DashboardLayoutMetrics.forScreenHeight(960)
        val compact = DashboardLayoutMetrics.forScreenHeight(820)
        val short = DashboardLayoutMetrics.forScreenHeight(740)

        assertTrue(tall.glucoseChartHeight > compact.glucoseChartHeight)
        assertTrue(compact.glucoseChartHeight > short.glucoseChartHeight)
        assertTrue(tall.metabolicChartHeight > compact.metabolicChartHeight)
        assertTrue(compact.metabolicChartHeight > short.metabolicChartHeight)
    }

    @Test
    fun `free dashboard height is transferred to the cgm graph in all visibility states`() {
        val metrics = DashboardLayoutMetrics.forScreenHeight(880)
        val gap = 5
        val both = metrics.cgmGraphHeight(DashboardVisibilityState(true, true), gap)
        val noDetails = metrics.cgmGraphHeight(DashboardVisibilityState(false, true), gap)
        val noMetabolic = metrics.cgmGraphHeight(DashboardVisibilityState(true, false), gap)
        val neither = metrics.cgmGraphHeight(DashboardVisibilityState(false, false), gap)

        assertEquals(both + metrics.statTileHeight + gap, noDetails)
        assertEquals(both + metrics.metabolicChartHeight + gap, noMetabolic)
        assertEquals(noDetails + metrics.metabolicChartHeight + gap, neither)
        assertTrue(neither > noMetabolic && noMetabolic > both)
    }
}
