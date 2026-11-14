package app.sugarlicious.mobile

import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import java.time.Instant
import java.time.ZoneId
import java.util.TreeSet

/** Each included calendar day needs measured coverage; missing hours are never invented. */
internal fun availableRangePeriods(state: TherapyDisplayState?, archive: List<GlucoseSample>, now: Long, zone: ZoneId): List<Int> {
    val current = state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality) }
    val raw = archive + state?.glucoseHistory.orEmpty() + listOfNotNull(current)
    val samples = CanonicalCgmHistory.merge(raw, now, state?.source, 92L * 86_400_000L, futureToleranceMs = 0, maxPoints = raw.size.coerceAtLeast(1))

    fun stream(sample: GlucoseSample) = Triple(sample.source, sample.sensorId, sample.sessionId)
    val validTimes = samples.groupBy(::stream).mapValues { (_, values) -> values.mapTo(hashSetOf()) { it.measuredAtEpochMs } }
    val invalid =
        raw.groupBy(::stream).mapValues { (key, values) ->
            TreeSet(
                values
                    .filter { it.quality != CgmQuality.VALID || !it.valueMgDl.isFinite() || it.valueMgDl !in 20.0..1000.0 }
                    .map { it.measuredAtEpochMs }
                    .filterNot { it in validTimes[key].orEmpty() },
            )
        }
    val cadence =
        samples.groupBy(::stream).mapValues { (_, values) ->
            val intervals =
                values
                    .zipWithNext()
                    .map { (a, b) -> b.measuredAtEpochMs - a.measuredAtEpochMs }
                    .filter { it in 1..330_000L }
                    .sorted()
            if (intervals.isEmpty()) 300_000L else intervals[intervals.size / 2]
        }
    val dates =
        buildSet {
            samples.zipWithNext().forEach { (a, b) ->
                if (stream(a) != stream(b) || b.measuredAtEpochMs - a.measuredAtEpochMs !in 1..minOf(450_000L, (cadence.getValue(stream(a)) * 1.5).toLong())) return@forEach
                if (invalid[stream(a)]?.higher(a.measuredAtEpochMs)?.let { it < b.measuredAtEpochMs } == true) return@forEach
                add(Instant.ofEpochMilli(a.measuredAtEpochMs).atZone(zone).toLocalDate())
                add(Instant.ofEpochMilli(b.measuredAtEpochMs - 1).atZone(zone).toLocalDate())
            }
        }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val consecutive = (0 until 90).takeWhile { today.minusDays(it.toLong()) in dates }.size
    return OverviewRangePeriodStore.periods.filter { it <= consecutive }
}

internal fun nextAvailableRangePeriod(current: Int, available: List<Int>): Int {
    val ordered = OverviewRangePeriodStore.periods.filter { it in available }
    if (ordered.isEmpty()) return current
    return ordered.firstOrNull { it > current } ?: ordered.first()
}
