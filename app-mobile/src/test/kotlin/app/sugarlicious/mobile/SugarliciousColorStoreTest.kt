package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarliciousColorStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `wave follows configured range text until its own picker is set and target stays green`() {
        val preferences = context.getSharedPreferences("independent_wave_colors", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        listOf(AppearanceMode.DARK, AppearanceMode.LIGHT).forEach { mode ->
            val pairs = mapOf(SugarliciousColorRole.OVERVIEW_WAVE_LOW to SugarliciousColorRole.GLUCOSE_LOW, SugarliciousColorRole.OVERVIEW_WAVE_VERY_LOW to SugarliciousColorRole.GLUCOSE_VERY_LOW, SugarliciousColorRole.OVERVIEW_WAVE_HIGH to SugarliciousColorRole.GLUCOSE_HIGH, SugarliciousColorRole.OVERVIEW_WAVE_VERY_HIGH to SugarliciousColorRole.GLUCOSE_VERY_HIGH)
            pairs.forEach { (wave, text) ->
                SugarliciousColorStore.save(preferences, mode, text, Color.MAGENTA)
                assertEquals(Color.MAGENTA, SugarliciousColorStore.load(preferences, mode).argb(wave))
                SugarliciousColorStore.save(preferences, mode, wave, Color.CYAN)
                assertEquals(Color.CYAN, SugarliciousColorStore.load(preferences, mode).argb(wave))
                assertEquals(Color.MAGENTA, SugarliciousColorStore.load(preferences, mode).argb(text))
            }
            assertEquals(0xFF22EB60.toInt(), SugarliciousColorStore.load(preferences, mode).argb(SugarliciousColorRole.OVERVIEW_WAVE_IN_RANGE))
        }
    }

    @Test fun `data colors retain the exact hue across modes while neutral ink and current outline invert`() {
        val preferences = context.getSharedPreferences("matching_data_color_defaults", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val dark = SugarliciousColorStore.load(preferences, AppearanceMode.DARK)
        val light = SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT)
        listOf(SugarliciousColorRole.RANGE_LOW, SugarliciousColorRole.RANGE_HIGH, SugarliciousColorRole.GLUCOSE_LOW, SugarliciousColorRole.GLUCOSE_HIGH, SugarliciousColorRole.THERAPY_IOB_PROGRESS, SugarliciousColorRole.THERAPY_COB_PROGRESS, SugarliciousColorRole.THERAPY_BASAL_PROGRESS, SugarliciousColorRole.GRAPH_IOB, SugarliciousColorRole.GRAPH_COB, SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY).forEach { role ->
            assertEquals(role.name, dark.argb(role), light.argb(role))
        }
        assertEquals(Color.BLACK, dark.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE))
        assertEquals(Color.WHITE, light.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE))
        assertTrue(dark.argb(SugarliciousColorRole.TEXT_PRIMARY) != light.argb(SugarliciousColorRole.TEXT_PRIMARY))
    }

    @Test
    fun `very high and very low dots have independent persistent color pickers`() {
        val preferences = context.getSharedPreferences("urgent_dot_colors", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        listOf("low", "high").forEach { range ->
            val dot = SugarliciousColorRole.entries.singleOrNull { it.preferenceKey == "cgm_dot_very_$range" }
            org.junit.Assert.assertNotNull("missing independent very-$range dot picker", dot)
            val role = requireNotNull(dot)
            assertTrue(role.configurable)
            val text = SugarliciousColorRole.entries.single { it.preferenceKey == "glucose_very_$range" }
            listOf(AppearanceMode.DARK, AppearanceMode.LIGHT).forEach { mode ->
                SugarliciousColorStore.save(preferences, mode, text, Color.MAGENTA)
                assertEquals("existing colors must survive migration", Color.MAGENTA, SugarliciousColorStore.load(preferences, mode).argb(role))
                SugarliciousColorStore.save(preferences, mode, role, Color.CYAN)
                val palette = SugarliciousColorStore.load(preferences, mode)
                assertEquals(Color.CYAN, palette.argb(role))
                assertEquals("changing dots must not recolor the glucose value", Color.MAGENTA, palette.argb(text))
            }
        }
    }

    @Test
    fun `fresh install uses approved mobile color profiles`() {
        val preferences = context.getSharedPreferences("approved_color_defaults", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        val dark = SugarliciousColorStore.load(preferences, AppearanceMode.DARK)
        val light = SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT)

        mapOf(
            SugarliciousColorRole.BACKGROUND to 0xFF191919.toInt(),
            SugarliciousColorRole.SURFACE to 0xFF191919.toInt(),
            SugarliciousColorRole.GRAPH_BACKGROUND to 0xFF191919.toInt(),
            SugarliciousColorRole.GLUCOSE_LOW to 0xFFF73D45.toInt(),
            SugarliciousColorRole.GLUCOSE_HIGH to 0xFFFCC93B.toInt(),
            SugarliciousColorRole.RANGE_IN_RANGE to 0xFF202020.toInt(),
            SugarliciousColorRole.PROGRESS_IN_RANGE to 0xFF4BFA7C.toInt(),
            SugarliciousColorRole.CGM_DOT_LOW to Color.WHITE,
            SugarliciousColorRole.CGM_DOT_IN_RANGE to Color.WHITE,
            SugarliciousColorRole.CGM_DOT_HIGH to Color.WHITE,
            SugarliciousColorRole.TARGET_VALUE to 0xFFDCDCDC.toInt(),
            SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY to 0xFF24A4B0.toInt(),
            SugarliciousColorRole.GRAPH_VERY_HIGH_LINE to 0x00FCC93B,
            SugarliciousColorRole.GRAPH_VERY_LOW_LINE to 0x00F73D45,
            SugarliciousColorRole.GRAPH_SIGNAL_LOSS to 0xB26E6E6E.toInt(),
        ).forEach { (role, expected) -> assertEquals("dark ${role.name}", expected, dark.argb(role)) }

        mapOf(
            SugarliciousColorRole.GLUCOSE_IN_RANGE to Color.BLACK,
            SugarliciousColorRole.GLUCOSE_HIGH to 0xFFFCC93B.toInt(),
            SugarliciousColorRole.RANGE_IN_RANGE to 0xFFEDEDED.toInt(),
            SugarliciousColorRole.PROGRESS_IN_RANGE to 0xFF4BFA7C.toInt(),
            SugarliciousColorRole.CGM_DOT_LOW to Color.BLACK,
            SugarliciousColorRole.CGM_DOT_IN_RANGE to Color.BLACK,
            SugarliciousColorRole.CGM_DOT_HIGH to Color.BLACK,
            SugarliciousColorRole.GRAPH_COB to 0xFFFF9D18.toInt(),
            SugarliciousColorRole.GRAPH_VERY_HIGH_LINE to 0x00FCC93B,
            SugarliciousColorRole.GRAPH_VERY_LOW_LINE to 0x00F73D45,
            SugarliciousColorRole.GRAPH_SIGNAL_LOSS to 0x38808080,
        ).forEach { (role, expected) -> assertEquals("light ${role.name}", expected, light.argb(role)) }
    }

    @Test
    fun `every configurable color role round trips exact ARGB in dark and light mode`() {
        val preferences = context.getSharedPreferences("color_picker_roundtrip", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        listOf("DARK", "LIGHT").forEachIndexed { modeIndex, mode ->
            preferences.edit().putString("themeMode", mode).commit()
            SugarliciousColorRole.entries.filter { it.configurable }.forEachIndexed { index, role ->
                val argb =
                    Color.argb(
                        40 + (index * 13 + modeIndex * 7) % 216,
                        20 + (index * 31) % 220,
                        25 + (index * 47) % 215,
                        30 + (index * 61) % 210,
                    )
                SugarliciousColorStore.save(preferences, role, argb)
                assertEquals("$mode ${role.name}", argb, SugarliciousColorStore.load(preferences).argb(role))
            }
        }
    }

    @Test
    fun `target value picker is exposed without inheriting legacy target band preference`() {
        val preferences = context.getSharedPreferences("target_value_legacy_isolation", Context.MODE_PRIVATE)
        val legacyTargetBand = Color.rgb(6, 48, 18)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .putInt("color.dark.target_band", legacyTargetBand)
            .commit()

        assertTrue(SugarliciousColorRole.TARGET_VALUE.configurable)
        assertEquals("target_value", SugarliciousColorRole.TARGET_VALUE.preferenceKey)
        assertEquals(
            SugarliciousColorRole.TARGET_VALUE.defaultArgb,
            SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.TARGET_VALUE),
        )
    }

    @Test
    fun `default target follows an overridden in range hue and stays brighter`() {
        val preferences = context.getSharedPreferences("target_value_derived_hue", Context.MODE_PRIVATE)
        val inRange = Color.rgb(42, 92, 160)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_IN_RANGE, inRange)

        val target = SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.TARGET_VALUE)
        val inRangeHsv = FloatArray(3).also { Color.colorToHSV(inRange, it) }
        val targetHsv = FloatArray(3).also { Color.colorToHSV(target, it) }

        assertEquals(inRangeHsv[0], targetHsv[0], 0.01f)
        assertTrue(targetHsv[2] > inRangeHsv[2])
    }

    @Test
    fun `explicit user colors are isolated between light and dark themes`() {
        val preferences = context.getSharedPreferences("theme_specific_user_override", Context.MODE_PRIVATE)
        val chosen = Color.argb(144, 7, 91, 203)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, chosen)

        assertEquals(chosen, SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.RANGE_HIGH))
        preferences.edit().putString("themeMode", "LIGHT").commit()
        assertEquals(
            SugarliciousColorRole.RANGE_HIGH.lightArgb,
            SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.RANGE_HIGH),
        )

        val lightChosen = Color.rgb(12, 34, 56)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, lightChosen)
        assertEquals(lightChosen, SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.RANGE_HIGH))

        preferences.edit().putString("themeMode", "DARK").commit()
        assertEquals(chosen, SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.RANGE_HIGH))
    }

    @Test
    fun `delta and unit color preserves alpha and stays isolated by appearance mode`() {
        val preferences = context.getSharedPreferences("delta_unit_color_scope", Context.MODE_PRIVATE)
        val light = Color.argb(122, 11, 22, 33)
        val dark = Color.argb(211, 44, 55, 66)
        preferences.edit().clear().commit()

        SugarliciousColorStore.save(preferences, AppearanceMode.LIGHT, SugarliciousColorRole.DELTA_UNIT, light)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.DELTA_UNIT, dark)

        assertEquals(light, SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT).argb(SugarliciousColorRole.DELTA_UNIT))
        assertEquals(dark, SugarliciousColorStore.load(preferences, AppearanceMode.DARK).argb(SugarliciousColorRole.DELTA_UNIT))
    }

    @Test
    fun `light graph and in range dots remain independently configurable`() {
        val preferences = context.getSharedPreferences("light_graph_contrast", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "LIGHT")
            .putInt("color.light.graph_background", Color.rgb(32, 32, 32))
            .putInt("color.light.cgm_dot_in_range", Color.WHITE)
            .commit()

        val palette = SugarliciousColorStore.load(preferences)
        assertEquals(Color.rgb(32, 32, 32), palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND))
        assertEquals(Color.WHITE, palette.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE))
        assertEquals(SugarliciousColorRole.CGM_DOT_LOW.lightArgb, palette.argb(SugarliciousColorRole.CGM_DOT_LOW))
        assertEquals(SugarliciousColorRole.CGM_DOT_HIGH.lightArgb, palette.argb(SugarliciousColorRole.CGM_DOT_HIGH))
    }

    @Test
    fun `explicit mode API edits preview profile without mutating runtime profile`() {
        val preferences = context.getSharedPreferences("explicit_preview_profile", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val light = Color.rgb(240, 230, 220)
        val dark = Color.rgb(12, 23, 34)
        SugarliciousColorStore.save(preferences, AppearanceMode.LIGHT, SugarliciousColorRole.BACKGROUND, light)
        SugarliciousColorStore.save(preferences, AppearanceMode.DARK, SugarliciousColorRole.BACKGROUND, dark)

        assertEquals(dark, SugarliciousColorStore.load(preferences).argb(SugarliciousColorRole.BACKGROUND))
        assertEquals(light, SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT).argb(SugarliciousColorRole.BACKGROUND))
    }

    @Test
    fun `new semantic graph roles inherit legacy appearance until individually changed`() {
        val preferences = context.getSharedPreferences("semantic_graph_role_migration", Context.MODE_PRIVATE)
        val high = Color.rgb(181, 92, 7)
        val low = Color.rgb(182, 8, 63)
        val divider = Color.rgb(72, 81, 90)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .putInt("color.dark.range_high", high)
            .putInt("color.dark.range_low", low)
            .putInt("color.dark.graph_divider", divider)
            .commit()

        var palette = SugarliciousColorStore.load(preferences)
        assertEquals(high, palette.argb(SugarliciousColorRole.GRAPH_HIGH_LINE))
        assertEquals(low, palette.argb(SugarliciousColorRole.GRAPH_LOW_LINE))
        assertEquals(divider, palette.argb(SugarliciousColorRole.GRAPH_AXIS_TICK))
        assertEquals(divider, palette.argb(SugarliciousColorRole.GRAPH_NOW_LINE))

        val independentNowLine = Color.rgb(9, 201, 211)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.GRAPH_NOW_LINE, independentNowLine)
        palette = SugarliciousColorStore.load(preferences)
        assertEquals(independentNowLine, palette.argb(SugarliciousColorRole.GRAPH_NOW_LINE))
        assertEquals(divider, palette.argb(SugarliciousColorRole.GRAPH_AXIS_TICK))
    }
}
