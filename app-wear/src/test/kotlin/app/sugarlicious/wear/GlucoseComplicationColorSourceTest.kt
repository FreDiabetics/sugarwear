package app.sugarlicious.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = SugarliciousWearApplication::class)
class GlucoseComplicationColorSourceTest {
    @Test
    fun `complication reads current Wear colors and never overrides target range`() {
        val app = ApplicationProvider.getApplicationContext<SugarliciousWearApplication>()
        WearDisplayPreferences.read(app)
        val preferences = app.getSharedPreferences(WearDisplayPreferences.PREFS, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putInt("appearance.dark.ui_color_glucose_high", 0xFF123456.toInt())
            .putInt("appearance.dark.ui_color_glucose_low", 0xFF654321.toInt())
            .commit()
        assertEquals(0xFF123456.toInt(), app.outOfRangeGlucoseColor(200.0))
        assertEquals(0xFF654321.toInt(), app.outOfRangeGlucoseColor(60.0))
        assertNull(app.outOfRangeGlucoseColor(120.0))
        assertNull(app.outOfRangeGlucoseColor(Double.NaN))
    }
}
