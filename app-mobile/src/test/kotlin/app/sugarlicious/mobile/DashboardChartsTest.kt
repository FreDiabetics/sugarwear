package app.sugarlicious.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.datasource.therapybridge.TherapyBridgePayloadAdapter
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.RangeExcursion
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyHistorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardChartsTest {
    @Test
    fun `transparent very high line leaves no antialias seam between identical range fills`() {
        val preferences = context.getSharedPreferences("seam_regression", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val yellow = Color.rgb(252, 201, 59)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, yellow)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_VERY_HIGH, yellow)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_VERY_HIGH_LINE, Color.TRANSPARENT)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
        try {
            val now = 50L * 60 * 60_000
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(205.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory = listOf(GlucoseSample(201.0, now - 300_000), GlucoseSample(205.0, now)),
                )
            val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, false, 2, clockEpochMs = now) }, 237)
            val yellowRows = (0 until bitmap.height).filter { bitmap.getPixel(180, it) == yellow }
            assertTrue(yellowRows.size > 5)
            (yellowRows.first()..yellowRows.last()).forEach { y -> assertEquals("range seam at y=$y", yellow, bitmap.getPixel(180, y)) }
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `metabolic scale ticks use the same y transform as IOB and COB curves`() {
        val plot = RectF(24f, 16f, 360f, 216f)
        val metrics = Paint().apply { textSize = 18f }.fontMetrics
        listOf(
            app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.STATIC, app.sugarlicious.model.GraphBounds(-2.0, 10.0)),
            app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.DYNAMIC, app.sugarlicious.model.GraphBounds(0.0, 8.2)),
            app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.LOGARITHMIC, app.sugarlicious.model.GraphBounds(0.0, 300.0)),
            app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.LOGARITHMIC_DYNAMIC, app.sugarlicious.model.GraphBounds(0.0, 93.0)),
        ).forEach { scale ->
            val layout = metabolicAxisTickLayout(plot, scale, metrics)
            assertEquals(plot.top, layout.maximumCenterY, 0.001f)
            assertEquals(plot.bottom, layout.minimumCenterY, 0.001f)
            val expectedZero = plot.bottom - scale.ratio(0.0).toFloat() * plot.height()
            assertEquals(expectedZero, layout.zeroCenterY, 0.001f)
            assertEquals(layout.maximumCenterY - (metrics.ascent + metrics.descent) / 2f, layout.maximumBaselineY, 0.001f)
        }
    }

    @Test fun `insulin activity series preserves finite negative AndroidAPS values`() {
        val points =
            listOf(
                TherapyHistorySample(1_000L, insulinActivityUnitsPerMinute = -0.012),
                TherapyHistorySample(2_000L, insulinActivityUnitsPerMinute = -0.008),
                TherapyHistorySample(3_000L, insulinActivityUnitsPerMinute = Double.NaN),
            )

        assertEquals(listOf(1_000L to -0.012, 2_000L to -0.008), finiteInsulinActivitySeries(points))
    }

    @Test fun `visible insulin activity retains supplied future samples`() {
        val values = listOf(1_000L to 0.01, 2_000L to 0.02, 3_000L to 0.03)

        assertEquals(values, visibleInsulinActivitySeries(values, 1_000L, 3_000L))
    }

    @Test fun `CGM insulin activity reaches now without crossing into prediction time`() {
        val values = listOf(1_000L to 0.01, 2_000L to 0.02)

        assertEquals(
            listOf(1_000L to 0.01, 2_000L to 0.02, 3_000L to 0.02),
            cgmInsulinActivitySeries(values, now = 3_000L, start = 0L, end = 5_000L),
        )
    }

    @Test fun `permanent current target reaches prediction viewport while temporary target does not`() {
        assertEquals(5_000L, targetDisplayEndEpochMs(2_000L, temporary = false, isCurrent = true, now = 3_000L, viewportEnd = 5_000L))
        assertEquals(3_000L, targetDisplayEndEpochMs(2_000L, temporary = true, isCurrent = true, now = 3_000L, viewportEnd = 5_000L))
    }

    @Test fun `insulin activity smoothing passes through samples without overshoot`() {
        val segments =
            monotoneCurveSegments(
                listOf(
                    0f to 10f,
                    10f to 4f,
                    30f to 7f,
                    50f to 2f,
                ),
            )

        assertEquals(3, segments.size)
        assertEquals(0f, segments.first().startX)
        assertEquals(10f, segments.first().startY)
        assertEquals(50f, segments.last().endX)
        assertEquals(2f, segments.last().endY)
        segments.forEach { segment ->
            val low = minOf(segment.startY, segment.endY)
            val high = maxOf(segment.startY, segment.endY)
            assertTrue(segment.control1Y in low..high)
            assertTrue(segment.control2Y in low..high)
        }
    }

    @Test fun `insulin activity smoothing ignores duplicate timestamps deterministically`() {
        val segments =
            monotoneCurveSegments(
                listOf(
                    0f to 10f,
                    10f to 8f,
                    10f to 99f,
                    20f to 6f,
                ),
            )

        assertEquals(2, segments.size)
        assertEquals(8f, segments.first().endY)
        assertEquals(8f, segments.last().startY)
    }

    @Test
    fun `insulin activity visual smoothing reduces an isolated peak while retaining endpoints`() {
        val smoothed =
            smoothActivitySeries(
                listOf(
                    1_000L to 0.0,
                    2_000L to 1.0,
                    3_000L to 0.0,
                ),
            )

        assertEquals(listOf(1_000L to 0.0, 2_000L to 0.5, 3_000L to 0.0), smoothed)
    }

    @Test
    fun `live edge extension cannot alter the last observed activity value`() {
        val rendered = smoothActivityToLiveEdge(listOf(1_000L to 0.0, 2_000L to 1.0), 3_000L, 0L, 4_000L)

        assertEquals(listOf(1_000L to 0.0, 2_000L to 1.0, 3_000L to 1.0), rendered)
    }

    @Test
    fun `insulin activity smoothing is independent of zoom viewport`() {
        val values =
            listOf(
                1_000L to 0.0,
                2_000L to 0.4,
                3_000L to 1.0,
                4_000L to 0.4,
                5_000L to 0.0,
            )

        val wide = smoothActivityToLiveEdge(values, liveEdge = 5_000L, start = 1_000L, end = 5_000L)
        val zoomed = smoothActivityToLiveEdge(values, liveEdge = 5_000L, start = 2_000L, end = 4_000L)

        assertEquals(wide, zoomed)
    }

    @Test
    fun `insulin activity lane shares the IOB zero instead of an arbitrary vertical offset`() {
        val plot = RectF(0f, 0f, 100f, 100f)
        val scale =
            app.sugarlicious.model.GraphAxisScale(
                app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC,
                app.sugarlicious.model.GraphBounds(0.0, 1.0),
            )

        assertEquals(100f, mapActivityY(0.0, scale, plot), 0.001f)
        assertEquals(20f, mapActivityY(1.0, scale, plot), 0.001f)
    }

    @Test
    fun `CGM insulin activity follows AndroidAPS zero baseline and eighty percent height`() {
        val plot = RectF(0f, 0f, 100f, 100f)
        val scale = cgmInsulinActivityScale(listOf(-0.25, 0.5))

        assertEquals(100f, mapCgmActivityY(0.0, scale, plot), 0.001f)
        assertEquals(20f, mapCgmActivityY(0.5, scale, plot), 0.001f)
    }

    @Test
    fun `CGM insulin activity is confined to the configured target range`() {
        val dataPlot = RectF(10f, 5f, 190f, 195f)

        val band = cgmInsulinActivityBand(dataPlot, targetTop = 42f, targetBottom = 138f)

        assertEquals(RectF(10f, 42f, 190f, 138f), band)
    }

    @Test
    fun `CGM insulin activity scale uses largest absolute AndroidAPS value`() {
        val scale = cgmInsulinActivityScale(listOf(-0.75, 0.5))

        assertEquals(0.0, scale.bounds.minimum, 0.0)
        assertEquals(0.75, scale.bounds.maximum, 0.0)
    }

    @Test fun `glucose chart renders source target and prediction streams`() {
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(129.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(100.0, 118.0, 112.0, 129.0).mapIndexed { index, value ->
                        GlucoseSample(
                            value,
                            now - (3 - index) * 15 * 60_000L,
                        )
                    },
                glucosePredictions =
                    listOf(
                        GlucosePrediction(
                            PredictionKind.IOB,
                            listOf(129.0, 120.0, 108.0).mapIndexed { index, value ->
                                GlucoseSample(
                                    value,
                                    now + index * 5 * 60_000L,
                                )
                            },
                        ),
                    ),
                target = TargetState(80.0, 160.0, valueMgDl = 100.0),
            )
        val viewport =
            ChartViewport(6).apply {
                setFutureWindow(15L * 60_000L)
            }
        val bitmap =
            render(
                GlucoseDashboardChart(context = context, sharedViewport = viewport).apply {
                    bind(
                        state = state,
                        unit = GlucoseUnit.MG_DL,
                        showPredictions = true,
                        durationHours = 6,
                        showTargetRange = true,
                        showTargetValue = true,
                        showPredictionIob = true,
                    )
                },
                230,
            )
        val inRangePixels =
            count(bitmap) {
                Color.green(it) > 150 && Color.green(it) > Color.red(it) * 1.3
            }
        val predictionPixels = count(bitmap) { Color.blue(it) > 180 && Color.green(it) > 120 }
        // Scale changes move the thin target marker across subpixels; test presence, not fixed coverage.
        assertTrue("target marker missing", inRangePixels > 0)
        assertTrue("source observations missing", count(bitmap) { Color.red(it) > 235 && Color.green(it) > 235 && Color.blue(it) > 235 } > state.glucoseHistory.size)
        assertTrue("prediction=$predictionPixels", predictionPixels > 2)
    }

    @Test fun `cgm graph renders current time divider when predictions are enabled`() {
        val preferences = context.getSharedPreferences("cgm_prediction_divider", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val divider = Color.rgb(17, 231, 199)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_NOW_LINE, divider)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
        val now = System.currentTimeMillis()
        val viewport = ChartViewport(6).apply { setFutureWindow(60L * 60_000L, now) }
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(120.0, now)),
                glucosePredictions =
                    listOf(
                        GlucosePrediction(
                            PredictionKind.IOB,
                            listOf(GlucoseSample(125.0, now + 5L * 60_000L)),
                        ),
                    ),
                target = TargetState(80.0, 160.0),
            )

        val bitmap =
            render(
                GlucoseDashboardChart(context = context, sharedViewport = viewport).apply {
                    bind(
                        state = state,
                        unit = GlucoseUnit.MG_DL,
                        showPredictions = true,
                        durationHours = 6,
                        showTargetRange = true,
                        showPredictionIob = true,
                    )
                },
                230,
            )

        val dividerPixels =
            count(bitmap) {
                kotlin.math.abs(Color.red(it) - Color.red(divider)) <= 32 &&
                    kotlin.math.abs(Color.green(it) - Color.green(divider)) <= 32 &&
                    kotlin.math.abs(Color.blue(it) - Color.blue(divider)) <= 32
            }
        assertTrue("divider pixels=$dividerPixels", dividerPixels > 8)
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test fun `in range picker drives the graph while high and low stay transparent without sustained excursion`() {
        val preferences = context.getSharedPreferences("chart_region_colors", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val high = Color.rgb(25, 40, 220)
        val inRange = Color.rgb(30, 210, 70)
        val low = Color.rgb(225, 35, 55)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, high)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_IN_RANGE, inRange)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_LOW, low)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(123.0, now)),
                target = TargetState(80.0, 160.0),
            )
        val bitmap =
            render(
                GlucoseDashboardChart(context).apply {
                    bind(
                        state = state,
                        unit = GlucoseUnit.MG_DL,
                        showPredictions = false,
                        durationHours = 3,
                        showTargetRange = true,
                    )
                },
                230,
            )

        assertTrue(count(bitmap) { it == inRange } > 100)
        assertEquals(0, count(bitmap) { it == high })
        assertEquals(0, count(bitmap) { it == low })
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test fun `range backgrounds continue across the prediction side`() {
        val preferences = context.getSharedPreferences("chart_prediction_range_color", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val inRange = Color.rgb(31, 211, 71)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_IN_RANGE, inRange)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(120.0, now)),
                target = TargetState(80.0, 160.0),
            )
        val viewport = ChartViewport(6).apply { setFutureWindow(60L * 60_000L, now) }
        val bitmap =
            render(
                GlucoseDashboardChart(context, sharedViewport = viewport).apply {
                    bind(state, GlucoseUnit.MG_DL, false, 6, showTargetRange = true, clockEpochMs = now)
                },
                230,
            )

        val rightPredictionLanePixels =
            (0 until bitmap.height).sumOf { y ->
                ((bitmap.width * 0.90).toInt() until (bitmap.width * 0.96).toInt()).count { x -> bitmap.getPixel(x, y) == inRange }
            }
        assertTrue("right prediction range pixels=$rightPredictionLanePixels", rightPredictionLanePixels > 20)
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test fun `jetzt label is anchored at the prediction divider instead of the right edge`() {
        assertEquals(360f, currentTimeLabelAnchor(dividerX = 360f, plotRight = 420f, edgePadding = 3f))
        assertEquals(417f, currentTimeLabelAnchor(dividerX = 425f, plotRight = 420f, edgePadding = 3f))
    }

    @Test fun `range excursion follows the first valid out of range value`() {
        val now = 10_000_000L

        fun samples(vararg values: Double) =
            values.mapIndexed { index, value ->
                GlucoseSample(value, now - (values.lastIndex - index) * 5 * 60_000L)
            }

        assertEquals(RangeExcursion.LOW, sustainedRangeExcursion(samples(79.0), 80.0, 160.0))
        assertEquals(RangeExcursion.LOW, sustainedRangeExcursion(samples(79.0, 70.0), 80.0, 160.0))
        assertEquals(RangeExcursion.HIGH, sustainedRangeExcursion(samples(161.0, 172.0), 80.0, 160.0))
        assertNull(sustainedRangeExcursion(samples(161.0, 159.0), 80.0, 160.0))
    }

    @Test fun `glucose chart marks stale signal period with configured signal loss color`() {
        val preferences = context.getSharedPreferences("chart_signal_loss_color", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val signalLoss = Color.rgb(210, 30, 60)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_SIGNAL_LOSS, signalLoss)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        val now = System.currentTimeMillis()
        val measured = now - 20L * 60_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(118.0, GlucoseUnit.MG_DL, measuredAtEpochMs = measured),
                glucoseHistory =
                    listOf(
                        GlucoseSample(116.0, measured - 5 * 60_000L),
                        GlucoseSample(118.0, measured),
                    ),
                target = TargetState(80.0, 160.0),
            )
        val bitmap =
            render(
                GlucoseDashboardChart(context).apply {
                    bind(state, GlucoseUnit.MG_DL, false, 3, showTargetRange = false, clockEpochMs = now)
                },
                230,
            )

        assertTrue(count(bitmap) { it == signalLoss } > 100)
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test fun `stale signal keeps confirmed high background until a new valid transition`() {
        val preferences = context.getSharedPreferences("chart_stale_high_color", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val high = Color.rgb(213, 151, 17)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, high)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        val now = System.currentTimeMillis()
        val measured = now - 20L * 60_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(176.0, GlucoseUnit.MG_DL, measuredAtEpochMs = measured),
                glucoseHistory =
                    listOf(
                        GlucoseSample(170.0, measured - 5L * 60_000L),
                        GlucoseSample(176.0, measured),
                    ),
                target = TargetState(80.0, 160.0),
            )
        val bitmap =
            render(
                GlucoseDashboardChart(context).apply {
                    bind(state, GlucoseUnit.MG_DL, false, 3, showTargetRange = true, clockEpochMs = now)
                },
                230,
            )

        assertTrue(
            count(bitmap) { Color.red(it) > 80 && Color.green(it) > 45 && Color.blue(it) < 45 } > 100,
        )
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test fun `glucose chart keeps future predictions at their real timestamps right of boundary`() {
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now - 10 * 60_000L,
                glucose =
                    GlucoseState(
                        121.0,
                        GlucoseUnit.MG_DL,
                        measuredAtEpochMs = now - 10 * 60_000L,
                    ),
                glucoseHistory =
                    listOf(
                        GlucoseSample(118.0, now - 20 * 60_000L),
                        GlucoseSample(121.0, now - 10 * 60_000L),
                    ),
                glucosePredictions =
                    listOf(
                        GlucosePrediction(
                            PredictionKind.IOB,
                            listOf(
                                GlucoseSample(121.0, now),
                                GlucoseSample(124.0, now + 5 * 60_000L),
                                GlucoseSample(127.0, now + 10 * 60_000L),
                            ),
                        ),
                    ),
            )
        val viewport = ChartViewport(1).apply { setFutureWindow(15 * 60_000L) }
        val bitmap =
            render(
                GlucoseDashboardChart(context, sharedViewport = viewport).apply {
                    bind(
                        state = state,
                        unit = GlucoseUnit.MG_DL,
                        showPredictions = true,
                        durationHours = 1,
                        showPredictionIob = true,
                        clockEpochMs = now,
                    )
                },
                230,
            )

        val predictionPixels =
            count(bitmap) {
                Color.blue(it) > 180 && Color.green(it) > 120
            }
        assertTrue("cached prediction=$predictionPixels", predictionPixels > 2)
    }

    @Test fun `target value color is opaque and brighter than a transparent target band`() {
        val translucentGreen = Color.argb(18, 12, 90, 30)
        val result = luminousTargetValueColor(translucentGreen)
        assertEquals(255, Color.alpha(result))
        assertTrue(Color.green(result) > Color.green(translucentGreen))
    }

    @Test
    fun `target history becomes one continuous step path with vertical transitions`() {
        val paths =
            targetStepPaths(
                samples =
                    listOf(
                        TargetSample(100.0, 0L, 10_000L),
                        TargetSample(120.0, 10_000L, 20_000L),
                        TargetSample(95.0, 20_000L, 30_000L),
                    ),
                start = 0L,
                end = 30_000L,
            )

        assertEquals(
            listOf(
                listOf(
                    0L to 100.0,
                    10_000L to 100.0,
                    10_000L to 120.0,
                    20_000L to 120.0,
                    20_000L to 95.0,
                    30_000L to 95.0,
                ),
            ),
            paths,
        )
    }

    @Test
    fun `missing target history remains a visible gap`() {
        val paths =
            targetStepPaths(
                samples =
                    listOf(
                        TargetSample(100.0, 0L, 10_000L),
                        TargetSample(120.0, 200_000L, 300_000L),
                    ),
                start = 0L,
                end = 300_000L,
            )

        assertEquals(2, paths.size)
    }

    @Test
    fun `temporary target returns to profile exactly at its explicit end`() {
        val paths =
            targetStepPaths(
                samples =
                    listOf(
                        TargetSample(100.0, 0L, 10_000L, temporary = false),
                        TargetSample(150.0, 10_000L, 20_000L, temporary = true),
                        // The profile value is first observed two minutes later. The renderer must still
                        // connect at the authoritative temp-target end.
                        TargetSample(100.0, 140_000L, 300_000L, temporary = false),
                    ),
                start = 0L,
                end = 300_000L,
            )

        assertEquals(
            listOf(
                0L to 100.0,
                10_000L to 100.0,
                10_000L to 150.0,
                20_000L to 150.0,
                20_000L to 100.0,
                300_000L to 100.0,
            ),
            paths.single(),
        )
    }

    @Test fun `metabolic chart renders independent iob and cob areas`() {
        val now = System.currentTimeMillis()
        val history =
            (0..5).map { index ->
                TherapyHistorySample(
                    now - (5 - index) * 15 * 60_000L,
                    totalIob = 0.7 + index * 0.25,
                    cobGrams = 8.0 + index * 5,
                    insulinActivityUnitsPerMinute = 0.008 + index * 0.002,
                    smbUnits = if (index == 2) 0.3 else null,
                )
            }
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = InsulinState(totalIob = 1.95),
                carbs = CarbState(cobGrams = 33.0),
                therapyHistory = history,
            )
        val bitmap = render(MetabolicDashboardChart(context).apply { bind(state, 6) }, 260)
        val bluePixels = count(bitmap) { Color.blue(it) > 170 && Color.blue(it) > Color.red(it) * 1.2 }
        val orangePixels = count(bitmap) { Color.red(it) > 170 && Color.green(it) > 70 && Color.blue(it) < 120 }
        val smbPixels = count(bitmap) { Color.green(it) > 170 && Color.blue(it) > 150 && Color.red(it) < 100 }
        val activityPixels = count(bitmap) { Color.red(it) > 190 && Color.green(it) > 150 && Color.blue(it) < 120 }
        assertTrue("blue=$bluePixels", bluePixels > 20)
        assertTrue("orange=$orangePixels", orangePixels > 20)
        // Rasterization may move one antialiased edge pixel when the Y transform changes.
        assertTrue("smb=$smbPixels", smbPixels >= 9)
        assertTrue("activity=$activityPixels", activityPixels > 4)
    }

    @Test fun `metabolic chart renders the current time divider through iob and cob`() {
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = InsulinState(totalIob = 1.2),
                carbs = CarbState(cobGrams = 18.0),
                therapyHistory =
                    listOf(
                        TherapyHistorySample(now - 30 * 60_000L, totalIob = 1.4, cobGrams = 24.0),
                        TherapyHistorySample(now, totalIob = 1.2, cobGrams = 18.0),
                    ),
            )
        val viewport = ChartViewport(6).apply { setFutureWindow(60L * 60_000L) }
        val bitmap =
            render(
                MetabolicDashboardChart(context, sharedViewport = viewport).apply {
                    bind(state, 6, scaleRightBlend = 1f, showPredictionDivider = true)
                },
                260,
            )
        val density = context.resources.displayMetrics.density
        val plotLeft = 0.5f * density
        val plotRight = bitmap.width - 0.5f * density - 38f * density
        val dividerX = (plotLeft + (plotRight - plotLeft) * 6f / 7f).toInt()
        val divider = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_DIVIDER)
        val dividerPixels = countNearColumn(bitmap, dividerX, divider, tolerance = 96)

        assertTrue("dividerX=$dividerX pixels=$dividerPixels", dividerPixels > 8)
    }

    @Test fun `metabolic value streams reach now but never enter prediction space`() {
        val now = 40_000_000L
        val history =
            listOf(
                TherapyHistorySample(now - 10 * 60_000L, totalIob = 1.4, cobGrams = 24.0, insulinActivityUnitsPerMinute = 0.018),
                TherapyHistorySample(now - 5 * 60_000L, totalIob = 1.2, cobGrams = 18.0, insulinActivityUnitsPerMinute = 0.014),
            )
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = InsulinState(totalIob = 1.2),
                carbs = CarbState(cobGrams = 18.0),
                therapyHistory = history,
            )
        val viewport = ChartViewport(6).apply { setFutureWindow(60L * 60_000L) }
        val bitmap =
            render(
                MetabolicDashboardChart(context, sharedViewport = viewport).apply {
                    bind(state, 6, scaleRightBlend = 1f, clockEpochMs = now)
                },
                260,
            )
        val density = context.resources.displayMetrics.density
        val plotLeft = 0.5f * density
        val plotRight = bitmap.width - 0.5f * density - 38f * density
        val nowX = (plotLeft + (plotRight - plotLeft) * 6f / 7f).toInt()
        val streamColors =
            listOf(
                SugarliciousColors.argb(SugarliciousColorRole.GRAPH_IOB),
                SugarliciousColors.argb(SugarliciousColorRole.GRAPH_COB),
                SugarliciousColors.argb(SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY),
            )

        streamColors.forEach { color ->
            assertTrue("stream ${color.toUInt().toString(16)} does not reach now", countNearColumn(bitmap, nowX, color, 30) > 0)
        }
    }

    @Test fun `metabolic series carries its last real value only to the live edge`() {
        val actual =
            extendSeriesToLiveEdge(
                values = listOf(100L to 1.4, 200L to 1.2, 450L to 99.0),
                liveEdge = 300L,
                viewportStart = 0L,
                viewportEnd = 500L,
            )

        assertEquals(listOf(100L to 1.4, 200L to 1.2, 300L to 1.2), actual)
    }

    @Test fun `metabolic markers retain their AndroidAPS size thresholds`() {
        assertEquals(7f, toolkitSmbMarkerSide(0.1))
        assertEquals(11f, toolkitSmbMarkerSide(0.25))
        assertEquals(11f, toolkitSmbMarkerSide(0.5))
    }

    @Test fun `static metabolic scales keep cob iob and activity values fixed while viewport pans`() {
        val session = app.sugarlicious.model.GraphScaleSession()
        val now = 20_000_000L
        val all =
            listOf(
                TherapyHistorySample(now - 6 * 60 * 60_000L, totalIob = 0.5, cobGrams = 10.0, insulinActivityUnitsPerMinute = 0.01),
                TherapyHistorySample(now - 3 * 60 * 60_000L, totalIob = 2.0, cobGrams = 80.0, insulinActivityUnitsPerMinute = 0.05),
                TherapyHistorySample(now, totalIob = 1.0, cobGrams = 30.0, insulinActivityUnitsPerMinute = 0.02),
            )
        val first = resolveMetabolicScales(session, app.sugarlicious.model.CgmGraphScaleMode.STATIC, all, all.take(2))
        val second = resolveMetabolicScales(session, app.sugarlicious.model.CgmGraphScaleMode.STATIC, all, all.drop(1))

        assertEquals(first.iob.ratio(1.0), second.iob.ratio(1.0), 0.0)
        assertEquals(first.cob.ratio(30.0), second.cob.ratio(30.0), 0.0)
        assertEquals(first.activity.ratio(0.02), second.activity.ratio(0.02), 0.0)
    }

    @Test fun `dynamic metabolic scales react to different visible cob and activity ranges`() {
        val session = app.sugarlicious.model.GraphScaleSession()
        val now = 20_000_000L
        val low =
            listOf(
                TherapyHistorySample(now - 10 * 60_000L, totalIob = 0.5, cobGrams = 10.0, insulinActivityUnitsPerMinute = 0.01),
                TherapyHistorySample(now, totalIob = 1.0, cobGrams = 20.0, insulinActivityUnitsPerMinute = 0.02),
            )
        val high =
            listOf(
                TherapyHistorySample(now - 10 * 60_000L, totalIob = 2.0, cobGrams = 60.0, insulinActivityUnitsPerMinute = 0.05),
                TherapyHistorySample(now, totalIob = 4.0, cobGrams = 100.0, insulinActivityUnitsPerMinute = 0.10),
            )
        val all = low + high
        val first = resolveMetabolicScales(session, app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC, all, low)
        val second = resolveMetabolicScales(session, app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC, all, high)

        assertTrue(first.cob.bounds != second.cob.bounds)
        assertTrue(first.activity.bounds != second.activity.bounds)
    }

    @Test fun `metabolic insulin activity ignores stale persisted scale and fills eighty percent at current maximum`() {
        val session =
            app.sugarlicious.model.GraphScaleSession().apply {
                useConfiguredBounds(app.sugarlicious.model.GraphAxis.INSULIN_ACTIVITY, app.sugarlicious.model.GraphBounds(0.0, 1.0))
            }
        val points =
            listOf(
                TherapyHistorySample(1_000L, insulinActivityUnitsPerMinute = 0.01),
                TherapyHistorySample(2_000L, insulinActivityUnitsPerMinute = 0.05),
            )

        val scales =
            resolveMetabolicScales(
                session = session,
                mode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                allPoints = points,
                visiblePoints = points,
            )

        assertEquals(1.0, scales.activity.ratio(0.05), 0.000001)
        assertEquals(0.8, scales.activity.ratio(0.04), 0.000001)
    }

    @Test fun `activity zero aligns with negative IOB axis and peak stays within its space`() {
        val plot = RectF(0f, 0f, 100f, 100f)
        val activity = app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.DYNAMIC, app.sugarlicious.model.GraphBounds(-.01, .04))
        val iob = app.sugarlicious.model.GraphAxisScale(CgmGraphScaleMode.DYNAMIC, app.sugarlicious.model.GraphBounds(-2.0, 8.0))
        assertEquals(80f, mapActivityY(0.0, activity, plot, iob), .001f)
        assertTrue(mapActivityY(.04, activity, plot, iob) in 0f..80f)
        assertTrue(mapActivityY(-.01, activity, plot, iob) in 80f..100f)
    }

    @Test fun `restored static IOB maximum cannot hide configured ten units`() {
        val session = app.sugarlicious.model.GraphScaleSession()
        session.useConfiguredBounds(app.sugarlicious.model.GraphAxis.IOB, app.sugarlicious.model.GraphBounds(0.0, 1.0))
        val points = listOf(TherapyHistorySample(1_000L, totalIob = 0.8))
        val scales = resolveMetabolicScales(session, CgmGraphScaleMode.STATIC, points, points, iobMaximumUnits = 10.0)
        assertEquals(10.0, scales.iob.bounds.maximum, 0.0)
    }

    @Test fun `dynamic iob ignores the configured static maximum`() {
        val scales =
            resolveMetabolicScales(
                session = app.sugarlicious.model.GraphScaleSession(),
                mode = app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC,
                allPoints = listOf(TherapyHistorySample(1_000L, totalIob = 1.0)),
                visiblePoints = listOf(TherapyHistorySample(1_000L, totalIob = 1.0)),
                iobMaximumUnits = 12.0,
            )

        assertTrue(scales.iob.bounds.maximum <= 2.0)
    }

    @Test
    fun `IOB graph never expands below minus two units`() {
        val points =
            listOf(
                TherapyHistorySample(1_000L, totalIob = -8.0),
                TherapyHistorySample(2_000L, totalIob = 3.0),
            )
        val scales =
            resolveMetabolicScales(
                session = app.sugarlicious.model.GraphScaleSession(),
                mode = app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC,
                allPoints = points,
                visiblePoints = points,
                iobMaximumUnits = 10.0,
            )

        assertEquals(-2.0, scales.iob.bounds.minimum, 0.0)
        assertEquals(0.0, scales.iob.ratio(-8.0), 0.0)
    }

    @Test fun `configured IOB and COB graph maxima use independent axes and retain negative IOB`() {
        val points =
            listOf(
                TherapyHistorySample(1_000L, totalIob = -2.0, cobGrams = 20.0),
                TherapyHistorySample(2_000L, totalIob = 3.0, cobGrams = 240.0),
            )
        val scales =
            resolveMetabolicScales(
                session = app.sugarlicious.model.GraphScaleSession(),
                mode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                allPoints = points,
                visiblePoints = points,
                iobMaximumUnits = 12.0,
                cobMaximumGrams = 180.0,
            )

        assertTrue(scales.iob.bounds.minimum <= -2.0)
        assertTrue(scales.iob.bounds.maximum >= 12.0)
        assertTrue(scales.cob.bounds.maximum >= 240.0)
        assertTrue(scales.iob.ratio(3.0) != scales.cob.ratio(20.0))
    }

    @Test fun `IOB and COB resolve with independent scale modes`() {
        val points = listOf(TherapyHistorySample(1_000L, totalIob = 1.0, cobGrams = 20.0))
        val scales =
            resolveMetabolicScales(
                session = app.sugarlicious.model.GraphScaleSession(),
                iobMode = app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC,
                cobMode = app.sugarlicious.model.CgmGraphScaleMode.LOGARITHMIC,
                allPoints = points,
                visiblePoints = points,
            )

        assertEquals(app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC, scales.iob.mode)
        assertEquals(app.sugarlicious.model.CgmGraphScaleMode.LOGARITHMIC, scales.cob.mode)
    }

    @Test fun `mobile BR TBR stays inside a compact top tenth of the graph`() {
        val bitmap = Bitmap.createBitmap(420, 220, Bitmap.Config.ARGB_8888)
        val plot = RectF(0f, 0f, 420f, 200f)
        drawMobileBasalOverlay(
            Canvas(bitmap),
            plot,
            0L,
            600_000L,
            listOf(
                TherapyHistorySample(0L, basalUnitsPerHour = 1.6, baseBasalUnitsPerHour = .8),
                TherapyHistorySample(600_000L, basalUnitsPerHour = 1.6, baseBasalUnitsPerHour = .8),
            ),
            true,
            true,
            1f,
            Color.CYAN,
        )
        assertTrue(count(bitmap) { Color.alpha(it) > 0 } > 0)
        for (y in 24 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                assertEquals("BR/TBR occupied CGM space at $x,$y", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
        }
    }

    @Test fun `basal overlay uses the complete graph plot`() {
        val plot = RectF(12f, 8f, 420f, 210f)

        assertEquals(plot, cgmBasalOverlayBounds(plot))
    }

    @Test fun `dynamic cob maximum includes relevant meal without fixed fifty gram padding`() {
        val now = 12 * 60 * 60_000L
        val start = now - 3 * 60 * 60_000L
        val events =
            listOf(
                TherapyEvent("relevant", TherapyEventKind.MEAL_CARBS, start - 60 * 60_000L, 80.0, carbsGrams = 80.0),
                TherapyEvent("expired", TherapyEventKind.MEAL_CARBS, start - 8 * 60 * 60_000L, 300.0, carbsGrams = 300.0),
            )
        val scales =
            resolveMetabolicScales(
                session = app.sugarlicious.model.GraphScaleSession(),
                mode = app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC,
                allPoints = listOf(TherapyHistorySample(now, cobGrams = 20.0)),
                visiblePoints = listOf(TherapyHistorySample(now, cobGrams = 20.0)),
                therapyEvents = events,
                viewportStartEpochMs = start,
                viewportEndEpochMs = now,
            )

        assertTrue(scales.cob.bounds.maximum >= 80.0)
        assertTrue(scales.cob.bounds.maximum < 130.0)
    }

    @Test fun `static axis bounds survive graph session recreation`() {
        val preferences = context.getSharedPreferences("static_graph_scales", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = StaticGraphScaleStore(preferences)
        val firstSession = app.sugarlicious.model.GraphScaleSession()
        store.restore(firstSession)
        val first =
            firstSession.resolve(
                axis = app.sugarlicious.model.GraphAxis.COB,
                mode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                seedValues = listOf(0.0, 40.0),
                visibleValues = listOf(0.0, 40.0),
                fallbackBounds = app.sugarlicious.model.GraphBounds(0.0, 10.0),
                minimumSpan = 1.0,
            )
        store.persist(app.sugarlicious.model.GraphAxis.COB, first)

        val recreatedSession = app.sugarlicious.model.GraphScaleSession()
        store.restore(recreatedSession)
        val afterRestartAndNewData =
            recreatedSession.resolve(
                axis = app.sugarlicious.model.GraphAxis.COB,
                mode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                seedValues = listOf(0.0, 40.0, 120.0),
                visibleValues = listOf(40.0, 120.0),
                fallbackBounds = app.sugarlicious.model.GraphBounds(0.0, 10.0),
                minimumSpan = 1.0,
            )

        assertEquals(first.bounds, afterRestartAndNewData.bounds)
    }

    @Test fun `metabolic chart renders classified bolus carb and ecarb events`() {
        val now = System.currentTimeMillis()
        val history =
            listOf(
                TherapyHistorySample(now - 60 * 60_000L, totalIob = 0.3, cobGrams = 5.0),
                TherapyHistorySample(now - 30 * 60_000L, totalIob = 2.0, cobGrams = 60.0),
                TherapyHistorySample(now, totalIob = 1.4, cobGrams = 35.0),
            )
        val events =
            listOf(
                TherapyEvent("meal-bolus", TherapyEventKind.MEAL_BOLUS, now - 45 * 60_000L, 7.6),
                TherapyEvent("smb", TherapyEventKind.SMB, now - 20 * 60_000L, 0.2),
                TherapyEvent("correction", TherapyEventKind.MANUAL_CORRECTION, now - 15 * 60_000L, 1.0),
                TherapyEvent("meal-carbs", TherapyEventKind.MEAL_CARBS, now - 45 * 60_000L, 60.0),
                TherapyEvent("ecarbs", TherapyEventKind.ECARBS, now - 10 * 60_000L, 2.0),
            )
        val bitmap =
            render(
                MetabolicDashboardChart(context).apply {
                    bind(TherapyDisplayState(receivedAtEpochMs = now, therapyHistory = history, therapyEvents = events), 3)
                },
                260,
            )
        assertTrue(count(bitmap) { Color.blue(it) > 150 && Color.blue(it) > Color.red(it) } > 30)
        assertTrue(count(bitmap) { Color.red(it) > 160 && Color.green(it) > 70 && Color.blue(it) < 140 } > 30)
    }

    @Test fun `bolus marker sizes use the three requested discrete thresholds`() {
        assertEquals(7f, bolusMarkerSide(0.1))
        assertEquals(9f, bolusMarkerSide(0.2))
        assertEquals(11f, bolusMarkerSide(0.5))
        assertEquals(13f, bolusMarkerSide(1.5))
        assertEquals(16f, bolusMarkerSide(1.6))
        assertEquals(16f, bolusMarkerSide(7.6))
    }

    @Test fun `event curve position is interpolated at the real timestamp`() {
        val values =
            listOf(
                TherapyHistorySample(1_000L, totalIob = 1.0, cobGrams = 10.0),
                TherapyHistorySample(3_000L, totalIob = 3.0, cobGrams = 30.0),
            )
        assertEquals(2.0, interpolateTherapyValue(values, 2_000L, iob = true)!!, 0.0001)
        assertEquals(20.0, interpolateTherapyValue(values, 2_000L, iob = false)!!, 0.0001)
    }

    @Test fun `extended carbs become readable simulation markers across their duration`() {
        val start = 1_000_000L
        val markers =
            expandECarbSimulation(
                TherapyEvent("ecarbs", TherapyEventKind.ECARBS, start, 50.0, durationMinutes = 180),
            )
        assertEquals(12, markers.size)
        assertEquals(start, markers.first().timestampEpochMs)
        assertEquals(start + 165L * 60_000L, markers.last().timestampEpochMs)
        assertEquals(50.0, markers.sumOf { it.amount }, 0.0001)
        assertTrue(markers.all { it.kind == TherapyEventKind.ECARBS && it.amount >= 1.0 })
        assertEquals(10, markers.count { it.amount == 4.0 })
        assertEquals(2, markers.count { it.amount == 5.0 })

        val smallSimulation =
            expandECarbSimulation(
                TherapyEvent("small-ecarbs", TherapyEventKind.ECARBS, start, 2.0, durationMinutes = 60),
            )
        assertEquals(2, smallSimulation.size)
        assertTrue(smallSimulation.all { it.amount >= 1.0 })
        assertEquals(start + 30L * 60_000L, smallSimulation.last().timestampEpochMs)

        val fractionalSimulation =
            expandECarbSimulation(
                TherapyEvent("fractional-ecarbs", TherapyEventKind.ECARBS, start, 2.4, durationMinutes = 60),
            )
        assertEquals(listOf(1.0, 1.4), fractionalSimulation.map { it.amount })
        assertTrue(fractionalSimulation.all { it.amount >= 1.0 })
    }

    @Test fun `sub gram ecarb entries cannot become readable treatment markers`() {
        val events =
            listOf(
                TherapyEvent("tiny", TherapyEventKind.ECARBS, 1_000L, 0.4),
                TherapyEvent("valid", TherapyEventKind.ECARBS, 2_000L, 1.0),
            )
        val drawable = events.flatMap(::expandECarbSimulation).filterNot { it.kind == TherapyEventKind.ECARBS && it.amount < 1.0 }
        assertEquals(listOf("valid"), drawable.map { it.id })
        assertTrue(drawable.all { it.amount >= 1.0 })
    }

    @Test fun `insulin activity history and supplied prediction share one real boundary point`() {
        val boundary = 10_000L
        val (actual, prediction) =
            continuousActivitySeries(
                actual = listOf(0L to 0.01, 5_000L to 0.02, boundary to 0.03),
                future = listOf(boundary to 0.03, 15_000L to 0.02, 20_000L to 0.01),
                boundaryTime = boundary,
            )
        assertEquals(boundary, actual.last().first)
        assertEquals(actual.last(), prediction.first())
    }

    @Test fun `CGM insulin activity uses AndroidAPS eighty percent inside target range`() {
        val now = System.currentTimeMillis()
        val scalePreferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        scalePreferences
            .edit()
            .putLong("graph.static.insulin_activity.minimum", 0.0.toBits())
            .putLong("graph.static.insulin_activity.maximum", 1.0.toBits())
            .commit()
        val history =
            (0..4).map { index ->
                TherapyHistorySample(
                    measuredAtEpochMs = now - (4 - index) * 5L * 60_000L,
                    insulinActivityUnitsPerMinute = 0.01 + index * 0.01,
                )
            }
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(115.0, now - 5L * 60_000L), GlucoseSample(120.0, now)),
                therapyHistory = history,
                target = TargetState(80.0, 160.0),
            )
        val bitmap =
            try {
                render(
                    GlucoseDashboardChart(context).apply {
                        bind(state, GlucoseUnit.MG_DL, false, 3, showActivity = true, clockEpochMs = now)
                    },
                    230,
                )
            } finally {
                scalePreferences.edit().clear().commit()
            }
        val activityTop =
            (0 until bitmap.height).firstOrNull { y ->
                (0 until bitmap.width).any { x ->
                    bitmap.getPixel(x, y) == SugarliciousColors.argb(SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY)
                }
            } ?: bitmap.height
        assertTrue("activityTop=$activityTop", activityTop in (bitmap.height * 0.20).toInt()..(bitmap.height * 0.65).toInt())
    }

    @Test fun `productive AAPS activity remains visible in every graph scale mode`() {
        val now = System.currentTimeMillis()
        val states =
            listOf(-8.0, -14.0, -20.0, -11.0).mapIndexed { index, bgi ->
                val measuredAt = now - (3 - index) * 5L * 60_000L
                requireNotNull(
                    TherapyBridgePayloadAdapter.parse(
                        mapOf(
                            "glucoseMgdl" to 120.0 + index,
                            "glucoseTimeStamp" to measuredAt,
                            "iob" to 2.0 - index * 0.1,
                            "insulinActivity" to -bgi / 500.0,
                            "suggested" to """{"reason":"Dev: 1, BGI: $bgi, ISF: 100, Target: 100"}""",
                        ),
                        measuredAt + 1_000L,
                    ),
                )
            }
        val state =
            states.last().copy(
                glucoseHistory = states.mapNotNull { it.glucose?.let { glucose -> GlucoseSample(glucose.valueMgDl, glucose.measuredAtEpochMs) } },
                therapyHistory = states.flatMap(TherapyDisplayState::therapyHistory),
                target = TargetState(80.0, 160.0),
            )

        CgmGraphScaleMode.entries.forEach { mode ->
            val bitmap =
                render(
                    GlucoseDashboardChart(context).apply {
                        bind(
                            state,
                            GlucoseUnit.MG_DL,
                            showPredictions = false,
                            durationHours = 3,
                            showActivity = true,
                            graphScaleMode = mode,
                            clockEpochMs = now,
                        )
                    },
                    230,
                )
            val activityPixels = count(bitmap) { it == SugarliciousColors.argb(SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY) }
            assertTrue("mode=$mode activity=$activityPixels", activityPixels > 4)
        }
    }

    @Test fun `glucose dots use alert color outside display range`() {
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(55.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(62.0, 58.0, 55.0).mapIndexed { index, value ->
                        GlucoseSample(value, now - (2 - index) * 5 * 60_000L)
                    },
                target = TargetState(80.0, 160.0),
            )
        val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, false, 6) }, 230)
        val redPixels = count(bitmap) { Color.red(it) > 180 && Color.red(it) > Color.green(it) * 1.5 }
        assertTrue("red=$redPixels", redPixels > 2)
    }

    @Test fun `glucose chart compresses sub target range and keeps zero above edge`() {
        val zero = glucoseLogRatio(0.0)
        val low = glucoseLogRatio(80.0)
        val targetHigh = glucoseLogRatio(160.0)
        val maximum = glucoseLogRatio(400.0)
        assertTrue("zero=$zero", zero > 0.0)
        assertTrue("subTarget=${low - zero}", low - zero < targetHigh - low)
        assertEquals(1.0, maximum, 0.0001)
    }

    @Test fun `configured glucose maximum becomes the visible top of the graph`() {
        assertEquals(1.0, glucoseLogRatio(300.0, 300.0), 0.0001)
        assertTrue(glucoseLogRatio(200.0, 300.0) < 1.0)
        assertEquals(1.0, glucoseLogRatio(600.0, 300.0), 0.0001)
    }

    @Test fun `dynamic scale expands immediately but resists minor jitter`() {
        val previous = app.sugarlicious.model.CgmGraphYScale(app.sugarlicious.model.CgmGraphScaleMode.DYNAMIC, 60.0, 220.0)
        assertEquals(previous, stableCgmScale(previous, previous.copy(minimumMgDl = 65.0, maximumMgDl = 215.0)))
        assertEquals(260.0, stableCgmScale(previous, previous.copy(maximumMgDl = 260.0)).maximumMgDl, 0.0)
    }

    @Test fun `viewport cannot pan beyond configured future edge`() {
        val now = 10_000_000L
        val viewport = ChartViewport(6)
        viewport.setFutureWindow(0L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now, viewport.endEpochMs(now))

        viewport.setFutureWindow(60L * 60_000L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now + 60L * 60_000L, viewport.endEpochMs(now))
    }

    @Test
    fun `viewport zoom out and pan stop at available history and absolute twenty four hours`() {
        val now = System.currentTimeMillis()
        val viewport = ChartViewport(6)
        viewport.setAvailablePastWindow(8L * 60L * 60_000L, now)
        viewport.setHours(24f)
        assertEquals(8f, viewport.hours, 0.0001f)

        viewport.zoom(0.01f)
        assertEquals(8f, viewport.hours, 0.0001f)
        viewport.setHours(2f)
        viewport.pan(100_000f, 100f, now)
        assertEquals(-6L * 60L * 60_000L, viewport.panMs)
        val boundaryEnd = viewport.endEpochMs(now)
        repeat(20) { viewport.pan(100_000f, 100f, now) }
        assertEquals(boundaryEnd, viewport.endEpochMs(now))

        viewport.setAvailablePastWindow(30L * 60L * 60_000L, now)
        viewport.setHours(30f)
        assertEquals(24f, viewport.hours, 0.0001f)
    }

    @Test fun `visible graph hour label follows manual fractional zoom`() {
        assertEquals("3h", formatVisibleGraphHours(3f))
        assertEquals("3.6h", formatVisibleGraphHours(3.6f).replace(',', '.'))
        assertEquals("24h", formatVisibleGraphHours(120f))
    }

    @Test fun `target dash phase follows the same pan and zoom transform as graph content`() {
        val initial = contentAnchoredDashPhase(viewportStartEpochMs = 0L, viewportDurationMs = 1_000L, plotWidthPx = 100f, periodPx = 12f)
        val panned = contentAnchoredDashPhase(viewportStartEpochMs = 200L, viewportDurationMs = 1_000L, plotWidthPx = 100f, periodPx = 12f)
        val zoomed = contentAnchoredDashPhase(viewportStartEpochMs = 200L, viewportDurationMs = 500L, plotWidthPx = 100f, periodPx = 12f)

        assertEquals(0f, initial, 0.0001f)
        assertEquals(8f, panned, 0.0001f)
        assertEquals(4f, zoomed, 0.0001f)
        assertTrue(initial != panned)
    }

    @Test
    fun `upper graph scale uses the rounded corner tangent`() {
        assertEquals(28f, roundedPlotTopTangentY(plotTop = 10f, cornerRadius = 18f), 0.0001f)
    }

    @Test
    fun `time mapping reserves the complete current dot inside the rounded plot`() {
        val plot = RectF(30f, 0f, 400f, 180f)
        val timeBounds = graphTimeBounds(plot, pointRadius = 7f, outlineWidth = 2f)

        assertEquals(391f, timeBounds.right, 0.0001f)
        assertEquals(391f, mapGraphTimeX(time = 1_000L, start = 0L, end = 1_000L, timeBounds), 0.0001f)
        assertTrue(mapGraphTimeX(1_000L, 0L, 1_000L, timeBounds) + 9f <= plot.right)
    }

    @Test
    fun `target cgm and now share one horizontal transform while panning`() {
        val bounds = graphTimeBounds(RectF(30f, 0f, 400f, 180f), pointRadius = 6f, outlineWidth = 1f)
        val targetAt = 700L
        val cgmAt = 700L

        val initialTargetX = mapGraphTimeX(targetAt, 0L, 1_000L, bounds)
        val initialCgmX = mapGraphTimeX(cgmAt, 0L, 1_000L, bounds)
        val pannedTargetX = mapGraphTimeX(targetAt, 200L, 1_200L, bounds)
        val pannedCgmX = mapGraphTimeX(cgmAt, 200L, 1_200L, bounds)

        assertEquals(initialCgmX, initialTargetX, 0.0001f)
        assertEquals(pannedCgmX, pannedTargetX, 0.0001f)
        assertEquals(pannedCgmX - initialCgmX, pannedTargetX - initialTargetX, 0.0001f)
    }

    @Test
    fun `available graph window follows real canonical history`() {
        val now = 50L * 60L * 60_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(
                        GlucoseSample(110.0, now - 7L * 60L * 60_000L),
                        GlucoseSample(120.0, now),
                    ),
            )

        assertEquals(7L * 60L * 60_000L, availableGlucoseHistoryWindowMs(state, now))
    }

    @Test
    fun `mobile CGM renderer retains overlapping valid sensor streams for the complete twenty four hours`() {
        val minute = 60_000L
        val now = 100L * 60L * minute
        val samples =
            (0..288).flatMap { index ->
                val timestamp = now - DisplayHistoryAccumulator.WINDOW_MS + index * 5L * minute
                listOf(
                    GlucoseSample(100.0 + index % 10, timestamp, sensorId = "sensor-a", sessionId = "session-a"),
                    GlucoseSample(110.0 + index % 10, timestamp, sensorId = "sensor-b", sessionId = "session-b"),
                )
            }

        val retained = canonicalMobileGraphHistory(samples, now, DataSourceId.ANDROID_APS)

        assertEquals(578, retained.size)
        assertEquals(now - DisplayHistoryAccumulator.WINDOW_MS, retained.first().measuredAtEpochMs)
    }

    @Test
    fun `metabolic graph reuses configurable plot and scale area colors`() {
        val preferences = context.getSharedPreferences("metabolic_region_colors", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val plotColor = Color.rgb(12, 96, 44)
        val scaleColor = Color.rgb(94, 17, 118)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_BACKGROUND, plotColor)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.SURFACE, scaleColor)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        val bitmap =
            render(
                MetabolicDashboardChart(context).apply {
                    bind(TherapyDisplayState(receivedAtEpochMs = 1_000L), 6, showTimeAxis = true, clockEpochMs = 1_000L)
                },
                260,
            )

        assertEquals(scaleColor, bitmap.getPixel(10, 80))
        assertEquals(plotColor, bitmap.getPixel(100, 20))
        assertEquals(scaleColor, bitmap.getPixel(100, 250))
        SugarliciousColors.apply(SugarliciousPalette.defaults())
    }

    @Test
    fun `overview graph window includes the oldest visible data stream`() {
        val hour = 60L * 60_000L
        val now = 50L * hour
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(110.0, now - 6L * hour)),
                therapyHistory = listOf(TherapyHistorySample(now - 24L * hour, totalIob = 1.0)),
            )

        assertEquals(24L * hour, availableOverviewHistoryWindowMs(state, now, requestedHours = 24))
    }

    @Test
    fun `twenty four hour zoom remains available while history is still loading`() {
        val hour = 60L * 60_000L
        val now = 50L * hour
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(118.0, now - hour)),
            )

        assertEquals(24L * hour, availableOverviewHistoryWindowMs(state, now, requestedHours = 24))
    }

    @Test fun `out of range predictions neither expand dynamic bounds nor pile up at plot edge`() {
        val now = 50L * 60 * 60_000L
        for (mode in listOf(CgmGraphScaleMode.DYNAMIC, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC)) {
            val scales = mutableListOf<app.sugarlicious.model.CgmGraphYScale>()

            fun image(prediction: Double): Bitmap {
                val state =
                    TherapyDisplayState(
                        receivedAtEpochMs = now,
                        glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                        glucoseHistory = listOf(GlucoseSample(110.0, now - 300_000), GlucoseSample(120.0, now)),
                        glucosePredictions =
                            listOf(
                                GlucosePrediction(
                                    PredictionKind.IOB,
                                    listOf(
                                        GlucoseSample(prediction, now),
                                        GlucoseSample(prediction, now + 300_000),
                                    ),
                                ),
                            ),
                    )
                val viewport = ChartViewport(2).apply { setFutureWindow(30 * 60_000L) }
                val chart =
                    GlucoseDashboardChart(context, sharedViewport = viewport).apply {
                        bind(state, GlucoseUnit.MG_DL, true, 2, showPredictionIob = true, graphScaleMode = mode, clockEpochMs = now)
                    }
                val bitmap = render(chart, 240)
                scales +=
                    GlucoseDashboardChart::class.java
                        .getDeclaredField("renderedYScale")
                        .apply { isAccessible = true }
                        .get(chart) as app.sugarlicious.model.CgmGraphYScale
                return bitmap
            }
            val first = image(600.0)
            val second = image(900.0)
            assertEquals("Prediction magnitude changed the actual rendered scale", scales[0], scales[1])
            assertTrue(scales.all { it.maximumMgDl < 600.0 })
            val predictionColor = SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_IOB)
            assertEquals("Out-of-range dots accumulated at the plot edge", 0, count(first) { it == predictionColor })
            assertEquals("Out-of-range dots accumulated at the plot edge", 0, count(second) { it == predictionColor })
        }
    }

    private fun render(
        view: View,
        height: Int,
    ): Bitmap {
        val width = 420
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun count(
        bitmap: Bitmap,
        predicate: (Int) -> Boolean,
    ): Int {
        var result = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (predicate(bitmap.getPixel(x, y))) result++
        return result
    }

    private fun countNearColumn(
        bitmap: Bitmap,
        centerX: Int,
        expected: Int,
        tolerance: Int,
    ): Int {
        var result = 0
        for (y in 0 until bitmap.height) {
            for (x in (centerX - 2).coerceAtLeast(0)..(centerX + 2).coerceAtMost(bitmap.width - 1)) {
                val actual = bitmap.getPixel(x, y)
                if (
                    kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
                    kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
                    kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= tolerance
                ) {
                    result += 1
                }
            }
        }
        return result
    }
}
