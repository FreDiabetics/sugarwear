package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import app.aapswear.protocol.WatchGraphColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class G7CollectorTileTrendSafetyTest {
    private val now = 2_000_000L
    private val colors =
        WatchGraphColors(
            graphBackground = 0xFF111111.toInt(),
            rangeLow = 0xFFAA0000.toInt(),
            rangeHigh = 0xFFCCAA00.toInt(),
            cgmLow = 0xFFFF0000.toInt(),
        )

    @Test
    fun `fresh valid reading exposes its resolved trend to the tile`() {
        val presentation = g7TilePresentation(reading(now, Trend.SINGLE_UP), colors, now)

        assertEquals(Trend.SINGLE_UP, presentation.trend)
    }

    @Test
    fun `signal loss retains last trend while sensor error exposes none`() {
        val stale = g7TilePresentation(reading(now - 13 * 60_000L, Trend.SINGLE_UP), colors, now)
        val sensorError =
            g7TilePresentation(
                reading(now, Trend.SINGLE_UP).copy(
                    glucoseMgDl = 0.0,
                    status = CgmReadingStatus.SENSOR_ERROR,
                ),
                colors,
                now,
            )

        assertEquals(Trend.SINGLE_UP, stale.trend)
        assertNull(sensorError.trend)
    }

    private fun reading(
        timestamp: Long,
        trend: Trend,
    ) = CgmReading(
        id = "reading-$timestamp-$trend",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = 123.0,
        timestampEpochMs = timestamp,
        receivedAtEpochMs = timestamp,
        trend = trend,
        status = CgmReadingStatus.VALID,
    )
}
