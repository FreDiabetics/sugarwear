package app.sugarlicious.mobile

import android.graphics.Paint
import app.sugarlicious.uishared.SystemTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGraphAxisBoundsTest {
    @Test
    fun `embedded graph time label ink stays inside its own rounded background`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val palette = WidgetColorStore.load(context, app.sugarlicious.model.AppearanceMode.DARK).with(WidgetColorRole.AXIS, android.graphics.Color.MAGENTA)
        listOf(1, 24).forEach { hours ->
            val bitmap =
                renderWidgetGraph(
                    null,
                    palette,
                    260,
                    160,
                    pixelDensity = 2f,
                    configuration = WidgetInstanceConfiguration(graphHours = hours, showTimeAxis = true, graphTimeAxisBold = true),
                    clipToWidgetShape = false,
                    graphCornerRadiusDp = 14.4f,
                )
            for (y in 135 until 160) {
                val inset = roundedWidgetContourInset(28.8f, 160f - y - .5f)
                for (x in 0 until 30) {
                    if (x + .5f < inset - 1f) assertTrue("${hours}h label spills outside its rounded graph at $x,$y", bitmap.getPixel(x, y) != android.graphics.Color.MAGENTA)
                }
            }
        }
    }

    @Test
    fun `value lane reserves bold label width without moving its left anchor into the graph`() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        SystemTypography.apply(paint, false)
        val metrics = widgetGraphMetrics(640, 360, 2f, responsiveWidgetLayout(320f, 180f), paint)
        SystemTypography.apply(paint, true)
        paint.textSize = metrics.axisTextSizePx
        assertTrue("bold value must fit to the right of its fixed anchor", metrics.yLabelLeftPx + paint.measureText("400") + metrics.edgeGapPx <= 640f)
        assertTrue("anchor must stay outside the graph", metrics.yLabelLeftPx > metrics.plot.right)
    }

    @Test
    fun `right value lane reserves only measured bold text plus edge and tick spacing`() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val metrics = widgetGraphMetrics(640, 360, 2f, responsiveWidgetLayout(320f, 180f), paint)
        SystemTypography.apply(paint, true)
        paint.textSize = metrics.axisTextSizePx
        val labelWidth = maxOf(paint.measureText("400"), paint.measureText("160"), paint.measureText("80"))
        assertTrue("value lane has an unnecessary extra gutter", 640f - metrics.yTickStartPx <= labelWidth + metrics.edgeGapPx + 6.01f)
        assertTrue(metrics.yLabelLeftPx + paint.measureText("400") < 640f)
    }

    @Test
    fun `embedded graph first time label starts at its own edge without outer widget padding`() {
        val labelWidth = 24f
        assertEquals(0f, widgetTimeAxisLabelX(4f, labelWidth, false, 320f, 0f, 0f) - labelWidth / 2f, .01f)
    }

    @Test
    fun `time labels clear the measured ink contour including compact composition`() {
        listOf(360f to 60f, 440f to 60f, 800f to 60f).forEach { (width, radius) ->
            val labelWidth = 32f
            val left = widgetTimeAxisLabelX(8f, labelWidth, false, width, 4f, radius)
            val right = widgetTimeAxisLabelX(width - 8f, labelWidth, true, width, 4f, radius)
            assertTrue(left - labelWidth / 2f >= radius)
            assertTrue(right <= width - radius)
        }
    }

    @Test
    fun `corner allowance uses actual label ink instead of unused font descender space`() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f }
        val ink = android.graphics.Rect().also { paint.getTextBounds("3h", 0, 2, it) }
        val inset = widgetTimeAxisInkInset(60f, 140f, 130f, "3h", paint)
        val fontInset = roundedWidgetContourInset(60f, 140f - 130f - paint.fontMetrics.descent)
        assertTrue("unused descender space must not push the label right", inset < fontInset)
        assertEquals(roundedWidgetContourInset(60f, 140f - 130f - ink.bottom), inset, 0.01f)
    }

    @Test
    fun `3h and jetzt labels stay fully within widget bounds`() {
        val density = 2f
        listOf(
            220 to 140,
            320 to 200,
            440 to 280,
            680 to 420,
            760 to 260,
            960 to 620,
            360 to 560,
        ).forEach { (width, height) ->
            val metrics = WidgetGraphLayoutMetrics.resolve(width, height, density, density)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = metrics.axisTextPx }
            val leftTextWidth = paint.measureText("3h")
            val rightTextWidth = paint.measureText("jetzt")

            assertTrue("3h clipped at ${width}x$height", metrics.plotLeftPx + leftTextWidth <= width)
            assertTrue("jetzt clipped at ${width}x$height", metrics.plotRightPx - rightTextWidth >= 0f)
            assertTrue("right axis reserve missing at ${width}x$height", metrics.plotRightPx + rightTextWidth < width + rightTextWidth)
        }
    }
}
