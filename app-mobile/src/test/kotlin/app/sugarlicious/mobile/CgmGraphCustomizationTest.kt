package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CgmGraphCustomizationTest {
    @Test
    fun `high and very high dot colors refresh without waiting for another reading`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(300.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory = listOf(GlucoseSample(200.0, now - 300_000L), GlucoseSample(300.0, now)),
            )
        val chart = GlucoseDashboardChart(context)

        fun bind() = chart.bind(state, GlucoseUnit.MG_DL, false, 3, clockEpochMs = now)
        try {
            SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
            bind()
            val shadow = org.robolectric.Shadows.shadowOf(chart)
            shadow.clearWasInvalidated()
            val high = Color.rgb(17, 211, 71)
            val veryHigh = Color.rgb(193, 27, 219)
            SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_HIGH, high)
            SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_VERY_HIGH, veryHigh)
            SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
            bind()
            assertTrue("A palette change must repaint with unchanged data and clock", shadow.wasInvalidated())
            val bitmap = render(chart, 320)
            assertTrue("high dot uses its own color", count(bitmap) { it == high } > 0)
            assertTrue("very high dot uses its own color", count(bitmap) { it == veryHigh } > 0)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `urgent dots render their independent colors instead of glucose text colors`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val low = Color.rgb(12, 210, 91)
        val high = Color.rgb(131, 24, 211)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_VERY_LOW, low)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_VERY_HIGH, high)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GLUCOSE_VERY_LOW, Color.RED)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GLUCOSE_VERY_HIGH, Color.YELLOW)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
        try {
            listOf(SugarliciousColorRole.CGM_DOT_VERY_LOW, SugarliciousColorRole.CGM_DOT_VERY_HIGH).forEach { role ->
                assertTrue(colorRoleVisible(role, showCgmGraph = true, showMetabolicGraph = false))
                assertFalse(colorRoleVisible(role, showCgmGraph = false, showMetabolicGraph = false))
            }
            val now = System.currentTimeMillis()
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(300.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory = listOf(GlucoseSample(45.0, now - 10 * 60_000L), GlucoseSample(300.0, now)),
                )
            val bitmap = renderPrediction(state, now)
            assertTrue("very-low dot fill must use its own picker", count(bitmap) { it == low } > 0)
            assertTrue("very-high dot fill must use its own picker", count(bitmap) { it == high } > 0)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `fresh appearance profiles reproduce approved mobile dot settings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("fresh_approved_dot_profiles", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        val dark = readMobileGraphStyle(preferences, app.sugarlicious.model.AppearanceMode.DARK)
        val light = readMobileGraphStyle(preferences, app.sugarlicious.model.AppearanceMode.LIGHT)

        assertEquals(3.0f, dark.cgmDotRadiusDp, 0.001f)
        assertTrue(dark.cgmHistoricalDotOutlineEnabled)
        assertEquals(0.6f, dark.cgmHistoricalDotOutlineWidthDp, 0.001f)
        assertEquals(1.2f, dark.cgmCurrentDotOutlineWidthDp, 0.001f)
        assertEquals(2.6f, light.cgmDotRadiusDp, 0.001f)
        assertFalse(light.cgmHistoricalDotOutlineEnabled)
        assertEquals(1.0f, light.cgmHistoricalDotOutlineWidthDp, 0.001f)
        assertEquals(1.0f, light.cgmCurrentDotOutlineWidthDp, 0.001f)
        assertEquals(1.6f, readMobilePredictionDotRadius(preferences, app.sugarlicious.model.AppearanceMode.DARK), 0.001f)
        assertEquals(0.2f, readMobilePredictionDotOutlineWidth(preferences, app.sugarlicious.model.AppearanceMode.DARK), 0.001f)
    }

    @Test
    fun `boundary paint restores exact configured alpha after reused paint state`() {
        val paint = Paint().apply { alpha = 211 }

        applyGraphBoundaryPaint(paint, Color.TRANSPARENT)
        assertEquals(0, paint.alpha)

        applyGraphBoundaryPaint(paint, Color.argb(73, 12, 34, 56))
        assertEquals(73, paint.alpha)
        assertEquals(12, Color.red(paint.color))
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    @Test
    fun `target value color reuses compatible picker role and persists independently from range`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val targetColor = Color.rgb(224, 42, 205)
        val rangeColor = Color.rgb(24, 180, 65)
        val oldTargetBandColor = Color.rgb(6, 48, 18)

        assertEquals(SugarliciousColorRole.TARGET_BAND, SugarliciousColorRole.TARGET_VALUE)
        assertEquals("target_value", SugarliciousColorRole.TARGET_VALUE.preferenceKey)
        assertTrue(SugarliciousColorRole.TARGET_VALUE.configurable)
        assertTrue(colorRoleVisible(SugarliciousColorRole.TARGET_VALUE, showCgmGraph = true, showMetabolicGraph = false))
        assertFalse(colorRoleVisible(SugarliciousColorRole.TARGET_VALUE, showCgmGraph = false, showMetabolicGraph = false))

        preferences.edit().putInt("color.dark.target_band", oldTargetBandColor).commit()
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.TARGET_VALUE, targetColor)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_IN_RANGE, rangeColor)
        val palette = SugarliciousColorStore.load(preferences)

        assertEquals(targetColor, palette.argb(SugarliciousColorRole.TARGET_VALUE))
        assertEquals(rangeColor, palette.argb(SugarliciousColorRole.RANGE_IN_RANGE))
        assertTrue(targetColor != rangeColor)
        assertTrue(oldTargetBandColor != palette.argb(SugarliciousColorRole.TARGET_VALUE))
    }

    @Test
    fun `configured target value color is used by target line`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val targetColor = Color.rgb(224, 42, 205)
        val alternateTargetColor = Color.rgb(30, 210, 70)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.TARGET_VALUE, targetColor)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        try {
            val now = System.currentTimeMillis()
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(122.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory =
                        listOf(
                            GlucoseSample(118.0, now - 10 * 60_000L),
                            GlucoseSample(120.0, now - 5 * 60_000L),
                            GlucoseSample(122.0, now),
                        ),
                    target = TargetState(80.0, 160.0, valueMgDl = 100.0),
                )

            val configured =
                render(
                    GlucoseDashboardChart(context).apply {
                        bind(
                            state = state,
                            unit = GlucoseUnit.MG_DL,
                            showPredictions = false,
                            durationHours = 3,
                            showTargetRange = false,
                            showTargetValue = true,
                            cgmDotOutlineEnabled = false,
                            clockEpochMs = now,
                        )
                    },
                    240,
                )

            SugarliciousColorStore.save(preferences, SugarliciousColorRole.TARGET_VALUE, alternateTargetColor)
            SugarliciousColors.apply(SugarliciousColorStore.load(preferences))
            val recolored =
                render(
                    GlucoseDashboardChart(context).apply {
                        bind(
                            state = state,
                            unit = GlucoseUnit.MG_DL,
                            showPredictions = false,
                            durationHours = 3,
                            showTargetRange = false,
                            showTargetValue = true,
                            cgmDotOutlineEnabled = false,
                            clockEpochMs = now,
                        )
                    },
                    240,
                )

            val changedPixels = bitmapDifferenceCount(configured, recolored)
            assertTrue("changedPixels=$changedPixels", changedPixels > 10)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `overview graph forwards enabled target value preference`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val targetColor = Color.rgb(224, 42, 205)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.TARGET_VALUE, targetColor)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        try {
            val now = System.currentTimeMillis()
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(122.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory =
                        listOf(
                            GlucoseSample(118.0, now - 10 * 60_000L),
                            GlucoseSample(120.0, now - 5 * 60_000L),
                            GlucoseSample(122.0, now),
                        ),
                    target = TargetState(80.0, 160.0, valueMgDl = 100.0),
                )
            val enabledUi =
                DashboardUiPreferences(
                    showCgmTargetValue = true,
                    cgmDotOutlineEnabled = false,
                )
            val disabledUi = enabledUi.copy(showCgmTargetValue = false)

            val enabled =
                render(
                    GlucoseDashboardChart(context).apply {
                        bindOverview(state, enabledUi, now)
                    },
                    240,
                )
            val disabled =
                render(
                    GlucoseDashboardChart(context).apply {
                        bindOverview(state, disabledUi, now)
                    },
                    240,
                )

            val changedPixels = bitmapDifferenceCount(enabled, disabled)
            assertTrue("changedPixels=$changedPixels", changedPixels > 10)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `changed target history invalidates an otherwise unchanged graph`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val targetColor = Color.rgb(224, 42, 205)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.TARGET_VALUE, targetColor)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        try {
            val now = System.currentTimeMillis()
            val base =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(122.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory =
                        listOf(
                            GlucoseSample(118.0, now - 10 * 60_000L),
                            GlucoseSample(122.0, now),
                        ),
                    target = TargetState(80.0, 160.0),
                )
            val chart = GlucoseDashboardChart(context)
            chart.bind(
                state = base,
                unit = GlucoseUnit.MG_DL,
                showPredictions = false,
                durationHours = 3,
                showTargetRange = false,
                showTargetValue = true,
                cgmDotOutlineEnabled = false,
                clockEpochMs = now,
            )
            val before = render(chart, 240)

            chart.bind(
                state =
                    base.copy(
                        targetHistory =
                            listOf(
                                TargetSample(
                                    valueMgDl = 100.0,
                                    startedAtEpochMs = now - 3 * 60 * 60_000L,
                                    endsAtEpochMs = now,
                                ),
                            ),
                    ),
                unit = GlucoseUnit.MG_DL,
                showPredictions = false,
                durationHours = 3,
                showTargetRange = false,
                showTargetValue = true,
                cgmDotOutlineEnabled = false,
                clockEpochMs = now,
            )
            val after = render(chart, 240)

            val changedPixels = bitmapDifferenceCount(before, after)
            assertTrue("changedPixels=$changedPixels", changedPixels > 40)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `prediction point size and zero outline preference change rendered dots`() {
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val predictionColor = Color.rgb(55, 190, 245)
        val outlineColor = Color.rgb(215, 40, 190)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.PREDICTION_IOB, predictionColor)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_CURRENT_OUTLINE, outlineColor)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        try {
            val now = System.currentTimeMillis()
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory =
                        listOf(
                            GlucoseSample(116.0, now - 5 * 60_000L),
                            GlucoseSample(120.0, now),
                        ),
                    glucosePredictions =
                        listOf(
                            GlucosePrediction(
                                PredictionKind.IOB,
                                listOf(
                                    GlucoseSample(120.0, now),
                                    GlucoseSample(126.0, now + 5 * 60_000L),
                                    GlucoseSample(132.0, now + 10 * 60_000L),
                                ),
                            ),
                        ),
                    target = TargetState(80.0, 160.0),
                )

            preferences
                .edit()
                .putFloat("cgm.prediction.dotRadiusDp", 1.0f)
                .putFloat("cgm.prediction.dotOutlineWidthDp", 0.0f)
                .commit()
            val small = renderPrediction(state, now)
            val smallFill = count(small) { it == predictionColor }
            val smallOutline = count(small) { it == outlineColor }

            preferences
                .edit()
                .putFloat("cgm.prediction.dotRadiusDp", 5.0f)
                .putFloat("cgm.prediction.dotOutlineWidthDp", 2.0f)
                .commit()
            val large = renderPrediction(state, now)
            val largeFill = count(large) { it == predictionColor }
            val largeOutline = count(large) { it == outlineColor }

            assertTrue("smallFill=$smallFill largeFill=$largeFill", largeFill > smallFill * 2)
            assertEquals(0, smallOutline)
            assertTrue("largeOutline=$largeOutline", largeOutline > 0)
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    @Test
    fun `prediction style preferences are clamped by dashboard model`() {
        preferences
            .edit()
            .clear()
            .putFloat("cgm.prediction.dotRadiusDp", 99f)
            .putFloat("cgm.prediction.dotOutlineWidthDp", -4f)
            .commit()

        val ui = DashboardUiPreferences.read(preferences)
        assertEquals(6.0f, ui.predictionDotRadiusDp, 0.0001f)
        assertEquals(0.0f, ui.predictionDotOutlineWidthDp, 0.0001f)
    }

    private fun renderPrediction(
        state: TherapyDisplayState,
        now: Long,
    ): Bitmap {
        val viewport = ChartViewport(1).apply { setFutureWindow(15 * 60_000L) }
        return render(
            GlucoseDashboardChart(context, sharedViewport = viewport).apply {
                bind(
                    state = state,
                    unit = GlucoseUnit.MG_DL,
                    showPredictions = true,
                    durationHours = 1,
                    showPredictionIob = true,
                    cgmDotOutlineEnabled = false,
                    clockEpochMs = now,
                )
            },
            240,
        )
    }

    private fun render(
        view: View,
        size: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, size, size)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun bitmapDifferenceCount(
        first: Bitmap,
        second: Bitmap,
    ): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var changed = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) changed++
            }
        }
        return changed
    }

    private fun count(
        bitmap: Bitmap,
        predicate: (Int) -> Boolean,
    ): Int {
        var matches = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (predicate(bitmap.getPixel(x, y))) matches++
            }
        }
        return matches
    }
}
