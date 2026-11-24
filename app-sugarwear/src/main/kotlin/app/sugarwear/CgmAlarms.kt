package app.sugarwear

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.wear.ongoing.OngoingActivity
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import kotlinx.serialization.json.Json

internal object CgmAlarmSettingsStore {
    fun read(context: Context): SugarWearAlarmSettings = SugarWearAlarmMigration.migrateIfNeeded(context)

    fun readActive(context: Context, now: Long = System.currentTimeMillis()): SugarWearAlarmSettings =
        read(context).let { it.forProfile(it.activeProfile(now).profile) }

    fun write(
        context: Context,
        settings: SugarWearAlarmSettings,
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        val previous = read(context)
        val now = System.currentTimeMillis()
        SugarWearAlarmMigration.writeSettings(context, settings)
        if (previous.manualProfile != settings.manualProfile || previous.schedule != settings.schedule || previous.scheduleEnabled != settings.scheduleEnabled) {
            CgmAlarmCoordinator.onProfileConfigurationChanged(context, previous.activeProfile(now), settings.activeProfile(now), now)
        }
        CgmSignalLossMonitor.scheduleFromState(context, CgmSensorStateStore(context).read(), now)
    }
}

internal object CgmAlarmCoordinator {
    private const val PREFERENCES = "g7_cgm_alarm_state"
    private const val KEY_STATE = "state_v2"
    private const val KEY_NOTICES = "pending_notices_v2"
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun acknowledgeEpisode(context: Context, expected: SugarWearAlarmInstance): Boolean =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (!matchesEpisode(context, expected.type, cgmAlarmEpisodeKey(expected))) return@synchronized false
            acknowledge(context, expected.type)
            true
        }

    fun matchesEpisode(context: Context, type: SugarWearAlarmType, key: String): Boolean {
        val current = pendingNotices(context).firstOrNull { it.type == type } ?: return false
        val sensor = CgmSensorStateStore(context).read().sensor
        if (sensor != null && (sensor.sensorId != current.sensorId || (sensor.sessionId != null && sensor.sessionId != current.sessionId))) return false
        return cgmAlarmEpisodeKey(current) == key
    }

    fun onProfileConfigurationChanged(
        context: Context,
        previous: ActiveSugarWearAlarmProfile,
        next: ActiveSugarWearAlarmProfile,
        now: Long,
    ) {
        val snapshot = read(context)
        val updated = snapshot.withActiveProfile(previous, now).withActiveProfile(next, now, configurationChanged = true)
        if (updated != snapshot) save(context, updated)
    }

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
        val dismissesAudibleAlarm = CgmAlarmArbitrator.select(snapshot.alarms)?.type == type
        val next =
            snapshot.alarms.map { alarm ->
                if (alarm.type == type && (alarm.state == SugarWearAlarmState.ACTIVE || alarm.state == SugarWearAlarmState.PENDING_DELAY)) {
                    val rule = CgmAlarmSettingsStore.readActive(context, nowEpochMs).ruleFor(type)
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
        if (dismissesAudibleAlarm) context.getSystemService(android.os.Vibrator::class.java)?.cancel()
        CgmAlarmRepeatScheduler.cancel(context, type)
    }

    fun isActive(context: Context, type: SugarWearAlarmType): Boolean =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            read(context).alarms.any { it.type == type && it.state == SugarWearAlarmState.ACTIVE }
        }

    fun pendingNotices(context: Context): List<SugarWearAlarmInstance> =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val saved =
                preferences
                    .getString(KEY_NOTICES, null)
                    ?.let {
                        runCatching { json.decodeFromString<List<SugarWearAlarmInstance>>(it) }.getOrNull()
                    }.orEmpty()
            val activeList = read(context).alarms.filter { it.state == SugarWearAlarmState.ACTIVE }
            val list = (saved + activeList).distinctBy { it.type }.sortedByDescending { SugarWearAlarmDefaults.priority(it.type) }
            list.filterNot { CgmAlarmPauseStore.isPaused(context, it.type) }
        }

    fun isVisible(context: Context, type: SugarWearAlarmType): Boolean = pendingNotices(context).any { it.type == type }

    private fun saveNotices(context: Context, notices: List<SugarWearAlarmInstance>) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_NOTICES, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SugarWearAlarmInstance.serializer()), notices.filterNot { it.type.isSensorLifetime }))
        }
    }

    private fun dismissNotice(context: Context, type: SugarWearAlarmType) =
        saveNotices(context, pendingNotices(context).filterNot { it.type == type })

    fun refreshNotification(context: Context, type: SugarWearAlarmType) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (CgmAlarmPauseStore.isPaused(context, type)) return@synchronized
            val alarm = read(context).alarms.firstOrNull { it.type == type && it.state == SugarWearAlarmState.ACTIVE } ?: return@synchronized
            val settings = CgmAlarmSettingsStore.readActive(context)
            if (settings.ruleFor(type).enabled) CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = true)
        }

    fun snooze(context: Context, type: SugarWearAlarmType, now: Long = System.currentTimeMillis()) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val snapshot = read(context)
            dismissNotice(context, type)
            val until = now + CgmAlarmDelivery.snoozeMinutes(context, type, now) * 60_000L
            save(
                context,
                snapshot.copy(
                    alarms =
                        snapshot.alarms.map {
                            if (it.type == type) it.copy(state = SugarWearAlarmState.SNOOZED, snoozedUntilEpochMs = until) else it
                        },
                ),
            )
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
        val (snapshot, settings) = readRuntime(context, nowEpochMs)
        val alarm = snapshot.alarms.firstOrNull { it.type == type } ?: return@synchronized
        val rule = settings.ruleFor(type)
        if (!rule.enabled) {
            CgmAlarmNotifier.cancel(context, type)
            CgmAlarmRepeatScheduler.cancel(context, type)
            return@synchronized
        }
        if (alarm.state == SugarWearAlarmState.SNOOZED && alarm.snoozedUntilEpochMs?.let { nowEpochMs >= it } == true) {
            evaluate(context, reading = CgmSensorStateStore(context).read().lastReading, nowEpochMs = nowEpochMs)
            return@synchronized
        }
        if (alarm.state != SugarWearAlarmState.ACTIVE || !rule.snoozeEnabled) return@synchronized
        if (type.usesGlucoseReading && CgmSensorStateStore(context).read().lastReading?.isFreshAlarmReading(nowEpochMs) != true) {
            evaluate(context, nowEpochMs = nowEpochMs)
            return@synchronized
        }
        CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = false)
        val updated =
            snapshot.alarms.map {
                if (it.type == type) it.copy(lastNotifiedAtEpochMs = nowEpochMs) else it
            }
        save(context, snapshot.copy(alarms = updated))
        CgmAlarmRepeatScheduler.schedule(context, type, rule.snoozeMinutes)
    }

    fun clearSuppressed(context: Context) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val notices = pendingNotices(context)
            val snapshot = read(context)
            save(context, snapshot.copy(sensorId = null, sessionId = null, alarms = snapshot.alarms.filter { it.type.isSensorLifetime }, sensorErrorSinceEpochMs = null))
            saveNotices(context, notices)
            CgmAlarmNotifier.cancelAll(context)
            CgmAlarmRepeatScheduler.cancelAll(context)
        }

    fun clearForSensorRemoval(context: Context) {
        clearSuppressed(context)
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.clearSynchronously("SugarWear alarm runtime cleanup failed")
    }

    fun restore(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(CgmSensorStateStore.lifecycleLock) {
        if (!CgmAlertPolicyStore.alarmsEnabled(context)) {
            clearSuppressed(context)
            return@synchronized
        }
        val (snapshot, settings) = readRuntime(context, nowEpochMs)
        SugarWearAlarmType.entries.forEach { type ->
            if (!settings.ruleFor(type).enabled || CgmAlarmPauseStore.isPaused(context, type, nowEpochMs)) {
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
                    val intervalMs = rule.snoozeMinutes * 60_000L
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
        val (previous, settings) = readRuntime(context, nowEpochMs)
        val stateStore = CgmSensorStateStore(context).read()
        val currentReading = reading ?: stateStore.lastReading

        val sameSession =
            currentReading == null ||
                previous.alarms.isEmpty() ||
                (previous.sensorId == currentReading.sensorId && previous.sessionId == currentReading.sessionId)
        if (!sameSession) {
            SugarWearAlarmType.entries.filterNot { it.isSensorLifetime }.forEach { CgmAlarmNotifier.cancel(context, it) }
            CgmAlarmRepeatScheduler.cancelAll(context)
        }

        val previousMap =
            if (sameSession) {
                previous.alarms.associateBy(SugarWearAlarmInstance::type).mapValues { (_, alarm) ->
                    if (alarm.state == SugarWearAlarmState.SNOOZED && alarm.snoozedUntilEpochMs?.let { nowEpochMs >= it } == true) {
                        alarm.copy(state = SugarWearAlarmState.INACTIVE)
                    } else {
                        alarm
                    }
                }
            } else {
                emptyMap()
            }

        val lastReadingAt = currentReading?.timestampEpochMs
        val signalLossDuration =
            if (lastReadingAt != null && lastReadingAt > 0) {
                (nowEpochMs - lastReadingAt) / 60_000L
            } else {
                0L
            }

        // Fresh error packets prove radio contact, but do not restart a continuous sensor fault.
        // Keep this anchor in the existing session-bound alarm snapshot across process restarts.
        val sensorErrorSince =
            if (currentReading?.status == CgmReadingStatus.SENSOR_ERROR) {
                (previous.sensorErrorSinceEpochMs.takeIf { sameSession } ?: lastReadingAt ?: nowEpochMs).coerceAtMost(nowEpochMs)
            } else {
                null
            }
        val sensorErrorDuration = sensorErrorSince?.let { (nowEpochMs - it).coerceAtLeast(0L) / 60_000L } ?: 0L

        val result =
            SugarWearAlarmEngine.evaluate(
                reading = currentReading,
                technicalError = technicalError,
                signalLossDurationMinutes = signalLossDuration,
                sensorErrorDurationMinutes = sensorErrorDuration,
                previousState = previousMap,
                settings = settings,
                nowEpochMs = nowEpochMs,
                evaluateGlucose = previous.acceptsGlucoseFrom(currentReading?.timestampEpochMs),
            )

        val withNotifications = result.updatedInstances.toMutableMap()
        val notices = pendingNotices(context).associateBy { it.type }.toMutableMap()

        SugarWearAlarmType.entries.forEach { type ->
            if (type.isSensorLifetime) return@forEach
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
                        CgmAlarmRepeatScheduler.schedule(context, type, rule.snoozeMinutes)
                    }
                }
                current?.state == SugarWearAlarmState.RESOLVED -> {
                    CgmAlarmDelivery.reset(context, type)
                    CgmAlarmNotifier.cancel(context, type)
                    CgmAlarmRepeatScheduler.cancel(context, type)
                }
                current?.state == SugarWearAlarmState.ACTIVE -> {
                    if (settings.ruleFor(type).enabled) {
                        CgmAlarmNotifier.show(context, current, settings, onlyAlertOnce = true, reading = currentReading)
                    } else {
                        CgmAlarmNotifier.cancel(context, type)
                        CgmAlarmRepeatScheduler.cancel(context, type)
                    }
                }
            }
        }

        save(
            context,
            SugarWearAlarmSnapshot(
                sensorId = currentReading?.sensorId ?: previous.sensorId,
                sessionId = currentReading?.sessionId ?: previous.sessionId,
                alarms = (withNotifications.values + previous.alarms.filter { it.type.isSensorLifetime }).sortedBy { SugarWearAlarmDefaults.priority(it.type) },
                sensorErrorSinceEpochMs = sensorErrorSince,
                activeProfile = previous.activeProfile,
                glucoseProfileStartedAtEpochMs = previous.glucoseProfileStartedAtEpochMs,
            ),
        )
        saveNotices(context, notices.values.toList())
    }

    /** Reconciles persisted episodes and returns the next verified sensor deadline. */
    fun reconcileSensorLifetime(context: Context, now: Long): Long? =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            val state = CgmSensorStateStore(context).read()
            val sensor = state.sensor
            val session = sensor?.sessionId
            val snapshot = read(context)
            val previous = snapshot.alarms.filter { it.type.isSensorLifetime }
            val current = previous.filter { sensor != null && session != null && it.sensorId == sensor.sensorId && it.sessionId == session }
            val lifetime = cgmSensorLifetime(state, now)
            val end = lifetime?.regularEndEpochMs
            val graceEnd = lifetime?.verifiedGraceEndEpochMs
            val deadlines = if (end != null && graceEnd != null && state.collectorEnabled && session != null) {
                listOf(SugarWearAlarmType.SENSOR_END_24H to end - 86_400_000L, SugarWearAlarmType.SENSOR_GRACE_START to end)
            } else {
                emptyList()
            }
            val enabled = CgmAlertPolicyStore.alarmsEnabled(context, now)
            val additions = deadlines.mapNotNull { (type, deadline) ->
                if (!enabled || now < deadline || current.any { it.type == type }) return@mapNotNull null
                SugarWearAlarmInstance(
                    type = type,
                    state = SugarWearAlarmState.ACTIVE,
                    sensorId = sensor?.sensorId,
                    sessionId = session,
                    conditionFirstMetAtEpochMs = deadline,
                    triggeredAtEpochMs = deadline,
                    lastNotifiedAtEpochMs = now,
                )
            }
            if (current != previous || additions.isNotEmpty()) {
                save(context, snapshot.copy(alarms = snapshot.alarms.filterNot { it.type.isSensorLifetime } + current + additions))
                previous.filterNot { it in current }.forEach { CgmAlarmNotifier.cancel(context, it.type) }
                if (additions.isNotEmpty()) {
                    val settings = CgmAlarmSettingsStore.readActive(context, now)
                    additions.forEach { CgmAlarmNotifier.show(context, it, settings, onlyAlertOnce = false) }
                }
            }
            deadlines.filter { (type, deadline) -> deadline > now && current.none { it.type == type } }
                .minOfOrNull { it.second }
        }

    /** Additional deadlines for the existing monitor, independent of the overview's 16-minute label. */
    fun nextTimedEvaluationAt(context: Context, state: app.sugarlicious.sensor.protocol.CgmPersistedState, now: Long): Long? =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (!state.collectorEnabled || !CgmAlertPolicyStore.alarmsEnabled(context, now)) return@synchronized null
            val reading = state.lastReading ?: return@synchronized null
            val snapshot = read(context)
            val sameSession = snapshot.sensorId == reading.sensorId && snapshot.sessionId == reading.sessionId
            val configured = CgmAlarmSettingsStore.read(context)
            val settings = configured.forProfile(configured.activeProfile(now).profile)
            val alarmDeadline =
                listOf(SugarWearAlarmType.SIGNAL_LOSS, SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM)
                    .mapNotNull { type ->
                        val rule = settings.ruleFor(type)
                        if (!rule.enabled || CgmAlarmPauseStore.isPaused(context, type, now)) return@mapNotNull null
                        val alarm = snapshot.alarms.firstOrNull { it.type == type }.takeIf { sameSession }
                        if (alarm?.state in setOf(SugarWearAlarmState.ACTIVE, SugarWearAlarmState.ACKNOWLEDGED, SugarWearAlarmState.SNOOZED)) return@mapNotNull null
                        val since =
                            when (type) {
                                SugarWearAlarmType.SIGNAL_LOSS -> reading.timestampEpochMs
                                SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> {
                                    if (reading.status != CgmReadingStatus.SENSOR_ERROR) return@mapNotNull null
                                    snapshot.sensorErrorSinceEpochMs.takeIf { sameSession } ?: reading.timestampEpochMs
                                }
                                else -> return@mapNotNull null
                            }
                        since + rule.durationMinutes * 60_000L
                    }.minOrNull()
            val profileDeadline = configured.schedule.takeIf { configured.scheduleEnabled }?.nextChangeAfter(now)
            listOfNotNull(alarmDeadline, profileDeadline).minOrNull()
        }

    private fun readRuntime(context: Context, now: Long): Pair<SugarWearAlarmSnapshot, SugarWearAlarmSettings> {
        val configured = CgmAlarmSettingsStore.read(context)
        val selection = configured.activeProfile(now)
        val previous = read(context)
        val updated = previous.withActiveProfile(selection, now)
        if (updated != previous) save(context, updated)
        return updated to configured.forProfile(selection.profile)
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

    private fun notificationId(type: SugarWearAlarmType): Int =
        when (type) {
            SugarWearAlarmType.URGENT_LOW -> 7101
            SugarWearAlarmType.URGENT_LOW_SOON -> 7102
            SugarWearAlarmType.LOW -> 7103
            SugarWearAlarmType.HIGH -> 7104
            SugarWearAlarmType.RAPID_RISE -> 7105
            SugarWearAlarmType.RAPID_FALL -> 7106
            SugarWearAlarmType.SIGNAL_LOSS -> 7107
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> 7108
            SugarWearAlarmType.SENSOR_END_24H -> 7110
            SugarWearAlarmType.SENSOR_GRACE_START -> 7111
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
        val episode = cgmAlarmEpisodeKey(alarm)
        val channelsReady =
            try {
                ensureAllChannels(context, settings)
                true
            } catch (error: RuntimeException) {
                android.util.Log.e("CgmAlarmNotifier", "Cannot prepare alarm channels", error)
                false
            }
        val destination = if (alarm.type.isSensorLifetime) {
            Intent(context, CgmWatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else {
            CgmAlarmActivity.intent(context, alarm.type)
        }
        val open =
            PendingIntent.getActivity(
                context,
                notificationId(alarm.type),
                destination
                    .putExtra("episode", episode)
                    .setData("sugarwear:alarm/${android.net.Uri.encode(episode)}".toUri()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val presentationReading =
            (reading ?: CgmSensorStateStore(context).read().lastReading)?.takeIf {
                (alarm.sensorId == null || alarm.sensorId == it.sensorId) && (alarm.sessionId == null || alarm.sessionId == it.sessionId)
            }
        if (!onlyAlertOnce) {
            try {
                playAlarm(context, alarm.type, settings)
            } catch (error: RuntimeException) {
                android.util.Log.e("CgmAlarmNotifier", "Alarm audio or vibration failed", error)
            }
        }
        if (!channelsReady) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        val delivery =
            cgmAlarmNotificationReadiness(
                permissionGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                appNotificationsEnabled = notifications.areNotificationsEnabled(),
                channelImportance = notifications.getNotificationChannel(channelId)?.importance,
            )
        if (!delivery.canSubmit) {
            if (!onlyAlertOnce) android.util.Log.w("CgmAlarmNotifier", "Wear alarm notification blocked: ${delivery.name}")
            return
        }
        if (delivery != CgmAlarmNotificationReadiness.READY && !onlyAlertOnce) {
            android.util.Log.w("CgmAlarmNotifier", "Wear alarm not prominent: ${delivery.name}")
        }
        val icon = IconCompat.createWithResource(context, app.sugarlicious.uishared.R.drawable.ic_alert)
        val body =
            when {
                alarm.type.isSensorLifetime -> requireNotNull(alarm.type.lifetimeDescription)
                alarm.type == SugarWearAlarmType.TECHNICAL_WARNING -> "Bitte den Systemstatus prüfen."
                alarm.triggerGlucoseMgDl != null -> "${kotlin.math.round(alarm.triggerGlucoseMgDl).toInt()} mg/dL"
                else -> cgmAlarmValue(presentationReading)
            }
        // Supported Wear OS path: a high-importance notification with a one-tap
        // route to the existing full-bleed Activity. Wear does not support FSI.
        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(if (alarm.type == SugarWearAlarmType.SENSOR_END_24H) R.drawable.ic_sensorend else R.drawable.ic_cgm_notification)
                .setColor(color(context, alarm.type))
                .setContentTitle(cgmAlarmHeadline(alarm.type))
                .setContentText(if (alarm.type.isSensorLifetime) body else audioAwareBody(context, body, settings))
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(onlyAlertOnce)
        if (alarm.type.isSensorLifetime) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .addAction(NotificationCompat.Action.Builder(icon, "OK", CgmAlarmDelivery.pending(context, alarm.type, "ack", episode)).build())
        } else {
            builder.addAction(NotificationCompat.Action.Builder(icon, "Schlummerzeit · ${CgmAlarmDelivery.snoozeMinutes(context, alarm.type)} Min.", open).build())
                .addAction(NotificationCompat.Action.Builder(icon, "Stumm", CgmAlarmDelivery.pending(context, alarm.type, "mute", episode)).build())
                .addAction(NotificationCompat.Action.Builder(icon, "Vibrieren", CgmAlarmDelivery.pending(context, alarm.type, "vibrate", episode)).build())
        }
        // The system-owned ongoing chip is reachable on the watch face/launcher.
        // Its lifecycle is tied to this episode's notification (ACK/snooze/cancel).
        // OngoingActivity is optional UI. Its failure cannot suppress the
        // underlying notification or corrupt the persisted alarm episode.
        try {
            OngoingActivity
                .Builder(context, notificationId(alarm.type), builder)
                .setStaticIcon(R.drawable.ic_cgm_notification)
                .setTouchIntent(open)
                .build()
                .apply(context)
        } catch (error: RuntimeException) {
            android.util.Log.w("CgmAlarmNotifier", "Wear ongoing indicator unavailable", error)
        }
        try {
            notifications.notify(notificationId(alarm.type), builder.build())
        } catch (error: RuntimeException) {
            android.util.Log.e("CgmAlarmNotifier", "Alarm notification submission failed", error)
        }
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
            Notification
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_cgm_notification)
                .setColor(color(context, type))
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
        val settings = CgmAlarmSettingsStore.readActive(context)
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
        val allowVibration = !settings.globalMuteAll && mode != "mute"
        if (allowVibration && (settings.globalVibrateOnly || rule.deliveryMode == SugarWearAlertDeliveryMode.VIBRATE || mode == "vibrate")) {
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
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, cgmAlarmTitle(type), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Eigenständiger SugarWear-Alarm: ${cgmAlarmTitle(type)}"
                enableVibration(false)
                val alarmAudio =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                setSound(null, alarmAudio)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun color(context: Context, type: SugarWearAlarmType): Int =
        CgmAppearanceStore(context).load().argb(
            if (type in setOf(SugarWearAlarmType.HIGH, SugarWearAlarmType.RAPID_RISE)) CgmAppearanceRole.GLUCOSE_HIGH else CgmAppearanceRole.GLUCOSE_LOW,
        )

    internal fun channelId(
        context: Context,
        type: SugarWearAlarmType,
        settings: SugarWearAlarmSettings,
    ): String =
        buildString {
            append(CHANNEL_PREFIX)
            append(type.id)
            append("_m")
            append(
                if (settings.globalMuteAll) {
                    '0'
                } else if (settings.globalVibrateOnly) {
                    'v'
                } else {
                    '1'
                },
            )
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
        SugarWearAlarmType.SENSOR_END_24H, SugarWearAlarmType.SENSOR_GRACE_START -> "Systemhinweis"
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
            val episode = intent.getStringExtra("episode") ?: return
            val pending = goAsync()
            CgmReceiverWork
                .dispatcher { app, _ -> CgmAlarmDelivery.perform(app, type, operation, episode) }
                .dispatch(context, intent.action, onFinished = pending::finish)
            return
        }
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { SugarWearAlarmType.valueOf(it) }.getOrNull() } ?: return
        val episode = intent.getStringExtra("episode") ?: return
        CgmAlarmDelivery.perform(context, type, "ack", episode)
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
