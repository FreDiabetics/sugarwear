package app.aapswear.g7watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import app.aapswear.g7.CgmAlarmType

/** Plays bundled alarms directly: Wear's notification UI substitutes its own notification chime. */
class G7AlarmAudioService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var activeType: CgmAlarmType? = null
    private var activeTest = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type")?.let { runCatching { CgmAlarmType.valueOf(it) }.getOrNull() }
        if (type == null || !G7AlarmSettingsStore.read(this).soundEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alarmwiedergabe", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        startForeground(
            7_490,
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_g7_notification)
                .setContentTitle("SugarWear · Alarmton")
                .setContentText(g7AlarmTitle(type))
                .setSilent(true)
                .build(),
        )
        val test = intent.getBooleanExtra("test", false)
        val current = activeType
        if (current != null && !activeTest && (test || priority(type) < priority(current))) return START_NOT_STICKY
        releasePlayer()
        activeType = type
        playingType = type
        activeTest = test
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val audio = getSystemService(AudioManager::class.java)
        focus =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
                .also { audio.requestAudioFocus(it) }
        runCatching {
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            assets.openFd(g7AlarmSoundAsset(type)).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            next.setOnCompletionListener { if (player === it) stopSelf() }
            next.setOnErrorListener { _, what, extra ->
                android.util.Log.e("G7AlarmAudio", "Alarm playback failed: $what/$extra")
                stopSelf()
                true
            }
            next.prepare()
            G7AlarmVolume.beforeSound(this)
            next.start()
            android.util.Log.i("G7AlarmAudio", "Playing bundled ${type.name} test=$test")
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, 60_000L)
        }.onFailure {
            android.util.Log.e("G7AlarmAudio", "Cannot play bundled alarm", it)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        G7AlarmVolume.restore(this)
        playingType = null
        super.onDestroy()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        private const val CHANNEL = "g7_alarm_playback"
        private var playingType: CgmAlarmType? = null

        fun play(context: Context, type: CgmAlarmType, test: Boolean = false) {
            runCatching { context.startForegroundService(Intent(context, G7AlarmAudioService::class.java).putExtra("type", type.name).putExtra("test", test)) }
                .onFailure { android.util.Log.e("G7AlarmAudio", "Alarm service start rejected", it) }
        }

        fun stop(context: Context, type: CgmAlarmType? = null) {
            if (type == null || playingType == type) context.stopService(Intent(context, G7AlarmAudioService::class.java))
        }

        private fun priority(type: CgmAlarmType): Int =
            when (type) {
                CgmAlarmType.VERY_LOW -> 100
                CgmAlarmType.LOW -> 90
                CgmAlarmType.SENSOR_ERROR -> 80
                CgmAlarmType.SIGNAL_LOSS -> 70
                CgmAlarmType.RAPID_FALL -> 60
                CgmAlarmType.RAPID_RISE -> 50
                CgmAlarmType.VERY_HIGH -> 40
                CgmAlarmType.HIGH -> 30
            }
    }
}
