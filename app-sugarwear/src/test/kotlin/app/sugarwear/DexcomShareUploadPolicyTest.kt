package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class DexcomShareUploadPolicyTest {
    private val now = 1_800_000_000_000L

    private fun reading(
        at: Long = now - 60_000L,
        sensor: String = "sensor",
        session: String = "session",
        origin: CgmReadingOrigin = CgmReadingOrigin.LIVE,
        status: CgmReadingStatus = CgmReadingStatus.VALID,
        displayOnly: Boolean = false,
        trend: Trend = Trend.FLAT,
        value: Double = 112.6,
    ) = CgmReading(
        id = "$sensor:$session:$at:$origin",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = sensor,
        sessionId = session,
        glucoseMgDl = value,
        timestampEpochMs = at,
        receivedAtEpochMs = now,
        status = status,
        displayOnly = displayOnly,
        origin = origin,
        trend = trend,
    )

    @Test fun `only live valid active session rows are eligible`() {
        val accepted = reading()
        val rows =
            DexcomShareUploadPolicy.eligible(
                listOf(
                    accepted,
                    accepted.copy(id = "duplicate"),
                    reading(sensor = "another"),
                    reading(session = "retired"),
                    reading(status = CgmReadingStatus.SENSOR_ERROR),
                    reading(status = CgmReadingStatus.INVALID),
                    reading(displayOnly = true),
                    reading(origin = CgmReadingOrigin.BACKFILL),
                    reading(at = now + 60_000L),
                    reading(at = now - DexcomShareUploadPolicy.MAX_LOOKBACK_MS - 1L),
                    reading(value = Double.NaN),
                ),
                "sensor",
                "session",
                0L,
                now,
            )
        assertEquals(1, rows.size)
        assertEquals(113, rows.single().glucoseMgDl)
        assertEquals(4, rows.single().trendOrdinal)
    }

    @Test fun `cursor prevents replay and preserves chronological order`() {
        val rows =
            DexcomShareUploadPolicy.eligible(
                listOf(reading(at = now - 60_000L), reading(at = now - 180_000L), reading(at = now - 120_000L)),
                "sensor",
                "session",
                now - 150_000L,
                now,
            )
        assertEquals(listOf(now - 120_000L, now - 60_000L), rows.map { it.measurementEpochMs })
    }

    @Test fun `egv payload retains actual measurement time and canonical trend`() {
        val at = now - 98_765L
        val rows =
            DexcomShareUploadPolicy.eligible(
                listOf(reading(at = at, trend = Trend.DOUBLE_DOWN)),
                "sensor",
                "session",
                0L,
                now,
            )
        val payload = DexcomShareUploadPolicy.payload("AB12345678", rows)
        val row = payload.getJSONArray("Egvs").getJSONObject(0)
        assertEquals("AB12345678", payload.getString("SN"))
        assertEquals(-5, payload.getInt("TA"))
        assertEquals(7, row.getInt("Trend"))
        assertEquals("/Date(${at / 1_000L * 1_000L})/", row.getString("DT"))
        assertEquals(row.getString("DT"), row.getString("ST"))
    }

    @Test fun `unknown trend does not manufacture a direction`() {
        val row =
            DexcomShareUploadPolicy
                .eligible(
                    listOf(reading(trend = Trend.UNKNOWN)),
                    "sensor",
                    "session",
                    0L,
                    now,
                ).single()
        assertEquals(0, row.trendOrdinal)
    }

    @Test fun `future and invalid glucose cannot leave watch`() {
        val rows =
            DexcomShareUploadPolicy.eligible(
                listOf(reading(at = now + 1L), reading(value = 0.0), reading(value = Double.POSITIVE_INFINITY)),
                "sensor",
                "session",
                0L,
                now,
            )
        assertTrue(rows.isEmpty())
    }
}
