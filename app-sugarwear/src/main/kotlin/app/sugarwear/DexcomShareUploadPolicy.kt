package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** The server receives only committed real measurements, never predictions or display fallbacks. */
internal data class DexcomShareEgv(
    val measurementEpochMs: Long,
    val glucoseMgDl: Int,
    val trendOrdinal: Int,
) {
    fun toJson(): JSONObject {
        val date = "/Date(${measurementEpochMs / 1_000L * 1_000L})/"
        return JSONObject()
            .put("Value", glucoseMgDl)
            .put("Trend", trendOrdinal)
            .put("DT", date)
            .put("ST", date)
    }
}

internal object DexcomShareUploadPolicy {
    const val MAX_LOOKBACK_MS = 8L * 60L * 60L * 1_000L
    const val FIRST_UPLOAD_LOOKBACK_MS = 20L * 60L * 1_000L
    const val MAX_BATCH = 12

    /** This deliberately excludes BACKFILL until historical upload semantics are verified. */
    fun eligible(
        readings: List<CgmReading>,
        canonicalSensorId: String,
        canonicalSessionId: String,
        afterEpochMs: Long,
        nowEpochMs: Long,
    ): List<DexcomShareEgv> =
        readings.asSequence()
            .filter { reading ->
                reading.source == DataSourceId.DEXCOM_G7_WATCH &&
                    reading.status == CgmReadingStatus.VALID &&
                    reading.origin == CgmReadingOrigin.LIVE &&
                    !reading.displayOnly &&
                    reading.sensorId == canonicalSensorId &&
                    reading.sessionId == canonicalSessionId &&
                    reading.timestampEpochMs > afterEpochMs &&
                    reading.timestampEpochMs in (nowEpochMs - MAX_LOOKBACK_MS)..nowEpochMs &&
                    reading.receivedAtEpochMs > 0L &&
                    reading.glucoseMgDl.isFinite() &&
                    reading.glucoseMgDl in 20.0..600.0
            }
            .sortedBy { it.timestampEpochMs }
            .distinctBy { it.timestampEpochMs }
            .take(MAX_BATCH)
            .map {
                DexcomShareEgv(
                    measurementEpochMs = it.timestampEpochMs,
                    glucoseMgDl = it.glucoseMgDl.roundToInt(),
                    trendOrdinal = dexcomTrendOrdinal(it.trend),
                )
            }
            .toList()

    fun payload(receiverSerial: String, entries: List<DexcomShareEgv>): JSONObject {
        require(receiverSerial.matches(Regex("[A-Za-z0-9_-]{5,40}")))
        require(entries.isNotEmpty() && entries.size <= MAX_BATCH)
        val records = JSONArray()
        entries.forEach { records.put(it.toJson()) }
        return JSONObject()
            .put("SN", receiverSerial)
            .put("TA", -5)
            .put("Egvs", records)
    }

    private fun dexcomTrendOrdinal(trend: Trend): Int =
        when (trend) {
            Trend.DOUBLE_UP -> 1
            Trend.SINGLE_UP -> 2
            Trend.FORTY_FIVE_UP -> 3
            Trend.FLAT -> 4
            Trend.FORTY_FIVE_DOWN -> 5
            Trend.SINGLE_DOWN -> 6
            Trend.DOUBLE_DOWN -> 7
            Trend.UNKNOWN -> 0
        }
}
