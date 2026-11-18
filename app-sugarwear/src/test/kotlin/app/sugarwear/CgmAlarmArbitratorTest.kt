package app.sugarwear

import org.junit.Assert.*
import org.junit.Test

class CgmAlarmArbitratorTest {
    @Test
    fun `urgent low has highest priority over low and high`() {
        val urgentLow = SugarWearAlarmInstance(type = SugarWearAlarmType.URGENT_LOW, state = SugarWearAlarmState.ACTIVE)
        val high = SugarWearAlarmInstance(type = SugarWearAlarmType.HIGH, state = SugarWearAlarmState.ACTIVE)
        val low = SugarWearAlarmInstance(type = SugarWearAlarmType.LOW, state = SugarWearAlarmState.ACTIVE)

        val selected = CgmAlarmArbitrator.select(listOf(high, urgentLow, low))
        assertNotNull(selected)
        assertEquals(SugarWearAlarmType.URGENT_LOW, selected!!.type)
    }

    @Test
    fun `high alarm replaces rapid rise`() {
        val rapidRise = SugarWearAlarmInstance(type = SugarWearAlarmType.RAPID_RISE, state = SugarWearAlarmState.ACTIVE)
        val high = SugarWearAlarmInstance(type = SugarWearAlarmType.HIGH, state = SugarWearAlarmState.ACTIVE)

        val selected = CgmAlarmArbitrator.select(listOf(rapidRise, high))
        assertNotNull(selected)
        assertEquals(SugarWearAlarmType.HIGH, selected!!.type)
    }

    @Test
    fun `low alarm replaces rapid fall`() {
        val rapidFall = SugarWearAlarmInstance(type = SugarWearAlarmType.RAPID_FALL, state = SugarWearAlarmState.ACTIVE)
        val low = SugarWearAlarmInstance(type = SugarWearAlarmType.LOW, state = SugarWearAlarmState.ACTIVE)

        val selected = CgmAlarmArbitrator.select(listOf(rapidFall, low))
        assertNotNull(selected)
        assertEquals(SugarWearAlarmType.LOW, selected!!.type)
    }

    @Test
    fun `returns null when no alarms are active`() {
        val inactive = SugarWearAlarmInstance(type = SugarWearAlarmType.HIGH, state = SugarWearAlarmState.INACTIVE)
        val selected = CgmAlarmArbitrator.select(listOf(inactive))
        assertNull(selected)
    }
}
