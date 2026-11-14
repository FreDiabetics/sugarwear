package app.sugarlicious.mobile

import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.model.AppearanceTerminology
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTerminologyMappingTest {
    @Test
    fun `mobile and widget equivalent roles use canonical labels`() {
        assertEquals(AppearanceTerminology.GRAPH_BACKGROUND, SugarliciousColorRole.GRAPH_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GRAPH_BACKGROUND, WidgetColorRole.GRAPH_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_LOW, SugarliciousColorRole.CGM_DOT_LOW.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_LOW, WidgetColorRole.DOT_LOW.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_IN_RANGE, SugarliciousColorRole.CGM_DOT_IN_RANGE.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_IN_RANGE, WidgetColorRole.DOT_IN_RANGE.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_HIGH, SugarliciousColorRole.CGM_DOT_HIGH.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_HIGH, WidgetColorRole.DOT_HIGH.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TEXT, SugarliciousColorRole.GRAPH_LABEL.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TEXT, WidgetColorRole.AXIS.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TICK, SugarliciousColorRole.GRAPH_AXIS_TICK.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TICK, WidgetColorRole.AXIS_TICK.label)
        assertEquals(AppearanceTerminology.GRAPH_HIGH_LINE, SugarliciousColorRole.GRAPH_HIGH_LINE.label)
        assertEquals(AppearanceTerminology.GRAPH_HIGH_LINE, WidgetColorRole.HIGH_LINE.label)
        assertEquals(AppearanceTerminology.GRAPH_LOW_LINE, SugarliciousColorRole.GRAPH_LOW_LINE.label)
        assertEquals(AppearanceTerminology.GRAPH_LOW_LINE, WidgetColorRole.LOW_LINE.label)
        assertEquals(AppearanceTerminology.GRAPH_NOW_LINE, SugarliciousColorRole.GRAPH_NOW_LINE.label)
        assertEquals(AppearanceTerminology.GRAPH_NOW_LINE, WidgetColorRole.DIVIDER.label)
        assertEquals(AppearanceTerminology.GRAPH_DIVIDER, SugarliciousColorRole.GRAPH_DIVIDER.label)
    }
}
