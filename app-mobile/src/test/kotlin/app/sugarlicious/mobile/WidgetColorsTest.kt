package app.sugarlicious.mobile

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetColorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context
            .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test fun `widget data colors match modes and current point has a white light outline`() {
        val dark = WidgetColorStore.load(context, AppearanceMode.DARK)
        val light = WidgetColorStore.load(context, AppearanceMode.LIGHT)
        listOf(WidgetColorRole.RANGE_HIGH, WidgetColorRole.RANGE_LOW, WidgetColorRole.HIGH_LINE, WidgetColorRole.LOW_LINE).forEach { role -> assertEquals(role.name, dark.argb(role), light.argb(role)) }
        assertEquals(Color.WHITE, light.argb(WidgetColorRole.CURRENT_DOT_OUTLINE))
        assertEquals(Color.BLACK, dark.argb(WidgetColorRole.CURRENT_DOT_OUTLINE))
        assertTrue(dark.argb(WidgetColorRole.TREND) != light.argb(WidgetColorRole.TREND))
    }

    @Test
    fun `all glucose severities have deterministic independent roles`() {
        assertEquals(WidgetColorRole.URGENT_LOW, widgetGlucoseColorRole(40.0, 80.0, 160.0))
        assertEquals(WidgetColorRole.LOW, widgetGlucoseColorRole(79.0, 80.0, 160.0))
        assertEquals(WidgetColorRole.IN_RANGE, widgetGlucoseColorRole(120.0, 80.0, 160.0))
        assertEquals(WidgetColorRole.HIGH, widgetGlucoseColorRole(161.0, 80.0, 160.0))
        assertEquals(WidgetColorRole.VERY_HIGH, widgetGlucoseColorRole(400.0, 80.0, 160.0))
    }

    @Test
    fun `very high widget value uses opaque mobile glucose color by default`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        val mobile = SugarliciousColorStore.load(preferences, AppearanceMode.DARK)

        val widget = WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.VERY_HIGH)

        assertEquals(mobile.argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH), widget)
        assertEquals(255, Color.alpha(widget))
    }

    @Test
    fun `copy from graph keeps very high value independent from transparent range fill`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        val rangeFill = Color.argb(76, 255, 208, 64)
        val glucoseVeryHigh = Color.rgb(210, 44, 51)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.RANGE_HIGH, rangeFill)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.GLUCOSE_VERY_HIGH, glucoseVeryHigh)

        WidgetColorStore.copyFromMobileGraph(context, AppearanceMode.DARK)

        assertEquals(glucoseVeryHigh, WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.VERY_HIGH))
    }

    @Test
    fun `legacy range fill copied into very high text is migrated for both appearance modes`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        val legacyRangeFill = Color.argb(76, 255, 208, 64)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.RANGE_HIGH, legacyRangeFill)
        preferences
            .edit()
            .putInt(WidgetColorStore.key(AppearanceMode.DARK, WidgetColorRole.VERY_HIGH), legacyRangeFill)
            .putInt(WidgetColorStore.key(AppearanceMode.LIGHT, WidgetColorRole.VERY_HIGH), legacyRangeFill)
            .commit()

        val dark = WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.VERY_HIGH)
        val light = WidgetColorStore.load(context, AppearanceMode.LIGHT).argb(WidgetColorRole.VERY_HIGH)

        assertEquals(255, Color.alpha(dark))
        assertEquals(255, Color.alpha(light))
        assertEquals(
            SugarliciousColorStore.load(preferences, AppearanceMode.DARK).argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
            dark,
        )
        assertEquals(
            SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT).argb(SugarliciousColorRole.GLUCOSE_VERY_HIGH),
            light,
        )
    }

    @Test
    fun `copy from graph is a snapshot and remains independent afterwards`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val first = Color.rgb(19, 81, 177)
        val later = Color.rgb(210, 44, 51)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_IN_RANGE, first)

        WidgetColorStore.copyFromMobileGraph(context)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.CGM_DOT_IN_RANGE, later)

        assertEquals(first, WidgetColorStore.load(context).argb(WidgetColorRole.IN_RANGE))
        assertTrue(WidgetColorStore.hasOverride(context, WidgetColorRole.IN_RANGE))
        WidgetColorStore.reset(context, WidgetColorRole.IN_RANGE)
        assertFalse(WidgetColorStore.hasOverride(context, WidgetColorRole.IN_RANGE))
    }

    @Test
    fun `light and dark widget palettes remain independent`() {
        val light = Color.rgb(245, 231, 201)
        val dark = Color.rgb(11, 24, 39)

        WidgetColorStore.save(context, AppearanceMode.LIGHT, WidgetColorRole.BACKGROUND, light)
        WidgetColorStore.save(context, AppearanceMode.DARK, WidgetColorRole.BACKGROUND, dark)

        assertEquals(light, WidgetColorStore.load(context, AppearanceMode.LIGHT).argb(WidgetColorRole.BACKGROUND))
        assertEquals(dark, WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.BACKGROUND))
        assertTrue(WidgetColorStore.hasOverride(context, AppearanceMode.LIGHT, WidgetColorRole.BACKGROUND))
        assertTrue(WidgetColorStore.hasOverride(context, AppearanceMode.DARK, WidgetColorRole.BACKGROUND))

        WidgetColorStore.reset(context, AppearanceMode.LIGHT, WidgetColorRole.BACKGROUND)

        assertFalse(WidgetColorStore.hasOverride(context, AppearanceMode.LIGHT, WidgetColorRole.BACKGROUND))
        assertEquals(dark, WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.BACKGROUND))
    }

    @Test
    fun `widget delta and unit override stays independent from mobile appearance`() {
        val mobile = Color.rgb(12, 34, 56)
        val widget = Color.argb(144, 78, 90, 123)
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.DELTA_UNIT, mobile)
        WidgetColorStore.save(context, AppearanceMode.DARK, WidgetColorRole.DELTA_UNIT, widget)

        assertEquals(widget, WidgetColorStore.load(context, AppearanceMode.DARK).argb(WidgetColorRole.DELTA_UNIT))
        assertEquals(mobile, SugarliciousColorStore.load(preferences, AppearanceMode.DARK).argb(SugarliciousColorRole.DELTA_UNIT))
    }

    @Test
    fun `graph widget renders canonical points and is registered`() {
        val now = 10_000_000L
        val palette =
            WidgetPalette(
                WidgetColorRole.entries.associateWith { role ->
                    when (role) {
                        WidgetColorRole.IN_RANGE -> Color.rgb(17, 231, 93)
                        WidgetColorRole.BACKGROUND -> Color.rgb(12, 15, 18)
                        WidgetColorRole.TEXT -> Color.WHITE
                        else -> Color.rgb(230, 80, 80)
                    }
                },
            )
        val state =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now,
                glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, Trend.FLAT, now - 60_000L),
                glucoseHistory =
                    listOf(
                        GlucoseSample(110.0, now - 10 * 60_000L),
                        GlucoseSample(120.0, now - 5 * 60_000L),
                    ),
                target = TargetState(lowMgDl = 80.0, highMgDl = 160.0),
            )

        val bitmap = renderWidgetGraph(state, palette, width = 400, height = 180, now = now)

        assertEquals(400, bitmap.width)
        assertEquals(180, bitmap.height)
        assertFalse(bitmap.isRecycled)
        assertEquals(3, canonicalWidgetSamples(state, now).size)
        val receiver =
            context.packageManager.getReceiverInfo(
                ComponentName(context, GraphWidgetReceiver::class.java),
                PackageManager.GET_META_DATA,
            )
        assertNotNull(receiver)
        assertNotNull(receiver.metaData)
    }

    @Test
    fun `freshness state is always explicit`() {
        assertEquals("AKTUELL", widgetFreshnessStatus(app.sugarlicious.model.Freshness.CURRENT))
        assertEquals("VERZÖGERT", widgetFreshnessStatus(app.sugarlicious.model.Freshness.DELAYED))
        assertEquals("VERALTET", widgetFreshnessStatus(app.sugarlicious.model.Freshness.STALE))
        assertEquals("SENSORFEHLER", widgetFreshnessStatus(app.sugarlicious.model.Freshness.ERROR))
        assertEquals("KEINE DATEN", widgetFreshnessStatus(app.sugarlicious.model.Freshness.NO_DATA))
    }

    @Test
    fun `light mode never adds an automatic icon silhouette`() {
        assertFalse(shouldOutlineSugarliciousIcon(isLight = true, colored = true))
        assertFalse(shouldOutlineSugarliciousIcon(isLight = false, colored = true))
        assertFalse(shouldOutlineSugarliciousIcon(isLight = true, colored = false))
    }
}
