package app.sugarwear

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import kotlin.math.roundToInt

internal enum class CgmAlarmVolumeResult { SYSTEM_VOLUME, APPLIED, SYSTEM_BLOCKED }

/** Bounded alarm/silent override. Media volume and the DND filter are never changed directly. */
internal object CgmAlarmVolume {
    private const val PREFS = "g7_alarm_volume"
    private const val RESTORE_AFTER_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var restoreTask: Runnable? = null

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("override", false)

    fun silentOverrideEnabled(context: Context): Boolean = prefs(context).getBoolean("silent_override", false)

    fun configureSilentOverride(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean("silent_override", enabled) }
        if (!enabled) restore(context)
    }

    fun percent(context: Context): Int = prefs(context).getInt("percent", 80).coerceIn(10, 100)

    fun configure(context: Context, enabled: Boolean, percent: Int = percent(context)) {
        prefs(context).edit {
            putBoolean("override", enabled)
            putInt("percent", percent.coerceIn(10, 100))
        }
        if (!enabled) restore(context)
    }

    @Synchronized
    fun beforeSound(context: Context): CgmAlarmVolumeResult {
        recoverExpired(context)
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        val original = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val preferences = prefs(app)
        val silent = silentOverrideEnabled(app) && (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || original == 0 || audio.isStreamMute(AudioManager.STREAM_ALARM) || preferences.contains("original"))
        if (!enabled(context) && !silent) return CgmAlarmVolumeResult.SYSTEM_VOLUME
        val policy = CgmAlarmSystemAccess.snapshot(app)
        if (audio.isVolumeFixed || policy.dndAllowsAlarmAudio == false) return CgmAlarmVolumeResult.SYSTEM_BLOCKED
        val changeRinger = silent && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL && policy.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val target = (audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * percent(app) / 100f).roundToInt().coerceAtLeast(1)
        // Persist recovery before changing the stream, including across process death.
        preferences.edit(commit = true) {
            if (!preferences.contains("original") || original != preferences.getInt("applied", -1)) putInt("original", original)
            putInt("applied", target)
            if (changeRinger) {
                if (!preferences.contains("original_ringer") || audio.ringerMode != preferences.getInt("applied_ringer", -1)) putInt("original_ringer", audio.ringerMode)
                putInt("applied_ringer", AudioManager.RINGER_MODE_NORMAL)
            }
            putLong("expires", SystemClock.elapsedRealtime() + RESTORE_AFTER_MS)
        }
        val manager = app.getSystemService(AlarmManager::class.java)
        val pending = restoreIntent(app)
        val at = SystemClock.elapsedRealtime() + RESTORE_AFTER_MS
        runCatching {
            if (manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
            if (changeRinger) {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                check(audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) { "System retained silent mode" }
            }
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            check(audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0 && !audio.isStreamMute(AudioManager.STREAM_ALARM)) { "System retained a muted alarm stream" }
        }.onFailure {
            restore(app)
            android.util.Log.w("CgmAlarmVolume", "Alarm volume override rejected by system", it)
            return CgmAlarmVolumeResult.SYSTEM_BLOCKED
        }
        restoreTask?.let(handler::removeCallbacks)
        restoreTask = Runnable { restore(app) }.also { handler.postDelayed(it, RESTORE_AFTER_MS) }
        return CgmAlarmVolumeResult.APPLIED
    }

    @Synchronized
    fun recoverExpired(context: Context) {
        val expiry = prefs(context).getLong("expires", 0L)
        val now = SystemClock.elapsedRealtime()
        if (expiry != 0L && (now >= expiry || expiry - now > RESTORE_AFTER_MS)) restore(context)
    }

    @Synchronized
    fun restore(context: Context) {
        val preferences = prefs(context)
        if (!preferences.contains("original")) return
        val audio = context.getSystemService(AudioManager::class.java)
        // A manual volume change during the alarm always wins over restoration.
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == preferences.getInt("applied", -1)) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, preferences.getInt("original", 0), 0) }
                .onFailure {
                    android.util.Log.w("CgmAlarmVolume", "Alarm volume restoration rejected", it)
                    return
                }
        }
        if (preferences.contains("original_ringer") && audio.ringerMode == preferences.getInt("applied_ringer", -1)) {
            try {
                audio.ringerMode = preferences.getInt("original_ringer", AudioManager.RINGER_MODE_SILENT)
            } catch (error: SecurityException) {
                android.util.Log.w("CgmAlarmVolume", "Silent mode restoration rejected", error)
                return
            }
        }
        preferences.edit(commit = true) {
            remove("original")
            remove("applied")
            remove("expires")
            remove("original_ringer")
            remove("applied_ringer")
        }
        context.getSystemService(AlarmManager::class.java).cancel(restoreIntent(context))
        restoreTask?.let(handler::removeCallbacks)
        restoreTask = null
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun restoreIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7_450,
            Intent(context, CgmAlarmVolumeRestoreReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class CgmAlarmVolumeRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CgmAlarmVolume.restore(context) else CgmAlarmVolume.recoverExpired(context)
    }
}
