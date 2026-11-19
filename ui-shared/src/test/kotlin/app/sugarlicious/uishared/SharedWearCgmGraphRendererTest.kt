package app.sugarlicious.uishared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GraphAxisLayoutSpec
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.RelativeGraphTimeAxis
import app.sugarlicious.model.TrendVisualAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedWearCgmGraphRendererTest {
    @Test fun `Vigil live edge reaches the scale without an extra inset`() {
        val style = DirectToWatchGraphDefaults.style()
        val metrics = SharedWearCgmGraphRenderer.metrics(450, 250, 2f, CgmThresholds.DEFAULT, style)
        assertEquals(0f, metrics.axisLeftPx - metrics.liveX, .01f)
    }

    @Test fun `Vigil contour touches now tick and five minute age equals one sample interval`() {
        val style = DirectToWatchGraphDefaults.style()
        val metrics = SharedWearCgmGraphRenderer.metrics(450, 205, 2f, CgmThresholds.DEFAULT, style)
        val now = 2_000_000_000_000L
        val window = GraphTimeWindow.live(now, 2 * 3_600_000L)
        val contourRadius = (style.dotRadiusDp + style.currentDotOutlineWidthDp) * 2f
        val fresh = metrics.sampleXFor(window, now)
        val previous = metrics.sampleXFor(window, now - 300_000L)
        val older = metrics.sampleXFor(window, now - 600_000L)
        assertEquals(metrics.liveX, fresh + contourRadius, .001f)
        assertEquals(metrics.plot.width() / 24f, fresh - previous, .001f)
        assertEquals(fresh - previous, previous - older, .001f)
        assertEquals(fresh - previous, metrics.liveX - (previous + contourRadius), .001f)
        assertEquals("The fresh contour reaches the scale without a gap", 0f, metrics.axisLeftPx - (fresh + contourRadius), .001f)
    }

    @Test
    fun `current dot outline is permanent and widths are selected independently`() {
        val style =
            SharedWearCgmGraphStyle(
                dotOutlineEnabled = false,
                historicalDotOutlineEnabled = false,
                currentDotOutlineEnabled = false,
                historicalDotOutlineWidthDp = 0.4f,
                currentDotOutlineWidthDp = 2.2f,
            )

        assertEquals(null, effectiveDotOutlineWidthDp(style, isCurrent = false))
        assertEquals(2.2f, effectiveDotOutlineWidthDp(style, isCurrent = true))
    }

    @Test
    fun `Wear and Mobile use the identical canonical glucose scale`() {
        listOf(0.0, 40.0, 70.0, 80.0, 120.0, 160.0, 250.0, 400.0).forEach { value ->
            assertEquals(GlucoseGraphScale.ratio(value), WearCgmGraphScale.ratio(value), 0.000001)
        }
    }

    @Test
    fun `shared scale preserves canonical lower padding and four hundred ceiling`() {
        assertEquals(GlucoseGraphScale.ratio(0.0), WearCgmGraphScale.ratio(0.0), 0.0)
        assertEquals(GlucoseGraphScale.ratio(40.0), WearCgmGraphScale.ratio(40.0), 0.0)
        assertEquals(1.0, WearCgmGraphScale.ratio(400.0), 0.0)
        assertEquals(1.0, WearCgmGraphScale.ratio(401.0), 0.0)
    }

    @Test
    fun `wear and collector adapters receive identical outline safe geometry`() {
        listOf(192 to 112, 320 to 170, 454 to 220).forEach { (width, height) ->
            val wear = SharedWearCgmGraphRenderer.metrics(width, height, 2f, CgmThresholds.DEFAULT)
            val collector = SharedWearCgmGraphRenderer.metrics(width, height, 2f, CgmThresholds.DEFAULT)
            val dotInset = (2.4f + 0.95f) * 2f
            assertEquals(wear, collector)
            assertEquals(dotInset, wear.plot.left, 0.01f)
            assertEquals(dotInset, wear.plot.top, 0.01f)
            assertEquals(width - 58f - dotInset, wear.plot.right, 0.01f)
            assertEquals(0f, wear.visualBounds.left, 0.01f)
            assertEquals(0f, wear.visualBounds.top, 0.01f)
            assertEquals(width.toFloat(), wear.visualBounds.right, 0.01f)
            assertTrue(wear.visualBounds.bottom > wear.plot.bottom)
            assertTrue(wear.plot.height() > 0f)
            assertTrue(wear.highY < wear.lowY)
        }
    }

    @Test
    fun `timestamp positioning is stable for all supported periods`() {
        val now = 2_000_000_000_000L
        listOf(1, 2, 3, 6, 12, 24).forEach { hours ->
            val window = GraphTimeWindow.live(now, hours * 60L * 60_000L)
            val metrics = SharedWearCgmGraphRenderer.metrics(320, 180, 2f, CgmThresholds.DEFAULT)
            assertEquals(metrics.plot.right, metrics.xFor(window, now), 0.01f)
            assertEquals(metrics.plot.left, metrics.xFor(window, window.startEpochMs), 0.01f)
            assertTrue(metrics.xFor(window, now - 5 * 60_000L) < metrics.plot.right)
        }
    }

    @Test
    fun `fixed reading moves left while live clock advances`() {
        val readingAt = 2_000_000_000_000L
        val duration = 3L * 60L * 60_000L
        val metrics = SharedWearCgmGraphRenderer.metrics(320, 180, 2f, CgmThresholds.DEFAULT)
        val xAtArrival = metrics.xFor(GraphTimeWindow.live(readingAt, duration), readingAt)
        val xAfterOneMinute = metrics.xFor(GraphTimeWindow.live(readingAt + 60_000L, duration), readingAt)
        val xAfterFourMinutes = metrics.xFor(GraphTimeWindow.live(readingAt + 4 * 60_000L, duration), readingAt)

        assertTrue(xAfterOneMinute < xAtArrival)
        assertTrue(xAfterFourMinutes < xAfterOneMinute)
        assertEquals(metrics.plot.right, xAtArrival, 0.01f)
    }

    @Test
    fun `maximum scale aligns with target labels and has no tick`() {
        val metrics = SharedWearCgmGraphRenderer.metrics(200, 160, 1f, CgmThresholds.DEFAULT)
        val layout = wearMaximumScaleLayout(metrics, 200, 1f, 18f, SharedWearCgmGraphStyle())
        assertTrue(layout.labelX >= metrics.axisLeftPx)
        val expected =
            minOf(
                metrics.axisLeftPx + GraphAxisLayoutSpec.COMPACT.plotToTickGapDp + 1.5f,
                200f - GraphAxisLayoutSpec.COMPACT.outerEdgePaddingDp - 18f,
            )
        assertEquals(expected, layout.labelX, 0.01f)
    }

    @Test
    fun `Vigil maximum value shares the left anchor of the high and low labels`() {
        val style = DirectToWatchGraphDefaults.style()
        val metrics = SharedWearCgmGraphRenderer.metrics(450, 250, 2f, CgmThresholds.DEFAULT, style)
        val maximum = wearMaximumScaleLayout(metrics, 450, 2f, 30f, style)
        assertEquals(metrics.axisLeftPx + 4f, maximum.labelX, .01f)
    }

    @Test
    fun `renderer draws maximum scale and time labels`() {
        val now = 2_000_000_000_000L
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val palette =
            SharedWearCgmGraphPalette(
                background = Color.BLACK,
                targetArea = Color.DKGRAY,
                highArea = Color.DKGRAY,
                lowArea = Color.DKGRAY,
                highLine = Color.GRAY,
                lowLine = Color.GRAY,
                dotHigh = Color.WHITE,
                dotInRange = Color.WHITE,
                dotLow = Color.WHITE,
                dotOutline = Color.WHITE,
                axisText = Color.MAGENTA,
                axisTick = Color.CYAN,
                nowLine = Color.CYAN,
                border = Color.TRANSPARENT,
                predictionIob = Color.WHITE,
                predictionCob = Color.WHITE,
                predictionUam = Color.WHITE,
                predictionZeroTemp = Color.WHITE,
            )
        SharedWearCgmGraphRenderer.render(
            Canvas(bitmap),
            bitmap.width,
            bitmap.height,
            2f,
            2f,
            SharedWearCgmGraphInput(
                history = emptyList(),
                timeWindow = GraphTimeWindow.live(now, 3L * 60L * 60_000L),
                nowEpochMs = now,
                thresholds = CgmThresholds.DEFAULT,
                palette = palette,
                style = SharedWearCgmGraphStyle(timeAxisEnabled = true, borderEnabled = false),
            ),
        )

        assertEquals("400", WearCgmGraphScale.MAXIMUM_MG_DL.toInt().toString())
        assertTrue(
            RelativeGraphTimeAxis
                .ticks(now - 3L * 60L * 60_000L, now, now)
                .map { it.label }
                .any { it.isNotBlank() },
        )
    }

    @Test
    fun `all supplied trend vectors preserve their source canvas`() {
        val resourceIds = TrendVisualAsset.entries.map(TrendDrawableResources::forAsset)
        assertEquals(TrendVisualAsset.entries.size, resourceIds.distinct().size)
        TrendVisualAsset.entries.forEach { asset ->
            assertTrue(TrendVectorPaths.forAsset(asset).isNotEmpty())
        }
        assertEquals(2, TrendVectorPaths.forAsset(TrendVisualAsset.DOUBLE_UP).size)
        assertEquals(2, TrendVectorPaths.forAsset(TrendVisualAsset.DOUBLE_DOWN).size)
    }
}
