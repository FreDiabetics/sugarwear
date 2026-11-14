package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGraphVisualQaTest {
    @Test
    fun `combined graph colored bands reach both horizontal surface edges`() {
        val state = stateFromHistory(listOf(120.0, 190.0))
        val config = WidgetInstanceConfiguration(graphCornerRadiusDp = 0)
        val bitmap = renderGlucoseGraphWidget(state, palette, 440, 440, now, pixelDensity = 2f, configuration = config)
        val graphTop = combinedWidgetMetrics(440, 440, 2f, config.glucoseGraphValuePercent).graphFrame.top
        val y = graphTop + 50
        assertEquals("left background must not overhang colored band", bitmap.getPixel(30, y), bitmap.getPixel(2, y))
        assertNotEquals("right scale band must extend through surface edge", palette.argb(WidgetColorRole.GRAPH_BACKGROUND), bitmap.getPixel(437, y))
    }

    @Test
    fun `compact glucose graph renders larger white delta and smaller gray unit`() {
        val source = stateWithTerminalValues(129.0, 124.0)
        val state = source.copy(glucose = source.glucose!!.copy(deltaMgDl = -5.0))
        val configuration = WidgetInstanceConfiguration()
        val bitmap = renderGlucoseGraphWidget(state, palette, 440, 440, now, pixelDensity = 2f, configuration = configuration)
        val baseline = combinedWidgetTopHeight(440, configuration.glucoseGraphValuePercent) - 12
        val whiteRows = mutableSetOf<Int>()
        val grayRows = mutableSetOf<Int>()
        for (y in baseline - 28 until baseline) {
            for (x in 24 until 230) {
                when (bitmap.getPixel(x, y)) {
                    android.graphics.Color.WHITE -> whiteRows.add(y)
                    0xFFB3B3B3.toInt() -> grayRows.add(y)
                }
            }
        }
        assertTrue("Delta must be white", whiteRows.isNotEmpty())
        assertTrue("Unit must be gray", grayRows.isNotEmpty())
        assertTrue("Unit must be smaller than delta", whiteRows.size > grayRows.size)
    }

    @Test
    fun `axis lanes retain opaque graph surface independently of transparent widget background`() {
        val bitmap =
            renderWidgetGraph(
                state = null,
                palette = palette,
                width = 440,
                height = 280,
                now = now,
                configuration = WidgetInstanceConfiguration(backgroundArgb = android.graphics.Color.TRANSPARENT),
                forceOpaqueGraphSurface = true,
            )
        assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(220, 275)))
        assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(425, 100)))
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val palette = WidgetColorStore.load(context)
    private val now = 2_000_000_000L
    private val density = 2f

    @Test
    fun `visual QA matrix renders exact launcher sizes without bitmap stretching`() {
        val outputDir = File("build/reports/widget-visual-qa").apply { mkdirs() }
        val matrix =
            listOf(
                "very-small" to (220 to 140),
                "small" to (320 to 200),
                "wide" to (760 to 260),
                "medium" to (440 to 280),
                "large" to (680 to 420),
                "very-large" to (960 to 620),
                "tall" to (360 to 560),
            )
        val state = stateWithTerminalValues(118.0, 124.0)

        matrix.forEach { (name, size) ->
            val bitmap = render(state, size.first, size.second)
            assertEquals(size.first, bitmap.width)
            assertEquals(size.second, bitmap.height)
            val file = File(outputDir, "android-graph-widget-$name.png")
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue(file.isFile)
            assertTrue(file.length() > 0L)
        }
    }

    @Test
    fun `widget colors first high and clears on first in-range reading`() {
        val width = 440
        val height = 280
        val layout = responsiveWidgetLayout(width / density, height / density)
        val metrics = widgetGraphMetrics(width, height, density, layout)
        val scale = widgetYScale(WidgetScaleMode.LOGARITHMIC, emptyList(), 70.0, 180.0)
        val sampleX = metrics.graphBounds.centerX()
        val sampleY = (metrics.graphBounds.top + scale.map(180.0, metrics.plot)) / 2f
        val x = sampleX.toInt().coerceIn(0, width - 1)
        val y = sampleY.toInt().coerceIn(0, height - 1)

        val oneHigh = render(stateFromHistory(listOf(120.0, 186.0)), width, height)
        val twoHigh = render(stateFromHistory(listOf(186.0, 191.0)), width, height)
        val backInRange = render(stateFromHistory(listOf(186.0, 191.0, 178.0)), width, height)

        assertEquals(oneHigh.getPixel(x, y), twoHigh.getPixel(x, y))
        assertNotEquals(oneHigh.getPixel(x, y), backInRange.getPixel(x, y))
    }

    @Test
    fun `latest dot remains inside plot and never reaches y axis gutter`() {
        val width = 760
        val height = 260
        val metrics = WidgetGraphLayoutMetrics.resolve(width, height, density, density)
        val maxDotCenter = metrics.plotRightPx - metrics.dotRadiusPx - metrics.dotOutlineWidthPx / 2f - metrics.gridStrokePx

        assertTrue(maxDotCenter + metrics.dotRadiusPx + metrics.dotOutlineWidthPx / 2f < metrics.yAxisLeftPx)
        assertTrue(metrics.plotRightPx < metrics.yAxisLeftPx)
        assertTrue(metrics.yAxisLeftPx < width)
    }

    private fun render(
        state: TherapyDisplayState,
        width: Int,
        height: Int,
    ): Bitmap =
        renderWidgetGraph(
            state = state,
            palette = palette,
            width = width,
            height = height,
            now = now,
            density = density,
            scaledDensity = density,
        )

    private fun stateWithTerminalValues(
        previous: Double,
        latest: Double,
    ): TherapyDisplayState = stateFromHistory(listOf(112.0, 116.0, previous, latest))

    private fun stateFromHistory(values: List<Double>): TherapyDisplayState {
        val points =
            values.mapIndexed { index, value ->
                val minutesBack = (values.lastIndex - index) * 5L
                GlucoseSample(valueMgDl = value, measuredAtEpochMs = now - minutesBack * 60_000L)
            }
        val latest = points.last()
        return TherapyDisplayState(
            source = DataSourceId.ANDROID_APS,
            receivedAtEpochMs = now,
            glucose =
                GlucoseState(
                    valueMgDl = latest.valueMgDl,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FLAT,
                    measuredAtEpochMs = latest.measuredAtEpochMs,
                ),
            glucoseHistory = points.dropLast(1),
            target = TargetState(lowMgDl = 80.0, highMgDl = 160.0),
        )
    }
}
