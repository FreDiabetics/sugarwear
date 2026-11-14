package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarWearAlarmMigrationTest {

    @Test
    fun `migrates legacy preferences to v2 settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldPrefs = context.getSharedPreferences("g7_cgm_alarm_settings", Context.MODE_PRIVATE)
        oldPrefs.edit()
            .putFloat("high", 180f)
            .putFloat("low", 70f)
            .putBoolean("high_enabled", true)
            .putBoolean("low_enabled", true)
            .putBoolean("rapid_rise_enabled", true)
            .apply()

        val settings = SugarWearAlarmMigration.migrateIfNeeded(context)

        assertEquals(180.0, settings.ruleFor(SugarWearAlarmType.HIGH).thresholdMgDl, 0.0)
        assertEquals(70.0, settings.ruleFor(SugarWearAlarmType.LOW).thresholdMgDl, 0.0)
        assertTrue(settings.ruleFor(SugarWearAlarmType.HIGH).enabled)
        assertTrue(settings.ruleFor(SugarWearAlarmType.LOW).enabled)
        assertTrue(settings.ruleFor(SugarWearAlarmType.RAPID_RISE).enabled)

        // Fixed URGENT_LOW rule
        assertEquals(55.0, settings.ruleFor(SugarWearAlarmType.URGENT_LOW).thresholdMgDl, 0.0)
        assertTrue(settings.ruleFor(SugarWearAlarmType.URGENT_LOW).enabled)
    }
}
