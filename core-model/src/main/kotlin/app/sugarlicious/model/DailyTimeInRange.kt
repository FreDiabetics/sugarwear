package app.sugarlicious.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class DailyTir(
    val date: LocalDate,
    val percent: Int?,
    val coveredMillis: Long,
)

/** Historical statistics only: never extends a sample across a signal gap or supplies live glucose. */
object DailyTimeInRange {
    const val ARCHIVE_WINDOW_MS = 6L * 24 * 60 * 60_000
    const val ARCHIVE_MAX_POINTS = 9_000

    // Extra calendar-day margin covers the oldest selected day across DST transitions.
    const val LONG_TERM_ARCHIVE_WINDOW_MS = 92L * 24 * 60 * 60_000

    fun calculate(samples: List<GlucoseSample>, now: Long, zone: ZoneId, thresholds: CgmThresholds): List<DailyTir> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val valid = CanonicalCgmHistory.merge(samples, now, windowMs = ARCHIVE_WINDOW_MS, futureToleranceMs = 0, maxPoints = ARCHIVE_MAX_POINTS)
        return (4 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end =
                minOf(
                    now,
                    date
                        .plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli(),
                )
            var covered = 0L
            var inRange = 0L
            var observedStart = Long.MAX_VALUE
            var observedEnd = Long.MIN_VALUE
            valid.forEachIndexed { index, sample ->
                val left = maxOf(start, sample.measuredAtEpochMs)
                val right = minOf(end, sample.measuredAtEpochMs + 5 * 60_000, valid.getOrNull(index + 1)?.measuredAtEpochMs ?: end)
                val duration = (right - left).coerceAtLeast(0)
                if (duration > 0) {
                    observedStart = minOf(observedStart, left)
                    observedEnd = maxOf(observedEnd, right)
                }
                covered += duration
                if (thresholds.classify(sample.valueMgDl) == CgmRangeClass.IN_RANGE) inRange += duration
            }
            // A newly installed archive can contain only the final minutes of an older day.
            // Never present that fragment as the day's TIR. Today is measured from midnight
            // to now; missing history is not filled or treated as time in range.
            val spansPeriod = observedStart <= start + 5 * 60_000 && observedEnd >= end - 5 * 60_000
            // The same two five-minute edge allowances are the total missing-data budget,
            // not permission for an unobserved gap between otherwise matching endpoints.
            val sufficientlyCovered = end - start - covered <= 10 * 60_000
            DailyTir(date, if (covered > 0 && thresholds.isValid && spansPeriod && sufficientlyCovered) (100.0 * inRange / covered).roundToInt() else null, covered)
        }
    }
}
