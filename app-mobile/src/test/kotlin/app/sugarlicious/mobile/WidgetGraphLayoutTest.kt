package app.sugarlicious.mobile

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGraphLayoutTest {
    @Test
    fun `system text scaling reserves larger axis lanes without clipping the plot`() {
        val layout = responsiveWidgetLayout(220f, 140f)
        val normal = widgetGraphMetrics(440, 280, 2f, layout, fontScale = 1f)
        val large = widgetGraphMetrics(440, 280, 2f, layout, fontScale = 2f)
        assertTrue(large.axisTextSizePx > normal.axisTextSizePx)
        assertTrue(large.plot.right < normal.plot.right)
        assertTrue(large.plot.height() > 0f)
        assertTrue(large.plot.left >= 0f && large.plot.right <= 440f)
    }

    private val density = 2f
    private val scaledDensity = 2f

    @Test
    fun `size classes cover launcher aspect variants`() {
        assertEquals(WidgetGraphSizeClass.VERY_SMALL, metrics(220, 140).sizeClass)
        assertEquals(WidgetGraphSizeClass.SMALL, metrics(320, 200).sizeClass)
        assertEquals(WidgetGraphSizeClass.MEDIUM, metrics(440, 280).sizeClass)
        assertEquals(WidgetGraphSizeClass.LARGE, metrics(680, 420).sizeClass)
        assertEquals(WidgetGraphSizeClass.EXTRA_WIDE, metrics(760, 260).sizeClass)
        assertEquals(WidgetGraphSizeClass.TALL, metrics(360, 560).sizeClass)
    }

    @Test
    fun `layout preserves exact requested bitmap dimensions`() {
        listOf(
            180 to 120,
            220 to 140,
            320 to 200,
            440 to 280,
            680 to 420,
            760 to 260,
            360 to 560,
        ).forEach { (width, height) ->
            val result = metrics(width, height)
            assertEquals(width, result.widthPx)
            assertEquals(height, result.heightPx)
        }
    }

    @Test
    fun `plot reserves right y axis and bottom x axis inside bitmap`() {
        listOf(
            220 to 140,
            320 to 200,
            440 to 280,
            680 to 420,
            760 to 260,
            360 to 560,
        ).forEach { (width, height) ->
            val result = metrics(width, height)
            assertTrue(result.plotLeftPx > 0f)
            assertTrue(result.plotRightPx < width)
            assertTrue(result.plotBottomPx < height)
            assertTrue(result.yAxisLeftPx > result.plotRightPx)
            assertTrue(result.yAxisLeftPx < width)
            assertTrue(result.plotBottomPx > result.plotTopPx)
            assertTrue(result.plotRightPx > result.plotLeftPx)
        }
    }

    @Test
    fun `high and low labels fit in reserved right gutter`() {
        listOf(
            220 to 140,
            320 to 200,
            440 to 280,
            680 to 420,
            760 to 260,
            360 to 560,
        ).forEach { (width, height) ->
            val result = metrics(width, height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = result.yAxisTextPx }
            val widest = maxOf(paint.measureText("160"), paint.measureText("80"))
            assertTrue(
                "Y labels clipped at ${width}x$height",
                result.yAxisLeftPx + widest <= width - result.outerInsetPx + 0.5f,
            )
        }
    }

    @Test
    fun `dot and line geometry stays capped as widgets grow`() {
        val medium = metrics(440, 280)
        val huge = metrics(960, 720)

        assertTrue(medium.dotRadiusPx / density <= 2.6f)
        assertTrue(huge.dotRadiusPx / density <= 2.6f)
        assertTrue(huge.dotOutlineWidthPx / density <= CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP)
        assertEquals(CgmGraphVisualPolicy.BOUNDARY_STROKE_DP, huge.boundaryStrokePx / density, 0.001f)
        assertEquals(CgmGraphVisualPolicy.GRID_STROKE_DP, huge.gridStrokePx / density, 0.001f)
        assertTrue(huge.axisTextPx / scaledDensity <= 9.5f)
        assertTrue(huge.yAxisTextPx / scaledDensity <= 10f)
    }

    @Test
    fun `current time line is plot edge with y axis space after it`() {
        val result = metrics(760, 260)
        val currentTimeX = result.plotRightPx

        assertTrue(currentTimeX < result.widthPx - result.outerInsetPx)
        assertTrue(result.yAxisLeftPx > currentTimeX)
        assertTrue(result.yAxisLeftPx < result.widthPx)
    }

    private fun metrics(
        width: Int,
        height: Int,
    ): WidgetGraphLayoutMetrics = WidgetGraphLayoutMetrics.resolve(width, height, density, scaledDensity)
}
