package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.TrendArrowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Regenerates the native picker artwork using the same renderers as the home screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPickerPreviewTest {
    @Test
    fun `picker artwork renders every supported family size at its own aspect ratio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = 1_792_411_200_000L
        val base = widgetPreviewState(now)
        val state =
            base.copy(
                glucose = base.glucose?.copy(deltaMgDl = 10.0, measuredAtEpochMs = now - 300_000L),
                insulin = InsulinState(totalIob = 1.2),
                carbs = CarbState(cobGrams = 15.0),
                basal = BasalState(currentUnitsPerHour = .7),
            )
        val output = File("build/reports/widget-picker-artwork").apply { mkdirs() }
        val trendStyle = TrendArrowStyle.defaults(AppearanceMode.DARK, android.graphics.Color.WHITE)
        val sizes = mapOf("small" to (154 to 69), "medium" to (154 to 175), "large" to (345 to 175))
        ConfigurableWidgetKind.entries.forEach { kind ->
            val supported =
                when (kind) {
                    ConfigurableWidgetKind.GLUCOSE_GRAPH -> sizes.filterKeys { it == "medium" }
                    else -> sizes
                }
            supported.forEach { (sizeName, size) ->
                val width = size.first * 2
                val height = size.second * 2
                val radius = adaptiveGlucoseCornerRadius(widgetFamilyLayout(size.first.toFloat(), size.second.toFloat()), size.second.toFloat(), 30f)
                val configuration = WidgetSystemDefaults.forKind(kind).copy(cornerRadiusDp = radius.toInt())
                val palette = WidgetColorStore.load(context, AppearanceMode.DARK).with(WidgetColorRole.BACKGROUND, configuration.backgroundArgb)
                val layout = responsiveWidgetLayout(size.first.toFloat(), size.second.toFloat())
                val bitmap =
                    when (kind) {
                        ConfigurableWidgetKind.GLUCOSE ->
                            renderAdaptiveGlucoseWidget(
                                state,
                                palette,
                                width,
                                height,
                                now,
                                pixelDensity = 2f,
                                configuration = configuration,
                                trendStyle = trendStyle,
                            )
                        ConfigurableWidgetKind.GRAPH ->
                            renderWidgetGraph(
                                state,
                                palette,
                                width,
                                height,
                                now,
                                layout = layout,
                                density = 2f,
                                configuration = configuration,
                                showScaleAndAge = true,
                            )
                        ConfigurableWidgetKind.GLUCOSE_GRAPH ->
                            renderGlucoseGraphWidget(
                                state,
                                palette,
                                width,
                                height,
                                now,
                                layout = layout,
                                pixelDensity = 2f,
                                configuration = configuration,
                                trendStyle = trendStyle,
                            )
                        ConfigurableWidgetKind.METABOLIC ->
                            renderTherapyIndicatorWidget(
                                context,
                                state,
                                palette,
                                width,
                                height,
                                now,
                                DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
                                configuration,
                                2f,
                                AppearanceMode.DARK,
                            )
                    }
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)
                assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(width / 2, height / 2)))
                File(output, "widget_picker_${kind.name.lowercase()}_$sizeName.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
        val legacyConfiguration = WidgetSystemDefaults.forKind(ConfigurableWidgetKind.GLUCOSE).copy(cornerRadiusDp = 38)
        val legacyPalette = WidgetColorStore.load(context, AppearanceMode.DARK).with(WidgetColorRole.BACKGROUND, legacyConfiguration.backgroundArgb)
        val legacy =
            mapOf(
                "glucose" to
                    renderMinimalGlucoseWidget(
                        state,
                        legacyPalette,
                        360,
                        152,
                        now,
                        pixelDensity = 2f,
                        options = GlucoseWidgetRenderOptions(cornerRadiusDp = 38f, trendStyle = trendStyle),
                    ),
                "metabolic" to
                    renderTherapyIndicatorWidget(
                        context,
                        state,
                        legacyPalette,
                        360,
                        152,
                        now,
                        DashboardUiPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
                        legacyConfiguration,
                        2f,
                        AppearanceMode.DARK,
                        adaptiveFamily = false,
                    ),
            )
        legacy.forEach { (name, bitmap) ->
            File(output, "widget_picker_legacy_$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        assertEquals(12, output.listFiles { file -> file.extension == "png" }?.size)
    }
}
