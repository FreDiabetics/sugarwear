package app.sugarlicious.model

import kotlin.math.ceil
import kotlin.math.floor

/** Pure, locale-independent relative time axis used by all Sugarlicious CGM graphs. */
data class RelativeGraphTimeTick(
    val timestampEpochMs: Long,
    val hoursBack: Int,
    val label: String,
)

object RelativeGraphTimeAxis {
    const val HOUR_MS: Long = 60L * 60_000L
    const val LIVE_EDGE_TOLERANCE_MS: Long = 15L * 60_000L

    fun label(hoursBack: Int): String = if (hoursBack <= 0) "jetzt" else "${hoursBack}h"

    fun intervalHours(visibleHistoryHours: Double): Int =
        when {
            visibleHistoryHours <= 3.25 -> 1
            visibleHistoryHours <= 6.5 -> 2
            visibleHistoryHours <= 12.5 -> 4
            else -> 6
        }

    /**
     * Produces ticks anchored to the current moment instead of wall-clock boundaries.
     * Future prediction space is intentionally ignored: there are no future clock labels.
     *
     * A normal live CGM viewport may end at the newest reading rather than exactly at the
     * current wall-clock time. If that edge is still within the live freshness window, the
     * right edge is labelled "jetzt" without moving any data point. Truly historical/panned
     * windows remain relative to the current moment and do not receive a false live label.
     */
    fun ticks(
        startEpochMs: Long,
        endEpochMs: Long,
        nowEpochMs: Long,
        fixedIntervalHours: Int? = null,
    ): List<RelativeGraphTimeTick> {
        if (endEpochMs <= startEpochMs || nowEpochMs <= 0L) return emptyList()
        val visibleEnd = minOf(endEpochMs, nowEpochMs)
        if (visibleEnd < startEpochMs) return emptyList()

        val oldestHoursBack = ceil((nowEpochMs - startEpochMs).coerceAtLeast(0L).toDouble() / HOUR_MS).toInt()
        val newestHoursBack = floor((nowEpochMs - visibleEnd).coerceAtLeast(0L).toDouble() / HOUR_MS).toInt()
        val visibleHistoryHours = (visibleEnd - startEpochMs).coerceAtLeast(0L).toDouble() / HOUR_MS
        val interval = fixedIntervalHours?.coerceAtLeast(1) ?: intervalHours(visibleHistoryHours)

        val ticks = mutableListOf<RelativeGraphTimeTick>()
        var hoursBack = oldestHoursBack - (oldestHoursBack % interval)
        if (hoursBack < newestHoursBack) hoursBack += interval
        while (hoursBack >= newestHoursBack) {
            val timestamp = nowEpochMs - hoursBack * HOUR_MS
            if (timestamp in startEpochMs..visibleEnd) {
                ticks += RelativeGraphTimeTick(timestamp, hoursBack, label(hoursBack))
            }
            hoursBack -= interval
        }

        val liveEdge = endEpochMs >= nowEpochMs - LIVE_EDGE_TOLERANCE_MS
        if (liveEdge && ticks.none { it.hoursBack == 0 }) {
            val timestamp = if (endEpochMs >= nowEpochMs) nowEpochMs else visibleEnd
            ticks += RelativeGraphTimeTick(timestamp, 0, "jetzt")
        }

        return ticks
            .distinctBy { it.timestampEpochMs }
            .sortedBy { it.timestampEpochMs }
    }
}
