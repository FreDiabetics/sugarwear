package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TherapyWidgetWorkerRenderTest {
    @Test
    fun `therapy ring ink leaves room inside its unchanged indicator slot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_792_411_200_000L
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK).with(WidgetColorRole.IOB_PROGRESS, Color.MAGENTA).with(WidgetColorRole.IOB_TRACK, Color.MAGENTA)
        val appearance = app.sugarlicious.model.TherapyProgressAppearance(enabledMetrics = setOf(app.sugarlicious.model.TherapyProgressMetric.IOB))
        val config = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC).copy(therapyProgressAppearance = appearance)
        val dashboard = DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
        val bitmap = renderTherapyIndicatorWidget(context, widgetPreviewState(now), palette, 400, 160, now, dashboard, config, 2f)
        val slot = resolveWidgetTherapyLayout(200f, 80f, appearance).single().slot
        val distances = (0 until 160).flatMap { y -> (0 until 400).filter { x -> bitmap.getPixel(x, y) == Color.MAGENTA }.map { x -> kotlin.math.hypot(x - slot.centerXDp * 2, y - slot.centerYDp * 2) } }
        assertTrue("only the ring should shrink inside the existing value and icon layout", distances.max() <= slot.diameterDp * .90f)
    }

    @Test
    fun `wide therapy graph contour follows its own toggle color and thickness`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_792_411_200_000L
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK).with(WidgetColorRole.GRAPH_OUTLINE, Color.MAGENTA)
        val dashboard = DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))

        fun render(enabled: Boolean, width: Float) =
            renderTherapyIndicatorWidget(
                context,
                widgetPreviewState(now),
                palette,
                690,
                350,
                now,
                dashboard,
                WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC).copy(graphOutlineEnabled = enabled, graphOutlineWidthDp = width),
                2f,
            )
        val off = render(false, 4f)
        val thin = render(true, 1f)
        val thick = render(true, 4f)
        assertTrue("disabled contour must differ from enabled contour", !off.sameAs(thin))

        fun count(bitmap: android.graphics.Bitmap) = (35..310).sumOf { y -> (370..665).count { x -> bitmap.getPixel(x, y) == Color.MAGENTA } }
        assertEquals(0, count(off))
        assertTrue("selected color and thickness must reach graph edge", count(thick) > count(thin))
    }

    @Test
    fun `wide therapy shows programmed TBR duration beside Basal without changing ring values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_792_411_200_000L
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK)
        val dashboard = DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))

        fun render(duration: Long) =
            renderTherapyIndicatorWidget(
                context,
                widgetPreviewState(now).copy(basal = app.sugarlicious.model.BasalState(currentUnitsPerHour = .45, tempAbsoluteUnitsPerHour = 1.125, tempPercent = 250, tempStartedAtEpochMs = now - 10 * 60_000L, tempDurationMinutes = duration)),
                palette,
                690,
                350,
                now,
                dashboard,
                WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC),
                2f,
            )
        val short = render(60)
        val long = render(300)
        assertTrue("TBR timing must appear beside the lower ring", (200..310).any { y -> (170..315).any { x -> short.getPixel(x, y) != long.getPixel(x, y) } })
    }

    @Test
    fun `wide therapy respects Mobile IOB and COB scaling instead of fixed defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK)
        val dashboard = DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
        val now = 1_792_411_200_000L
        val config = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC)

        fun render(maximum: Float) =
            renderTherapyIndicatorWidget(
                context,
                widgetPreviewState(now),
                palette,
                690,
                350,
                now,
                dashboard.copy(
                    iobGraphScaleMode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                    cobGraphScaleMode = app.sugarlicious.model.CgmGraphScaleMode.STATIC,
                    iobGraphMaximumUnits = maximum,
                    cobGraphMaximumGrams = maximum * 20f,
                ),
                config,
                2f,
            )
        assertTrue("Mobile scale settings must change the widget chart", !render(5f).sameAs(render(50f)))
    }

    @Test
    fun `wide therapy retains Basal at short height and never nests another graph on tablets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK)
        val now = 1_792_411_200_000L
        val dashboard = DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
        listOf(600 to 220, 1400 to 350).forEach { (width, height) ->
            val bitmap = renderTherapyIndicatorWidget(context, widgetPreviewState(now), palette, width, height, now, dashboard, WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC), 2f)
            val basal = palette.argb(WidgetColorRole.BASAL_PROGRESS)
            assertTrue("Basal missing from left pane at ${width}x$height", (0 until height).any { y -> (0 until width / 2).any { x -> bitmap.getPixel(x, y) == basal } })
        }
    }

    @Test
    fun `wide therapy renders both history lanes on worker without changing Mobile scale preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        val palette = WidgetColorStore.load(context, AppearanceMode.DARK)
        val dashboard = DashboardUiPreferences.read(preferences)
        val before = preferences.all.toMap()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val bitmap =
                executor
                    .submit<android.graphics.Bitmap> {
                        val config = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.METABOLIC).copy(cornerRadiusDp = 30)
                        renderTherapyIndicatorWidget(context, widgetPreviewState(1_792_411_200_000L), palette, 690, 350, 1_792_411_200_000L, dashboard, config, 2f)
                    }.get(30, TimeUnit.SECONDS)
            assertEquals(690, bitmap.width)
            assertEquals(350, bitmap.height)
            assertTrue("graph region must contain history, not a blank panel", (70..280).any { y -> (400..650).any { x -> Color.blue(bitmap.getPixel(x, y)) > 100 } })
            assertEquals("widget must not write the Mobile axis session", before, preferences.all)
        } finally {
            executor.shutdownNow()
        }
    }
}
