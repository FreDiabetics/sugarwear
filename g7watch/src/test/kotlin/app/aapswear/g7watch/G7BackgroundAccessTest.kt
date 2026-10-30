package app.aapswear.g7watch

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7BackgroundAccessTest {
    @Test fun `battery settings use direct request then safe system fallbacks`() {
        val intents = G7BackgroundAccess.batterySettingsIntents("app.sugarwear")

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intents[0].action)
        assertEquals("package:app.sugarwear", intents[0].dataString)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intents[1].action)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents[2].action)
        assertEquals("package:app.sugarwear", intents[2].dataString)
        assertEquals(Settings.ACTION_SETTINGS, intents[3].action)
    }
}
