package app.sugarlicious.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrendArrowStylePreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = context.getSharedPreferences("trend-style-test", Context.MODE_PRIVATE)

    @Before fun clear() =
        preferences
            .edit()
            .clear()
            .commit()
            .let { Unit }

    @Test fun `light and dark values stay independent`() {
        val light =
            TrendArrowStylePreferences
                .read(
                    preferences,
                    AppearanceMode.LIGHT,
                    0xFF112233.toInt(),
                ).copy(fillColor = 1, outlineEnabled = false)
        val dark =
            TrendArrowStylePreferences
                .read(
                    preferences,
                    AppearanceMode.DARK,
                    0xFF445566.toInt(),
                ).copy(fillColor = 2, outlineEnabled = true)
        TrendArrowStylePreferences.write(preferences, AppearanceMode.LIGHT, light)
        TrendArrowStylePreferences.write(preferences, AppearanceMode.DARK, dark)
        assertEquals(1, TrendArrowStylePreferences.read(preferences, AppearanceMode.LIGHT, 0).fillColor)
        assertEquals(2, TrendArrowStylePreferences.read(preferences, AppearanceMode.DARK, 0).fillColor)
        assertFalse(TrendArrowStylePreferences.read(preferences, AppearanceMode.LIGHT, 0).outlineEnabled)
        assertTrue(TrendArrowStylePreferences.read(preferences, AppearanceMode.DARK, 0).outlineEnabled)
    }

    @Test fun `reset only affects requested profile`() {
        TrendArrowStylePreferences.write(
            preferences,
            AppearanceMode.LIGHT,
            TrendArrowStylePreferences.read(preferences, AppearanceMode.LIGHT, 1).copy(fillColor = 3),
        )
        TrendArrowStylePreferences.write(
            preferences,
            AppearanceMode.DARK,
            TrendArrowStylePreferences.read(preferences, AppearanceMode.DARK, 2).copy(fillColor = 4),
        )
        TrendArrowStylePreferences.reset(preferences, AppearanceMode.LIGHT)
        assertFalse(TrendArrowStylePreferences.hasOverride(preferences, AppearanceMode.LIGHT))
        assertTrue(TrendArrowStylePreferences.hasOverride(preferences, AppearanceMode.DARK))
    }
}
