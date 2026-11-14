package app.sugarwear

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
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import kotlinx.serialization.json.Json

internal object CgmAlarmSettingsStore {
    fun read(context: Context): SugarWearAlarmSettings = SugarWearAlarmMigration.migrateIfNeeded(context)

    fun write(
        context: Context,
        settings: SugarWearAlarmSettings,
    ) {
        SugarWearAlarmMigration.writeSettings(context, settings)
    }
}

internal object CgmAlarmCoordinator {
    private const val PREFERENCES = "g7_cgm_alarm_state"
    private const val KEY_STATE = "state_v2"
    private const val KEY_NOTICES = "pending_notices_v2"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun onReading(
        context: Context,
        reading: CgmReading,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        if (reading.status == CgmReadingStatus.INVALID) return@synchronized
        evaluate(context, reading = reading, nowEpochMs = nowEpochMs)
    }

    fun onSignalLoss(
        context: Context,
        reading: CgmReading?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        evaluate(context, reading = reading, nowEpochMs = nowEpochMs)
    }

    fun onTechnicalError(
        context: Context,
        error: CgmCollectorError,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        evaluate(context, technicalError = error, nowEpochMs = nowEpochMs)
    }

    fun acknowledge(
        context: Context,
        type: SugarWearAlarmType,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        dismissNotice(context, type)
        val snapshot = read(context)
        val next = snapshot.alarms.map { alarm ->
            if (alarm.type == type && (alarm.state == SugarWearAlarmState.ACTIVE || alarm.state == SugarWearAlarmState.PENDING_DELAY)) {
                val rule = CgmAlarmSettingsStore.read(context).ruleFor(type)
                val snoozeUntil = if (rule.snoozeEnabled && rule.snoozeMinutes > 0) nowEpochMs + rule.snoozeMinutes * 60_000L else null
                alarm.copy(
                    state = if (snoozeUntil != null) SugarWearAlarmState.SNOOZED else SugarWearAlarmState.ACKNOWLEDGED,
                    acknowledgedAtEpochMs = nowEpochMs,
                    snoozedUntilEpochMs = snoozeUntil,
                )
            } else {
                alarm
            }
        }
        save(context, snapshot.copy(alarms = next))
        CgmAlarmNotifier.cancel(context, type)
        CgmAlarmRepeatScheduler.cancel(context, type)
    }

    fun isActive(context: Context, type: SugarWearAlarmType): Boolean =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            read(context).alarms.any { it.type == type && it.state == SugarWearAlarmState.ACTIVE }
        }

    fun pendingNotices(context: Context): List<SugarWearAlarmInstance> =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val saved = preferences.getString(KEY_NOTICES, null)?.let {
                runCatching { json.decodeFromString<List<SugarWearAlarmInstance>>(it) }.getOrNull()
            }.orEmpty()
            val activeList = read(context).alarms.filter { it.state == SugarWearAlarmState.ACTIVE }
            val list = (saved + activeList).distinctBy { it.type }.sortedByDescending { SugarWearAlarmDefaults.priority(it.type) }
            list.filterNot { CgmAlarmPauseStore.isPaused(context, it.type) }
        }

    fun isVisible(context: Context, type: SugarWearAlarmType): Boolean = pendingNotices(context).any { it.type == type }

    private fun saveNotices(context: Context, notices: List<SugarWearAlarmInstance>) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_NOTICES, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SugarWearAlarmInstance.serializer()), notices))
        }
    }

    private fun dismissNotice(context: Context, type: SugarWearAlarmType) =
        saveNotices(context, pendingNotices(context).filterNot { it.type == type })

    fun refreshNotification(context: Context, type: SugarWearAlarmType) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (CgmAlarmPauseStore.isPaused(context, type)) return@synchronized
            val alarm = read(context).alarms.firstOrNull { it.type == type && it.state == SugarWearAlarmState.ACTIVE } ?: return@synchronized
            CgmAlarmNotifier.show(context, alarm, CgmAlarmSettingsStore.read(context), onlyAlertOnce = true)
        }

    fun snooze(context: Context, type: SugarWearAlarmType, now: Long = System.currentTimeMillis()) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val snapshot = read(context)
            dismissNotice(context, type)
            val rule = CgmAlarmSettingsStore.read(context).ruleFor(type)
            val until = now + CgmAlarmDelivery.snoozeMinutes(context, type) * 60_000L
            save(context, snapshot.copy(alarms = snapshot.alarms.map {
                if (it.type == type) it.copy(state = SugarWearAlarmState.SNOOZED, snoozedUntilEpochMs = until) else it
            }))
            CgmAlarmNotifier.cancel(context, type)
            CgmAlarmRepeatScheduler.scheduleAt(context, type, until)
        }

    fun repeatActive(
        context: Context,
        type: SugarWearAlarmType,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context) || CgmAlarmPauseStore.isPaused(context, type, nowEpochMs)) {
            CgmAlarmNotifier.cancel(context, type)
            CgmAlarmRepeatScheduler.cancel(context, type)
            return@synchronized
        }
        val settings = CgmAlarmSettingsStore.read(context)
        val snapshot = read(context)
        val alarm = snapshot.alarms.firstOrNull { it.type == type } ?: return@synchronized
        if (alarm.state == SugarWearAlarmState.SNOOZED && alarm.snoozedUntilEpochMs?.let { nowEpochMs >= it } == true) {
            evaluate(context, reading = CgmSensorStateStore(context).read().lastReading, nowEpochMs = nowEpochMs)
            return@synchronized
        }
        if (alarm.state != SugarWearAlarmState.ACTIVE && alarm.state != SugarWearAlarmState.SNOOZED) return@synchronized
        CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = false)
        val updated = snapshot.alarms.map {
            if (it.type == type) it.copy(lastNotifiedAtEpochMs = nowEpochMs) else it
        }
        save(context, snapshot.copy(alarms = updated))
        val rule = settings.ruleFor(type)
        val repeatMinutes = CgmAlarmDelivery.snoozeMinutes(context, type).takeIf { it > 0 } ?: rule.snoozeMinutes
        CgmAlarmRepeatScheduler.schedule(context, type, repeatMinutes)
    }

    fun clearSuppressed(context: Context) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val notices = pendingNotices(context)
            save(context, SugarWearAlarmSnapshot())
            saveNotices(context, notices)
            CgmAlarmNotifier.cancelAll(context)
            CgmAlarmRepeatScheduler.cancelAll(context)
        }

    fun clearForSensorRemoval(context: Context) {
        clearSuppressed(context)
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        check(preferences.edit().clear().commit()) { "SugarWear alarm runtime cleanup failed" }
    }

    fun restore(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        val settings = CgmAlarmSettingsStore.read(context)
        val snapshot = read(context)
        SugarWearAlarmType.entries.forEach { type ->
            if (CgmAlarmPauseStore.isPaused(context, type, nowEpochMs)) {
                CgmAlarmNotifier.cancel(context, type)
                CgmAlarmRepeatScheduler.cancel(context, type)
                return@forEach
            }
            val alarm = snapshot.alarms.firstOrNull { it.type == type }
            if (alarm?.state == SugarWearAlarmState.SNOOZED) {
                alarm.snoozedUntilEpochMs?.let { CgmAlarmRepeatScheduler.scheduleAt(context, type, it) }
            } else if (alarm?.state == SugarWearAlarmState.ACTIVE) {
                CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = true)
                val rule = settings.ruleFor(type)
                if (rule.snoozeEnabled) {
                    val repeatMinutes = CgmAlarmDelivery.snoozeMinutes(context, type).takeIf { it > 0 } ?: rule.snoozeMinutes
                    val intervalMs = repeatMinutes * 60_000L
                    val nextRepeat = (alarm.lastNotifiedAtEpochMs ?: alarm.triggeredAtEpochMs ?: nowEpochMs) + intervalMs
                    CgmAlarmRepeatScheduler.scheduleAt(context, type, maxOf(nextRepeat, nowEpochMs + 1_000L))
                } else {
                    CgmAlarmRepeatScheduler.cancel(context, type)
                }
            } else {
                CgmAlarmNotifier.cancel(context, type)
                CgmAlarmRepeatScheduler.cancel(context, type)
            }
        }
    }

    private fun evaluate(
        context: Context,
        reading: CgmReading? = null,
        technicalError: CgmCollectorError? = null,
        nowEpochMs: Long,
    ) {
        val settings = CgmAlarmSettingsStore.read(context)
        val previous = read(context)
        val stateStore = CgmSensorStateStore(context).read()
        val currentReading = reading ?: stateStore.lastReading

        val sameSession = currentReading == null ||
            previous.alarms.isEmpty() ||
            (previous.sensorId == currentReading.sensorId && previous.sessionId == currentReading.sessionId)
        if (!sameSession) {
            CgmAlarmNotifier.cancelAll(context)
            CgmAlarmRepeatScheduler.cancelAll(context)
        }

        val previousMap = if (sameSession) {
            previous.alarms.associateBy(SugarWearAlarmInstance::type).mapValues { (_, alarm) ->
                if (alarm.state == SugarWearAlarmState.SNOOZED && alarm.snoozedUntilEpochMs?.let { nowEpochMs >= it } == true) {
                    alarm.copy(state = SugarWearAlarmState.INACTIVE)
                } else alarm
            }
        } else emptyMap()

        val lastReadingAt = currentReading?.timestampEpochMs
        val signalLossDuration = if (lastReadingAt != null && lastReadingAt > 0) {
            (nowEpochMs - lastReadingAt) / 60_000L
        } else 0L

        val sensorErrorDuration = if (currentReading?.status == CgmReadingStatus.SENSOR_ERROR && lastReadingAt != null) {
            (nowEpochMs - lastReadingAt) / 60_000L
        } else 0L

        val result = SugarWearAlarmEngine.evaluate(
            reading = currentReading,
            technicalError = technicalError,
            signalLossDurationMinutes = signalLossDuration,
            sensorErrorDurationMinutes = sensorErrorDuration,
            previousState = previousMap,
            settings = settings,
            nowEpochMs = nowEpochMs,
        )

        val withNotifications = result.updatedInstances.toMutableMap()
        val notices = pendingNotices(context).associateBy { it.type }.toMutableMap()

        SugarWearAlarmType.entries.forEach { type ->
            val old = previousMap[type]
            val current = result.updatedInstances[type]
            val isPaused = CgmAlarmPauseStore.isPaused(context, type, nowEpochMs)
            when {
                isPaused -> {
                    dismissNotice(context, type)
                    CgmAlarmDelivery.reset(context, type)
                    CgmAlarmNotifier.cancel(context, type)
                    CgmAlarmRepeatScheduler.cancel(context, type)
                    if (old != null) {
                        withNotifications[type] = old.copy(state = SugarWearAlarmState.INACTIVE)
                    } else {
                        withNotifications.remove(type)
                    }
                }
                current?.state == SugarWearAlarmState.ACTIVE && old?.state != SugarWearAlarmState.ACTIVE -> {
                    notices[type] = current
                    CgmAlarmDelivery.reset(context, type)
                    CgmAlarmNotifier.show(context, current, settings, onlyAlertOnce = false, reading = currentReading)
                    withNotifications[type] = current.copy(lastNotifiedAtEpochMs = nowEpochMs)
                    val rule = settings.ruleFor(type)
                    if (rule.snoozeEnabled) {
                        val repeatMinutes = CgmAlarmDelivery.snoozeMinutes(context, type).takeIf { it > 0 } ?: rule.snoozeMinutes
                        CgmAlarmRepeatScheduler.schedule(context, type, repeatMinutes)
                    }
                }
                current?.state == SugarWearAlarmState.RESOLVED -> {
                    CgmAlarmDelivery.reset(context, type)
                    CgmAlarmNotifier.cancel(context, type)
                    CgmAlarmRepeatScheduler.cancel(context, type)
                }
                current?.state == SugarWearAlarmState.ACTIVE -> {
                    CgmAlarmNotifier.show(context, current, settings, onlyAlertOnce = true, reading = currentReading)
                }
            }
        }

        save(
            context,
            SugarWearAlarmSnapshot(
                sensorId = currentReading?.sensorId ?: previous.sensorId,
                sessionId = currentReading?.sessionId ?: previous.sessionId,
                alarms = withNotifications.values.sortedBy { SugarWearAlarmDefaults.priority(it.type) },
            ),
        )
        saveNotices(context, notices.values.toList())
    }

    private fun read(context: Context): SugarWearAlarmSnapshot =
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString<SugarWearAlarmSnapshot>(it) }.getOrNull() }
            ?: SugarWearAlarmSnapshot()

    private fun save(
        context: Context,
        snapshot: SugarWearAlarmSnapshot,
    ) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, json.encodeToString(SugarWearAlarmSnapshot.serializer(), snapshot))
        }
    }
}

internal object CgmAlarmNotifier {
    private const val CHANNEL_PREFIX = "sugarwear_alarm_v9_"
    private const val CHANNEL_FAMILY_PREFIX = "sugarwear_alarm_"

    private fun notificationId(type: SugarWearAlarmType): Int = when (type) {
        SugarWearAlarmType.URGENT_LOW -> 7101
        SugarWearAlarmType.URGENT_LOW_SOON -> 7102
        SugarWearAlarmType.LOW -> 7103
        SugarWearAlarmType.HIGH -> 7104
        SugarWearAlarmType.RAPID_RISE -> 7105
        SugarWearAlarmType.RAPID_FALL -> 7106
        SugarWearAlarmType.SIGNAL_LOSS -> 7107
        SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> 7108
        SugarWearAlarmType.TECHNICAL_WARNING -> 7109
    }

    fun show(
        context: Context,
        alarm: SugarWearAlarmInstance,
        settings: SugarWearAlarmSettings,
        onlyAlertOnce: Boolean,
        reading: CgmReading? = null,
    ) {
        val channelId = channelId(context, alarm.type, settings)
        ensureAllChannels(context, settings)
        val open = PendingIntent.getActivity(
            context,
            notificationId(alarm.type),
            CgmAlarmActivity.intent(context, alarm.type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!onlyAlertOnce) playAlarm(context, alarm.type, settings)
        val icon = Icon.createWithResource(context, app.sugarlicious.uishared.R.drawable.ic_alert)
        context.getSystemService(NotificationManager::class.java).notify(
            notificationId(alarm.type),
            Notification.Builder(context, channelId)
                .setSmallIcon(app.sugarlicious.uishared.R.drawable.ic_alert)
                .setColor(color(alarm.type))
                .setContentTitle(cgmAlarmHeadline(alarm.type))
                .setContentText(audioAwareBody(context, reading?.let { cgmAlarmValue(it) } ?: cgmAlarmReadingText(context), settings))
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(if (onlyAlertOnce) null else open, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(onlyAlertOnce)
                .addAction(Notification.Action.Builder(icon, "Schlummerzeit · ${CgmAlarmDelivery.snoozeMinutes(context, alarm.type)} Min.", open).build())
                .addAction(Notification.Action.Builder(icon, "Stumm", CgmAlarmDelivery.pending(context, alarm.type, "mute")).build())
                .addAction(Notification.Action.Builder(icon, "Vibrieren", CgmAlarmDelivery.pending(context, alarm.type, "vibrate")).build())
                .build(),
        )
    }

    fun cancel(
        context: Context,
        type: SugarWearAlarmType,
    ) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(type))
        CgmAlarmAudioService.stop(context, type)
    }

    fun cancelAll(context: Context) = SugarWearAlarmType.entries.forEach { cancel(context, it) }

    fun showTest(
        context: Context,
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
    ) {
        val channelId = channelId(context, type, settings)
        ensureAllChannels(context, settings)
        playAlarm(context, type, settings, test = true)
        context.getSystemService(NotificationManager::class.java).notify(
            testNotificationId(type),
            Notification.Builder(context, channelId)
                .setSmallIcon(app.sugarlicious.uishared.R.drawable.ic_alert)
                .setColor(color(type))
                .setContentTitle("Test · ${cgmAlarmTitle(type)}")
                .setContentText(audioAwareBody(context, "Sound, Vibration und Nicht-stören-Verhalten dieses Alarms werden geprüft.", settings))
                .setCategory(Notification.CATEGORY_ALARM)
                .setSilent(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(10_000L)
                .build(),
        )
    }

    private fun audioAwareBody(context: Context, body: String, settings: SugarWearAlarmSettings): String {
        val warning = if (!settings.globalMuteAll) cgmAlarmAudioWarning(CgmAlarmSystemAccess.snapshot(context)) else null
        return if (warning == null) body else "$warning $body"
    }

    fun prepareCurrentChannels(context: Context) {
        CgmAlarmVolume.recoverExpired(context)
        val settings = CgmAlarmSettingsStore.read(context)
        SugarWearAlarmType.entries.forEach { type -> ensureChannel(context, channelId(context, type, settings), type, settings) }
    }

    fun ensureAllChannels(
        context: Context,
        settings: SugarWearAlarmSettings,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val desiredIds = SugarWearAlarmType.entries.associateWith { channelId(context, it, settings) }
        desiredIds.forEach { (type, id) -> ensureChannel(context, id, type, settings) }
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_FAMILY_PREFIX) && it.id !in desiredIds.values }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    private fun playAlarm(context: Context, type: SugarWearAlarmType, settings: SugarWearAlarmSettings, test: Boolean = false) {
        val mode = if (test) null else CgmAlarmDelivery.mode(context, type)
        val rule = settings.ruleFor(type)
        val allowSound = !settings.globalMuteAll && !settings.globalVibrateOnly && rule.deliveryMode != SugarWearAlertDeliveryMode.VIBRATE
        if (allowSound && mode == null) CgmAlarmAudioService.play(context, type, test)
        if ((settings.globalVibrateOnly || rule.deliveryMode == SugarWearAlertDeliveryMode.VIBRATE || mode == "vibrate") && mode != "mute") {
            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(
                android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1),
                android.os.VibrationAttributes.Builder().setUsage(android.os.VibrationAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun ensureChannel(
        context: Context,
        channelId: String,
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
    ) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, cgmAlarmTitle(type), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Eigenständiger SugarWear-Alarm: ${cgmAlarmTitle(type)}"
                enableVibration(false)
                val alarmAudio = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(null, alarmAudio)
                setBypassDnd(CgmAlarmSystemAccess.isAccessGranted(context))
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun color(type: SugarWearAlarmType): Int =
        if (type in setOf(SugarWearAlarmType.URGENT_LOW, SugarWearAlarmType.URGENT_LOW_SOON, SugarWearAlarmType.LOW, SugarWearAlarmType.SIGNAL_LOSS, SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM, SugarWearAlarmType.TECHNICAL_WARNING)) {
            0xFFFF5C69.toInt()
        } else {
            0xFFFFD040.toInt()
        }

    internal fun channelId(
        context: Context,
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
    ): String =
        buildString {
            append(CHANNEL_PREFIX)
            append(type.id)
            append("_m")
            append(if (settings.globalMuteAll) '0' else if (settings.globalVibrateOnly) 'v' else '1')
            append("_d")
            append(if (CgmAlarmSystemAccess.isAccessGranted(context)) '1' else '0')
        }

    private const val TEST_NOTIFICATION_BASE = 7_300

    internal fun testNotificationId(type: SugarWearAlarmType): Int = TEST_NOTIFICATION_BASE + notificationId(type)
}

internal fun cgmAlarmSoundAsset(type: SugarWearAlarmType): String = SugarWearAlarmDefaults.audioAssetPath(type)

internal fun cgmAlarmTitle(type: SugarWearAlarmType): String = type.titleGerman

internal fun cgmAlarmSoundName(type: SugarWearAlarmType): String =
    when (type) {
        SugarWearAlarmType.URGENT_LOW -> "Urgent Low"
        SugarWearAlarmType.URGENT_LOW_SOON -> "Urgent Low Soon"
        SugarWearAlarmType.LOW -> "Low"
        SugarWearAlarmType.HIGH -> "High Alert"
        SugarWearAlarmType.RAPID_RISE -> "Rise Rate"
        SugarWearAlarmType.RAPID_FALL -> "Fall Rate"
        SugarWearAlarmType.SIGNAL_LOSS -> "Signal Loss"
        SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> "Sensorfehler-Beep"
        SugarWearAlarmType.TECHNICAL_WARNING -> "Beep"
    }

internal object CgmAlarmRepeatScheduler {
    fun schedule(
        context: Context,
        type: SugarWearAlarmType,
        minutes: Int,
    ) {
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(5, 120) * 60_000L
        scheduleAt(context, type, triggerAt)
    }

    fun scheduleAt(
        context: Context,
        type: SugarWearAlarmType,
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
        type: SugarWearAlarmType,
    ) {
        val pending = pendingIntent(context, type)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun cancelAll(context: Context) = SugarWearAlarmType.entries.forEach { cancel(context, it) }

    private fun pendingIntent(
        context: Context,
        type: SugarWearAlarmType,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7_200 + SugarWearAlarmDefaults.priority(type),
            Intent(context, CgmAlarmRepeatReceiver::class.java)
                .setAction(CgmAlarmRepeatReceiver.ACTION_REPEAT)
                .putExtra(CgmAlarmRepeatReceiver.EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class CgmAlarmAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == CgmAlarmDelivery.ACTION) {
            val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { SugarWearAlarmType.valueOf(it) }.getOrNull() } ?: return
            val operation = intent.getStringExtra("operation") ?: return
            val pending = goAsync()
            CgmReceiverWork
                .dispatcher { app, _ -> CgmAlarmDelivery.perform(app, type, operation) }
                .dispatch(context, intent.action, onFinished = pending::finish)
            return
        }
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { SugarWearAlarmType.valueOf(it) }.getOrNull() } ?: return
        CgmAlarmCoordinator.acknowledge(context, type)
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "app.aapswear.g7watch.ACKNOWLEDGE_CGM_ALARM"
        const val EXTRA_TYPE = "alarm_type"
    }
}

class CgmAlarmRepeatReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_REPEAT) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { SugarWearAlarmType.valueOf(it) }.getOrNull() } ?: return
        CgmAlarmCoordinator.repeatActive(context, type)
    }

    companion object {
        const val ACTION_REPEAT = "app.aapswear.g7watch.REPEAT_CGM_ALARM"
        const val EXTRA_TYPE = "alarm_type"
    }
}
