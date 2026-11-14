package app.sugarlicious.mobile

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.CgmGraphScaleMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewGraphDefaultsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `previous automatic twenty four hour migration returns overview to three hours`() {
        val preferences = context.getSharedPreferences("overview_graph_default_migration", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean("graphHoursDefault24MigratedV4", true)
            .putInt("graphHours", 24)
            .commit()

        assertEquals(3, resolveOverviewGraphHoursPreference(preferences, 24))
        assertEquals(3, preferences.getInt("graphHours", -1))
    }

    @Test
    fun `mobile graph scale mode and static bounds persist`() {
        val storage = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        storage
            .edit()
            .clear()
            .putString(DashboardUiPreferences.GRAPH_SCALE_MODE_KEY, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC.name)
            .putFloat(DashboardUiPreferences.GRAPH_MINIMUM_KEY, 55f)
            .putFloat(DashboardUiPreferences.GRAPH_MAXIMUM_KEY, 350f)
            .putFloat(DashboardUiPreferences.COB_PROGRESS_MAXIMUM_KEY, 180f)
            .commit()

        val preferences = DashboardUiPreferences.read(storage)
        assertEquals(CgmGraphScaleMode.LOGARITHMIC_DYNAMIC, preferences.graphScaleMode)
        assertEquals(0.0, preferences.graphMinimumMgDl, 0.0)
        assertEquals(350.0, preferences.graphMaximumMgDl, 0.0)
        assertEquals(180f, preferences.cobProgressMaximumGrams, 0f)
    }

    @Test
    fun `mobile graph uses real zero when no legacy minimum exists`() {
        val storage = context.getSharedPreferences("dashboard_ui_zero_minimum", android.content.Context.MODE_PRIVATE)
        storage.edit().clear().commit()

        assertEquals(0.0, DashboardUiPreferences.read(storage).graphMinimumMgDl, 0.0)
    }

    @Test
    fun `fresh mobile settings reproduce the approved system baseline`() {
        val storage = context.getSharedPreferences("dashboard_ui_approved_baseline", android.content.Context.MODE_PRIVATE)
        storage.edit().clear().commit()

        val preferences = DashboardUiPreferences.read(storage)

        assertEquals(DisplayUnitPreference.MG_DL, preferences.unit)
        assertEquals(GlucoseTileDetailMode.THERAPY, preferences.glucoseTileDetailMode)
        assertEquals(80.1f, preferences.cobProgressMaximumGrams, 0.001f)
        assertEquals(100f, preferences.cobGraphMaximumGrams, 0.001f)
        assertEquals(true, preferences.showCgmCurrentBasal)
        assertEquals(true, preferences.showCgmBaseBasal)
        assertEquals(true, preferences.showCgmPredictions)
        assertEquals(true, preferences.showCgmPredictionIob)
        assertEquals(true, preferences.showCgmPredictionCob)
        assertEquals(true, preferences.showCgmPredictionUam)
        assertEquals(true, preferences.showCgmPredictionZeroTemp)
        assertEquals(true, preferences.showMetabolicGraph)
        assertEquals(2, preferences.graphHours)
        assertEquals(CgmGraphScaleMode.DYNAMIC, preferences.graphScaleMode)
        assertEquals(CgmGraphScaleMode.DYNAMIC, preferences.iobGraphScaleMode)
        assertEquals(CgmGraphScaleMode.DYNAMIC, preferences.cobGraphScaleMode)
        assertEquals(251.0, preferences.graphMaximumMgDl, 0.0)
        assertEquals(true, preferences.liveNotification)
        assertEquals(2, preferences.notificationGraphHours)
        assertEquals(120, preferences.trendScalePercent)
    }

    @Test
    fun `explicit non legacy overview duration is preserved`() {
        val preferences = context.getSharedPreferences("overview_graph_explicit_duration", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putInt("graphHours", 12)
            .commit()

        assertEquals(12, resolveOverviewGraphHoursPreference(preferences, 12))
        assertEquals(12, preferences.getInt("graphHours", -1))
    }
}
