package app.sugarlicious.mobile

import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import java.time.Instant
import java.time.ZoneId
import java.util.TreeSet
import kotlin.math.roundToInt

internal data class MeasuredRangeStats(
    val sampleCount: Int,
    val inRange: Int?,
    val below: Int?,
    val above: Int?,
    val coveredDurationMs: Long = 0L,
    val belowDurationMs: Double? = null,
    val aboveDurationMs: Double? = null,
)

internal data class DailyGlucoseRanges(
    val tir: MeasuredRangeStats,
    val titr: MeasuredRangeStats,
)

private const val DEFAULT_CADENCE_MS = 300_000L
private const val MAX_CONTINUOUS_INTERVAL_MS = 450_000L

/** Integrates only short, compatible measured intervals. No extrapolation or gap filling. */
internal fun dailyGlucoseRanges(
    state: TherapyDisplayState?,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    days: Int = 1,
    archive: List<GlucoseSample> = emptyList(),
    thresholds: CgmThresholds? = null,
): DailyGlucoseRanges {
    require(days in OverviewRangePeriodStore.periods)
    val start =
        Instant
            .ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .minusDays(days.toLong() - 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    val current = state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality) }
    val raw = archive + state?.glucoseHistory.orEmpty() + listOfNotNull(current)
    val samples =
        CanonicalCgmHistory
            .merge(raw, now, state?.source, (now - start + MAX_CONTINUOUS_INTERVAL_MS).coerceAtLeast(1L), futureToleranceMs = 0, maxPoints = raw.size.coerceAtLeast(1))
            .filter { it.measuredAtEpochMs in (start - MAX_CONTINUOUS_INTERVAL_MS)..now }
    val sampleCount = samples.count { it.measuredAtEpochMs >= start }
    val validTimes = samples.groupBy { Triple(it.source, it.sensorId, it.sessionId) }.mapValues { (_, values) -> values.mapTo(hashSetOf()) { it.measuredAtEpochMs } }
    val invalidTimes =
        raw.groupBy { Triple(it.source, it.sensorId, it.sessionId) }.mapValues { (stream, values) ->
            TreeSet(values.filter { it.quality != CgmQuality.VALID || !it.valueMgDl.isFinite() || it.valueMgDl !in 20.0..1000.0 }.map { it.measuredAtEpochMs }.filterNot { it in validTimes[stream].orEmpty() })
        }
    val cadence =
        samples.groupBy { Triple(it.source, it.sensorId, it.sessionId) }.mapValues { (_, group) ->
            val intervals =
                group
                    .zipWithNext()
                    .map { (a, b) -> b.measuredAtEpochMs - a.measuredAtEpochMs }
                    .filter { it in 1..330_000L }
                    .sorted()
            if (intervals.isEmpty()) DEFAULT_CADENCE_MS else intervals[intervals.size / 2]
        }
    val covered =
        samples.zipWithNext().mapNotNull { (a, b) ->
            val span = b.measuredAtEpochMs - a.measuredAtEpochMs
            val sameStream = a.source == b.source && a.sensorId == b.sensorId && a.sessionId == b.sessionId
            val limit = minOf(MAX_CONTINUOUS_INTERVAL_MS, (cadence.getValue(Triple(a.source, a.sensorId, a.sessionId)) * 1.5).toLong())
            val invalidBetween = invalidTimes[Triple(a.source, a.sensorId, a.sessionId)]?.higher(a.measuredAtEpochMs)?.let { it < b.measuredAtEpochMs } == true
            if (!sameStream || span !in 1..limit || invalidBetween) return@mapNotNull null
            val from = maxOf(start, a.measuredAtEpochMs)
            val to = minOf(now, b.measuredAtEpochMs)
            if (to <= from) return@mapNotNull null

            fun valueAt(time: Long) = a.valueMgDl + (b.valueMgDl - a.valueMgDl) * (time - a.measuredAtEpochMs).toDouble() / span
            CoveredGlucoseInterval(to - from, valueAt(from), valueAt(to))
        }
    val total = covered.sumOf { it.durationMs }

    fun stats(low: Double, high: Double): MeasuredRangeStats {
        if (total == 0L) return MeasuredRangeStats(sampleCount, null, null, null)
        val below = covered.sumOf { it.timeBelow(low) }
        val above = covered.sumOf { it.timeAbove(high) }
        val inside = (total - below - above).coerceAtLeast(0.0)

        fun percent(duration: Double) = (100.0 * duration / total).roundToInt().coerceIn(0, 100)
        return MeasuredRangeStats(sampleCount, percent(inside), percent(below), percent(above), total, below, above)
    }
    return DailyGlucoseRanges(stats(thresholds?.lowMgDl ?: 70.0, thresholds?.highMgDl ?: 180.0), stats(70.0, 140.0))
}

private data class CoveredGlucoseInterval(
    val durationMs: Long,
    val from: Double,
    val to: Double,
) {
    fun timeBelow(bound: Double): Double {
        if (from == to) return if (from < bound) durationMs.toDouble() else 0.0
        val crossing = ((bound - from) / (to - from)).coerceIn(0.0, 1.0)
        return durationMs * if (to > from) crossing else 1.0 - crossing
    }

    fun timeAbove(bound: Double): Double {
        if (from == to) return if (from > bound) durationMs.toDouble() else 0.0
        return durationMs - timeBelow(bound)
    }
}

internal fun localDayStartEpochMs(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant
        .ofEpochMilli(now)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
