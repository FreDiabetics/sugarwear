package app.sugarlicious.mobile

import app.sugarlicious.model.TherapyHistorySample

internal fun finiteInsulinActivitySeries(points: List<TherapyHistorySample>): List<Pair<Long, Double>> =
    points
        .mapNotNull { point ->
            point.insulinActivityUnitsPerMinute?.takeIf(Double::isFinite)?.let { point.measuredAtEpochMs to it }
        }.sortedBy { it.first }

internal fun smoothActivitySeries(values: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
    val ordered = values.distinctBy { it.first }.sortedBy { it.first }
    if (ordered.size < 3) return ordered
    return ordered.mapIndexed { index, point ->
        if (index == 0 || index == ordered.lastIndex) {
            point
        } else {
            val smoothed = (ordered[index - 1].second + 2.0 * point.second + ordered[index + 1].second) / 4.0
            point.first to smoothed
        }
    }
}

internal fun visibleInsulinActivitySeries(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
): List<Pair<Long, Double>> =
    values
        .asSequence()
        .filter { (timestamp, value) -> timestamp in start..end && value.isFinite() }
        .distinctBy { it.first }
        .sortedBy { it.first }
        .toList()

internal fun smoothActivityToLiveEdge(
    values: List<Pair<Long, Double>>,
    liveEdge: Long,
    start: Long,
    end: Long,
): List<Pair<Long, Double>> {
    if (start > end) return emptyList()
    // Smooth the canonical series before applying any viewport. Otherwise zooming changes which
    // samples are endpoints of the moving average and visibly reshapes the same time interval.
    val actual =
        smoothActivitySeries(values.filter { (timestamp, value) -> timestamp <= liveEdge && value.isFinite() })
            .toMutableList()
    // Keep off-screen neighbours in the path so cubic tangents do not change with zoom. Canvas
    // clipping removes them visually without changing the curve inside the viewport.
    if (actual.isNotEmpty() && actual.last().first < liveEdge) actual += liveEdge to actual.last().second
    return actual
}

internal fun cgmInsulinActivitySeries(
    values: List<Pair<Long, Double>>,
    now: Long,
    start: Long,
    end: Long,
): List<Pair<Long, Double>> = smoothActivityToLiveEdge(values, now, start, end)
