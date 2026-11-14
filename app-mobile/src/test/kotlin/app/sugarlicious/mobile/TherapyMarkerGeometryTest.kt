package app.sugarlicious.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TherapyMarkerGeometryTest {
    private fun visible(top: Float, left: Float = 40f, right: Float = 60f, radius: Float = 0f): Boolean =
        therapyInkClearsUpperContour(left, right, top, 0f, 100f, 10f, radius)

    @Test
    fun `amount disappears before its triangle reaches the upper contour`() {
        val markerTop = 19f
        val gap = 2f
        val labelInkHeight = 8f
        assertTrue(visible(markerTop))
        assertFalse(visible(markerTop - gap - labelInkHeight))
        assertTrue(visible(21f - gap - labelInkHeight))
    }

    @Test
    fun `marker disappears at contact and beyond instead of being clamped or cropped`() {
        assertTrue(visible(10.01f))
        assertFalse(visible(10f))
        assertFalse(visible(9f))
    }

    @Test
    fun `corner check uses full ink width and the curved upper boundary`() {
        assertTrue(visible(11f, radius = 10f))
        assertFalse(visible(13f, left = 0f, right = 10f, radius = 10f))
        assertFalse(visible(13f, left = 90f, right = 100f, radius = 10f))
        assertTrue(visible(20.01f, left = 0f, right = 10f, radius = 10f))
    }

    @Test
    fun `larger label ink is hidden without changing a still visible marker`() {
        val markerTop = 30f
        assertTrue(visible(markerTop))
        assertTrue(visible(markerTop - 2f - 8f))
        assertFalse(visible(markerTop - 2f - 20f))
        assertFalse(visible(Float.NaN))
    }
}
