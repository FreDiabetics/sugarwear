package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TherapyIndicatorGeometryTest {
    @Test fun `system font scaling is counted once in the final ring geometry`() {
        for (diameter in listOf(48f, 66f, 82f, 120f)) {
            for (fontScale in listOf(1f, 1.5f, 2f)) {
                val textSp = TherapyIndicatorGeometry.fontSizeSp(TherapyIndicatorGeometry.VALUE_TEXT_SP, diameter, fontScale)
                assertEquals(14f / 66f, textSp * fontScale / diameter, .0001f)
            }
        }
    }

    @Test fun `ring value and icon proportions survive surface resizing`() {
        for (diameter in listOf(48f, 66f, 82f, 120f)) {
            val scale = TherapyIndicatorGeometry.scale(diameter)
            assertEquals(14f / 66f, TherapyIndicatorGeometry.VALUE_TEXT_SP * scale / diameter, .0001f)
            TherapyProgressMetric.entries.forEach { metric ->
                val icon = TherapyIndicatorGeometry.iconSizeDp(metric, diameter)
                val center = diameter * TherapyIndicatorGeometry.ICON_CENTER_Y_FRACTION
                assertTrue(center + icon / 2f <= diameter + 4f * scale)
                assertEquals(TherapyIndicatorGeometry.iconSizeDp(metric, 66f) / 66f, icon / diameter, .0001f)
            }
        }
    }
}
