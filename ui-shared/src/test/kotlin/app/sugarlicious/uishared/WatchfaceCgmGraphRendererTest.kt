package app.sugarlicious.uishared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphTimeWindow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchfaceCgmGraphRendererTest {
    private val now = 50_000_000L
    private val minute = 60_000L

    @Test
    fun `Orbit current marker has opaque black interior and retains its light outline`() {
        val bitmap = Bitmap.createBitmap(660, 330, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(Canvas(bitmap), 660, 330, listOf(GlucoseSample(120.0, now)), GraphTimeWindow.live(now, 90 * minute), CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0), targetLines = false, semicircle = true)
        assertEquals(Color.BLACK, bitmap.getPixel(554, 211))
        assertTrue((540..568).any { x -> Color.red(bitmap.getPixel(x, 211)) > 220 && Color.alpha(bitmap.getPixel(x, 211)) == 255 })
    }

    @Test
    fun `Orbit has readable right labels above high and below low while ApeX has none`() {
        val bitmap = Bitmap.createBitmap(660, 330, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(Canvas(bitmap), 660, 330, listOf(GlucoseSample(120.0, now)), GraphTimeWindow.live(now, 90 * minute), CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0), semicircle = true)

        fun count(left: Int, top: Int, right: Int, bottom: Int) = (left until right).sumOf { x -> (top until bottom).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 128 } }
        assertTrue("high label must be visible above its guide", count(480, 110, 630, 142) > 100)
        assertTrue("low label must be visible beneath its guide", count(540, 280, 655, 329) > 100)
        val apex = Bitmap.createBitmap(660, 330, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(Canvas(apex), 660, 330, listOf(GlucoseSample(120.0, now)), GraphTimeWindow.live(now, 90 * minute), CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0), semicircle = true, showThresholdLabels = false)
        assertTrue("ApeX has no high label", (480 until 630).all { x -> (110 until 142).all { y -> Color.alpha(apex.getPixel(x, y)) == 0 } })
        assertTrue("ApeX has no low label", (540 until 655).all { x -> (280 until 329).all { y -> Color.alpha(apex.getPixel(x, y)) == 0 } })
    }

    @Test
    fun `Apex cap retains samples inside original circle curvature above middle complications`() {
        val bitmap = Bitmap.createBitmap(660, 194, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(
            Canvas(bitmap),
            660,
            194,
            (0..18).map { GlucoseSample(if (it % 2 == 0) 250.0 else 50.0, now - (18 - it) * 5 * minute) },
            GraphTimeWindow.live(now, 90 * minute),
            CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0),
            targetLines = false,
            semicircle = true,
            showThresholdLabels = false,
        )
        var visible = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                    visible++
                    assertTrue("cap ink outside original circular curvature", (x - 330.0) * (x - 330.0) + (y - 330.0) * (y - 330.0) <= 330.0 * 330.0)
                }
            }
        }
        assertTrue("cap must retain genuine samples", visible > 100)
    }

    @Test
    fun `semicircle layout retains all sample dots inside the watchface mask without bending glucose levels`() {
        val bitmap = Bitmap.createBitmap(660, 330, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(
            Canvas(bitmap),
            660,
            330,
            (0..18).map { GlucoseSample(120.0, now - (18 - it) * 5 * minute) },
            GraphTimeWindow.live(now, 90 * minute),
            CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0),
            targetLines = false,
            semicircle = true,
        )
        val visibleColumns = mutableListOf<Int>()
        for (x in 0 until 660) {
            val pixels = (0 until 330).filter { Color.alpha(bitmap.getPixel(x, it)) > 128 }
            if (pixels.isNotEmpty()) visibleColumns.add(x)
            pixels.forEach { y ->
                assertTrue("dot outside upper circular mask at $x,$y", (x - 330.0) * (x - 330.0) + (y - 330.0) * (y - 330.0) <= 330.0 * 330.0)
            }
        }
        assertTrue("history must occupy most of the chart width", visibleColumns.last() - visibleColumns.first() > 400)
        val centers = listOf(106, 230, 330, 430).map { x -> (0 until 330).filter { Color.alpha(bitmap.getPixel(x, it)) > 128 }.average() }
        assertTrue("equal glucose values must stay on a straight horizontal level", centers.all { it.isFinite() } && centers.max() - centers.min() < 1)
    }

    @Test
    fun `both range guides remain clearly visible at watchface size`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now)))
        assertTrue((12..22).count { Color.alpha(bitmap.getPixel(0, it)) > 128 } >= 3)
        assertTrue((108..118).count { Color.alpha(bitmap.getPixel(0, it)) > 128 } >= 3)
    }

    @Test
    fun `range lines reach image edges but every range area stays transparent over black`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now)))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 80)))
        assertEquals(0, Color.alpha(bitmap.getPixel(399, 80)))
        assertEquals(Color.rgb(184, 184, 184), bitmap.getPixel(0, 17))
        assertEquals(Color.rgb(120, 120, 120), bitmap.getPixel(399, 113))
    }

    @Test
    fun `dots use opaque light medium and dark tones for target high and low`() {
        val bitmap = render(listOf(GlucoseSample(60.0, now - 90 * minute), GlucoseSample(120.0, now - 60 * minute), GlucoseSample(220.0, now - 30 * minute), GlucoseSample(120.0, now)))
        assertEquals(Color.rgb(120, 120, 120), bitmap.getPixel(9, 117))
        assertEquals(Color.WHITE, bitmap.getPixel(136, 82))
        assertEquals(Color.rgb(184, 184, 184), bitmap.getPixel(264, 23))
    }

    @Test
    fun `watchface always renders ninety minutes and ignores older samples regardless of legacy setting`() {
        val recent = (0..18).map { GlucoseSample(100.0 + it, now - (18 - it) * 5 * minute) }
        val old = GlucoseSample(1000.0, now - 100 * minute)
        assertTrue(render(recent + old, 6 * 60 * minute).sameAs(render(recent, 90 * minute)))
        assertTrue(render(recent, 5 * minute).sameAs(render(recent, 90 * minute)))
    }

    @Test
    fun `empty or invalid data leaves a transparent image rather than a fake graph or loading panel`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now, quality = CgmQuality.INVALID)))
        assertTrue((0 until bitmap.height).all { y -> (0 until bitmap.width).all { x -> Color.alpha(bitmap.getPixel(x, y)) == 0 } })
    }

    @Test
    fun `measured dots stay visible and separated while current ring and outer transparency are retained`() {
        val bitmap = render((0..12).map { GlucoseSample(120.0, now - (12 - it) * 5 * minute) })
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(200, 139))
        assertEquals("no fill between genuine dots", 0, Color.alpha(bitmap.getPixel(24, 70)))
        val historicalHeight = (62..78).count { Color.alpha(bitmap.getPixel(136, it)) > 128 }
        assertTrue("historical dots must be larger than the previous 2.7-unit dots", historicalHeight >= 12)
        val currentPixels = (55..85).filter { Color.alpha(bitmap.getPixel(391, it)) > 0 }
        val currentHeight = currentPixels.last() - currentPixels.first() + 1
        assertTrue("current ring must be larger than historical dots", currentHeight > historicalHeight)
        assertEquals("current marker remains hollow", 0, Color.alpha(bitmap.getPixel(390, 70)))
    }

    @Test
    fun `signal gaps remain empty and latest point drifts left instead of being pinned to now`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now - 60 * minute), GlucoseSample(120.0, now - 5 * minute)))
        assertTrue((50..90).all { Color.alpha(bitmap.getPixel(280, it)) < 128 })
        assertTrue((386 until bitmap.width).all { x -> (50..90).all { y -> Color.alpha(bitmap.getPixel(x, y)) < 128 } })
    }

    @Test
    fun `sensor change is not joined even if timestamps are adjacent`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now - 5 * minute, sensorId = "old"), GlucoseSample(120.0, now, sensorId = "new")))
        assertTrue((50..90).all { Color.alpha(bitmap.getPixel(379, it)) < 128 })
    }

    @Test
    fun `subtle threshold guides labels and duration give context without a filled graph panel`() {
        val bitmap = render(listOf(GlucoseSample(120.0, now)))
        assertEquals("high boundary is clearly visible", 255, Color.alpha(bitmap.getPixel(200, 17)))
        assertEquals("low boundary is clearly visible", 255, Color.alpha(bitmap.getPixel(200, 113)))
        assertEquals("no numeric scale lane at the left", 0, Color.alpha(bitmap.getPixel(0, 80)))
        assertEquals("no target area fill", 0, Color.alpha(bitmap.getPixel(200, 70)))
        assertTrue("watchface graph has no time label", (0 until 300).all { x -> (0..14).all { y -> Color.alpha(bitmap.getPixel(x, y)) == 0 } })
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(200, 139))
    }

    private fun render(history: List<GlucoseSample>, duration: Long = 60 * minute): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 140, Bitmap.Config.ARGB_8888)
        WatchfaceCgmGraphRenderer.render(Canvas(bitmap), 400, 140, history, GraphTimeWindow.live(now, duration), CgmThresholds())
        return bitmap
    }
}
