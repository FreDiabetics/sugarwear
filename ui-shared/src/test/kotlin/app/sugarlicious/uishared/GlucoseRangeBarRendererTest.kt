package app.sugarlicious.uishared

import android.graphics.Bitmap
import android.graphics.Canvas
import app.sugarlicious.model.GlucoseBarRange
import app.sugarlicious.model.GlucoseBarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlucoseRangeBarRendererTest {
    private val renderer = GlucoseRangeBarRenderer()
    private val palette = GlucoseRangeBarPalette()

    @Test
    fun `target wave draws two rounded colored sections with a central gap`() {
        val bitmap = Bitmap.createBitmap(200, 12, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(bitmap), 200f, 12f, GlucoseBarState(GlucoseBarRange.TARGET, .5f), palette, 7f)
            assertEquals(palette.target, bitmap.getPixel(20, 6))
            assertEquals(palette.target, bitmap.getPixel(180, 6))
            assertNotEquals(palette.target, bitmap.getPixel(100, 6))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `missing or stale data draws only the muted track`() {
        val bitmap = Bitmap.createBitmap(200, 12, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(bitmap), 200f, 12f, null, palette)
            assertEquals(palette.underlay, bitmap.getPixel(100, 6))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `low and high match the exact Mobile color roles`() {
        val lowBitmap = Bitmap.createBitmap(200, 12, Bitmap.Config.ARGB_8888)
        val highBitmap = Bitmap.createBitmap(200, 12, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(lowBitmap), 200f, 12f, GlucoseBarState(GlucoseBarRange.LOW, .5f), palette)
            renderer.draw(Canvas(highBitmap), 200f, 12f, GlucoseBarState(GlucoseBarRange.HIGH, .5f), palette)
            assertEquals(palette.low, lowBitmap.getPixel(20, 6))
            assertEquals(palette.high, highBitmap.getPixel(20, 6))
        } finally {
            lowBitmap.recycle()
            highBitmap.recycle()
        }
    }
}
