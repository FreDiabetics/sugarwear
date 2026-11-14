package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGraphMobileParityTest {
    @Test
    fun `widget graph visual defaults match Mobile CGM defaults`() {
        assertEquals(2.4f, CgmGraphVisualPolicy.DOT_RADIUS_DP, 0.001f)
        assertEquals(0.95f, CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP, 0.001f)
        assertEquals(1.0f, CgmGraphVisualPolicy.BOUNDARY_STROKE_DP, 0.001f)
        assertEquals(0.7f, CgmGraphVisualPolicy.GRID_STROKE_DP, 0.001f)
        assertEquals(8.5f, CgmGraphVisualPolicy.AXIS_TEXT_SP, 0.001f)
    }
}
