package app.sugarlicious.mobile

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPresentationTest {
    @Test
    fun `adaptive detail rows preserve palette colors in light and dark layouts`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = 1_792_411_200_000L
        app.sugarlicious.model.AppearanceMode.entries.forEach { mode ->
            listOf(308 to 350, 640 to 350).forEach { (width, height) ->
                val colors = listOf(0xFF990099.toInt(), 0xFF006600.toInt(), 0xFF000099.toInt())
                val palette =
                    WidgetColorStore.load(context, mode).with(
                        mapOf(
                            WidgetColorRole.BACKGROUND to Color.WHITE,
                            WidgetColorRole.DELTA to colors[0],
                            WidgetColorRole.AGE to colors[1],
                            WidgetColorRole.TIR to colors[2],
                            WidgetColorRole.UNIT to 0xFF665544.toInt(),
                        ),
                    )
                val frame =
                    renderAdaptiveGlucoseWidget(
                        state = widgetPreviewState(now),
                        palette = palette,
                        width = width,
                        height = height,
                        now = now,
                        pixelDensity = 2f,
                        configuration = WidgetInstanceConfiguration(),
                        trendStyle =
                            app.sugarlicious.model.TrendArrowStyle
                                .defaults(mode, Color.BLACK),
                    )
                (colors + 0xFF665544.toInt()).forEach { expected ->
                    assertTrue(
                        "Missing detail color $expected in $mode at $width",
                        (height / 2 until height).any { y ->
                            (0 until if (width > 400) width / 2 else width).any { x -> frame.getPixel(x, y) == expected }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun `widget supplements share the stronger configurable numeric emphasis`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = 1_792_411_200_000L

        fun grayInk(bold: Boolean): Int {
            val frame =
                renderAdaptiveGlucoseWidget(
                    state = widgetPreviewState(now),
                    palette = WidgetColorStore.load(context, app.sugarlicious.model.AppearanceMode.DARK).with(WidgetColorRole.UNIT, 0xFFB3B3B3.toInt()),
                    width = 308,
                    height = 350,
                    now = now,
                    pixelDensity = 2f,
                    configuration = WidgetInstanceConfiguration(detailDeltaBold = bold, detailAgeBold = bold, tirBold = bold),
                    trendStyle =
                        app.sugarlicious.model.TrendArrowStyle
                            .defaults(app.sugarlicious.model.AppearanceMode.DARK, Color.WHITE),
                )
            return (0 until frame.height).sumOf { y -> (0 until frame.width).count { x -> frame.getPixel(x, y) == 0xFFB3B3B3.toInt() } }
        }
        val normal = grayInk(false)
        assertTrue(normal > 0)
        assertTrue(grayInk(true) > normal)
    }

    @Test
    fun `detail text supports real bold and explicitly disabled emphasis`() {
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }
        val suffix = Paint(number)
        applyWidgetDetailWeight(number, true)
        applyWidgetDetailWeight(suffix, false)
        assertEquals(700, number.typeface.weight)
        assertEquals(400, suffix.typeface.weight)
        assertTrue(number.measureText("538") > suffix.measureText("538"))
    }

    @Test
    fun `detail rows separate numeric emphasis from descriptive text`() {
        assertEquals(WidgetDetailText("", "−5", " mg/dL"), widgetDetailText(0, "−5 mg/dL"))
        assertEquals(WidgetDetailText("vor ", "5", " Min"), widgetDetailText(1, "5m"))
        assertEquals(WidgetDetailText("", "89%", " im Ziel"), widgetDetailText(2, "89%"))
        assertEquals(WidgetDetailText("", "--", ""), widgetDetailText(1, "--"))
    }

    @Test
    fun `compact value and supplement use a centered group with equal outer margins`() {
        val columns = compactGlucoseColumns(308f, 160f, 40f, 2f)
        assertTrue(columns.valueLeft > 28f)
        assertTrue(columns.supplementCenter < 308f * .82f)
        assertEquals(columns.valueLeft, 308f - columns.supplementCenter - 20f, .001f)
    }

    @Test
    fun `adaptive compact glucose value and trend are optically centered vertically`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = 1_792_411_200_000L
        val bitmap =
            renderAdaptiveGlucoseWidget(
                state = widgetPreviewState(now),
                palette = WidgetColorStore.load(context, app.sugarlicious.model.AppearanceMode.DARK),
                width = 308,
                height = 138,
                now = now,
                pixelDensity = 2f,
                configuration = WidgetInstanceConfiguration(),
                trendStyle =
                    app.sugarlicious.model.TrendArrowStyle
                        .defaults(app.sugarlicious.model.AppearanceMode.DARK, Color.WHITE),
            )
        val rows =
            (0 until bitmap.height).filter { y ->
                (28 until 215).any { x ->
                    val color = bitmap.getPixel(x, y)
                    Color.alpha(color) > 240 && Color.red(color) > 240 && Color.green(color) > 240 && Color.blue(color) > 240
                }
            }
        assertTrue(rows.isNotEmpty())
        assertEquals(bitmap.height / 2f, (rows.first() + rows.last() + 1) / 2f, 2f)
    }

    @Test
    fun `native DeviceVendor family bounds must not have compact padding subtracted twice`() {
        val insets = widgetContentInsetsDp(WidgetKind.METABOLIC, deviceVendorCompact = true, nativeHostSized = true)
        val actual = widgetRenderSizeDp(154.28572f, 68.85714f, insets)
        assertEquals(154.28572f, actual.widthDp, .001f)
        assertEquals(68.85714f, actual.heightDp, .001f)
        val options =
            android.os.Bundle().apply {
                putInt("semWidgetSize", 2)
                putFloat("hsResizeRatio", 1f)
            }
        assertTrue(widgetUsesNativeHostBounds("app.sugarlicious.mobile.MetabolicWidgetReceiver", options))
        assertTrue(widgetUsesNativeHostBounds("app.sugarlicious.mobile.TherapyFamily2x1Receiver", options))
        assertTrue(widgetUsesNativeHostBounds("app.sugarlicious.mobile.GraphFamily2x1Receiver", options))
        assertTrue(widgetUsesNativeHostBounds("app.sugarlicious.mobile.GlucoseFamily2x1Receiver", options))
        assertTrue(widgetUsesNativeHostBounds("app.sugarlicious.mobile.AdditionalWidgetReceiver", options))
        options.putFloat("hsResizeRatio", .8333333f)
        assertFalse(widgetUsesNativeHostBounds("app.sugarlicious.mobile.MetabolicWidgetReceiver", options))
    }

    @Test
    fun `glucose widget uses the full host surface`() {
        assertEquals(WidgetContentInsets.ZERO, widgetContentInsetsDp(WidgetKind.GLUCOSE, deviceVendorCompact = false))
    }

    @Test
    fun `therapy progress widget matches glucose surface and supports pill shape`() {
        assertEquals(WidgetContentInsets.ZERO, widgetContentInsetsDp(WidgetKind.METABOLIC, deviceVendorCompact = false))
        assertTrue(widgetSupportsPill(ConfigurableWidgetKind.METABOLIC))
    }

    @Test
    fun `deviceVendor compact size adds the same inset to every mobile widget`() {
        WidgetKind.entries.forEach { kind ->
            assertEquals(
                WidgetContentInsets(horizontalDp = 8f, verticalDp = 9f),
                widgetContentInsetsDp(kind, deviceVendorCompact = true),
            )
            assertEquals(WidgetContentInsets.ZERO, widgetContentInsetsDp(kind, deviceVendorCompact = false))
        }
    }

    @Test
    fun `widget bitmap is rendered for the actual compact content box`() {
        val size =
            widgetRenderSizeDp(
                hostWidthDp = 201.6f,
                hostHeightDp = 100.8f,
                insets = WidgetContentInsets(horizontalDp = 8f, verticalDp = 9f),
            )

        assertEquals(185.6f, size.widthDp, 0.01f)
        assertEquals(82.8f, size.heightDp, 0.01f)
    }

    @Test
    fun `widget settings preview uses the same compact homescreen dimensions`() {
        val size = widgetPreviewRenderSizeDp(WidgetKind.GLUCOSE_GRAPH, 201.6f, 201.6f, deviceVendorCompact = true)

        assertEquals(185.6f, size.widthDp, 0.01f)
        assertEquals(183.6f, size.heightDp, 0.01f)
    }

    @Test
    fun `global deviceVendor compact size setting persists`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WidgetLayoutSizeStore.setDeviceVendorCompact(context, true)
        assertTrue(WidgetLayoutSizeStore.isDeviceVendorCompact(context))

        WidgetLayoutSizeStore.setDeviceVendorCompact(context, false)
        assertFalse(WidgetLayoutSizeStore.isDeviceVendorCompact(context))
    }

    @Test
    fun `deviceVendor compact size is enabled for existing widgets until user disables it`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertTrue(WidgetLayoutSizeStore.isDeviceVendorCompact(context))
    }

    private val now = 20_000_000L
    private val thresholds = app.sugarlicious.model.CgmThresholds.DEFAULT
    private val palette =
        WidgetPalette(
            WidgetColorRole.entries.associateWith { role ->
                when (role) {
                    WidgetColorRole.GRAPH_BACKGROUND, WidgetColorRole.BACKGROUND -> Color.BLACK
                    WidgetColorRole.IN_RANGE, WidgetColorRole.TEXT, WidgetColorRole.AXIS -> Color.WHITE
                    WidgetColorRole.HIGH, WidgetColorRole.RANGE_HIGH, WidgetColorRole.HIGH_LINE -> Color.YELLOW
                    WidgetColorRole.LOW, WidgetColorRole.RANGE_LOW, WidgetColorRole.LOW_LINE -> Color.RED
                    else -> Color.GRAY
                }
            },
        )

    @Test
    fun `responsive layout covers narrow wide low and high surfaces`() {
        assertEquals(WidgetWidthClass.VERY_NARROW, responsiveWidgetLayout(90f, 80f).widthClass)
        assertEquals(WidgetHeightClass.LOW, responsiveWidgetLayout(90f, 80f).heightClass)
        assertEquals(WidgetWidthClass.VERY_WIDE, responsiveWidgetLayout(620f, 260f).widthClass)
        assertEquals(WidgetHeightClass.HIGH, responsiveWidgetLayout(620f, 260f).heightClass)
        assertTrue(responsiveWidgetLayout(90f, 80f).glucoseTextSp < responsiveWidgetLayout(400f, 220f).glucoseTextSp)
    }

    @Test
    fun `widget family layout maps deviceVendor 2x1 2x2 and 4x2 content sizes`() {
        assertEquals(WidgetFamilyLayout.COMPACT_2X1, widgetFamilyLayout(185.6f, 82.8f))
        assertEquals(WidgetFamilyLayout.DETAIL_2X2, widgetFamilyLayout(185.6f, 183.6f))
        assertEquals(WidgetFamilyLayout.WIDE_4X2, widgetFamilyLayout(413.9f, 183.6f))
        assertEquals(WidgetFamilyLayout.COMPACT_2X1, widgetFamilyLayout(299.9f, 120f))
        assertEquals(WidgetFamilyLayout.WIDE_4X2, widgetFamilyLayout(300f, 120f))
    }

    @Test
    fun `glucose family text follows compact and detail contracts`() {
        assertEquals(
            WidgetGlucoseFamilyText(age = "5m", delta = "-5"),
            widgetGlucoseFamilyText(WidgetFamilyLayout.COMPACT_2X1, "5m", "-5", "mg/dL", 89),
        )
        assertEquals(
            WidgetGlucoseFamilyText(delta = "+10 mg/dL", age = "5m", tir = "89%"),
            widgetGlucoseFamilyText(WidgetFamilyLayout.DETAIL_2X2, "5m", "+10", "mg/dL", 89),
        )
        assertEquals(
            WidgetGlucoseFamilyText(delta = "+10 mg/dL", age = "5m", tir = "--"),
            widgetGlucoseFamilyText(WidgetFamilyLayout.WIDE_4X2, "5m", "+10", "mg/dL", null),
        )
    }

    @Test
    fun `current day tir excludes yesterday future and invalid samples`() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
        val samples =
            listOf(
                GlucoseSample(100.0, Instant.parse("2026-10-19T06:00:00Z").toEpochMilli()),
                GlucoseSample(120.0, Instant.parse("2026-10-19T07:00:00Z").toEpochMilli()),
                GlucoseSample(200.0, Instant.parse("2026-10-19T08:00:00Z").toEpochMilli()),
                GlucoseSample(110.0, Instant.parse("2026-10-18T20:00:00Z").toEpochMilli()),
                GlucoseSample(100.0, Instant.parse("2026-10-19T13:00:00Z").toEpochMilli()),
                GlucoseSample(1_200.0, Instant.parse("2026-10-19T09:00:00Z").toEpochMilli()),
            )

        assertEquals(67, currentDayTirPercent(samples, 80.0, 160.0, now, zone))
        assertEquals(null, currentDayTirPercent(emptyList(), 80.0, 160.0, now, zone))
    }

    @Test
    fun `adaptive glucose geometry reserves a graph only for the 4x2 layout`() {
        val compact = adaptiveGlucoseGeometry(202f, 101f, WidgetFamilyLayout.COMPACT_2X1)
        val detail = adaptiveGlucoseGeometry(202f, 228f, WidgetFamilyLayout.DETAIL_2X2)
        val wide = adaptiveGlucoseGeometry(430f, 228f, WidgetFamilyLayout.WIDE_4X2)

        assertEquals(null, compact.graphBounds)
        assertEquals(null, detail.graphBounds)
        assertEquals(null, compact.dividerX)
        assertEquals(215f, wide.dividerX!!, 0.01f)
        assertEquals(230.05f, wide.graphBounds!!.left, 0.01f)
        assertEquals(228f * .70f, wide.graphBounds.height(), 0.01f)
        assertTrue(wide.valueBounds.right < wide.graphBounds.left)
    }

    @Test
    fun `adaptive glucose forces pill only for compact layout`() {
        assertEquals(50f, adaptiveGlucoseCornerRadius(WidgetFamilyLayout.COMPACT_2X1, 100f, 28f), 0.01f)
        assertEquals(28f, adaptiveGlucoseCornerRadius(WidgetFamilyLayout.DETAIL_2X2, 200f, 28f), 0.01f)
        assertEquals(28f, adaptiveGlucoseCornerRadius(WidgetFamilyLayout.WIDE_4X2, 200f, 28f), 0.01f)
    }

    @Test
    fun `adaptive glucose renderer produces every supported surface`() {
        val current = state(listOf(sample(110.0, -10), sample(115.0, -5)), 115.0)
        listOf(202 to 101, 202 to 228, 430 to 228).forEach { (width, height) ->
            val bitmap =
                renderAdaptiveGlucoseWidget(
                    state = current,
                    palette = palette,
                    width = width,
                    height = height,
                    now = now,
                    thresholds = thresholds,
                    pixelDensity = 1f,
                    configuration = WidgetInstanceConfiguration(),
                    trendStyle =
                        app.sugarlicious.model.TrendArrowStyle
                            .defaults(app.sugarlicious.model.AppearanceMode.DARK, Color.WHITE),
                )
            assertEquals(width, bitmap.width)
            assertEquals(height, bitmap.height)
            assertFalse(bitmap.isRecycled)
        }
    }

    @Test
    fun `graph label uses dark contrast only on confirmed high area`() {
        assertEquals(WidgetColorRole.HIGH_REGION_LABEL, graphScaleAgeColorRole(app.sugarlicious.model.RangeExcursion.HIGH))
        assertEquals(WidgetColorRole.AXIS, graphScaleAgeColorRole(null))
        assertEquals("3h · 5m", graphScaleAgeText(3, 5))
        assertEquals("3h", graphScaleAgeText(3, null))
    }

    @Test
    fun `graph scale mode cycles and wraps in canonical order`() {
        assertEquals(6, nextWidgetGraphHours(3))
        assertEquals(1, nextWidgetGraphHours(24))
        assertEquals(WidgetScaleMode.DYNAMIC, nextWidgetScaleMode(WidgetScaleMode.STATIC))
        assertEquals(WidgetScaleMode.LOGARITHMIC, nextWidgetScaleMode(WidgetScaleMode.DYNAMIC))
        assertEquals(WidgetScaleMode.STATIC, nextWidgetScaleMode(WidgetScaleMode.LOGARITHMIC))
    }

    @Test
    fun `therapy widget uses two slots at 2x1 and IOB COB above centered Basal at 2x2`() {
        val compact = resolveWidgetTherapyLayout(202f, 101f, TherapyProgressAppearance())
        val detail = resolveWidgetTherapyLayout(202f, 228f, TherapyProgressAppearance())

        assertEquals(listOf(TherapyProgressMetric.IOB, TherapyProgressMetric.COB), compact.map { it.metric })
        assertEquals(1, compact.map { it.slot.centerYDp }.distinct().size)
        assertEquals(TherapyProgressMetric.entries, detail.map { it.metric })
        assertEquals(101f, detail[2].slot.centerXDp, 0.01f)
        assertEquals(detail[0].slot.centerYDp, detail[1].slot.centerYDp, 0.01f)
        assertTrue(detail[0].slot.centerYDp < detail[2].slot.centerYDp)
        assertTrue(detail[0].slot.centerXDp < detail[1].slot.centerXDp)
        val horizontal = detail[1].slot.centerXDp - detail[0].slot.centerXDp
        val diagonal = kotlin.math.hypot(detail[2].slot.centerXDp - detail[0].slot.centerXDp, detail[2].slot.centerYDp - detail[0].slot.centerYDp)
        assertEquals("equidistant indicator centers", horizontal, diagonal, .01f)
    }

    @Test
    fun `first high colors value and graph immediately`() {
        val configured = thresholds.copy(highMgDl = 160.0)
        val first = state(listOf(sample(150.0, -10), sample(162.0, -5)), 162.0)
        val second = state(listOf(sample(162.0, -10), sample(165.0, -5)), 165.0)
        assertEquals(WidgetColorRole.HIGH, presentation(first, configured).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.HIGH, presentation(first, configured).excursion)
        assertEquals(WidgetColorRole.HIGH, presentation(second, configured).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.HIGH, presentation(second, configured).excursion)
    }

    @Test
    fun `first low colors value and graph immediately`() {
        val first = state(listOf(sample(100.0, -10), sample(65.0, -5)), 65.0)
        val second = state(listOf(sample(65.0, -10), sample(60.0, -5)), 60.0)
        assertEquals(WidgetColorRole.LOW, presentation(first).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.LOW, presentation(first).excursion)
        assertEquals(WidgetColorRole.LOW, presentation(second).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.LOW, presentation(second).excursion)
    }

    @Test
    fun `return in range resets immediately and duplicate does not change range tint`() {
        val returned = state(listOf(sample(190.0, -15), sample(195.0, -10), sample(150.0, -5)), 150.0)
        val duplicate = sample(190.0, -5, sequence = 7)
        val duplicates = state(listOf(duplicate, duplicate.copy()), 190.0)
        assertEquals(WidgetColorRole.IN_RANGE, presentation(returned).visibleRole)
        assertEquals(WidgetColorRole.HIGH, presentation(duplicates).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.HIGH, presentation(duplicates).excursion)
    }

    @Test
    fun `stale glucose preserves the last confirmed range role`() {
        val stale = state(listOf(sample(190.0, -40), sample(195.0, -35)), 195.0, currentMinutes = -35)
        assertEquals(WidgetColorRole.HIGH, presentation(stale).visibleRole)
        assertEquals(app.sugarlicious.model.RangeExcursion.HIGH, presentation(stale).excursion)
    }

    @Test
    fun `graph and glucose render at every requested surface without card inset`() {
        listOf(96 to 72, 160 to 90, 260 to 140, 420 to 180, 640 to 260).forEach { (width, height) ->
            val state = state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0)
            val graph = renderWidgetGraph(state, palette, width, height, now, thresholds)
            val glucose = renderMinimalGlucoseWidget(state, palette, width, height, now, thresholds)
            assertEquals(width, graph.width)
            assertEquals(height, graph.height)
            assertEquals(width, glucose.width)
            assertEquals(height, glucose.height)
            assertFalse(graph.isRecycled)
            assertFalse(glucose.isRecycled)
        }
    }

    @Test
    fun `glucose supplement composes delta age and unit in the approved rows`() {
        assertEquals(
            WidgetGlucoseSupplement(topLeft = "−2", bottomRight = "mg/dL"),
            widgetGlucoseSupplement(showDelta = true, showAge = false, delta = "−2", age = "2m", unit = "mg/dL"),
        )
        assertEquals(
            WidgetGlucoseSupplement(topLeft = "−2", separator = "·", topRight = "2m", bottomRight = "mg/dL"),
            widgetGlucoseSupplement(showDelta = true, showAge = true, delta = "−2", age = "2m", unit = "mg/dL"),
        )
        assertEquals(
            WidgetGlucoseSupplement(topRight = "2m"),
            widgetGlucoseSupplement(showDelta = false, showAge = true, delta = "−2", age = "2m", unit = "mg/dL"),
        )
        assertEquals(null, widgetGlucoseSupplement(false, false, "−2", "2m", "mg/dL"))
    }

    @Test
    fun `glucose and trend move into the left region when a supplement is visible`() {
        assertEquals(100f, widgetGlucoseGroupStartX(300f, 100f, hasSupplement = false), 0.01f)
        assertEquals(43f, widgetGlucoseGroupStartX(300f, 100f, hasSupplement = true), 0.01f)
    }

    @Test
    fun `therapy widget renders three mobile rings and preserves background transparency`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val transparentBackground = Color.argb(72, 10, 20, 30)
        val transparentPalette = palette.with(WidgetColorRole.BACKGROUND, transparentBackground)
        val bitmap =
            renderTherapyIndicatorWidget(
                context,
                null,
                transparentPalette,
                402,
                202,
                now,
                DashboardUiPreferences(),
                WidgetInstanceConfiguration(backgroundArgb = transparentBackground),
                2f,
            )

        assertEquals(402, bitmap.width)
        assertEquals(202, bitmap.height)
        assertTrue(
            (0 until bitmap.width).any { x ->
                Color.alpha(bitmap.getPixel(x, bitmap.height / 2)) == Color.alpha(transparentBackground)
            },
        )
        val occupiedCells =
            (0 until 3).count { cell ->
                val left = cell * bitmap.width / 3
                val right = (cell + 1) * bitmap.width / 3
                (left until right step 2).any { x ->
                    (0 until bitmap.height step 2).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 72 }
                }
            }
        assertEquals(3, occupiedCells)
    }

    @Test
    fun `graph metrics reserve complete axes and keep mobile dot proportions`() {
        listOf(96 to 72, 160 to 90, 260 to 140, 420 to 180, 640 to 260).forEach { (width, height) ->
            val layout = responsiveWidgetLayout(width.toFloat(), height.toFloat())
            val metrics = widgetGraphMetrics(width, height, 1f, layout, Paint())
            assertEquals(0f, metrics.graphBounds.left, 0.01f)
            assertEquals(0f, metrics.graphBounds.top, 0.01f)
            assertTrue(metrics.plot.left > metrics.graphBounds.left)
            assertTrue(metrics.plot.right < metrics.graphBounds.right)
            assertTrue(metrics.plot.right < width)
            assertTrue(metrics.plot.bottom < height)
            assertTrue(metrics.plot.width() > 0f)
            assertTrue(metrics.plot.height() > 0f)
            assertEquals(2.4f, metrics.dotRadiusPx, 0.01f)
            assertEquals(0.95f, metrics.outlineWidthPx, 0.01f)
        }
    }

    @Test
    fun `trend arrow keeps the same vector scale in every direction`() {
        val targetHeight = 48f
        val scales =
            app.sugarlicious.model.TrendVisualAsset.entries
                .map { asset -> trendArrowGeometry(targetHeight, app.sugarlicious.model.TrendVisualSpec(asset)).scalePx }
        scales.forEach { scale -> assertEquals(scales.first(), scale, 0.0001f) }
        assertEquals(targetHeight, 60f * scales.first(), 0.05f)
    }

    @Test
    fun `notification renderer preserves every supplied trend canvas and double arrow aspect`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected =
            mapOf(
                Trend.DOUBLE_UP to 125f / 60f,
                Trend.SINGLE_UP to 1f,
                Trend.FORTY_FIVE_UP to 1f,
                Trend.FLAT to 1f,
                Trend.FORTY_FIVE_DOWN to 1f,
                Trend.SINGLE_DOWN to 1f,
                Trend.DOUBLE_DOWN to 125f / 60f,
            )
        expected.forEach { (trend, aspect) ->
            val bitmap = NotificationTrendRenderer.render(context, trend, 60)!!
            assertEquals(60, bitmap.height)
            assertEquals((60f * aspect).roundToInt(), bitmap.width)
        }
    }

    @Test
    fun `widget trend arrow follows the 2x2 reference ratio at every density`() {
        listOf(1f, 2f, 3f, 4f).forEach { density ->
            val textHeight = 48f * density
            assertEquals(textHeight * 0.642f, widgetTrendArrowTargetHeight(textHeight, 1f, density), 0.01f)
        }
    }

    @Test
    fun `low graph surfaces devote more height to plot than legacy axis band`() {
        val layout = responsiveWidgetLayout(160f, 72f)
        val metrics = widgetGraphMetrics(320, 144, 2f, layout, Paint(), showTimeAxis = true)
        assertTrue(metrics.plot.bottom > 100f)
        assertTrue(metrics.axisBaselinePx > 130f)
    }

    @Test
    fun `combined two by two widget renders left glucose block and larger graph`() {
        val bitmap =
            renderGlucoseGraphWidget(
                state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0),
                palette,
                440,
                440,
                now,
                thresholds,
                responsiveWidgetLayout(220f, 220f),
                2f,
                WidgetInstanceConfiguration(showTimeAxis = true),
            )
        assertEquals(440, bitmap.width)
        assertEquals(440, bitmap.height)
        assertFalse(bitmap.isRecycled)
        val metrics = combinedWidgetMetrics(440, 440, 2f, DEFAULT_COMBINED_WIDGET_VALUE_PERCENT)
        assertTrue(metrics.headerHeightPx in 160..175)
        assertTrue(metrics.graphFrame.top < metrics.headerHeightPx)
        assertTrue(metrics.headerHeightPx - metrics.graphFrame.top <= 8)
        assertEquals(440, metrics.graphFrame.bottom)
    }

    @Test
    fun `combined widget applies background transparency once and keeps graph opaque`() {
        val translucentBackground = 0x66000000
        val transparentPalette =
            palette
                .with(WidgetColorRole.BACKGROUND, translucentBackground)
                .with(WidgetColorRole.GRAPH_BACKGROUND, 0x66333333)
        val configuration =
            WidgetInstanceConfiguration(
                backgroundArgb = translucentBackground,
                backgroundEnabled = true,
                cornerRadiusDp = 28,
            )
        val bitmap =
            renderGlucoseGraphWidget(
                state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0),
                transparentPalette,
                440,
                440,
                now,
                thresholds,
                responsiveWidgetLayout(220f, 220f),
                2f,
                configuration,
            )
        val metrics = combinedWidgetMetrics(440, 440, 2f, DEFAULT_COMBINED_WIDGET_VALUE_PERCENT)

        assertEquals(0x66, Color.alpha(bitmap.getPixel(220, 24)))
        assertEquals(0xFF, Color.alpha(bitmap.getPixel(metrics.graphFrame.centerX(), metrics.graphFrame.centerY())))
    }

    @Test
    fun `combined widget foreground remains opaque over translucent background`() {
        val bitmap =
            renderGlucoseGraphWidget(
                state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0),
                palette,
                440,
                440,
                now,
                thresholds,
                responsiveWidgetLayout(220f, 220f),
                2f,
                WidgetInstanceConfiguration(
                    backgroundArgb = 0x44000000,
                    outlineEnabled = true,
                    outlineArgb = Color.GREEN,
                    cornerRadiusDp = 28,
                ),
            )
        val headerHeight = combinedWidgetMetrics(440, 440, 2f, DEFAULT_COMBINED_WIDGET_VALUE_PERCENT).headerHeightPx
        val opaqueForegroundPixels =
            (0 until headerHeight).sumOf { y ->
                (0 until bitmap.width).count { x ->
                    val pixel = bitmap.getPixel(x, y)
                    Color.alpha(pixel) == 0xFF && pixel != Color.BLACK
                }
            }

        assertTrue("value, arrow and outline must provide fully opaque foreground pixels", opaqueForegroundPixels > 100)
        assertEquals(0xFF, Color.alpha(bitmap.getPixel(bitmap.width / 2, 1)))
    }

    @Test
    fun `two by two widget renders 200 percent value and widest double arrow without failure`() {
        val bitmap =
            renderGlucoseGraphWidget(
                state(listOf(sample(245.0, -10), sample(250.0, -5)), 250.0).copy(
                    glucose = state(listOf(sample(245.0, -10), sample(250.0, -5)), 250.0).glucose?.copy(trend = Trend.DOUBLE_UP),
                ),
                palette,
                440,
                440,
                now,
                thresholds,
                responsiveWidgetLayout(220f, 220f),
                2f,
                WidgetInstanceConfiguration(glucoseScalePercent = 200, trendScalePercent = 200),
            )
        assertEquals(440, bitmap.width)
        assertEquals(440, bitmap.height)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun `widget graph clips every range fill to all four configured corners`() {
        val high = state(listOf(sample(190.0, -10), sample(195.0, -5)), 195.0)
        val low = state(listOf(sample(65.0, -10), sample(60.0, -5)), 60.0)
        val inRange = state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0)
        val configuration =
            WidgetInstanceConfiguration(
                showTimeAxis = false,
                backgroundEnabled = false,
                graphCornerRadiusDp = 24,
            )

        listOf(high, low, inRange).forEach { current ->
            val width = 320
            val height = 180
            val layout = responsiveWidgetLayout(width.toFloat(), height.toFloat())
            val metrics =
                widgetGraphMetrics(
                    width,
                    height,
                    1f,
                    layout,
                    Paint(),
                    showTimeAxis = false,
                    graphCornerRadiusDp = configuration.graphCornerRadiusDp.toFloat(),
                )
            val bitmap =
                renderWidgetGraph(
                    current,
                    palette,
                    width,
                    height,
                    now,
                    thresholds,
                    layout,
                    1f,
                    configuration,
                    clipToWidgetShape = false,
                )
            val left = metrics.graphBounds.left.toInt()
            val top = metrics.graphBounds.top.toInt()
            val right = metrics.graphBounds.right.toInt() - 1
            val bottom = metrics.graphBounds.bottom.toInt() - 1
            val clipRegion =
                Region().apply {
                    setPath(
                        widgetGraphClipPath(metrics),
                        Region(Rect(left, top, right + 1, bottom + 1)),
                    )
                }
            assertTrue(clipRegion.contains(metrics.graphBounds.centerX().toInt(), metrics.graphBounds.centerY().toInt()))
            listOf(left to top, right to top, left to bottom, right to bottom).forEach { (x, y) ->
                assertFalse("rounded graph corner at $x,$y must stay outside the plot clip", clipRegion.contains(x, y))
            }
            assertFalse(bitmap.isRecycled)
        }
    }

    @Test
    fun `only combined widget aligns graph inset with glucose value`() {
        val standalone =
            widgetGraphMetrics(
                440,
                260,
                2f,
                responsiveWidgetLayout(220f, 130f),
                Paint(),
            )
        val combined =
            widgetGraphMetrics(
                440,
                260,
                2f,
                responsiveWidgetLayout(220f, 130f),
                Paint(),
                graphLeftInsetDp = 12f,
            )

        assertEquals(0f, standalone.graphBounds.left, 0.01f)
        assertEquals(0f, combined.graphBounds.left, 0.01f)
        assertEquals(standalone.plot.left, combined.plot.left, 0.01f)
    }

    @Test
    fun `combined and standalone graph bands share full width surface edges`() {
        val layout = responsiveWidgetLayout(220f, 130f)
        val standalone = widgetGraphMetrics(440, 260, 2f, layout, Paint())
        val combined =
            widgetGraphMetrics(
                440,
                260,
                2f,
                layout,
                Paint(),
                graphLeftInsetDp = 12f,
            )

        assertEquals(0f, standalone.graphBounds.left, 0.01f)
        assertEquals(440f, standalone.graphBounds.right, 0.01f)
        assertEquals(0f, combined.graphBounds.left, 0.01f)
        assertEquals(440f, combined.graphBounds.right, 0.01f)
        assertTrue(combined.plot.left > combined.graphBounds.left)
    }

    @Test
    fun `widget target labels start directly beside the live boundary`() {
        val density = 2f
        val metrics =
            widgetGraphMetrics(
                440,
                260,
                density,
                responsiveWidgetLayout(220f, 130f),
                Paint(),
            )

        val pointExtent = metrics.plot.left - metrics.graphBounds.left
        assertTrue(metrics.yLabelLeftPx >= metrics.plot.right + pointExtent)
        assertTrue(metrics.yLabelLeftPx - metrics.plot.right <= pointExtent + 2f * density)
    }

    @Test
    fun `graph corner radius is responsive and remains bounded by graph geometry`() {
        listOf(96 to 72, 320 to 180, 640 to 520).forEach { (width, height) ->
            val metrics =
                widgetGraphMetrics(
                    width,
                    height,
                    1f,
                    responsiveWidgetLayout(width.toFloat(), height.toFloat()),
                    Paint(),
                    graphCornerRadiusDp = MAX_WIDGET_GRAPH_CORNER_RADIUS_DP.toFloat(),
                )
            assertTrue(metrics.graphCornerRadiusPx <= metrics.graphBounds.width() / 2f)
            assertTrue(metrics.graphCornerRadiusPx <= metrics.graphBounds.height() / 2f)
            assertTrue(metrics.graphCornerRadiusPx >= 0f)
        }
    }

    @Test
    fun `graph and combined widget clip their outer corners to the configured shape`() {
        val current = state(listOf(sample(115.0, -10), sample(120.0, -5)), 120.0)
        val configuration = WidgetInstanceConfiguration(cornerRadiusDp = 28)
        val graph = renderWidgetGraph(current, palette, 320, 180, now, thresholds, configuration = configuration)
        val combined = renderGlucoseGraphWidget(current, palette, 320, 320, now, thresholds, configuration = configuration)

        assertEquals(0, Color.alpha(graph.getPixel(0, 0)))
        assertEquals(0, Color.alpha(combined.getPixel(0, combined.height - 1)))
    }

    @Test
    fun `widget point moves left when only clock advances`() {
        val timestamp = now - 5L * 60_000L
        val history = 3L * 60L * 60_000L
        val plot = RectF(0f, 0f, 600f, 220f)
        val first = widgetGraphPointX(timestamp, GraphTimeWindow.live(now, history), plot)
        val oneMinuteLater = widgetGraphPointX(timestamp, GraphTimeWindow.live(now + 60_000L, history), plot)

        assertTrue(oneMinuteLater < first)
    }

    @Test
    fun `render hardware resize regression matrix when requested`() {
        val output = System.getenv("WIDGET_MATRIX_DIR")?.let(::File) ?: return
        output.mkdirs()
        val state =
            state(
                history = (0..36).map { index -> sample(105.0 + index * 3.8, -180 + index * 5) },
                current = 242.0,
                currentMinutes = 0,
            )
        val density = 2f
        listOf(
            "very-small" to (192 to 144),
            "small" to (320 to 180),
            "medium" to (520 to 280),
            "large" to (840 to 360),
            "extra-wide" to (1280 to 520),
        ).forEach { (name, dimensions) ->
            val (width, height) = dimensions
            val bitmap =
                renderWidgetGraph(
                    state = state,
                    palette = palette,
                    width = width,
                    height = height,
                    now = now,
                    thresholds = thresholds,
                    layout = responsiveWidgetLayout(width / density, height / density),
                    pixelDensity = density,
                )
            FileOutputStream(File(output, "$name.png")).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        listOf("combined-2x2" to (440 to 440), "combined-1x2" to (440 to 220)).forEach { (name, dimensions) ->
            val (width, height) = dimensions
            val bitmap =
                renderGlucoseGraphWidget(
                    state,
                    palette,
                    width,
                    height,
                    now,
                    thresholds,
                    responsiveWidgetLayout(width / density, height / density),
                    density,
                    WidgetInstanceConfiguration(showTimeAxis = true),
                )
            FileOutputStream(File(output, "$name.png")).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    private fun presentation(
        state: TherapyDisplayState,
        configuredThresholds: app.sugarlicious.model.CgmThresholds = thresholds,
    ) = widgetRangePresentation(state, canonicalWidgetSamples(state, now), configuredThresholds, now)

    private fun sample(
        value: Double,
        minutes: Int,
        sequence: Long? = null,
    ) = GlucoseSample(
        valueMgDl = value,
        measuredAtEpochMs = now + minutes * 60_000L,
        source = DataSourceId.ANDROID_APS,
        sensorId = "sensor",
        sessionId = "session",
        sequenceNumber = sequence,
    )

    private fun state(
        history: List<GlucoseSample>,
        current: Double,
        currentMinutes: Int = -5,
    ) = TherapyDisplayState(
        source = DataSourceId.ANDROID_APS,
        receivedAtEpochMs = now,
        glucose = GlucoseState(current, GlucoseUnit.MG_DL, Trend.FORTY_FIVE_UP, now + currentMinutes * 60_000L),
        glucoseHistory = history,
        target = TargetState(lowMgDl = thresholds.lowMgDl, highMgDl = thresholds.highMgDl),
    )
}
