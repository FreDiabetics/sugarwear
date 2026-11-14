package app.sugarlicious.model

/**
 * Canonical time window for every Sugarlicious time-based graph renderer.
 *
 * The live edge is an immutable data-space timestamp for a render. A manually panned viewport may
 * move it outside [startEpochMs, endEpochMs]. Every renderer derives every X coordinate again from
 * its event timestamp; no renderer may retain a previous pixel position or distribute points by
 * list index.
 */
data class GraphTimeWindow(
    val startEpochMs: Long,
    val liveEdgeEpochMs: Long,
    val endEpochMs: Long,
) {
    init {
        require(startEpochMs < endEpochMs)
    }

    val durationMs: Long get() = endEpochMs - startEpochMs

    fun xFraction(timestampEpochMs: Long): Float = ((timestampEpochMs - startEpochMs).toDouble() / durationMs.toDouble()).toFloat()

    /** Maps an event timestamp directly into a renderer plot without retaining pixel state. */
    fun plotX(
        timestampEpochMs: Long,
        plotLeft: Float,
        plotWidth: Float,
    ): Float = plotLeft + xFraction(timestampEpochMs) * plotWidth

    companion object {
        fun endingAt(
            viewportEndEpochMs: Long,
            historyDurationMs: Long,
            futureDurationMs: Long = 0L,
        ): GraphTimeWindow {
            require(historyDurationMs > 0L)
            require(futureDurationMs >= 0L)
            val liveEdge = viewportEndEpochMs - futureDurationMs
            return GraphTimeWindow(
                startEpochMs = liveEdge - historyDurationMs,
                liveEdgeEpochMs = liveEdge,
                endEpochMs = viewportEndEpochMs,
            )
        }

        fun live(
            nowEpochMs: Long,
            historyDurationMs: Long,
            futureDurationMs: Long = 0L,
        ): GraphTimeWindow =
            endingAt(
                viewportEndEpochMs = nowEpochMs + futureDurationMs,
                historyDurationMs = historyDurationMs,
                futureDurationMs = futureDurationMs,
            )
    }
}
