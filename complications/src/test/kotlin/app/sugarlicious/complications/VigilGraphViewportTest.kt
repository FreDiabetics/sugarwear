package app.sugarlicious.complications

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VigilGraphViewportTest {
    @Test fun `fixed WFF artwork is independent of device density`() {
        fun render(qualifiers: String): android.graphics.Bitmap {
            RuntimeEnvironment.setQualifiers(qualifiers)
            return Robolectric
                .buildService(DirectToWatchGraphComplication::class.java)
                .create()
                .get()
                .renderGraph(null, 1_800_000_000_000L, 6)
        }
        val pixel = render("w213dp-h213dp-320dpi")
        val galaxy = render("w226dp-h226dp-340dpi")
        assertTrue("WFF performs the device scaling once for the complete face", pixel.sameAs(galaxy))
        listOf(426 to pixel, 480 to galaxy).forEach { (size, graph) ->
            val face = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(face)
            val scale = size / 450f
            canvas.scale(scale, scale)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(graph, 0f, 140f, null)
            val output = java.io.File("build/reports/vigil-viewport/graph-$size.png")
            requireNotNull(output.parentFile).mkdirs()
            output.outputStream().use { face.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun `graph axis honors system font scale independently of geometry density`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val original = service.resources.configuration.fontScale
        try {
            service.resources.configuration.fontScale = 1f
            val regular = service.renderGraph(null, 1_800_000_000_000L, 6)
            service.resources.configuration.fontScale = 1.3f
            val enlarged = service.renderGraph(null, 1_800_000_000_000L, 6)
            assertTrue("System font scale must change the axis artwork", !regular.sameAs(enlarged))
        } finally {
            service.resources.configuration.fontScale = original
        }
    }

    @Test fun `entire graph contour stays inside circular display with design margin`() {
        val service = Robolectric.buildService(DirectToWatchGraphComplication::class.java).create().get()
        val bitmap = service.renderGraph(null, 1_800_000_000_000L, 6)
        val radius = 225f + 1f
        var ink = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                ink++
                val dx = x + 0.5f - 225f
                val dy = y + 140.5f - 225f
                assertTrue("Graph pixel ($x, $y) is outside the round safe area", dx * dx + dy * dy <= radius * radius)
            }
        }
        assertTrue(ink > 0)
    }
}
