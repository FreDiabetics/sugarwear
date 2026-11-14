package app.sugarlicious.datasource.external

import app.sugarlicious.model.*
import kotlinx.serialization.json.*
import java.time.Instant

/** Public producer contracts
raw sensor signals are never treated as glucose estimates. */
object ExternalCgmAdapters {
    const val XDRIP_ACTION = "com.eveningoutpost.dexdrip.BgEstimate"
    const val XDRIP_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_BG_ESTIMATE"
    const val LIBRE_ACTION = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING"
    private const val PREFIX = "com.eveningoutpost.dexdrip.Extras."

    const val LIBRE_SCAN_ACTION = "com.librelink.app.ThirdPartyIntegration.SENSOR_SCAN"

    fun local(action: String?, extras: Map<String, Any?>, now: Long): TherapyDisplayState? {
        if (action == LIBRE_SCAN_ACTION) {
            val serial = (extras["sensorSerial"] as? String)?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
            val start = (extras["sensorStartTime"] as? Number)?.toLong()
            if (start != null && start !in 1..now) return null
            val readings = extras["readings"] as? List<*> ?: return null
            if (readings.size > 2000) return null
            val samples =
                readings.mapNotNull { value ->
                    val item = value as? Map<*, *> ?: return@mapNotNull null
                    val glucose = (item["glucoseValue"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val time = (item["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
                    if (!valid(glucose, time, now) || start != null && time < start) return@mapNotNull null
                    GlucoseSample(glucose, time, DataSourceId.LIBRE_PATCHED, sensorId = serial, sessionId = start?.let { "libre:$serial:$it" }, receivedAtEpochMs = now)
                }
            return state(samples, now)
        }
        // GLUCOSE_READING carries the raw/unsmoothed Libre input, not the processed scan value.
        if (action != XDRIP_ACTION) return null
        val value = (extras[PREFIX + "BgEstimate"] as? Number)?.toDouble() ?: return null
        val time = (extras[PREFIX + "Time"] as? Number)?.toLong() ?: return null
        if (!valid(value, time, now)) return null
        val start = (extras[PREFIX + "SensorStartedAt"] as? Number)?.toLong()
        if (start != null && start !in 1..time) return null
        val hiddenSlope = (extras[PREFIX + "BgSlopeName"] as? String) in setOf("9", "NOT COMPUTABLE", "RateOutOfRange")
        val rate = if (hiddenSlope) null else (extras[PREFIX + "BgSlope"] as? Number)?.toDouble()?.times(60_000)?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 100 }
        return state(listOf(GlucoseSample(value, time, DataSourceId.XDRIP_PLUS, sessionId = start?.let { "xdrip:$it" }, receivedAtEpochMs = now)), now, rate)
    }

    fun cloudEndpoint(payload: String, now: Long): TherapyDisplayState? =
        runCatching {
            val array = Json.parseToJsonElement(payload).jsonArray
            require(array.size <= 2000)
            val samples =
                array.mapNotNull { element ->
                    val entry = element as? JsonObject ?: return@mapNotNull null
                    if (entry["type"]?.jsonPrimitive?.contentOrNull?.let { it != "sgv" } == true) return@mapNotNull null
                    val value = entry["sgv"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val time = entry["date"]?.jsonPrimitive?.longOrNull ?: entry["dateString"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
                    if (!valid(value, time, now)) return@mapNotNull null
                    GlucoseSample(value, time, DataSourceId.NIGHTSCOUT, receivedAtEpochMs = now)
                }
            state(samples, now)
        }.getOrNull()

    private fun valid(value: Double, time: Long, now: Long) = value.isFinite() && value in 20.0..1000.0 && time in (now - 86_400_000L)..now

    private fun state(samples: List<GlucoseSample>, now: Long, rate: Double? = null): TherapyDisplayState? {
        val history = CanonicalCgmHistory.merge(samples, now, maxPoints = 2000)
        val latest = history.lastOrNull() ?: return null
        val derived = CanonicalTrendPolicy.derive(latest, history, TrendRateProfile.SUGARLICIOUS)
        val canonicalRate = derived?.rateMgDlPerMinute ?: rate
        val trend = derived?.trend ?: canonicalRate?.let { CanonicalTrendPolicy.fromRate(it, TrendRateProfile.SUGARLICIOUS) } ?: Trend.UNKNOWN
        val provenance =
            buildMap {
                put(CanonicalDataField.GLUCOSE, ValueProvenance.SOURCE)
                if (trend != Trend.UNKNOWN) put(CanonicalDataField.TREND, ValueProvenance.DERIVED)
                if (derived != null) put(CanonicalDataField.DELTA, ValueProvenance.DERIVED)
            }
        return TherapyDisplayState(
            source = latest.source,
            sourceContract = "EXTERNAL_CGM_V1",
            receivedAtEpochMs = now,
            glucose = GlucoseState(latest.valueMgDl, GlucoseUnit.MG_DL, trend, latest.measuredAtEpochMs, deltaMgDl = derived?.deltaMgDl, source = latest.source, sensorId = latest.sensorId, sessionId = latest.sessionId, receivedAtEpochMs = now, trendRateMgDlPerMinute = canonicalRate, trendOrigin = if (canonicalRate != null) ValueProvenance.DERIVED else ValueProvenance.UNAVAILABLE),
            glucoseHistory = history,
            capabilities = setOf(DataCapability.GLUCOSE) + (if (trend != Trend.UNKNOWN) setOf(DataCapability.TREND) else emptySet()) + (if (derived != null) setOf(DataCapability.DELTA) else emptySet()),
            fieldSources = provenance.keys.associateWith { latest.source },
            fieldProvenance = provenance,
        )
    }
}
