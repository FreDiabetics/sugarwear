package app.sugarlicious.uishared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TherapyRingRendererTest {
    private fun render(side: Int, progress: Float?, background: Int = Color.TRANSPARENT): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val stroke = side * 7f / 66f
        TherapyRingRenderer().draw(canvas, RectF(stroke / 2, stroke / 2, side - stroke / 2, side - stroke / 2), stroke, progress, Color.CYAN)
        return bitmap
    }

    private fun pixel(bitmap: Bitmap, angle: Double): Int {
        val radius = (bitmap.width - bitmap.width * 7f / 66f) / 2f
        val phase = Math.toRadians(angle)
        return bitmap.getPixel((bitmap.width / 2f + radius * kotlin.math.cos(phase)).toInt(), (bitmap.height / 2f + radius * kotlin.math.sin(phase)).toInt())
    }

    @Test fun `filled arc remaining arc and separation match Mobile at different sizes without end dots`() {
        for (side in listOf(264, 528)) {
            val bitmap = render(side, .5f)
            assertEquals(Color.CYAN, pixel(bitmap, 200.0))
            assertEquals(77, Color.alpha(pixel(bitmap, 350.0)))
            assertEquals("end cap remains track colored without a bright dot", 77, Color.alpha(pixel(bitmap, 410.0)))
            val stroke = side * 7.0 / 66.0
            val gapSample = 270.0 + Math.toDegrees(.82 * stroke / ((side - stroke) / 2.0))
            assertEquals("proportional visible gap", 0, Color.alpha(pixel(bitmap, gapSample)))
            bitmap.recycle()
        }
    }

    @Test fun `empty unknown invalid and full progress retain honest endpoint states`() {
        listOf(null, Float.NaN, 0f, -1f).forEach { progress ->
            val bitmap = render(264, progress)
            assertEquals(77, Color.alpha(pixel(bitmap, 200.0)))
            assertEquals(77, Color.alpha(pixel(bitmap, 410.0)))
            bitmap.recycle()
        }
        val full = render(264, 1f)
        assertEquals(Color.CYAN, pixel(full, 200.0))
        assertEquals(Color.CYAN, pixel(full, 410.0))
        full.recycle()
    }

    @Test fun `track composites onto both light and dark tile surfaces and exports native previews`() {
        val directory = File("build/reports/therapy-ring-visual-qa").apply { mkdirs() }
        listOf(Color.BLACK, Color.WHITE).forEach { background ->
            listOf(0f, .25f, .5f, .9f, 1f).forEach { progress ->
                val bitmap = render(264, progress, background)
                assertEquals(255, Color.alpha(pixel(bitmap, 350.0)))
                if (progress == .5f) {
                    val track = pixel(bitmap, 350.0)
                    assertTrue(Color.green(track) > Color.red(track))
                    assertTrue(Color.blue(track) > Color.red(track))
                }
                File(directory, "ring-${if (background == Color.WHITE) "light" else "dark"}-${(progress * 100).toInt()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
