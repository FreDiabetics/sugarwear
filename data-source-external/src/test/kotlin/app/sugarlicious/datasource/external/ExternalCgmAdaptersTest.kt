package app.sugarlicious.datasource.external

import app.sugarlicious.model.*
import kotlin.test.*

class ExternalCgmAdaptersTest {
    private val now = 1_800_000_000_000L
    private val prefix = "com.eveningoutpost.dexdrip.Extras."

    @Test fun `xdrip estimate and milliseconds slope become canonical glucose and trend`() {
        val result = assertNotNull(ExternalCgmAdapters.local(ExternalCgmAdapters.XDRIP_ACTION, mapOf(prefix + "BgEstimate" to 123.0, prefix + "Time" to now, prefix + "BgSlope" to 0.00004), now))
        assertEquals(DataSourceId.XDRIP_PLUS, result.source)
        assertEquals(123.0, result.glucose!!.valueMgDl)
        assertEquals(Trend.SINGLE_UP, result.glucose!!.trend)
        assertEquals(2.4, result.glucose!!.trendRateMgDlPerMinute!!, 0.0001)
    }

    @Test fun `raw values missing timestamps seconds and future broadcasts never invent readings`() {
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.XDRIP_ACTION, mapOf(prefix + "Raw" to 123.0, prefix + "Time" to now), now))
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_ACTION, mapOf("glucose" to 123.0), now))
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_ACTION, mapOf("glucose" to 123.0, "timestamp" to now / 1000), now))
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_ACTION, mapOf("glucose" to 123.0, "timestamp" to now + 1), now))
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_ACTION, mapOf("glucose" to Double.NaN, "timestamp" to now), now))
    }

    @Test fun `Libre scan retains sensor identity and rejects unprocessed live values`() {
        val result = assertNotNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_SCAN_ACTION, mapOf("readings" to listOf(mapOf("glucoseValue" to 96.0, "timestamp" to now)), "sensorSerial" to "sensor-a"), now))
        assertEquals(DataSourceId.LIBRE_PATCHED, result.source)
        assertEquals("sensor-a", result.glucose!!.sensorId)
        assertEquals(Trend.UNKNOWN, result.glucose!!.trend)
        assertNull(result.glucose!!.deltaMgDl)
        assertNull(ExternalCgmAdapters.local(ExternalCgmAdapters.LIBRE_ACTION, mapOf("glucose" to 96.0, "timestamp" to now), now))
    }

    @Test fun `xdrip sensor start creates real session identity while description does not`() {
        fun reading(start: Long) = assertNotNull(ExternalCgmAdapters.local(ExternalCgmAdapters.XDRIP_ACTION, mapOf(prefix + "BgEstimate" to 123.0, prefix + "Time" to now, prefix + "SensorStartedAt" to start, prefix + "SourceInfo" to "G6 Native"), now))
        val first = reading(now - 600000).glucose!!
        val second = reading(now - 300000).glucose!!
        assertNotEquals(first.sessionId, second.sessionId)
        assertNull(first.sensorId)
    }

    @Test fun `CloudEndpoint history sorts deduplicates and derives using measured timestamps`() {
        val payload = """[{"sgv":124,"date":$now,"type":"sgv"},{"sgv":120,"date":${now - 300000}},{"sgv":124,"date":$now},{"sgv":300,"date":${now + 1}},{"sgv":30,"date":${now - 90000000}}]"""
        val result = assertNotNull(ExternalCgmAdapters.cloudEndpoint(payload, now))
        assertEquals(DataSourceId.NIGHTSCOUT, result.source)
        assertEquals(2, result.glucoseHistory.size)
        assertEquals(4.0, result.glucose!!.deltaMgDl)
        assertEquals(Trend.FLAT, result.glucose!!.trend)
        assertNull(ExternalCgmAdapters.cloudEndpoint("[]", now))
        assertNull(ExternalCgmAdapters.cloudEndpoint("{\"error\":\"unauthorized\"}", now))
    }
}
