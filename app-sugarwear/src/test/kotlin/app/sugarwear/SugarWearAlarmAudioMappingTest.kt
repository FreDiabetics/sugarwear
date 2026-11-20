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
class SugarWearAlarmAudioMappingTest {
    @Test
    fun `high alarm maps explicitly to high_alert mp4 asset`() {
        assertEquals("alarms/high_alert.mp4", SugarWearAlarmDefaults.audioAssetPath(SugarWearAlarmType.HIGH))
    }

    @Test
    fun `all nine active alarms have distinct user supplied audio mappings`() {
        val expected =
            mapOf(
                SugarWearAlarmType.URGENT_LOW to "alarms/alerts_sounds_medium_urgent_low.mp4",
                SugarWearAlarmType.URGENT_LOW_SOON to "alarms/alerts_sounds_medium_urgent_low_soon.mp4",
                SugarWearAlarmType.LOW to "alarms/alerts_sounds_medium_low.mp4",
                SugarWearAlarmType.HIGH to "alarms/alerts_sounds_medium_high.mp4",
                SugarWearAlarmType.RAPID_RISE to "alarms/alerts_sounds_medium_rising_rate.mp4",
                SugarWearAlarmType.RAPID_FALL to "alarms/alerts_sounds_medium_falling_rate.mp4",
                SugarWearAlarmType.SIGNAL_LOSS to "alarms/alerts_sounds_medium_signal_loss.mp4",
                SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM to "alarms/alerts_sounds_medium_bsi.mp4",
                SugarWearAlarmType.TECHNICAL_WARNING to "alarms/alerts_sounds_medium_technical_alerts.mp4",
            )
        assertEquals(expected, SugarWearAlarmType.entries.associateWith(SugarWearAlarmDefaults::preferredAudioAssetPath))
        assertEquals(expected.size, expected.values.distinct().size)
        assertEquals("alarms/alerts_sounds_medium_system_alert.mp4", SugarWearAlarmDefaults.SYSTEM_ALERT_AUDIO_ASSET)
    }

    @Test
    fun `old installations retain working fallback sound assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SugarWearAlarmType.entries.forEach { type ->
            openCgmAlarmSoundAsset(context, type).use { descriptor ->
                assertTrue(descriptor.length > 0L)
            }
        }
    }

    @Test
    fun `audio priority order is strictly defined`() {
        val pUrgentLow = SugarWearAlarmDefaults.priority(SugarWearAlarmType.URGENT_LOW)
        val pTech = SugarWearAlarmDefaults.priority(SugarWearAlarmType.TECHNICAL_WARNING)
        val pUrgentLowSoon = SugarWearAlarmDefaults.priority(SugarWearAlarmType.URGENT_LOW_SOON)
        val pLow = SugarWearAlarmDefaults.priority(SugarWearAlarmType.LOW)
        val pRapidFall = SugarWearAlarmDefaults.priority(SugarWearAlarmType.RAPID_FALL)
        val pHigh = SugarWearAlarmDefaults.priority(SugarWearAlarmType.HIGH)
        val pRapidRise = SugarWearAlarmDefaults.priority(SugarWearAlarmType.RAPID_RISE)
        val pSignalLoss = SugarWearAlarmDefaults.priority(SugarWearAlarmType.SIGNAL_LOSS)
        val pTemp = SugarWearAlarmDefaults.priority(SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM)

        assertTrue(pUrgentLow > pTech)
        assertTrue(pTech > pUrgentLowSoon)
        assertTrue(pUrgentLowSoon > pLow)
        assertTrue(pLow > pRapidFall)
        assertTrue(pRapidFall > pHigh)
        assertTrue(pHigh > pRapidRise)
        assertTrue(pRapidRise > pSignalLoss)
        assertTrue(pSignalLoss > pTemp)
    }

    @Test
    fun `all 9 alarm assets exist in assets directory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SugarWearAlarmType.entries.forEach { type ->
            val assetPath = SugarWearAlarmDefaults.audioAssetPath(type)
            context.assets.openFd(assetPath).use { descriptor ->
                assertTrue(descriptor.length > 0L)
            }
        }
    }
}
