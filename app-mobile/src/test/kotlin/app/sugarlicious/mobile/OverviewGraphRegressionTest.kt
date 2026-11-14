package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewGraphRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `legacy auto forced twenty four hour overview restores once to three hours`() {
        val preferences = context.getSharedPreferences("overview_graph_migration", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean("graphHoursDefault24MigratedV4", true)
            .putInt("graphHours", 24)
            .commit()

        val resolved = resolveOverviewGraphHoursPreference(preferences, 24)

        assertEquals(3, resolved)
        assertEquals(3, preferences.getInt("graphHours", -1))
        assertTrue(preferences.getBoolean("graphHoursDefault3MigratedV5", false))
    }

    @Test
    fun `explicit three hour choice survives migration`() {
        val preferences = context.getSharedPreferences("overview_graph_explicit", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putBoolean("graphHoursDefault24MigratedV4", true)
            .putInt("graphHours", 3)
            .commit()

        assertEquals(3, resolveOverviewGraphHoursPreference(preferences, 3))
    }

    @Test
    fun `requested twenty four hour viewport restores after asynchronous history arrives`() {
        val hourMs = 60L * 60_000L
        val viewport = ChartViewport(24)

        viewport.setAvailablePastWindow(hourMs)
        assertEquals(1f, viewport.hours, 0.001f)

        viewport.setAvailablePastWindow(24L * hourMs)
        assertEquals(24f, viewport.hours, 0.001f)
    }

    @Test
    fun `now divider never rewrites actual or prediction timestamp positions`() {
        val divider = 100f

        assertEquals(divider, graphCenterBeforeDivider(divider, 4f, 2f, 2f), 0.001f)
        assertEquals(divider, graphCenterAfterDivider(divider, 4f, 2f, 2f), 0.001f)
    }

    @Test
    fun `target range boundary colors preserve configured transparency`() {
        val configuredColor = Color.argb(37, 12, 90, 180)

        val boundaryColor = graphBoundaryColor(configuredColor)

        assertEquals(37, Color.alpha(boundaryColor))
        assertEquals(configuredColor, boundaryColor)
    }
}
