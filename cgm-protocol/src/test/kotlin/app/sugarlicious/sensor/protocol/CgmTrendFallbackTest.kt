package app.sugarlicious.sensor.protocol

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CgmTrendFallbackTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `missing sensor trend derives rate from valid adjacent reading`() {
        val previous = previousReading(value = 100.0, at = now - 5 * 60_000L)
        val current =
            SensorProtocolReading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = 2L,
                glucoseMgDl = 110.0,
                sensorTimestampEpochMs = now,
                receivedAtEpochMs = now,
                trendRateMgDlPerMinute = null,
                sensorState = CgmSensorState.ACTIVE,
            ).toCgm(previous)

        assertEquals(10.0, current.deltaMgDl)
        assertEquals(2.0, current.trendRateMgDlPerMinute)
        assertEquals(Trend.SINGLE_UP, current.trend)
    }

    @Test
    fun `sensor supplied trend rate always wins over derived fallback`() {
        val previous = previousReading(value = 100.0, at = now - 5 * 60_000L)
        val current =
            SensorProtocolReading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = 2L,
                glucoseMgDl = 110.0,
                sensorTimestampEpochMs = now,
                receivedAtEpochMs = now,
                trendRateMgDlPerMinute = 0.2,
                sensorState = CgmSensorState.ACTIVE,
            ).toCgm(previous)

        assertEquals(0.2, current.trendRateMgDlPerMinute)
        assertEquals(Trend.FLAT, current.trend)
    }

    @Test
    fun `fallback never derives trend across invalid timing or session boundaries`() {
        val tooOld = previousReading(value = 100.0, at = now - 20 * 60_000L)
        val wrongSession = previousReading(value = 100.0, at = now - 5 * 60_000L).copy(sessionId = "old-session")
        val source =
            SensorProtocolReading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = 2L,
                glucoseMgDl = 110.0,
                sensorTimestampEpochMs = now,
                receivedAtEpochMs = now,
                trendRateMgDlPerMinute = null,
                sensorState = CgmSensorState.ACTIVE,
            )

        val aged = source.toCgm(tooOld)
        val switched = source.toCgm(wrongSession)

        assertNull(aged.deltaMgDl)
        assertNull(aged.trendRateMgDlPerMinute)
        assertEquals(Trend.UNKNOWN, aged.trend)
        assertNull(switched.deltaMgDl)
        assertNull(switched.trendRateMgDlPerMinute)
        assertEquals(Trend.UNKNOWN, switched.trend)
    }

    private fun previousReading(
        value: Double,
        at: Long,
    ) = CgmReading(
        id = "previous-$at",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = value,
        timestampEpochMs = at,
        receivedAtEpochMs = at,
        status = CgmReadingStatus.VALID,
    )
}
