package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.protocol.DirectToWatchSettingsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7VigilDefaultsTest {
    @Test fun `Vigil defaults are two hours white dots opaque limits and no graph contour`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(DirectToWatchSettingsContract.PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = G7DirectToWatchSettingsStore(context)
        assertEquals(2, store.graphHours())
        assertFalse(store.graphStyle().borderEnabled)
        assertEquals(.9f, store.graphStyle().currentDotOutlineWidthDp, .001f)
        val colors = store.graphColors()
        listOf(colors.cgmLow, colors.cgmVeryLow, colors.cgmInRange, colors.cgmHigh, colors.cgmVeryHigh).forEach {
            assertEquals(0xFFFFFFFF.toInt(), it)
        }
        assertEquals(255, android.graphics.Color.alpha(colors.highLine))
        assertEquals(255, android.graphics.Color.alpha(colors.lowLine))
    }
}
