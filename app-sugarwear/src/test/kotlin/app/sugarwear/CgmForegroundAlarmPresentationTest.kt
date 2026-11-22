package app.sugarwear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmForegroundAlarmPresentationTest {
    @Test
    fun `one new episode opens only once and fresh higher priority wins`() {
        val seen = mutableSetOf<String>()
        val low =
            SugarWearAlarmInstance(
                type = SugarWearAlarmType.LOW,
                state = SugarWearAlarmState.ACTIVE,
                triggeredAtEpochMs = 1_000L,
            )
        val urgent =
            SugarWearAlarmInstance(
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
    fun `closing an alarm screen preserves the next alarm raised in the same foreground session`() {
        val visibility = CgmAlarmAppVisibility()
        visibility.onStarted() // normal overview
        assertTrue(visibility.consumeBaselineRequired())
        visibility.onStarted() // full-screen alarm opens above overview
        visibility.onStopped() // covered overview stops
        visibility.onStarted() // overview returns while alarm screen still exists
        assertFalse(visibility.consumeBaselineRequired())
        visibility.onStopped() // alarm screen stops
        visibility.onStopped() // user backgrounds overview
        visibility.onStarted()
        assertTrue(visibility.consumeBaselineRequired())
    }

    @Test
    fun `new alarm triggered under full-screen alarm is not lost on return`() {
        val first =
            SugarWearAlarmInstance(
                type = SugarWearAlarmType.HIGH,
                state = SugarWearAlarmState.ACTIVE,
                triggeredAtEpochMs = 10_000L,
            )
        val second =
            SugarWearAlarmInstance(
                type = SugarWearAlarmType.URGENT_LOW,
                state = SugarWearAlarmState.ACTIVE,
                triggeredAtEpochMs = 20_000L,
            )
        val seen = mutableSetOf(cgmAlarmEpisodeKey(first))
        assertEquals(second, CgmForegroundAlarmPresentation.pickNext(seen, listOf(first, second)))
        assertNull(CgmForegroundAlarmPresentation.pickNext(seen, listOf(first, second)))
    }

    @Test
    fun `already visible episode is only baselined when opening app`() {
        val alarm =
            SugarWearAlarmInstance(
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
