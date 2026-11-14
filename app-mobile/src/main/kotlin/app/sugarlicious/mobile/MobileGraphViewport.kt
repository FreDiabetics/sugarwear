package app.sugarlicious.mobile

import android.content.SharedPreferences
import androidx.core.content.edit
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState

private const val HOUR_MS = 60L * 60_000L
private const val OVERVIEW_GRAPH_HOURS_MIGRATION = "graphHoursDefault3MigratedV5"
internal val OVERVIEW_GRAPH_HOUR_OPTIONS = listOf(1, 2, 3, 6, 12, 24)

internal fun availableGlucoseHistoryWindowMs(state: TherapyDisplayState?, nowEpochMs: Long): Long {
    val earliest =
        buildList {
            state?.glucoseHistory.orEmpty().forEach { sample ->
                if (sample.measuredAtEpochMs <= nowEpochMs) add(sample.measuredAtEpochMs)
            }
            state
                ?.glucose
                ?.measuredAtEpochMs
                ?.takeIf { it <= nowEpochMs }
                ?.let(::add)
        }.minOrNull() ?: return 0L
    return (nowEpochMs - earliest).coerceIn(0L, 24L * HOUR_MS)
}

internal fun canonicalMobileGraphHistory(
    samples: List<GlucoseSample>,
    nowEpochMs: Long,
    preferredSource: DataSourceId?,
): List<GlucoseSample> =
    CanonicalCgmHistory.merge(
        samples = samples,
        nowEpochMs = nowEpochMs,
        preferredSource = preferredSource,
        windowMs = DisplayHistoryAccumulator.WINDOW_MS,
        maxPoints = DisplayHistoryAccumulator.MAX_POINTS,
    )

internal fun availableOverviewHistoryWindowMs(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
    requestedHours: Int,
): Long {
    val oldestVisibleTimestamp =
        buildList {
            state?.glucoseHistory.orEmpty().forEach { add(it.measuredAtEpochMs) }
            state?.glucose?.let { add(it.measuredAtEpochMs) }
            state?.therapyHistory.orEmpty().forEach { add(it.measuredAtEpochMs) }
            state?.therapyEvents.orEmpty().forEach { add(it.timestampEpochMs) }
            state?.targetHistory.orEmpty().forEach { add(it.startedAtEpochMs) }
        }.asSequence().filter { it <= nowEpochMs }.minOrNull()
    val actualWindowMs = oldestVisibleTimestamp?.let { nowEpochMs - it } ?: 0L
    val requestedWindowMs = requestedHours.coerceIn(1, 24) * HOUR_MS
    return maxOf(actualWindowMs, requestedWindowMs).coerceAtMost(24L * HOUR_MS)
}

internal fun resolveOverviewGraphHoursPreference(preferences: SharedPreferences, durationHours: Int): Int {
    val normalized = durationHours.takeIf { it in OVERVIEW_GRAPH_HOUR_OPTIONS } ?: 3
    if (preferences.getBoolean(OVERVIEW_GRAPH_HOURS_MIGRATION, false)) return normalized
    val previousAuto24 =
        preferences.getBoolean("graphHoursDefault24MigratedV4", false) && preferences.getInt("graphHours", normalized) == 24
    val resolved =
        when {
            !preferences.contains("graphHours") -> 3
            previousAuto24 -> 3
            else -> normalized
        }
    preferences.edit {
        putInt("graphHours", resolved)
        putBoolean(OVERVIEW_GRAPH_HOURS_MIGRATION, true)
    }
    return resolved
}

internal fun graphCenterBeforeDivider(dividerX: Float, radiusPx: Float, outlineWidthPx: Float, safetyPx: Float): Float = dividerX

internal fun graphCenterAfterDivider(dividerX: Float, radiusPx: Float, outlineWidthPx: Float, safetyPx: Float): Float = dividerX

internal data class GraphViewportSnapshot(
    val startEpochMs: Long,
    val liveEdgeEpochMs: Long,
    val endEpochMs: Long,
) {
    val durationMs: Long get() = endEpochMs - startEpochMs
    val visibleHours: Float get() = durationMs.toFloat() / HOUR_MS
}

internal data class GraphViewportSavedState(
    val historyHours: Float,
    val navigationEndEpochMs: Long?,
)
