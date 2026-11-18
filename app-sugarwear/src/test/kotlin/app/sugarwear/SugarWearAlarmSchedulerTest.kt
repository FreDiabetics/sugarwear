package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarWearAlarmSchedulerTest {
    @Test
    fun `scheduling repeat alarms schedules future pending intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CgmAlarmRepeatScheduler.schedule(context, SugarWearAlarmType.HIGH, 60)
        CgmAlarmRepeatScheduler.cancel(context, SugarWearAlarmType.HIGH)
    }
}
