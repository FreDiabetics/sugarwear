package app.sugarlicious.storage

import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample

/**
 * Builds the display-only future prediction timeline without rewriting source timestamps.
 *
 * AAPS renders CGM, predictions and the Now line on one real time axis. Prediction packets may
 * contain a seed point at or before the boundary. When source samples straddle the boundary we
 * interpolate exactly once in data space, then retain every real future timestamp unchanged.
 */
object PredictionDisplayTimeline {
    const val MAX_DISPLAY_WINDOW_MS = 2L * 60L * 60_000L

    fun anchor(
        predictions: List<GlucosePrediction>,
        nowEpochMs: Long,
    ): List<GlucosePrediction> =
        predictions.mapNotNull { series ->
            val valid =
                series.samples
                    .asSequence()
                    .filter { it.valueMgDl.isFinite() && it.valueMgDl in 20.0..1000.0 }
                    .distinctBy { it.measuredAtEpochMs }
                    .sortedBy { it.measuredAtEpochMs }
                    .toList()
            val exact = valid.firstOrNull { it.measuredAtEpochMs == nowEpochMs }
            val before = valid.lastOrNull { it.measuredAtEpochMs < nowEpochMs }
            val after = valid.firstOrNull { it.measuredAtEpochMs > nowEpochMs }
            if (after == null) return@mapNotNull null
            val boundary =
                exact ?: if (before != null) {
                    val fraction =
                        (nowEpochMs - before.measuredAtEpochMs).toDouble() /
                            (after.measuredAtEpochMs - before.measuredAtEpochMs).toDouble()
                    GlucoseSample(
                        valueMgDl = before.valueMgDl + (after.valueMgDl - before.valueMgDl) * fraction,
                        measuredAtEpochMs = nowEpochMs,
                        source = after.source,
                        sensorId = after.sensorId ?: before.sensorId,
                        sessionId = after.sessionId ?: before.sessionId,
                        receivedAtEpochMs = listOfNotNull(before.receivedAtEpochMs, after.receivedAtEpochMs).maxOrNull(),
                    )
                } else {
                    null
                }
            val samples =
                buildList {
                    boundary?.let(::add)
                    addAll(valid.filter { it.measuredAtEpochMs > nowEpochMs })
                }.distinctBy { it.measuredAtEpochMs }
            if (samples.isEmpty()) null else series.copy(samples = samples)
        }

    fun futureWindowMs(
        predictions: List<GlucosePrediction>,
        nowEpochMs: Long,
    ): Long =
        anchor(predictions, nowEpochMs)
            .flatMap { it.samples }
            .maxOfOrNull { it.measuredAtEpochMs }
            ?.minus(nowEpochMs)
            ?.coerceIn(0L, MAX_DISPLAY_WINDOW_MS)
            ?: 0L
}
