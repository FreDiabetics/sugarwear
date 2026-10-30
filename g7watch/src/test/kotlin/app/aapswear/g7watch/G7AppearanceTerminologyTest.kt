package app.aapswear.g7watch

import app.aapswear.model.AppearanceTerminology
import org.junit.Assert.assertEquals
import org.junit.Test

class G7AppearanceTerminologyTest {
    @Test
    fun `collector appearance roles use canonical labels`() {
        assertEquals(AppearanceTerminology.APP_BACKGROUND, G7AppearanceRole.MENU_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GLUCOSE_IN_RANGE, G7AppearanceRole.GLUCOSE_IN_RANGE.label)
        assertEquals(AppearanceTerminology.TREND_ARROW, G7AppearanceRole.GLUCOSE_TREND.label)
        assertEquals(AppearanceTerminology.GRAPH_BACKGROUND, G7AppearanceRole.GRAPH_BACKGROUND.label)
        assertEquals(AppearanceTerminology.GRAPH_TARGET_AREA, G7AppearanceRole.GRAPH_TARGET_AREA.label)
        assertEquals(AppearanceTerminology.GRAPH_DOT_OUTLINE, G7AppearanceRole.GRAPH_DOT_OUTLINE.label)
        assertEquals(AppearanceTerminology.GRAPH_AXIS_TEXT, G7AppearanceRole.GRAPH_AXIS_TEXT.label)
    }
}
