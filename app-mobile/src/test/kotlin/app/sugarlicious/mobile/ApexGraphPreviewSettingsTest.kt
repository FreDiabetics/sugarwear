package app.sugarlicious.mobile

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApexGraphPreviewSettingsTest {
    @Test
    fun `ApeX preview follows complication time window and configured CGM thresholds rather than app defaults`() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("complication_setup", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putInt("graph_hours", 6)
            .commit()
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putFloat(CgmThresholdPreferences.HIGH, 160f)
            .putFloat(CgmThresholdPreferences.LOW, 80f)
            .commit()
        val settings = loadApexGraphPreviewSettings(context)
        assertEquals(1.5, settings.hours, .001)
        assertEquals(160.0, settings.thresholds.highMgDl, .001)
        assertEquals(80.0, settings.thresholds.lowMgDl, .001)
    }

    @Test
    fun `unconfigured preview uses the same ninety minute window as the native graph`() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("complication_setup", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        assertEquals(1.5, loadApexGraphPreviewSettings(context).hours, .001)
    }
}
