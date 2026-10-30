package app.aapswear.g7watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VigilPreviewSnapshotReceiverTest {
    @Test fun `export native Vigil preview preserves captured twenty two ten device typography`() {
        val context = RuntimeEnvironment.getApplication()
        // Immutable handless 480 px device capture: 114 mg/dL, 2 h graph, original native 22:10 clock.
        val source = requireNotNull(javaClass.getResourceAsStream("/vigil-preview-base.png")).use(BitmapFactory::decodeStream)
        context
            .getSharedPreferences("vigil_interaction", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val result = renderVigilPreview(context, source, replaceClock = false)
        var clockInk = 0
        var preservedClockInk = 0
        for (y in 370..453) {
            for (x in 96..383) {
                // The supplied markers may now occupy formerly black clock-slot background.
                // Preserve the captured native clock's actual white glyphs, not empty space.
                if (source.getPixel(x, y) == Color.WHITE) {
                    clockInk++
                    if (result.getPixel(x, y) == Color.WHITE) preservedClockInk++
                }
            }
        }
        assertTrue(clockInk > 100)
        // The canonical 32-second hand legitimately crosses a few clock glyphs.
        assertTrue("Preserve native typography underneath the overlaid hands", preservedClockInk == clockInk)
        assertEquals("Keep captured glucose header away from the new seconds hand", source.getPixel(160, 80), result.getPixel(160, 80))
        assertTrue("Default preview has no hands or markers", result.sameAs(source))
        val output = File("build/reports/vigil-visual-qa/preview.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `device snapshot changes only the clock slot`() {
        val context = RuntimeEnvironment.getApplication()
        val source = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        File(context.filesDir, "vigil-preview-source.png").outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val result = renderVigilPreview(context, source, includeDecorations = false)
        assertEquals(480, result.width)
        for (y in 0 until 480) {
            for (x in 0 until 480) {
                if (x !in 95..384 || y !in 369..454) assertEquals("Snapshot pixel $x,$y", source.getPixel(x, y), result.getPixel(x, y))
            }
        }
        assertTrue((371..452).any { y -> (96..383).any { x -> result.getPixel(x, y) == Color.WHITE } })
    }

    @Test fun `full device preview adds native WFF markers and both canonical hands`() {
        val context = RuntimeEnvironment.getApplication()
        val source = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        File(context.filesDir, "vigil-preview-source.png").outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val result = renderVigilPreview(context, source, includeDecorations = true)
        assertTrue("The native red seconds hand must point to 32 seconds", Color.red(result.getPixel(181, 433)) > 200)
        assertTrue("The native analog hands must overlay the center", (190..260).any { y -> (190..260).any { x -> result.getPixel(x, y) != Color.MAGENTA } })
        assertTrue(
            "Original bright hands must render above their dark shadow layers",
            (190..260).any { y ->
                (190..260).any { x ->
                    val color = result.getPixel(x, y)
                    Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) > 200
                }
            },
        )
        assertEquals("The glucose graph away from the hands stays source-faithful", Color.MAGENTA, result.getPixel(40, 250))
        assertEquals("Canvas corners stay source-faithful", Color.MAGENTA, result.getPixel(0, 0))
    }
}
