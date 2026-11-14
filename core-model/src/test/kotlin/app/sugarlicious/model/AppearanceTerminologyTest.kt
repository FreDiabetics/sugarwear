package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceTerminologyTest {
    @Test
    fun `canonical labels are stable and unambiguous`() {
        assertEquals("Graph-Hintergrund", AppearanceTerminology.GRAPH_BACKGROUND)
        assertEquals("Zielbereich", AppearanceTerminology.GRAPH_TARGET_AREA)
        assertEquals("CGM-Punkte · im Ziel", AppearanceTerminology.GRAPH_DOT_IN_RANGE)
        assertEquals("CGM-Dot-Kontur", AppearanceTerminology.GRAPH_DOT_OUTLINE)
        assertEquals("Trendpfeil", AppearanceTerminology.TREND_ARROW)

        val graphLabels =
            listOf(
                AppearanceTerminology.GRAPH_BACKGROUND,
                AppearanceTerminology.GRAPH_LOW_AREA,
                AppearanceTerminology.GRAPH_VERY_LOW_AREA,
                AppearanceTerminology.GRAPH_TARGET_AREA,
                AppearanceTerminology.GRAPH_HIGH_AREA,
                AppearanceTerminology.GRAPH_VERY_HIGH_AREA,
                AppearanceTerminology.GRAPH_LOW_LINE,
                AppearanceTerminology.GRAPH_VERY_LOW_LINE,
                AppearanceTerminology.GRAPH_HIGH_LINE,
                AppearanceTerminology.GRAPH_VERY_HIGH_LINE,
                AppearanceTerminology.GRAPH_DOT_LOW,
                AppearanceTerminology.GRAPH_DOT_IN_RANGE,
                AppearanceTerminology.GRAPH_DOT_HIGH,
                AppearanceTerminology.GRAPH_DOT_OUTLINE,
                AppearanceTerminology.GRAPH_AXIS_TEXT,
                AppearanceTerminology.GRAPH_AXIS_TICK,
                AppearanceTerminology.GRAPH_NOW_LINE,
                AppearanceTerminology.GRAPH_DIVIDER,
            )
        assertTrue(graphLabels.all(String::isNotBlank))
        assertEquals(graphLabels.size, graphLabels.distinct().size)
    }

    @Test
    fun `dot outline controls share the mobile reference range`() {
        assertEquals(0.25f, AppearanceControlContract.DOT_OUTLINE_MIN_DP)
        assertEquals(3.0f, AppearanceControlContract.DOT_OUTLINE_MAX_DP)
        assertEquals(0.05f, AppearanceControlContract.DOT_OUTLINE_STEP_DP)
        assertEquals(0.25f, AppearanceControlContract.normalizeDotOutlineWidth(-2f))
        assertEquals(3.0f, AppearanceControlContract.normalizeDotOutlineWidth(9f))
        assertEquals(1.25f, AppearanceControlContract.quantizeDotOutlineWidth(1.23f))
        assertEquals(1.20f, AppearanceControlContract.quantizeDotOutlineWidth(1.22f))
    }
}
