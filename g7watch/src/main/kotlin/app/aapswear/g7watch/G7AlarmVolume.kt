package app.aapswear.g7watch

import android.app.AlarmManager
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

/** Optional, bounded override of the alarm stream. Never changes media volume or DND mode. */
internal object G7AlarmVolume {
    private const val PREFS = "g7_alarm_volume"
    private const val RESTORE_AFTER_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var restoreTask: Runnable? = null

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("override", false)

    fun percent(context: Context): Int = prefs(context).getInt("percent", 80).coerceIn(10, 100)

    fun configure(context: Context, enabled: Boolean, percent: Int = percent(context)) {
        prefs(context).edit {
            putBoolean("override", enabled)
            putInt("percent", percent.coerceIn(10, 100))
        }
        if (!enabled) restore(context)
    }

    @Synchronized
    fun beforeSound(context: Context) {
        recoverExpired(context)
        if (!enabled(context)) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        if (audio.isVolumeFixed) return
        val original = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = (audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * percent(app) / 100f).roundToInt().coerceAtLeast(1)
        val preferences = prefs(app)
        // Persist recovery before changing the stream, including across process death.
        preferences.edit(commit = true) {
            if (!preferences.contains("original") || original != preferences.getInt("applied", -1)) putInt("original", original)
            putInt("applied", target)
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
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }.onFailure {
            restore(app)
            android.util.Log.w("G7AlarmVolume", "Alarm volume override rejected by system", it)
            return
        }
        restoreTask?.let(handler::removeCallbacks)
        restoreTask = Runnable { restore(app) }.also { handler.postDelayed(it, RESTORE_AFTER_MS) }
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
                    android.util.Log.w("G7AlarmVolume", "Alarm volume restoration rejected", it)
                    return
                }
        }
        preferences.edit(commit = true) {
            remove("original")
            remove("applied")
            remove("expires")
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
            Intent(context, G7AlarmVolumeRestoreReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class G7AlarmVolumeRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) G7AlarmVolume.restore(context) else G7AlarmVolume.recoverExpired(context)
    }
}
