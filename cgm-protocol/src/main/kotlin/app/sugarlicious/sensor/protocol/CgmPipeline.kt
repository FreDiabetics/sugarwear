package app.sugarlicious.sensor.protocol

import app.sugarlicious.model.CanonicalTrendPolicy
import app.sugarlicious.model.CgmPresentationPolicy
import app.sugarlicious.model.CgmPresentationStatus
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendRateProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.abs

interface CgmReadingRepository {
    val latestReading: StateFlow<CgmReading?>

    suspend fun insert(reading: CgmReading): Boolean

    suspend fun insertOrIgnore(reading: CgmReading): Boolean = insert(reading)

    suspend fun getLatest(): CgmReading?

    suspend fun getPrevious(): CgmReading?

    suspend fun getRecent(sinceEpochMs: Long): List<CgmReading>

    suspend fun getRange(
        fromEpochMs: Long,
        toEpochMs: Long,
    ): List<CgmReading>
}

object CgmReadingIdentity {
    /** A sequence is transport metadata and is not stable across LIVE and BACKFILL packets. */
    fun create(
        sensorId: String,
        sessionId: String,
        timestampEpochMs: Long,
    ): String = listOf(sensorId, sessionId, timestampEpochMs.toString()).joinToString(":")
}

object CgmDeltaCalculator {
    private const val MIN_INTERVAL_MS = 2 * 60_000L
    private const val MAX_INTERVAL_MS = 8 * 60_000L

    fun calculate(
        current: CgmReading,
        previous: CgmReading?,
    ): Double? {
        if (previous == null || current.sensorId != previous.sensorId || current.sessionId != previous.sessionId) return null
        if (current.status != CgmReadingStatus.VALID || previous.status != CgmReadingStatus.VALID) return null
        if (!current.glucoseMgDl.isFinite() || !previous.glucoseMgDl.isFinite()) return null
        if (current.glucoseMgDl !in 20.0..1_000.0 || previous.glucoseMgDl !in 20.0..1_000.0) return null
        val interval = current.timestampEpochMs - previous.timestampEpochMs
        if (interval !in MIN_INTERVAL_MS..MAX_INTERVAL_MS) return null
        return current.glucoseMgDl - previous.glucoseMgDl
    }
}

object CgmTrendRateCalculator {
    fun calculate(
        current: CgmReading,
        previous: CgmReading?,
    ): Double? {
        val delta = CgmDeltaCalculator.calculate(current, previous) ?: return null
        val previousReading = previous ?: return null
        val intervalMinutes = (current.timestampEpochMs - previousReading.timestampEpochMs) / 60_000.0
        if (!intervalMinutes.isFinite() || intervalMinutes <= 0.0) return null
        return delta / intervalMinutes
    }
}

object CgmTrendMapper {
    fun fromRate(rateMgDlPerMinute: Double?): Trend =
        CanonicalTrendPolicy.fromRate(rateMgDlPerMinute, TrendRateProfile.DEXCOM_G7)

    fun fromCgm(value: CgmTrend): Trend =
        when (value) {
            CgmTrend.DOUBLE_UP -> Trend.DOUBLE_UP
            CgmTrend.SINGLE_UP -> Trend.SINGLE_UP
            CgmTrend.FORTY_FIVE_UP -> Trend.FORTY_FIVE_UP
            CgmTrend.FLAT -> Trend.FLAT
            CgmTrend.FORTY_FIVE_DOWN -> Trend.FORTY_FIVE_DOWN
            CgmTrend.SINGLE_DOWN -> Trend.SINGLE_DOWN
            CgmTrend.DOUBLE_DOWN -> Trend.DOUBLE_DOWN
            CgmTrend.UNKNOWN -> Trend.UNKNOWN
        }
}

fun SensorProtocolReading.toCgm(previous: CgmReading? = null): CgmReading {
    val status =
        when {
            sensorState == CgmSensorState.ERROR -> CgmReadingStatus.SENSOR_ERROR
            !glucoseMgDl.isFinite() || glucoseMgDl !in 20.0..1_000.0 -> CgmReadingStatus.INVALID
            else -> CgmReadingStatus.VALID
        }
    val base =
        CgmReading(
            id = CgmReadingIdentity.create(sensorId, sessionId, sensorTimestampEpochMs),
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = sensorId,
            sessionId = sessionId,
            glucoseMgDl = glucoseMgDl,
            timestampEpochMs = sensorTimestampEpochMs,
            receivedAtEpochMs = receivedAtEpochMs,
            trendRateMgDlPerMinute = trendRateMgDlPerMinute,
            predictedMgDl = predictedMgDl,
            sensorAgeSeconds = sensorAgeSeconds,
            sequenceNumber = sequenceNumber,
            status = status,
            displayOnly = displayOnly,
            rawSourceTimestamp = sensorClockSeconds,
            sensorStartEpochMs = sensorStartEpochMs,
            sensorEndEpochMs = sensorEndEpochMs,
            graceEndEpochMs = graceEndEpochMs,
            protocolStatusCode = protocolStatusCode,
            calibrationStateCode = calibrationStateCode,
            reservedField = reservedField,
            origin = origin,
        )
    val delta = CgmDeltaCalculator.calculate(base, previous)
    val resolvedTrendRate = trendRateMgDlPerMinute ?: CgmTrendRateCalculator.calculate(base, previous)
    return base.copy(
        deltaMgDl = delta,
        trendRateMgDlPerMinute = resolvedTrendRate,
        trend = CgmTrendMapper.fromRate(resolvedTrendRate),
    )
}

@Serializable enum class CgmAlarmType { VERY_HIGH, HIGH, LOW, VERY_LOW, RAPID_RISE, RAPID_FALL, SIGNAL_LOSS, SENSOR_ERROR }

@Serializable enum class CgmAlarmState { INACTIVE, ACTIVE, ACKNOWLEDGED, SNOOZED, RESOLVED }

@Serializable
data class CgmAlarm(
    val type: CgmAlarmType,
    val state: CgmAlarmState,
    val triggeredAtEpochMs: Long,
    val readingId: String?,
    val snoozedUntilEpochMs: Long? = null,
    val lastNotifiedAtEpochMs: Long? = null,
    val acknowledgedAtEpochMs: Long? = null,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val triggerGlucoseMgDl: Double? = null,
)

@Serializable
data class CgmAlarmSettings(
    val veryHighThreshold: Double,
    val highThreshold: Double,
    val lowThreshold: Double,
    val veryLowThreshold: Double,
    val rapidRiseThreshold: Double,
    val rapidFallThreshold: Double,
    val signalLossMinutes: Int,
    val hysteresisMgDl: Double = 5.0,
    val veryHighEnabled: Boolean = true,
    val highEnabled: Boolean = true,
    val lowEnabled: Boolean = true,
    val veryLowEnabled: Boolean = true,
    val rapidRiseEnabled: Boolean = true,
    val rapidFallEnabled: Boolean = true,
    val signalLossEnabled: Boolean = true,
    val sensorErrorEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val repeatEnabled: Boolean = true,
    val repeatIntervalMinutes: Int = 15,
) {
    init {
        require(veryHighThreshold > highThreshold)
        require(lowThreshold > veryLowThreshold)
        require(hysteresisMgDl >= 0.0)
        require(signalLossMinutes > 0)
        require(repeatIntervalMinutes > 0)
    }
}

object CgmAlarmEngine {
    fun evaluate(
        reading: CgmReading?,
        current: Map<CgmAlarmType, CgmAlarm>,
        settings: CgmAlarmSettings,
        nowEpochMs: Long,
    ): Map<CgmAlarmType, CgmAlarm> {
        val next =
            current
                .filterValues { alarm ->
                    reading == null ||
                        (alarm.sensorId == reading.sensorId && alarm.sessionId == reading.sessionId)
                }.toMutableMap()

        fun update(
            type: CgmAlarmType,
            active: Boolean,
        ) {
            val old = next[type]
            if (active && old?.state !in setOf(CgmAlarmState.ACTIVE, CgmAlarmState.ACKNOWLEDGED, CgmAlarmState.SNOOZED)) {
                next[type] =
                    CgmAlarm(
                        type = type,
                        state = CgmAlarmState.ACTIVE,
                        triggeredAtEpochMs = nowEpochMs,
                        readingId = reading?.id,
                        sensorId = reading?.sensorId,
                        sessionId = reading?.sessionId,
                        triggerGlucoseMgDl = reading?.glucoseMgDl,
                    )
            } else if (!active && old != null && old.state != CgmAlarmState.RESOLVED) {
                next[type] = old.copy(state = CgmAlarmState.RESOLVED)
            }
        }

        fun wasActive(type: CgmAlarmType): Boolean = next[type]?.state in setOf(CgmAlarmState.ACTIVE, CgmAlarmState.ACKNOWLEDGED, CgmAlarmState.SNOOZED)

        val signalLossMs = settings.signalLossMinutes * 60_000L
        val validReading =
            reading?.takeIf {
                val ageMs = nowEpochMs - it.timestampEpochMs
                it.status == CgmReadingStatus.VALID &&
                    it.glucoseMgDl.isFinite() &&
                    it.glucoseMgDl in 20.0..1_000.0 &&
                    ageMs in 0L until signalLossMs
            }
        val value = validReading?.glucoseMgDl
        val veryHigh =
            settings.veryHighEnabled &&
                value != null &&
                (
                    value >= settings.veryHighThreshold ||
                        (wasActive(CgmAlarmType.VERY_HIGH) && value >= settings.veryHighThreshold - settings.hysteresisMgDl)
                )
        val high =
            settings.highEnabled &&
                !veryHigh &&
                value != null &&
                (
                    value >= settings.highThreshold ||
                        (wasActive(CgmAlarmType.HIGH) && value >= settings.highThreshold - settings.hysteresisMgDl)
                )
        val veryLow =
            settings.veryLowEnabled &&
                value != null &&
                (
                    value <= settings.veryLowThreshold ||
                        (wasActive(CgmAlarmType.VERY_LOW) && value <= settings.veryLowThreshold + settings.hysteresisMgDl)
                )
        val low =
            settings.lowEnabled &&
                !veryLow &&
                value != null &&
                (
                    value <= settings.lowThreshold ||
                        (
                            wasActive(
                                CgmAlarmType.LOW,
                            ) &&
                                value <= settings.lowThreshold + settings.hysteresisMgDl
                        )
                )

        // A range/rate alarm may only recover from a new validated, fresh CGM reading. A stale
        // timeout is a freshness transition and must not manufacture a normal glucose event.
        if (validReading != null) {
            update(CgmAlarmType.VERY_HIGH, veryHigh)
            update(CgmAlarmType.HIGH, high)
            update(CgmAlarmType.VERY_LOW, veryLow)
            update(CgmAlarmType.LOW, low)
            update(
                CgmAlarmType.RAPID_RISE,
                settings.rapidRiseEnabled && validReading.trendRateMgDlPerMinute?.let { it >= settings.rapidRiseThreshold } == true,
            )
            update(
                CgmAlarmType.RAPID_FALL,
                settings.rapidFallEnabled && validReading.trendRateMgDlPerMinute?.let { it <= -abs(settings.rapidFallThreshold) } == true,
            )
        }
        val presentationStatus =
            reading?.let {
                CgmPresentationPolicy.classify(
                    measuredAtEpochMs = it.timestampEpochMs,
                    quality =
                        when (it.status) {
                            CgmReadingStatus.VALID -> CgmQuality.VALID
                            CgmReadingStatus.SENSOR_ERROR -> CgmQuality.SENSOR_ERROR
                            CgmReadingStatus.INVALID -> CgmQuality.INVALID
                        },
                    nowEpochMs = nowEpochMs,
                )
            }
        update(CgmAlarmType.SIGNAL_LOSS, settings.signalLossEnabled && presentationStatus == CgmPresentationStatus.SIGNAL_LOSS)
        if (reading != null) {
            update(
                CgmAlarmType.SENSOR_ERROR,
                settings.sensorErrorEnabled && presentationStatus == CgmPresentationStatus.SENSOR_ERROR,
            )
        }
        return next
    }

    fun acknowledge(
        alarm: CgmAlarm,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmAlarm = alarm.copy(state = CgmAlarmState.ACKNOWLEDGED, acknowledgedAtEpochMs = nowEpochMs)

    fun markNotified(
        alarm: CgmAlarm,
        nowEpochMs: Long,
    ): CgmAlarm = alarm.copy(lastNotifiedAtEpochMs = nowEpochMs)

    fun snooze(
        alarm: CgmAlarm,
        untilEpochMs: Long,
    ): CgmAlarm {
        require(untilEpochMs > alarm.triggeredAtEpochMs)
        return alarm.copy(state = CgmAlarmState.SNOOZED, snoozedUntilEpochMs = untilEpochMs)
    }

    fun shouldRepeat(
        alarm: CgmAlarm,
        settings: CgmAlarmSettings,
        nowEpochMs: Long,
    ): Boolean =
        settings.repeatEnabled &&
            alarm.state == CgmAlarmState.ACTIVE &&
            nowEpochMs - (alarm.lastNotifiedAtEpochMs ?: alarm.triggeredAtEpochMs) >= settings.repeatIntervalMinutes * 60_000L
}
