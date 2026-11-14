package app.sugarlicious.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.protocol.WatchGlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearDisplaySystemDefaultsTest {
    @Test
    fun `fresh wear settings reproduce approved system baseline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(WearDisplayPreferences.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val defaults = WearDisplayPreferences.read(context)
        assertEquals(android.graphics.Color.WHITE, defaults.trendArrowStyle.fillColor)

        assertEquals(6, defaults.graphHours)
        assertFalse(defaults.showPredictions)
        assertEquals(WatchGlucoseUnit.AAPS, defaults.glucoseUnit)
        assertEquals(WatchDataSource.PHONE, defaults.dataSource)
        assertTrue(defaults.showTherapyStats)
        assertEquals(2.4f, defaults.graphStyle.cgmDotRadiusDp, 0.001f)
        assertFalse(defaults.graphStyle.cgmHistoricalDotOutlineEnabled)
        assertEquals(0.95f, defaults.graphStyle.cgmHistoricalDotOutlineWidthDp, 0.001f)
    }
}
