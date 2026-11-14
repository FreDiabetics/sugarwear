package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GlucoseTrendSizingTest {
    @Test
    fun `2x2 reference renderbox ratio stays stable`() {
        assertEquals(37.236f, GlucoseTrendSizing.arrowHeightForGlucoseHeight(58f), 0.01f)
    }

    @Test
    fun `arrow follows glucose height without changing typography`() {
        val glucoseHeight = 44f
        assertEquals(
            GlucoseTrendSizing.REFERENCE_ARROW_TO_GLUCOSE_HEIGHT,
            GlucoseTrendSizing.arrowHeightForGlucoseHeight(glucoseHeight) / glucoseHeight,
            0.0001f,
        )
    }

    @Test
    fun `shared scale accepts 200 percent and clamps physical extremes`() {
        assertEquals(0.7f, GlucoseTrendSizing.scaleFactor(1), 0.0001f)
        assertEquals(1f, GlucoseTrendSizing.scaleFactor(100), 0.0001f)
        assertEquals(2f, GlucoseTrendSizing.scaleFactor(200), 0.0001f)
        assertEquals(2f, GlucoseTrendSizing.scaleFactor(999), 0.0001f)
    }

    @Test
    fun `mobile default uses exact accepted 2x2 reference geometry`() {
        assertEquals(40.38f, GlucoseTrendSizing.REFERENCE_GLUCOSE_TEXT_SP, 0.001f)
        assertEquals(18.62f, GlucoseTrendSizing.REFERENCE_TREND_HEIGHT_DP, 0.001f)
    }
}
