package app.sugarwear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CgmForegroundAlarmPresentationTest {
    @Test
    fun `one new episode opens only once and fresh higher priority wins`() {
        val seen = mutableSetOf<String>()
        val low = SugarWearAlarmInstance(
            type = SugarWearAlarmType.LOW,
            state = SugarWearAlarmState.ACTIVE,
            triggeredAtEpochMs = 1_000L,
        )
        val urgent = SugarWearAlarmInstance(
            type = SugarWearAlarmType.URGENT_LOW,
            state = SugarWearAlarmState.ACTIVE,
            triggeredAtEpochMs = 1_000L,
        )
        assertEquals(urgent, CgmForegroundAlarmPresentation.pickNext(seen, listOf(low, urgent)))
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, listOf(low, urgent)))
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, listOf(urgent)))
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, emptyList()))
        val nextEpisode = low.copy(triggeredAtEpochMs = 2_000L)
        assertEquals(nextEpisode, CgmForegroundAlarmPresentation.pickNext(seen, listOf(nextEpisode)))
    }

    @Test
    fun `already visible episode is only baselined when opening app`() {
        val alarm = SugarWearAlarmInstance(
            type = SugarWearAlarmType.HIGH,
            state = SugarWearAlarmState.ACTIVE,
            triggeredAtEpochMs = 3_000L,
        )
        val seen = mutableSetOf(cgmAlarmEpisodeKey(alarm))
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, listOf(alarm)))
        val updated = alarm.copy(lastNotifiedAtEpochMs = 4_000L)
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, listOf(updated)))
    }
}
