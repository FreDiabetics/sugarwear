package app.sugarlicious.uishared

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarWearDisplayDefaultsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `Galaxy reference is the fallback and explicit edits survive until reset`() {
        val values = SugarWearDisplayDefaults.preferences(context, "direct_to_watch")
        assertEquals(2, values.getInt("graph.hours", 3))
        assertEquals(.9f, values.getFloat("graph_style_current_dot_outline_width", 0f), .001f)
        values.edit().putInt("graph.hours", 6).commit()
        assertEquals(6, values.getInt("graph.hours", 3))
        values.edit().remove("graph.hours").commit()
        assertEquals(2, values.getInt("graph.hours", 3))
    }

    @Test fun `all display defaults use white glucose dots and matching low and high tiers`() {
        val values = SugarWearDisplayDefaults.preferences(context, "g7_appearance")
        for (mode in listOf("dark", "light")) {
            assertEquals(values.getInt("color.$mode.glucose_low", 0), values.getInt("color.$mode.glucose_very_low", 1))
            assertEquals(values.getInt("color.$mode.glucose_high", 0), values.getInt("color.$mode.glucose_very_high", 1))
            assertEquals(Color.WHITE, values.getInt("color.$mode.graph_dot_high", 0))
            assertEquals(Color.BLACK, values.getInt("color.$mode.menu_background", 0))
            assertEquals(0, Color.alpha(values.getInt("color.$mode.menu_border", -1)))
        }
    }

    @Test fun `display reset preserves sensor credentials and history namespaces`() {
        val credentials = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        credentials.edit().putString("sentinel", "keep").commit()
        val history = context.getSharedPreferences("g7_collector_state", Context.MODE_PRIVATE)
        history.edit().putLong("sentinel", 42L).commit()
        SugarWearDisplayDefaults.reset(context)
        assertEquals("keep", credentials.getString("sentinel", null))
        assertEquals(42L, history.getLong("sentinel", 0L))
    }

    @Test fun `signal loss warning has a white label and no panel background`() {
        val warning = SignalLossWarningView(context)
        val label = warning.getChildAt(1) as android.widget.TextView
        assertEquals("Signalverlust", label.text.toString())
        assertEquals(Color.WHITE, label.currentTextColor)
        assertNull(warning.background)
    }

    @Test fun `accent palette derives from the requested green with dark readable button ink`() {
        assertEquals(0xFF22EB60.toInt(), SugarliciousAccentPalette.primary(true))
        assertEquals(Color.BLACK, SugarliciousAccentPalette.onPrimary)
        assertTrue(Color.green(SugarliciousAccentPalette.container) > Color.red(SugarliciousAccentPalette.container))
    }
}
