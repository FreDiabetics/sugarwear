package app.sugarwear

import app.sugarlicious.model.AppearanceTerminology
import org.junit.Assert.assertEquals
import org.junit.Test

class CgmAppearanceTerminologyTest {
    @Test
    fun `collector appearance roles use canonical labels`() {
        assertEquals(AppearanceTerminology.APP_BACKGROUND, CgmAppearanceRole.MENU_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GLUCOSE_IN_RANGE, CgmAppearanceRole.GLUCOSE_IN_RANGE.label)
        assertEquals(AppearanceTerminology.TREND_ARROW, CgmAppearanceRole.GLUCOSE_TREND.label)
        assertEquals(AppearanceTerminology.GRAPH_BACKGROUND, CgmAppearanceRole.GRAPH_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GRAPH_TARGET_AREA, CgmAppearanceRole.GRAPH_TARGET_AREA.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_OUTLINE, CgmAppearanceRole.GRAPH_DOT_OUTLINE.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TEXT, CgmAppearanceRole.GRAPH_AXIS_TEXT.label)
    }
}
