package app.sugarlicious.mobile

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectIntegrationTest {
    private val now = 2_000_000_000_000L

    @Test
    fun `builds stable interstitial records from history and current glucose`() {
        val state =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now,
                glucose = GlucoseState(126.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(
                        GlucoseSample(120.0, now - 5 * 60_000L, DataSourceId.ANDROID_APS),
                        GlucoseSample(120.0, now - 5 * 60_000L, DataSourceId.ANDROID_APS),
                        GlucoseSample(Double.NaN, now - 10 * 60_000L, DataSourceId.ANDROID_APS),
                    ),
            )

        val records = HealthConnectIntegration.buildGlucoseRecords(state, now)

        assertEquals(2, records.size)
        assertEquals(listOf(120.0, 126.0), records.map { it.level.inMilligramsPerDeciliter })
        assertTrue(records.all { it.specimenSource == BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID })
        assertEquals("sugarlicious:cgm:ANDROID_APS:$now", records.last().metadata.clientRecordId)
        assertEquals(now, records.last().metadata.clientRecordVersion)
    }

    @Test
    fun `resumes from last confirmed timestamp and marks direct collector as watch`() {
        val previous = now - 5 * 60_000L
        val state =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                receivedAtEpochMs = now,
                glucose = GlucoseState(130.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(
                        GlucoseSample(120.0, previous - 5 * 60_000L, DataSourceId.DEXCOM_G7_WATCH),
                        GlucoseSample(125.0, previous, DataSourceId.DEXCOM_G7_WATCH),
                    ),
            )

        val records = HealthConnectIntegration.buildGlucoseRecords(state, now, previous)

        assertEquals(2, records.size)
        assertEquals(listOf(previous, now), records.map { it.time.toEpochMilli() })
        assertTrue(records.all { it.metadata.device?.type == Device.TYPE_WATCH })
    }

    @Test
    fun `rejects stale future and physiologically invalid values`() {
        val state =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now,
                glucoseHistory =
                    listOf(
                        GlucoseSample(100.0, now - 25L * 60L * 60_000L),
                        GlucoseSample(10.0, now),
                        GlucoseSample(110.0, now + 6 * 60_000L),
                    ),
            )

        assertTrue(HealthConnectIntegration.buildGlucoseRecords(state, now).isEmpty())
    }
}
