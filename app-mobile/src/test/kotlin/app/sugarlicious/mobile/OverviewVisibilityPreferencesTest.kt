package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverviewVisibilityPreferencesTest {
    @Test fun `legacy exclusive mode is preserved until independent switches are saved`() {
        val p = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("overview-migration-test", Context.MODE_PRIVATE)
        p
            .edit()
            .clear()
            .putString(DashboardUiPreferences.GLUCOSE_TILE_DETAIL_MODE_KEY, "TIR")
            .commit()
        val migrated = DashboardUiPreferences.read(p)
        assertTrue(migrated.showTir)
        assertFalse(migrated.showTherapyProgress)
        assertFalse(migrated.showTitr)
        p
            .edit()
            .putBoolean("overview.showTherapyProgress", true)
            .putBoolean("overview.showTitr", true)
            .commit()
        val combined = DashboardUiPreferences.read(p)
        assertTrue(combined.showTir && combined.showTitr && combined.showTherapyProgress)
        p.edit().putBoolean("overview.showTir", false).commit()
        assertFalse(DashboardUiPreferences.read(p).showTir)
        assertTrue(DashboardUiPreferences.read(p).showTitr)
        assertTrue(DashboardUiPreferences.read(p).showTherapyProgress)
    }
}
