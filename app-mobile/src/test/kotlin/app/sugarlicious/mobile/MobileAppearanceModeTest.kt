package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileAppearanceModeTest {
    @Test fun `legacy isolated widget follows app mode without resnapshotting its colors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dashboard = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        dashboard.edit().putString("themeMode", "DARK").commit()
        WidgetInstanceConfigurationStore.save(context, 8103, WidgetInstanceConfiguration(backgroundArgb = android.graphics.Color.RED))
        context
            .getSharedPreferences("widget_instance_configuration", Context.MODE_PRIVATE)
            .edit()
            .remove("8103.theme_mode")
            .putInt("8103.isolated_profile_version", 3)
            .commit()
        WidgetInstanceConfigurationStore.readIsolated(context, 8103, ConfigurableWidgetKind.GLUCOSE)
        dashboard.edit().putString("themeMode", "LIGHT").commit()
        val stored = WidgetInstanceConfigurationStore.readIsolated(context, 8103, ConfigurableWidgetKind.GLUCOSE)
        assertEquals(DashboardThemeMode.LIGHT, stored.themeMode)
        assertEquals(android.graphics.Color.RED, stored.backgroundArgb)
    }

    @Test fun `system follows device while explicit appearance ignores device changes`() {
        assertEquals(AppearanceMode.LIGHT, resolveSettingsAppearance(DashboardThemeMode.SYSTEM, false))
        assertEquals(AppearanceMode.DARK, resolveSettingsAppearance(DashboardThemeMode.SYSTEM, true))
        assertEquals(AppearanceMode.LIGHT, resolveSettingsAppearance(DashboardThemeMode.LIGHT, true))
        assertEquals(AppearanceMode.DARK, resolveSettingsAppearance(DashboardThemeMode.DARK, false))
        assertEquals(DashboardThemeMode.SYSTEM, settingsSystemMode(true, true))
        assertEquals(DashboardThemeMode.DARK, settingsSystemMode(false, true))
        assertEquals(DashboardThemeMode.LIGHT, settingsSystemMode(false, false))
    }

    @Test fun `widgets always inherit app mode while retaining independent appearance details`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dashboard = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        dashboard.edit().putString("themeMode", "DARK").commit()
        assertEquals(DashboardThemeMode.DARK, WidgetInstanceConfigurationStore.read(context, 8101).themeMode)
        WidgetInstanceConfigurationStore.save(context, 8101, WidgetInstanceConfiguration(themeMode = DashboardThemeMode.LIGHT))
        WidgetInstanceConfigurationStore.save(context, 8102, WidgetInstanceConfiguration(themeMode = DashboardThemeMode.SYSTEM))
        assertEquals(DashboardThemeMode.DARK, WidgetInstanceConfigurationStore.read(context, 8101).themeMode)
        assertEquals(DashboardThemeMode.DARK, WidgetInstanceConfigurationStore.read(context, 8102).themeMode)
        assertEquals("DARK", dashboard.getString("themeMode", null))
        assertEquals(DashboardThemeMode.SYSTEM, WidgetSystemDefaults.forKind(ConfigurableWidgetKind.GLUCOSE).themeMode)
    }
}
