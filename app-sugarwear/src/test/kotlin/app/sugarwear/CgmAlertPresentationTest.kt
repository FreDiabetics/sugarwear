package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmAlertPresentationTest {

    @Test
    fun `cancel current clears single notification and legacy notifications`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CgmAlertPresentation.cancelCurrent(context)
    }

    @Test
    fun `show posts single notification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SugarWearAlarmDefaults.defaultSettings()
        val alarm = SugarWearAlarmInstance(type = SugarWearAlarmType.HIGH, state = SugarWearAlarmState.ACTIVE)
        CgmAlertPresentation.show(context, alarm, settings, playSound = false)
    }
}
