package app.sugarwear

import android.content.Context
import androidx.core.content.edit

internal object CgmAlarmPauseStore {
    private const val PREFS = "g7_cgm_alarm_pause"
    private const val KEY_PAUSE_UNTIL = "paused_until_epoch_ms"
    private const val KEY_LAST_DURATION = "last_pause_duration_minutes"

    fun pausedUntil(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Long? {
        val until = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_PAUSE_UNTIL, 0L)
        return if (until > nowEpochMs) until else null
    }

    fun lastPauseDurationMinutes(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LAST_DURATION, 60).coerceIn(15, 720)

    fun isPaused(
        context: Context,
        type: SugarWearAlarmType,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        // URGENT_LOW and TECHNICAL_WARNING must NEVER be paused!
        if (type == SugarWearAlarmType.URGENT_LOW || type == SugarWearAlarmType.TECHNICAL_WARNING) return false
        return pausedUntil(context, nowEpochMs) != null
    }

    fun setPause(
        context: Context,
        durationMinutes: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val clamped = durationMinutes.coerceIn(5, 720)
        val until = nowEpochMs + clamped * 60_000L
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_PAUSE_UNTIL, until)
            putInt(KEY_LAST_DURATION, clamped)
        }
    }

    fun clearPause(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_PAUSE_UNTIL)
        }
    }
}
