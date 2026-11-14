package app.sugarlicious.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinalOverviewAppearanceTest {
    @Test fun `notification icon uses light accent in light mode and dark accent in dark mode`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        listOf(false, true).forEach { dark ->
            val context =
                app.createConfigurationContext(
                    Configuration(app.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    },
                )
            val expected = (if (dark) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)).primary.toArgb()
            assertEquals(expected, DeviceVendorLiveNotificationAdapter.systemNotificationIconColor(context))
            val background = (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary.toArgb()
            assertEquals(background, DeviceVendorLiveNotificationAdapter.systemAccentColor(context))
        }
    }

    @Test fun `all four glucose bar colors have independent pickers including underlay alpha`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bar_color_regression", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val roles = listOf(SugarliciousColorRole.OVERVIEW_WAVE_LOW, SugarliciousColorRole.OVERVIEW_WAVE_IN_RANGE, SugarliciousColorRole.OVERVIEW_WAVE_HIGH, SugarliciousColorRole.OVERVIEW_WAVE_UNDERLAY)
        app.sugarlicious.model.AppearanceMode.entries.forEach { mode ->
            roles.forEachIndexed { index, role ->
                assertTrue(role.configurable)
                assertEquals("Glukosebalken", mobileAppearanceObjectTitle(role))
                val argb = 0x66112200 + index
                SugarliciousColorStore.save(prefs, mode, role, argb)
                assertEquals(argb, SugarliciousColorStore.load(prefs, mode).argb(role))
            }
        }
    }
}
