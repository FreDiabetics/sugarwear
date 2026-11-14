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
