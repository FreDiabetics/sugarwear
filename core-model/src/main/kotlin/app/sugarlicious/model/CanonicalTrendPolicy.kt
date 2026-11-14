package app.sugarlicious.model

import kotlin.math.abs
import kotlin.math.roundToInt

enum class TrendRateProfile { SUGARLICIOUS, ANDROID_APS, DEXCOM_G7 }

data class TrendDerivation(
    val deltaMgDl: Double,
    val elapsedMinutes: Double,
    val rateMgDlPerMinute: Double,
    val trend: Trend,
    val previousMeasuredAtEpochMs: Long,
)

/** The sole semantic normalization and derived-rate classification policy. */
object CanonicalTrendPolicy {
    private const val MIN_INTERVAL_MINUTES = 3.0
    private const val MAX_INTERVAL_MINUTES = 12.0
    private const val IDEAL_INTERVAL_MINUTES = 5.0

    fun fromDirection(direction: String?): Trend =
        when (direction?.trim()) {
            "DoubleUp", "⇈" -> Trend.DOUBLE_UP
            "SingleUp", "↑" -> Trend.SINGLE_UP
            "FortyFiveUp", "↗" -> Trend.FORTY_FIVE_UP
            "Flat", "→" -> Trend.FLAT
            "FortyFiveDown", "↘" -> Trend.FORTY_FIVE_DOWN
            "SingleDown", "↓" -> Trend.SINGLE_DOWN
            "DoubleDown", "⇊" -> Trend.DOUBLE_DOWN
            else -> Trend.UNKNOWN
        }

    fun fromRate(
        rateMgDlPerMinute: Double?,
        profile: TrendRateProfile,
    ): Trend {
        if (rateMgDlPerMinute == null || !rateMgDlPerMinute.isFinite()) return Trend.UNKNOWN
        return when (profile) {
            TrendRateProfile.SUGARLICIOUS,
            TrendRateProfile.ANDROID_APS,
            ->
                fromFiveMinuteDelta(rateMgDlPerMinute * IDEAL_INTERVAL_MINUTES, profile)
            TrendRateProfile.DEXCOM_G7 ->
                when {
                    rateMgDlPerMinute < -3.0 -> Trend.DOUBLE_DOWN
                    rateMgDlPerMinute <= -2.0 -> Trend.SINGLE_DOWN
                    rateMgDlPerMinute <= -1.0 -> Trend.FORTY_FIVE_DOWN
                    rateMgDlPerMinute < 1.0 -> Trend.FLAT
                    rateMgDlPerMinute < 2.0 -> Trend.FORTY_FIVE_UP
                    rateMgDlPerMinute <= 3.0 -> Trend.SINGLE_UP
                    else -> Trend.DOUBLE_UP
                }
        }
    }

    fun fromFiveMinuteDelta(
        deltaMgDl: Double?,
        profile: TrendRateProfile,
    ): Trend {
        if (deltaMgDl == null || !deltaMgDl.isFinite()) return Trend.UNKNOWN
        if (profile == TrendRateProfile.DEXCOM_G7) {
            return fromRate(deltaMgDl / IDEAL_INTERVAL_MINUTES, profile)
        }
        return when (deltaMgDl.roundToInt()) {
            in Int.MIN_VALUE..-14 -> Trend.DOUBLE_DOWN
            in -13..-10 -> Trend.SINGLE_DOWN
            in -9..-6 -> Trend.FORTY_FIVE_DOWN
            in -5..5 -> Trend.FLAT
            in 6..9 -> Trend.FORTY_FIVE_UP
            in 10..13 -> Trend.SINGLE_UP
            else -> Trend.DOUBLE_UP
        }
    }

    fun derive(
        current: GlucoseSample,
        history: List<GlucoseSample>,
        profile: TrendRateProfile,
    ): TrendDerivation? {
        if (!current.isUsable()) return null
        val previous =
            history
                .asSequence()
                .filter { it.isUsable() && it.isComparableTo(current) }
                .filter { it.measuredAtEpochMs < current.measuredAtEpochMs }
                .map { it to (current.measuredAtEpochMs - it.measuredAtEpochMs) / 60_000.0 }
                .filter { (_, minutes) -> minutes in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES }
                .minByOrNull { (_, minutes) -> abs(minutes - IDEAL_INTERVAL_MINUTES) }
                ?: return null
        val delta = current.valueMgDl - previous.first.valueMgDl
        val rate = delta / previous.second
        return TrendDerivation(delta, previous.second, rate, fromRate(rate, profile), previous.first.measuredAtEpochMs)
    }

    private fun GlucoseSample.isUsable(): Boolean =
        quality == CgmQuality.VALID && valueMgDl.isFinite() && valueMgDl in 20.0..1_000.0

    private fun GlucoseSample.isComparableTo(current: GlucoseSample): Boolean =
        source == current.source &&
            identitiesMatch(sensorId, current.sensorId) &&
            identitiesMatch(sessionId, current.sessionId)

    private fun identitiesMatch(
        previous: String?,
        current: String?,
    ): Boolean = previous == null && current == null || previous != null && previous == current
}
