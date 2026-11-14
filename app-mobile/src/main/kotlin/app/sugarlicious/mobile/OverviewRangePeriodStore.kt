package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit

internal object OverviewRangePeriodStore {
    val periods = listOf(1, 2, 3, 7, 14, 30, 90)

    private fun preferences(context: Context) = context.getSharedPreferences("overview_range_ui", Context.MODE_PRIVATE)

    fun read(context: Context): Int = preferences(context).getInt("days", 1).takeIf { it in periods } ?: 1

    fun select(context: Context, days: Int): Int {
        require(days in periods)
        preferences(context).edit { putInt("days", days) }
        return days
    }

    fun advance(context: Context, current: Int, available: List<Int> = periods): Int {
        if (available.isEmpty()) return current
        val next = nextAvailableRangePeriod(current, available)
        return select(context, next)
    }
}
