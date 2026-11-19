package app.sugarlicious.complications

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendArrowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrendComplicationIconTest {
    @Test
    fun `plain glucose ranged icon shares trend canvas without enlarging its ink`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val glucose = requireNotNull(TrendComplicationIcon.glucoseValueImage(context, 60)).image.loadDrawable(context)!!
        val trend = requireNotNull(TrendComplicationIcon.watchFaceBitmap(context, Trend.SINGLE_DOWN, 60))
        assertEquals(trend.width, glucose.intrinsicWidth)
        assertEquals(trend.height, glucose.intrinsicHeight)
    }

    @Test
    fun `legacy phone preview and Wear app geometry do not alter watchface icons`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val before = requireNotNull(TrendComplicationIcon.watchFaceBitmap(context, Trend.DOUBLE_UP, 60))
        context
            .getSharedPreferences("complication_appearance", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("2.trendScale", 200)
            .putInt("2.trendX", 50)
            .putInt("2.trendY", -50)
            .apply()
        context
            .getSharedPreferences("watch_display", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("trend_scale_percent", 200)
            .apply()
        val after = requireNotNull(TrendComplicationIcon.watchFaceBitmap(context, Trend.DOUBLE_UP, 60))
        assertTrue(before.sameAs(after))
    }

    @Test
    fun `watchface single and double arrows have identical visible heights on identical host canvases`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmaps = listOf(Trend.SINGLE_UP, Trend.DOUBLE_UP, Trend.SINGLE_DOWN, Trend.DOUBLE_DOWN).map { requireNotNull(TrendComplicationIcon.watchFaceBitmap(context, it, 60)) }

        fun inkHeight(bitmap: android.graphics.Bitmap): Int {
            val rows = (0 until bitmap.height).filter { y -> (0 until bitmap.width).any { x -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 } }
            return rows.last() - rows.first() + 1
        }
        assertEquals(1, bitmaps.map { it.width to it.height }.distinct().size)
        assertTrue(bitmaps.all { inkHeight(it) in 59..61 })
    }

    @Test
    fun `all complication trends preserve supplied canvas geometry`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected =
            mapOf(
                Trend.DOUBLE_UP to 125,
                Trend.SINGLE_UP to 60,
                Trend.FORTY_FIVE_UP to 60,
                Trend.FLAT to 60,
                Trend.FORTY_FIVE_DOWN to 60,
                Trend.SINGLE_DOWN to 60,
                Trend.DOUBLE_DOWN to 125,
            )
        expected.forEach { (trend, expectedWidth) ->
            val bitmap = TrendComplicationIcon.render(context, trend, 60)
            assertNotNull(bitmap)
            assertEquals(60, bitmap!!.height)
            assertEquals(expectedWidth, bitmap.width)
        }
    }

    @Test
    fun scaledDoubleArrowsKeepIntrinsicAspectRatioAtAllSupportedScales() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(70, 100, 150, 200).forEach { scale ->
            listOf(Trend.DOUBLE_UP, Trend.DOUBLE_DOWN).forEach { trend ->
                val bitmap = requireNotNull(TrendComplicationIcon.renderScaled(context, trend, 60, scale))
                assertEquals(125f / 60f, bitmap.width.toFloat() / bitmap.height, 0.02f)
            }
        }
    }

    @Test
    fun `double arrows are letterboxed into a square payload without horizontal compression`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(Trend.DOUBLE_UP, Trend.DOUBLE_DOWN).forEach { trend ->
            val wide = requireNotNull(TrendComplicationIcon.render(context, trend, 60))
            val normalized = requireNotNull(TrendComplicationIcon.watchFaceBitmap(context, trend, 60))
            assertEquals(normalized.height, normalized.width)
            assertEquals(wide.width, normalized.width)
            assertEquals(nonTransparentAspectRatio(wide), nonTransparentAspectRatio(normalized), 0.02f)
            assertTrue(
                (0 until normalized.height).any { y ->
                    android.graphics.Color.alpha(normalized.getPixel(normalized.width / 4, y)) > 0
                },
            )
            assertTrue(
                (0 until normalized.height).any { y ->
                    android.graphics.Color.alpha(normalized.getPixel(normalized.width * 3 / 4, y)) >
                        0
                },
            )
        }
    }

    @Test
    fun complicationScaleUsesMostOfHostIconAtDefaultAndStillGrows() {
        val small = TrendComplicationIcon.glyphFillFraction(70)
        val default = TrendComplicationIcon.glyphFillFraction(100)
        val large = TrendComplicationIcon.glyphFillFraction(200)

        assertEquals(0.35f, small, 0.001f)
        assertEquals(0.50f, default, 0.001f)
        assertTrue(default > small)
        assertEquals(1.0f, large, 0.001f)
    }

    @Test
    fun runtimeGlyphAt200PercentIsVisiblyTwiceThe100PercentHeight() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val style = TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt()).copy(outlineEnabled = false)
        val normal = requireNotNull(TrendComplicationIcon.renderScaled(context, Trend.FLAT, 34, 100, style = style))
        val large = requireNotNull(TrendComplicationIcon.renderScaled(context, Trend.FLAT, 34, 200, style = style))
        assertTrue(nonTransparentHeight(large) >= nonTransparentHeight(normal) * 1.8f)
    }

    @Test
    fun croppedRuntimeGlyphHasNoInvisibleLayoutPadding() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val padded = requireNotNull(TrendComplicationIcon.renderScaled(context, Trend.FLAT, 34, 80))
        val cropped = TrendComplicationIcon.cropTransparentPadding(padded)

        assertTrue(cropped.width < padded.width)
        assertTrue(cropped.height < padded.height)
        assertTrue((0 until cropped.height).any { y -> android.graphics.Color.alpha(cropped.getPixel(0, y)) > 0 })
        assertTrue((0 until cropped.height).any { y -> android.graphics.Color.alpha(cropped.getPixel(cropped.width - 1, y)) > 0 })
    }

    @Test fun `runtime renderer accepts the same outline style used by previews`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val style = TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt()).copy(outlineEnabled = false, alpha = 0.6f)
        val bitmap = TrendComplicationIcon.renderScaled(context, Trend.FLAT, 60, 100, style = style)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.hasAlpha())
    }

    private fun nonTransparentHeight(bitmap: android.graphics.Bitmap): Int {
        val rows =
            (0 until bitmap.height).filter { y ->
                (0 until bitmap.width).any { x -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 }
            }
        return if (rows.isEmpty()) 0 else rows.last() - rows.first() + 1
    }

    private fun nonTransparentAspectRatio(bitmap: android.graphics.Bitmap): Float {
        val xs =
            (0 until bitmap.width).filter { x ->
                (0 until bitmap.height).any { y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 }
            }
        return if (xs.isEmpty()) 0f else (xs.last() - xs.first() + 1).toFloat() / nonTransparentHeight(bitmap)
    }
}
