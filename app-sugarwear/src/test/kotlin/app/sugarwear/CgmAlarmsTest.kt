package app.sugarwear

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmAlarmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }

    @Suppress("DEPRECATION")
    @Test
    fun `alarm routes bundled sound to own service without system notification chime`() {
        val app = shadowOf(context as android.app.Application)
        while (app.nextStartedService != null) { /* drain prior requests */ }
        val settings = CgmAlarmSettingsStore.read(context)
        CgmAlarmNotifier.showTest(context, SugarWearAlarmType.URGENT_LOW, settings)
        val service = app.nextStartedService
        assertEquals(CgmAlarmAudioService::class.java.name, service.component?.className)
        assertEquals(SugarWearAlarmType.URGENT_LOW.name, service.getStringExtra("type"))
        assertTrue(service.getBooleanExtra("test", false))
        val notification = shadowOf(notificationManager).getNotification(CgmAlarmNotifier.testNotificationId(SugarWearAlarmType.URGENT_LOW))
        assertNull(notification.sound)
        assertNull(notificationManager.getNotificationChannel(notification.channelId).sound)
    }

    @Test
    fun `every collector alarm keeps its dedicated bundled asset`() {
        val expected =
            mapOf(
                SugarWearAlarmType.URGENT_LOW to "alarms/alerts_sounds_urgent_low_alarm.mp4",
                SugarWearAlarmType.URGENT_LOW_SOON to "alarms/alerts_sounds_urgent_low_alarm.mp4",
                SugarWearAlarmType.LOW to "alarms/alerts_sounds_low.mp4",
                SugarWearAlarmType.HIGH to "alarms/high_alert.mp4",
                SugarWearAlarmType.RAPID_RISE to "alarms/alerts_sounds_rise_rate.mp4",
                SugarWearAlarmType.RAPID_FALL to "alarms/alerts_sounds_fall_rate.mp4",
                SugarWearAlarmType.SIGNAL_LOSS to "alarms/alerts_sounds_signal_loss_alert.mp4",
                SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM to "alarms/alerts_sounds_beep.mp4",
                SugarWearAlarmType.TECHNICAL_WARNING to "alarms/alerts_sounds_beep.mp4",
            )
        assertEquals(expected, SugarWearAlarmType.entries.associateWith(::cgmAlarmSoundAsset))
        expected.values.forEach { asset ->
            context.assets.openFd(asset).use { descriptor -> assertTrue(descriptor.length > 0L) }
        }
    }

    @Test
    fun `settings preparation registers channels without posting or changing alarm preferences`() {
        val settings = CgmAlarmSettingsStore.read(context)

        CgmAlarmNotifier.prepareCurrentChannels(context)

        val channelIds = SugarWearAlarmType.entries.map { CgmAlarmNotifier.channelId(context, it, settings) }
        channelIds.forEach { id ->
            val channel = notificationManager.getNotificationChannel(id)
            assertNotNull(channel)
            assertFalse(channel.shouldVibrate())
            assertNull(channel.sound)
        }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `legacy collector error channel stays silent to prevent duplicate technical sounds`() {
        CgmErrorNotifier.ensureChannel(context)
        val channel = notificationManager.getNotificationChannel(CgmErrorNotifier.channelId(context))
        assertNotNull(channel)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun `all nine alarm channels are silent because the alarm service owns sound`() {
        val settings = CgmAlarmSettingsStore.read(context)
        CgmAlarmNotifier.ensureAllChannels(context, settings)

        SugarWearAlarmType.entries.forEach { type ->
            val channel = notificationManager.getNotificationChannel(CgmAlarmNotifier.channelId(context, type, settings))
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
            assertFalse(channel.shouldVibrate())
            assertNull(channel.sound)
            assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        }
        assertEquals(9, notificationManager.notificationChannels.count { it.id.startsWith("sugarwear_alarm_v9_") })
    }

    @Test
    fun `alarm sound provider exposes every bundled sound to the notification service`() {
        SugarWearAlarmType.entries.forEach { type ->
            context.contentResolver.openAssetFileDescriptor(cgmAlarmSoundUri(context, type), "r")!!.use {
                assertTrue(it.length > 0L)
            }
        }
    }

    @Test
    fun `test alarm uses dedicated notification without mutating real alarm state`() {
        val statePreferences = context.getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE)
        val before = statePreferences.all.toMap()
        val settings = CgmAlarmSettingsStore.read(context)

        CgmAlarmNotifier.showTest(context, SugarWearAlarmType.RAPID_FALL, settings)

        assertNotNull(shadowOf(notificationManager).getNotification(CgmAlarmNotifier.testNotificationId(SugarWearAlarmType.RAPID_FALL)))
        assertEquals(before, statePreferences.all.toMap())
    }

    @Before
    fun setUp() {
        CgmSensorStateStore(context).save(
            app.sugarlicious.sensor.protocol
                .CgmPersistedState(collectorEnabled = true),
        )
        context
            .getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmAlertPolicyStore.setPolicy(context, true)
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        CgmAlarmCoordinator.clearSuppressed(context)
        CgmAlertPolicyStore.setPolicy(context, false)
    }

    @Test
    fun `new and restored alarm use ongoing Wear notification without unsupported fullscreen intent`() {
        val settings = CgmAlarmSettingsStore.read(context).copy(globalMuteAll = true)
        val alarm =
            SugarWearAlarmInstance(
                type = SugarWearAlarmType.LOW,
                state = SugarWearAlarmState.ACTIVE,
                triggeredAtEpochMs = NOW,
            )

        CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = false)
        val fresh = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        assertNotNull(fresh)
        assertNull(fresh.fullScreenIntent)
        assertNotNull(fresh.contentIntent)
        assertEquals(android.app.Notification.CATEGORY_ALARM, fresh.category)
        assertTrue(fresh.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)

        CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = true)
        val restored = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        assertNotNull(restored)
        assertNull(restored.fullScreenIntent)
        assertNotNull(restored.contentIntent)
    }


    @Test
    fun `denied notification permission retains the alarm for restoration`() {
        val application = context as android.app.Application
        val shadow = shadowOf(application)
        shadow.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        try {
            CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
            assertTrue(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
            assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))

            shadow.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            CgmAlarmCoordinator.restore(context, NOW + 1_000L)
            assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        } finally {
            shadow.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun `active notification is restored after process restart`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        notificationManager.cancel(LOW_NOTIFICATION_ID)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))

        CgmAlarmCoordinator.restore(context, NOW + 1_000L)

        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `sensor session change cancels alarms from the previous sensor`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))

        CgmAlarmCoordinator.onReading(
            context,
            reading("sensor-b", "session-b", 110.0),
            NOW + 5 * 60_000L,
        )

        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `invalid packet does not resolve an alarm from the last valid reading`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)

        CgmAlarmCoordinator.onReading(
            context,
            reading("sensor-a", "session-a", 0.0).copy(status = CgmReadingStatus.INVALID),
            NOW + 5 * 60_000L,
        )

        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `urgent low threshold is fixed at 55 mg dl`() {
        val rule = CgmAlarmSettingsStore.read(context).ruleFor(SugarWearAlarmType.URGENT_LOW)
        assertEquals(55.0, rule.thresholdMgDl, 0.0)
        assertTrue(rule.enabled)
    }

    @Test
    fun `automatic suppression expires when the canonical Mobile timeout is reached`() {
        CgmAlertPolicyStore.setPolicy(context, false, NOW + 1_000L)

        assertEquals(false, CgmAlertPolicyStore.alarmsEnabled(context, NOW))
        assertEquals(NOW + 1_000L, CgmAlertPolicyStore.nextAutomaticEnableAt(context, NOW))
        assertEquals(true, CgmAlertPolicyStore.alarmsEnabled(context, NOW + 1_000L))
        assertNull(CgmAlertPolicyStore.nextAutomaticEnableAt(context, NOW + 1_000L))
    }

    @Test
    fun `enabling Watch alarms evaluates the reading that caused source failover`() {
        val transitionReading =
            reading("sensor-a", "session-a", 65.0).copy(
                timestampEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
            )
        CgmAlertPolicyStore.setPolicy(context, false)
        CgmSensorStateStore(context).save(
            CgmPersistedState(
                sensor = CgmSensor(sensorId = "sensor-a", sessionId = "session-a"),
                collectorEnabled = true,
                lastReading = transitionReading,
            ),
        )

        applySourceControl(
            context = context,
            cgmSelected = false,
            alarmsEnabled = true,
            automaticEnableAt = null,
        )

        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `source failover does not alarm from a manually paused collector`() {
        val transitionReading =
            reading("sensor-a", "session-a", 65.0).copy(
                timestampEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
            )
        CgmSensorStateStore(context).save(
            CgmPersistedState(
                sensor = CgmSensor(sensorId = "sensor-a", sessionId = "session-a"),
                collectorEnabled = false,
                lastReading = transitionReading,
            ),
        )
        applySourceControl(context, cgmSelected = true, alarmsEnabled = true, automaticEnableAt = null)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        assertEquals(false, CgmSensorStateStore(context).read().collectorEnabled)
    }

    @Test
    fun `local alarm opt in survives a mobile source suppression signal`() {
        CgmAlertPolicyStore.setLocalEnabled(context, true)
        CgmAlertPolicyStore.setPolicy(context, false)
        assertTrue(CgmAlertPolicyStore.alarmsEnabled(context))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        val notification = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        assertNull(notification.fullScreenIntent)
        assertNotNull(notification.contentIntent)
        assertEquals(listOf("Schlummerzeit · 10 Min.", "Stumm", "Vibrieren"), notification.actions.map { it.title.toString() })
        CgmSensorStateStore(context).save(CgmSensorStateStore(context).read().copy(collectorEnabled = false))
        assertFalse(CgmAlertPolicyStore.alarmsEnabled(context))
    }

    @Test
    fun `snoozed low alarm resumes after snooze interval with a fresh reading`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmCoordinator.snooze(context, SugarWearAlarmType.LOW, NOW)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        val next = reading("sensor-a", "session-a", 65.0).copy(timestampEpochMs = NOW + 9 * 60_000L)
        CgmAlarmCoordinator.onReading(context, next, NOW + 9 * 60_000L)
        assertFalse(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
        CgmAlarmCoordinator.onReading(context, next.copy(timestampEpochMs = NOW + 60 * 60_000L), NOW + 60 * 60_000L)
        assertTrue(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `muting one alarm keeps its notification and does not suppress another alarm type`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.perform(context, SugarWearAlarmType.LOW, "mute")
        assertEquals("mute", CgmAlarmDelivery.mode(context, SugarWearAlarmType.LOW))
        assertNull(CgmAlarmDelivery.mode(context, SugarWearAlarmType.HIGH))
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertNull(CgmAlarmDelivery.mode(context, SugarWearAlarmType.LOW))
    }

    @Test
    fun `new notification actions refer to the episode being posted`() {
        val low = SugarWearAlarmType.LOW
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        val episode = cgmAlarmEpisodeKey(CgmAlarmCoordinator.pendingNotices(context).single { it.type == low })
        val notification = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        for (action in notification.actions.drop(1)) {
            assertEquals(episode, shadowOf(action.actionIntent).savedIntent.getStringExtra("episode"))
        }
    }

    @Test
    fun `all muted mode can display a notification with neither sound nor vibration`() {
        val type = SugarWearAlarmType.LOW
        val app = shadowOf(context as android.app.Application)
        while (app.nextStartedService != null) { /* drain prior requests */ }
        val settings = CgmAlarmSettingsStore.read(context).copy(globalMuteAll = true, globalVibrateOnly = true)
        CgmAlarmNotifier.showTest(context, type, settings)
        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
        assertFalse(shadowOf(vibrator).isVibrating)
        assertNull(app.nextStartedService)
        assertNotNull(shadowOf(notificationManager).getNotification(CgmAlarmNotifier.testNotificationId(type)))
    }

    @Test
    fun `selected snooze duration updates notification and controls actual wake time`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.setSnoozeMinutes(context, SugarWearAlarmType.LOW, 20)
        val notification = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        assertEquals(
            "Schlummerzeit · 20 Min.",
            notification.actions
                .first()
                .title
                .toString(),
        )
        assertEquals(notification.contentIntent, notification.actions.first().actionIntent)
        CgmAlarmCoordinator.snooze(context, SugarWearAlarmType.LOW, NOW)
        CgmAlarmDelivery.reset(context, SugarWearAlarmType.LOW)
        assertEquals(20, CgmAlarmDelivery.snoozeMinutes(context, SugarWearAlarmType.LOW))
        assertEquals(10, CgmAlarmDelivery.snoozeMinutes(context, SugarWearAlarmType.HIGH))
    }

    @Test
    fun `snooze duration stays within selectable limits`() {
        CgmAlarmDelivery.setSnoozeMinutes(context, SugarWearAlarmType.LOW, -5)
        assertEquals(5, CgmAlarmDelivery.snoozeMinutes(context, SugarWearAlarmType.LOW))
        CgmAlarmDelivery.setSnoozeMinutes(context, SugarWearAlarmType.LOW, 200)
        assertEquals(120, CgmAlarmDelivery.snoozeMinutes(context, SugarWearAlarmType.LOW))
    }

    @Test
    fun `unacknowledged notice survives recovery until explicitly acknowledged`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertFalse(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
        assertTrue(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
        CgmAlarmCoordinator.restore(context, NOW)
        assertTrue(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
        CgmAlarmDelivery.perform(context, SugarWearAlarmType.LOW, "ack")
        assertFalse(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertFalse(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
    }

    @Test
    fun `muting preserves the overview notice while snoozing removes it`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.perform(context, SugarWearAlarmType.LOW, "mute")
        assertTrue(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
        CgmAlarmCoordinator.snooze(context, SugarWearAlarmType.LOW, NOW)
        assertFalse(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))
    }

    @Test
    fun `pausing alarms suppresses non-URGENT_LOW alarms but NEVER suppresses URGENT_LOW alarm`() {
        CgmAlarmPauseStore.setPause(context, 60, NOW)
        assertTrue(CgmAlarmPauseStore.isPaused(context, SugarWearAlarmType.HIGH, NOW))
        assertTrue(CgmAlarmPauseStore.isPaused(context, SugarWearAlarmType.LOW, NOW))
        assertFalse(CgmAlarmPauseStore.isPaused(context, SugarWearAlarmType.URGENT_LOW, NOW))

        // Trigger LOW alarm (65 mg/dL) while pause is active -> suppressed
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertFalse(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
        assertFalse(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.LOW))

        // Trigger URGENT_LOW alarm (50 mg/dL) while pause is active -> MUST NOT BE SUPPRESSED!
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 50.0), NOW)
        assertTrue(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.URGENT_LOW))
        assertTrue(CgmAlarmCoordinator.isVisible(context, SugarWearAlarmType.URGENT_LOW))

        // Clear pause -> normal operation restored for LOW
        CgmAlarmPauseStore.clearPause(context)
        assertFalse(CgmAlarmPauseStore.isPaused(context, SugarWearAlarmType.LOW, NOW))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertTrue(CgmAlarmCoordinator.isActive(context, SugarWearAlarmType.LOW))
    }

    @Test
    fun `stale acknowledgement cannot dismiss a new episode of the same type`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        val old = CgmAlarmCoordinator.pendingNotices(context).first { it.type == SugarWearAlarmType.LOW }
        val staleAction = CgmAlarmDelivery.pending(context, old.type, "ack", cgmAlarmEpisodeKey(old))
        CgmAlarmCoordinator.onReading(context, reading("sensor-b", "session-b", 65.0), NOW + 1)
        assertFalse(CgmAlarmCoordinator.acknowledgeEpisode(context, old))
        CgmAlarmDelivery.perform(context, old.type, "ack", cgmAlarmEpisodeKey(old))
        assertTrue(CgmAlarmCoordinator.isVisible(context, old.type))
        val current = CgmAlarmCoordinator.pendingNotices(context).first { it.type == old.type }
        val newAction = CgmAlarmDelivery.pending(context, current.type, "ack", cgmAlarmEpisodeKey(current))
        assertFalse(staleAction == newAction)
        assertTrue(CgmAlarmCoordinator.acknowledgeEpisode(context, current))
        assertFalse(CgmAlarmCoordinator.isVisible(context, old.type))
    }

    @Test
    fun `acknowledging alarm preserves sensor state and reading`() {
        val value = reading("sensor-a", "session-a", 65.0)
        CgmSensorStateStore(context).save(CgmPersistedState(sensor = CgmSensor(value.sensorId, value.sessionId), collectorEnabled = true, lastReading = value))
        CgmAlarmCoordinator.onReading(context, value, NOW)
        val before = CgmSensorStateStore(context).read()
        val notice = CgmAlarmCoordinator.pendingNotices(context).first { it.type == SugarWearAlarmType.LOW }
        assertTrue(CgmAlarmCoordinator.acknowledgeEpisode(context, notice))
        assertEquals(before, CgmSensorStateStore(context).read())
    }

    @Test
    fun `acknowledging an older lower priority notice preserves urgent vibration`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        val low = CgmAlarmCoordinator.pendingNotices(context).first { it.type == SugarWearAlarmType.LOW }
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 40.0), NOW + 1)
        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(1_000L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        assertTrue(CgmAlarmCoordinator.acknowledgeEpisode(context, low))
        assertTrue(shadowOf(vibrator).isVibrating)
        CgmAlarmCoordinator.acknowledge(context, SugarWearAlarmType.URGENT_LOW, NOW + 2)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    private fun reading(
        sensorId: String,
        sessionId: String,
        glucose: Double,
    ) = CgmReading(
        id = "$sensorId-$sessionId-$glucose",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = sensorId,
        sessionId = sessionId,
        glucoseMgDl = glucose,
        timestampEpochMs = NOW,
        receivedAtEpochMs = NOW,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val LOW_NOTIFICATION_ID = 7103
    }
}
