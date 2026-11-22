package app.sugarwear

import app.sugarlicious.model.CgmRangeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class CgmAlarmColorRoleTest {
    @Test
    fun `high and low fullscreen alarm colors remain fixed despite later in range glucose`() {
        val inRange = CgmRangeClass.IN_RANGE
        assertEquals(CgmAppearanceRole.GLUCOSE_HIGH, cgmAlarmColorRole(SugarWearAlarmType.HIGH, inRange))
        assertEquals(CgmAppearanceRole.GLUCOSE_LOW, cgmAlarmColorRole(SugarWearAlarmType.LOW, inRange))
        assertEquals(CgmAppearanceRole.GLUCOSE_LOW, cgmAlarmColorRole(SugarWearAlarmType.URGENT_LOW, CgmRangeClass.HIGH))
        assertEquals(CgmAppearanceRole.GLUCOSE_LOW, cgmAlarmColorRole(SugarWearAlarmType.URGENT_LOW_SOON, null))
        assertEquals(CgmAppearanceRole.GLUCOSE_HIGH, cgmAlarmColorRole(SugarWearAlarmType.HIGH, null))
    }

    @Test
    fun `rate and technical alarm colors still reflect only validated live ranges`() {
        assertEquals(CgmAppearanceRole.GLUCOSE_HIGH, cgmAlarmColorRole(SugarWearAlarmType.RAPID_RISE, CgmRangeClass.HIGH))
        assertEquals(CgmAppearanceRole.GLUCOSE_LOW, cgmAlarmColorRole(SugarWearAlarmType.RAPID_FALL, CgmRangeClass.VERY_LOW))
        assertEquals(CgmAppearanceRole.GLUCOSE_IN_RANGE, cgmAlarmColorRole(SugarWearAlarmType.RAPID_RISE, CgmRangeClass.IN_RANGE))
        assertEquals(CgmAppearanceRole.GLUCOSE_NO_SOURCE, cgmAlarmColorRole(SugarWearAlarmType.TECHNICAL_WARNING, null))
    }
}
