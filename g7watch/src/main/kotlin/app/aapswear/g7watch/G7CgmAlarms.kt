package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import androidx.core.content.edit
import app.aapswear.g7.CgmAlarm
import app.aapswear.g7.CgmAlarmEngine
import app.aapswear.g7.CgmAlarmSettings
import app.aapswear.g7.CgmAlarmState
import app.aapswear.g7.CgmAlarmType
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class G7AlarmSnapshot(
    val sensorId: String? = null,
    val sessionId: String? = null,
    val alarms: List<CgmAlarm> = emptyList(),
)

internal object G7AlarmSettingsStore {
    private const val PREFERENCES = "g7_cgm_alarm_settings"

    fun read(context: Context): CgmAlarmSettings {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val thresholds = G7GraphColorStore(context).readThresholds()
        return CgmAlarmSettings(
            veryHighThreshold = thresholds.veryHighMgDl,
            highThreshold = thresholds.highMgDl,
            lowThreshold = thresholds.lowMgDl,
            veryLowThreshold = thresholds.veryLowMgDl,
            rapidRiseThreshold = preferences.getFloat("rapid_rise", 2f).toDouble().coerceIn(0.5, 10.0),
            rapidFallThreshold = preferences.getFloat("rapid_fall", 2f).toDouble().coerceIn(0.5, 10.0),
            signalLossMinutes = 16,
            veryHighEnabled = preferences.getBoolean("very_high_enabled", true),
            highEnabled = preferences.getBoolean("high_enabled", true),
            lowEnabled = preferences.getBoolean("low_enabled", true),
            veryLowEnabled = preferences.getBoolean("very_low_enabled", true),
            rapidRiseEnabled = preferences.getBoolean("rapid_rise_enabled", true),
            rapidFallEnabled = preferences.getBoolean("rapid_fall_enabled", true),
            signalLossEnabled = preferences.getBoolean("signal_loss_enabled", true),
            sensorErrorEnabled = preferences.getBoolean("sensor_error_enabled", true),
            vibrationEnabled = preferences.getBoolean("vibration_enabled", true),
            soundEnabled = preferences.getBoolean("sound_enabled", true),
            repeatEnabled = preferences.getBoolean("repeat_enabled", true),
            repeatIntervalMinutes = preferences.getInt("repeat_minutes", 15).coerceIn(5, 120),
        )
    }

    fun write(
        context: Context,
        settings: CgmAlarmSettings,
    ) {
        require(
            app.aapswear.model
                .CgmThresholds(
                    settings.veryHighThreshold,
                    settings.highThreshold,
                    settings.lowThreshold,
                    settings.veryLowThreshold,
                ).isValid,
        )
        require(settings.signalLossMinutes == 16)
        G7GraphColorStore(context).saveThresholds(
            app.aapswear.model.CgmThresholds(
                settings.veryHighThreshold,
                settings.highThreshold,
                settings.lowThreshold,
                settings.veryLowThreshold,
            ),
        )
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putFloat("very_high", settings.veryHighThreshold.toFloat())
            putFloat("high", settings.highThreshold.toFloat())
            putFloat("low", settings.lowThreshold.toFloat())
            putFloat("rapid_rise", settings.rapidRiseThreshold.toFloat())
            putFloat("rapid_fall", settings.rapidFallThreshold.toFloat())
            putBoolean("very_high_enabled", settings.veryHighEnabled)
            putBoolean("high_enabled", settings.highEnabled)
            putBoolean("low_enabled", settings.lowEnabled)
            putBoolean("very_low_enabled", settings.veryLowEnabled)
            putBoolean("rapid_rise_enabled", settings.rapidRiseEnabled)
            putBoolean("rapid_fall_enabled", settings.rapidFallEnabled)
            putBoolean("signal_loss_enabled", settings.signalLossEnabled)
            putBoolean("sensor_error_enabled", settings.sensorErrorEnabled)
            putBoolean("vibration_enabled", settings.vibrationEnabled)
            putBoolean("sound_enabled", settings.soundEnabled)
            putBoolean("repeat_enabled", settings.repeatEnabled)
            putInt("repeat_minutes", settings.repeatIntervalMinutes.coerceIn(5, 120))
        }
    }
}

internal object G7CgmAlarmCoordinator {
    private const val PREFERENCES = "g7_cgm_alarm_state"
    private const val KEY_STATE = "state_v1"
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun onReading(
        context: Context,
        reading: CgmReading,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!G7AlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        // An invalid packet is diagnostic input only. It must neither trigger glucose alarms nor
        // resolve an already active alarm based on the last validated reading.
        if (reading.status == CgmReadingStatus.INVALID) return@synchronized
        evaluate(context, reading, nowEpochMs)
    }

    fun onSignalLoss(
        context: Context,
        reading: CgmReading?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!G7AlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        evaluate(context, reading, nowEpochMs)
    }

    fun acknowledge(
        context: Context,
        type: CgmAlarmType,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val snapshot = read(context)
        val next =
            snapshot.alarms.map { alarm ->
                if (alarm.type == type && alarm.state == CgmAlarmState.ACTIVE) {
                    CgmAlarmEngine.acknowledge(alarm, nowEpochMs)
                } else {
                    alarm
                }
            }
        save(context, snapshot.copy(alarms = next))
        G7CgmAlarmNotifier.cancel(context, type)
        G7AlarmRepeatScheduler.cancel(context, type)
    }

    fun repeatActive(
        context: Context,
        type: CgmAlarmType,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!G7AlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        val settings = G7AlarmSettingsStore.read(context)
        val snapshot = read(context)
        val alarm = snapshot.alarms.firstOrNull { it.type == type } ?: return@synchronized
        if (!CgmAlarmEngine.shouldRepeat(alarm, settings, nowEpochMs)) return@synchronized
        G7CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = false)
        val updated =
            snapshot.alarms.map {
                if (it.type == type) CgmAlarmEngine.markNotified(it, nowEpochMs) else it
            }
        save(context, snapshot.copy(alarms = updated))
        G7AlarmRepeatScheduler.schedule(context, type, settings.repeatIntervalMinutes)
    }

    fun clearSuppressed(context: Context) {
        save(context, G7AlarmSnapshot())
        G7CgmAlarmNotifier.cancelAll(context)
        G7AlarmRepeatScheduler.cancelAll(context)
    }

    /** Restores persistent alarm visibility and repeat scheduling after reboot/process restart. */
    fun restore(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(G7SensorStateStore.lifecycleLock) {
        if (!G7AlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        val settings = G7AlarmSettingsStore.read(context)
        val snapshot = read(context)
        CgmAlarmType.entries.forEach { type ->
            val alarm = snapshot.alarms.firstOrNull { it.type == type }
            if (alarm?.state == CgmAlarmState.ACTIVE) {
                // Re-create the ongoing card silently; only a new transition or scheduled repeat
                // may play the configured alarm sound again.
                G7CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = true)
                if (settings.repeatEnabled) {
                    val intervalMs = settings.repeatIntervalMinutes * 60_000L
                    val nextRepeat = (alarm.lastNotifiedAtEpochMs ?: alarm.triggeredAtEpochMs) + intervalMs
                    G7AlarmRepeatScheduler.scheduleAt(context, type, maxOf(nextRepeat, nowEpochMs + 1_000L))
                } else {
                    G7AlarmRepeatScheduler.cancel(context, type)
                }
            } else {
                G7CgmAlarmNotifier.cancel(context, type)
                G7AlarmRepeatScheduler.cancel(context, type)
            }
        }
    }

    private fun evaluate(
        context: Context,
        reading: CgmReading?,
        nowEpochMs: Long,
    ) {
        val settings = G7AlarmSettingsStore.read(context)
        val previous = read(context)
        val sameSession =
            reading == null ||
                previous.alarms.isEmpty() ||
                (previous.sensorId == reading.sensorId && previous.sessionId == reading.sessionId)
        if (!sameSession) {
            G7CgmAlarmNotifier.cancelAll(context)
            G7AlarmRepeatScheduler.cancelAll(context)
        }
        val oldMap = if (sameSession) previous.alarms.associateBy(CgmAlarm::type) else emptyMap()
        val normalizedReading =
            reading?.takeIf {
                it.status == CgmReadingStatus.VALID || it.status == CgmReadingStatus.SENSOR_ERROR
            }
        val evaluated = CgmAlarmEngine.evaluate(normalizedReading, oldMap, settings, nowEpochMs)
        val withNotifications = evaluated.toMutableMap()

        CgmAlarmType.entries.forEach { type ->
            val old = oldMap[type]
            val current = evaluated[type]
            when {
                current?.state == CgmAlarmState.ACTIVE && old?.state != CgmAlarmState.ACTIVE -> {
                    G7CgmAlarmNotifier.show(context, current, settings, onlyAlertOnce = false)
                    withNotifications[type] = CgmAlarmEngine.markNotified(current, nowEpochMs)
                    if (settings.repeatEnabled) {
                        G7AlarmRepeatScheduler.schedule(context, type, settings.repeatIntervalMinutes)
                    }
                }
                current?.state == CgmAlarmState.RESOLVED -> {
                    G7CgmAlarmNotifier.cancel(context, type)
                    G7AlarmRepeatScheduler.cancel(context, type)
                }
            }
        }
        save(
            context,
            G7AlarmSnapshot(
                sensorId = reading?.sensorId ?: previous.sensorId,
                sessionId = reading?.sessionId ?: previous.sessionId,
                alarms = withNotifications.values.sortedBy(CgmAlarm::type),
            ),
        )
    }

    private fun read(context: Context): G7AlarmSnapshot =
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString<G7AlarmSnapshot>(it) }.getOrNull() }
            ?: G7AlarmSnapshot()

    private fun save(
        context: Context,
        snapshot: G7AlarmSnapshot,
    ) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, json.encodeToString(G7AlarmSnapshot.serializer(), snapshot))
        }
    }
}

internal object G7CgmAlarmNotifier {
    private const val CHANNEL_PREFIX = "g7_cgm_alarm_v7_"
    private const val CHANNEL_FAMILY_PREFIX = "g7_cgm_alarm_"
    private const val NOTIFICATION_BASE = 7_100

    fun show(
        context: Context,
        alarm: CgmAlarm,
        settings: CgmAlarmSettings,
        onlyAlertOnce: Boolean,
    ) {
        // Channel sound/vibration are immutable after creation. Route a changed app setting to a
        // distinct channel while preserving any system-level customization of an existing one.
        val channelId = channelId(context, alarm.type, settings)
        ensureAllChannels(context, settings)
        val open =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_BASE + alarm.type.ordinal,
                g7OpenAppIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val acknowledge =
            PendingIntent.getBroadcast(
                context,
                NOTIFICATION_BASE + alarm.type.ordinal,
                Intent(context, G7CgmAlarmAcknowledgeReceiver::class.java)
                    .setAction(G7CgmAlarmAcknowledgeReceiver.ACTION_ACKNOWLEDGE)
                    .putExtra(G7CgmAlarmAcknowledgeReceiver.EXTRA_TYPE, alarm.type.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (!onlyAlertOnce) playAlarm(context, alarm.type, settings)
        val icon = Icon.createWithResource(context, R.drawable.ic_g7_notification)
        context.getSystemService(NotificationManager::class.java).notify(
            notificationId(alarm.type),
            Notification
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_g7_notification)
                .setColor(color(alarm.type))
                .setContentTitle(title(alarm.type))
                .setContentText(body(alarm.type))
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_ALARM)
                .setSilent(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(onlyAlertOnce)
                .addAction(Notification.Action.Builder(icon, "Quittieren", acknowledge).build())
                .build(),
        )
    }

    fun cancel(
        context: Context,
        type: CgmAlarmType,
    ) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(type))
        G7AlarmAudioService.stop(context, type)
    }

    fun cancelAll(context: Context) = CgmAlarmType.entries.forEach { cancel(context, it) }

    fun showTest(
        context: Context,
        type: CgmAlarmType,
        settings: CgmAlarmSettings,
    ) {
        val channelId = channelId(context, type, settings)
        ensureAllChannels(context, settings)
        playAlarm(context, type, settings, test = true)
        context.getSystemService(NotificationManager::class.java).notify(
            testNotificationId(type),
            Notification
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_g7_notification)
                .setColor(color(type))
                .setContentTitle("Test · ${title(type)}")
                .setContentText("Sound, Vibration und Nicht-stören-Verhalten dieses Alarms werden geprüft.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setSilent(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(10_000L)
                .build(),
        )
    }

    /** Prepares native settings before the first alarm; never posts, cancels, or prunes channels. */
    fun prepareCurrentChannels(context: Context) {
        G7AlarmVolume.recoverExpired(context)
        val settings = G7AlarmSettingsStore.read(context)
        CgmAlarmType.entries.forEach { type -> ensureChannel(context, channelId(context, type, settings), type, settings) }
    }

    fun ensureAllChannels(
        context: Context,
        settings: CgmAlarmSettings,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val desiredIds = CgmAlarmType.entries.associateWith { channelId(context, it, settings) }
        desiredIds.forEach { (type, id) -> ensureChannel(context, id, type, settings) }
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_FAMILY_PREFIX) && it.id !in desiredIds.values }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    private fun playAlarm(context: Context, type: CgmAlarmType, settings: CgmAlarmSettings, test: Boolean = false) {
        if (settings.soundEnabled) G7AlarmAudioService.play(context, type, test)
        if (settings.vibrationEnabled) {
            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(
                android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1),
                android.os.VibrationAttributes
                    .Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                    .build(),
            )
        }
    }

    private fun ensureChannel(
        context: Context,
        channelId: String,
        type: CgmAlarmType,
        settings: CgmAlarmSettings,
    ) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, title(type), NotificationManager.IMPORTANCE_LOW).apply {
                description = "Eigenständiger SugarWear-Alarm: ${title(type)}"
                enableVibration(settings.vibrationEnabled)
                val alarmAudio =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                setSound(null, alarmAudio)
                setBypassDnd(G7AlarmSystemAccess.isAccessGranted(context))
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun title(type: CgmAlarmType): String =
        when (type) {
            CgmAlarmType.VERY_HIGH -> "Glukose sehr hoch"
            CgmAlarmType.HIGH -> "Glukose hoch"
            CgmAlarmType.LOW -> "Glukose tief"
            CgmAlarmType.VERY_LOW -> "Glukose sehr tief"
            CgmAlarmType.RAPID_RISE -> "Glukose steigt schnell"
            CgmAlarmType.RAPID_FALL -> "Glukose fällt schnell"
            CgmAlarmType.SIGNAL_LOSS -> "Signalverlust"
            CgmAlarmType.SENSOR_ERROR -> "Sensorfehler"
        }

    private fun body(type: CgmAlarmType): String =
        when (type) {
            CgmAlarmType.SIGNAL_LOSS -> "Seit mindestens 16 Minuten kein valider direkter Watch-Wert."
            CgmAlarmType.VERY_LOW -> "Glukosewert liegt bei oder unter 40 mg/dL."
            else -> "SugarWear hat den Alarmzustand ${title(type)} erkannt."
        }

    private fun color(type: CgmAlarmType): Int =
        if (type in setOf(CgmAlarmType.VERY_LOW, CgmAlarmType.LOW, CgmAlarmType.SIGNAL_LOSS, CgmAlarmType.SENSOR_ERROR)) {
            0xFFFF5C69.toInt()
        } else {
            0xFFFFD040.toInt()
        }

    private fun notificationId(type: CgmAlarmType): Int = NOTIFICATION_BASE + type.ordinal

    internal fun channelId(
        context: Context,
        type: CgmAlarmType,
        settings: CgmAlarmSettings,
    ): String =
        buildString {
            append(CHANNEL_PREFIX)
            append(type.name.lowercase())
            append("_s")
            append(if (settings.soundEnabled) '1' else '0')
            append("_v")
            append(if (settings.vibrationEnabled) '1' else '0')
            append("_d")
            append(if (G7AlarmSystemAccess.isAccessGranted(context)) '1' else '0')
        }

    private const val TEST_NOTIFICATION_BASE = 7_300

    internal fun testNotificationId(type: CgmAlarmType): Int = TEST_NOTIFICATION_BASE + type.ordinal
}

internal fun g7AlarmSoundAsset(type: CgmAlarmType): String =
    when (type) {
        CgmAlarmType.VERY_HIGH -> "alarms/alerts_sounds_high_alert.mp4"
        CgmAlarmType.HIGH -> "alarms/alerts_sounds_high.mp4"
        CgmAlarmType.LOW -> "alarms/alerts_sounds_low.mp4"
        CgmAlarmType.VERY_LOW -> "alarms/alerts_sounds_urgent_low_alarm.mp4"
        CgmAlarmType.RAPID_RISE -> "alarms/alerts_sounds_rise_rate.mp4"
        CgmAlarmType.RAPID_FALL -> "alarms/alerts_sounds_fall_rate.mp4"
        CgmAlarmType.SIGNAL_LOSS -> "alarms/alerts_sounds_signal_loss_alert.mp4"
        CgmAlarmType.SENSOR_ERROR -> "alarms/alerts_sounds_beep.mp4"
    }

internal fun g7AlarmTitle(type: CgmAlarmType): String =
    when (type) {
        CgmAlarmType.VERY_HIGH -> "Sehr hoch"
        CgmAlarmType.HIGH -> "Hoch"
        CgmAlarmType.LOW -> "Tief"
        CgmAlarmType.VERY_LOW -> "Sehr tief"
        CgmAlarmType.RAPID_RISE -> "Schnell steigend"
        CgmAlarmType.RAPID_FALL -> "Schnell fallend"
        CgmAlarmType.SIGNAL_LOSS -> "Signalverlust"
        CgmAlarmType.SENSOR_ERROR -> "Sensorfehler"
    }

internal fun g7AlarmSoundName(type: CgmAlarmType): String =
    when (type) {
        CgmAlarmType.VERY_HIGH -> "High Alert"
        CgmAlarmType.HIGH -> "High"
        CgmAlarmType.LOW -> "Low"
        CgmAlarmType.VERY_LOW -> "Urgent Low"
        CgmAlarmType.RAPID_RISE -> "Rise Rate"
        CgmAlarmType.RAPID_FALL -> "Fall Rate"
        CgmAlarmType.SIGNAL_LOSS -> "Signal Loss"
        CgmAlarmType.SENSOR_ERROR -> "Sensorfehler-Beep"
    }

internal object G7AlarmRepeatScheduler {
    fun schedule(
        context: Context,
        type: CgmAlarmType,
        minutes: Int,
    ) {
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(5, 120) * 60_000L
        scheduleAt(context, type, triggerAt)
    }

    fun scheduleAt(
        context: Context,
        type: CgmAlarmType,
        triggerAtEpochMs: Long,
    ) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(triggerAtEpochMs, System.currentTimeMillis() + 1_000L),
            pendingIntent(context, type),
        )
    }

    fun cancel(
        context: Context,
        type: CgmAlarmType,
    ) {
        val pending = pendingIntent(context, type)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun cancelAll(context: Context) = CgmAlarmType.entries.forEach { cancel(context, it) }

    private fun pendingIntent(
        context: Context,
        type: CgmAlarmType,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7_200 + type.ordinal,
            Intent(context, G7CgmAlarmRepeatReceiver::class.java)
                .setAction(G7CgmAlarmRepeatReceiver.ACTION_REPEAT)
                .putExtra(G7CgmAlarmRepeatReceiver.EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class G7CgmAlarmAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { CgmAlarmType.valueOf(it) }.getOrNull() } ?: return
        G7CgmAlarmCoordinator.acknowledge(context, type)
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "app.aapswear.g7watch.ACKNOWLEDGE_CGM_ALARM"
        const val EXTRA_TYPE = "alarm_type"
    }
}

class G7CgmAlarmRepeatReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_REPEAT) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { CgmAlarmType.valueOf(it) }.getOrNull() } ?: return
        G7CgmAlarmCoordinator.repeatActive(context, type)
    }

    companion object {
        const val ACTION_REPEAT = "app.aapswear.g7watch.REPEAT_CGM_ALARM"
        const val EXTRA_TYPE = "alarm_type"
    }
}
