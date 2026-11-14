package app.sugarwear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmAlarmSettings
import app.sugarlicious.sensor.protocol.CgmAlarmType
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
        val settings = CgmAlarmSettingsStore.read(context).copy(soundEnabled = true)
        CgmAlarmNotifier.showTest(context, CgmAlarmType.VERY_LOW, settings)
        val service = app.nextStartedService
        assertEquals(CgmAlarmAudioService::class.java.name, service.component?.className)
        assertEquals(CgmAlarmType.VERY_LOW.name, service.getStringExtra("type"))
        assertTrue(service.getBooleanExtra("test", false))
        val notification = shadowOf(notificationManager).getNotification(CgmAlarmNotifier.testNotificationId(CgmAlarmType.VERY_LOW))
        assertNull(notification.sound)
        assertNull(notificationManager.getNotificationChannel(notification.channelId).sound)
    }

    @Test
    fun `every collector alarm keeps its dedicated bundled asset`() {
        val expected =
            mapOf(
                CgmAlarmType.VERY_HIGH to "alarms/alerts_sounds_high_alert.mp4",
                CgmAlarmType.HIGH to "alarms/alerts_sounds_high.mp4",
                CgmAlarmType.LOW to "alarms/alerts_sounds_low.mp4",
                CgmAlarmType.VERY_LOW to "alarms/alerts_sounds_urgent_low_alarm.mp4",
                CgmAlarmType.RAPID_RISE to "alarms/alerts_sounds_rise_rate.mp4",
                CgmAlarmType.RAPID_FALL to "alarms/alerts_sounds_fall_rate.mp4",
                CgmAlarmType.SIGNAL_LOSS to "alarms/alerts_sounds_signal_loss_alert.mp4",
                CgmAlarmType.SENSOR_ERROR to "alarms/alerts_sounds_beep.mp4",
            )
        assertEquals(expected, CgmAlarmType.entries.associateWith(::cgmAlarmSoundAsset))
        expected.values.forEach { asset ->
            context.assets.openFd(asset).use { descriptor -> assertTrue(descriptor.length > 0L) }
        }
    }

    @Test
    fun `settings preparation registers channels without posting or changing alarm preferences`() {
        val settings = CgmAlarmSettingsStore.read(context).copy(soundEnabled = false, vibrationEnabled = false)
        CgmAlarmSettingsStore.write(context, settings)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)

        CgmAlarmNotifier.prepareCurrentChannels(context)

        val deniedIds = CgmAlarmType.entries.map { CgmAlarmNotifier.channelId(context, it, settings) }
        deniedIds.forEach { id ->
            val channel = notificationManager.getNotificationChannel(id)
            assertNotNull(channel)
            assertFalse(channel.canBypassDnd())
            assertFalse(channel.shouldVibrate())
            assertNull(channel.sound)
        }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(settings, CgmAlarmSettingsStore.read(context))

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        CgmAlarmNotifier.prepareCurrentChannels(context)

        CgmAlarmType.entries.forEach { type ->
            val channel = notificationManager.getNotificationChannel(CgmAlarmNotifier.channelId(context, type, settings))
            assertTrue(channel.canBypassDnd())
        }
        deniedIds.forEach { assertNotNull(notificationManager.getNotificationChannel(it)) }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(settings, CgmAlarmSettingsStore.read(context))
    }

    @Test
    fun `all eight alarm channels are silent because the alarm service owns sound`() {
        val settings = CgmAlarmSettingsStore.read(context)
        CgmAlarmNotifier.ensureAllChannels(context, settings)

        CgmAlarmType.entries.forEach { type ->
            val channel = notificationManager.getNotificationChannel(CgmAlarmNotifier.channelId(context, type, settings))
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
            assertFalse(channel.shouldVibrate())
            assertNull(channel.sound)
            assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        }
        assertEquals(8, notificationManager.notificationChannels.count { it.id.startsWith("g7_cgm_alarm_v8_") })
    }

    @Test
    fun `alarm sound provider exposes every bundled sound to the notification service`() {
        CgmAlarmType.entries.forEach { type ->
            context.contentResolver.openAssetFileDescriptor(cgmAlarmSoundUri(context, type), "r")!!.use {
                assertTrue(it.length > 0L)
            }
        }
    }

    @Test
    fun `collector error channel migrates away from removed raw sound resource`() {
        notificationManager.createNotificationChannel(
            NotificationChannel("direct_watch_collector_errors_v2", "legacy", NotificationManager.IMPORTANCE_HIGH),
        )

        CgmErrorNotifier.ensureChannel(context)

        assertNull(notificationManager.getNotificationChannel("direct_watch_collector_errors_v2"))
        assertNull(notificationManager.getNotificationChannel("direct_watch_collector_errors_v3"))
        assertNotNull(notificationManager.getNotificationChannel(CgmErrorNotifier.channelId(context)).sound)
    }

    @Test
    fun `test alarm uses dedicated notification without mutating real alarm state`() {
        val statePreferences = context.getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE)
        val before = statePreferences.all.toMap()
        val settings = CgmAlarmSettingsStore.read(context)

        CgmAlarmNotifier.showTest(context, CgmAlarmType.RAPID_FALL, settings)

        assertNotNull(shadowOf(notificationManager).getNotification(CgmAlarmNotifier.testNotificationId(CgmAlarmType.RAPID_FALL)))
        assertEquals(before, statePreferences.all.toMap())
    }

    @Test
    fun `alarm helpers cover every alarm class without changing unrelated flags`() {
        val initial = CgmAlarmSettingsStore.read(context)
        CgmAlarmType.entries.forEach { type ->
            val disabled = withAlarmEnabled(initial, type, false)
            assertFalse(alarmEnabled(disabled, type))
            CgmAlarmType.entries.filterNot { it == type }.forEach { other ->
                assertEquals(alarmEnabled(initial, other), alarmEnabled(disabled, other))
            }
            assertTrue(cgmAlarmTitle(type).isNotBlank())
            assertTrue(cgmAlarmSoundName(type).isNotBlank())
        }
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
        context
            .getSharedPreferences("g7_cgm_alarm_settings", Context.MODE_PRIVATE)
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
    fun `urgent low threshold uses canonical default despite stale legacy alarm preference`() {
        context
            .getSharedPreferences("g7_cgm_alarm_settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("very_low", 30f)
            .commit()

        assertEquals(50.0, CgmAlarmSettingsStore.read(context).veryLowThreshold, 0.0)
    }

    @Test
    fun `alarm preferences round trip without changing fixed safety boundaries`() {
        CgmAlarmSettingsStore.write(
            context,
            CgmAlarmSettings(
                veryHighThreshold = 280.0,
                highThreshold = 190.0,
                lowThreshold = 75.0,
                veryLowThreshold = 45.0,
                rapidRiseThreshold = 3.0,
                rapidFallThreshold = 2.5,
                signalLossMinutes = 16,
                soundEnabled = false,
                vibrationEnabled = false,
                repeatIntervalMinutes = 30,
            ),
        )

        val restored = CgmAlarmSettingsStore.read(context)
        assertEquals(280.0, restored.veryHighThreshold, 0.0)
        assertEquals(190.0, restored.highThreshold, 0.0)
        assertEquals(75.0, restored.lowThreshold, 0.0)
        assertEquals(45.0, restored.veryLowThreshold, 0.0)
        assertEquals(16, restored.signalLossMinutes)
        assertEquals(30, restored.repeatIntervalMinutes)
        assertEquals(false, restored.soundEnabled)
        assertEquals(false, restored.vibrationEnabled)
    }

    @Test
    fun `direct numeric threshold editing preserves strict alarm ordering`() {
        val settings =
            CgmAlarmSettingsStore
                .read(
                    context,
                ).copy(veryHighThreshold = 250.0, highThreshold = 180.0, lowThreshold = 80.0, veryLowThreshold = 50.0)

        assertEquals(170.0, withThreshold(settings, CgmAlarmType.HIGH, 170.0)?.highThreshold)
        assertEquals(70.0, withThreshold(settings, CgmAlarmType.LOW, 70.0)?.lowThreshold)
        assertNull(withThreshold(settings, CgmAlarmType.HIGH, 80.0))
        assertNull(withThreshold(settings, CgmAlarmType.LOW, 180.0))
        assertNull(withThreshold(settings, CgmAlarmType.VERY_LOW, 39.0))
        assertNull(withThreshold(settings, CgmAlarmType.VERY_HIGH, 401.0))
    }

    @Test
    fun `corrupt alarm thresholds are normalized before engine construction`() {
        context
            .getSharedPreferences("g7_cgm_alarm_settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("very_high", 100f)
            .putFloat("high", 900f)
            .putFloat("low", 900f)
            .putFloat("rapid_rise", -4f)
            .commit()

        val restored = CgmAlarmSettingsStore.read(context)
        assertEquals(true, restored.veryHighThreshold > restored.highThreshold)
        assertEquals(true, restored.lowThreshold > restored.veryLowThreshold)
        assertEquals(0.5, restored.rapidRiseThreshold, 0.0)
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
        assertNotNull(notification.fullScreenIntent)
        assertEquals(listOf("Schlummerzeit · 10 Min.", "Stumm", "Vibrieren"), notification.actions.map { it.title.toString() })
        CgmSensorStateStore(context).save(CgmSensorStateStore(context).read().copy(collectorEnabled = false))
        assertFalse(CgmAlertPolicyStore.alarmsEnabled(context))
    }

    @Test
    fun `snoozed low alarm resumes only after ten minutes with a fresh reading`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmCoordinator.snooze(context, CgmAlarmType.LOW, NOW)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        val next = reading("sensor-a", "session-a", 65.0).copy(timestampEpochMs = NOW + 9 * 60_000L)
        CgmAlarmCoordinator.onReading(context, next, NOW + 9 * 60_000L)
        assertFalse(CgmAlarmCoordinator.isActive(context, CgmAlarmType.LOW))
        CgmAlarmCoordinator.onReading(context, next.copy(timestampEpochMs = NOW + 10 * 60_000L), NOW + 10 * 60_000L)
        assertTrue(CgmAlarmCoordinator.isActive(context, CgmAlarmType.LOW))
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `muting one alarm keeps its notification and does not suppress another alarm type`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.perform(context, CgmAlarmType.LOW, "mute")
        assertEquals("mute", CgmAlarmDelivery.mode(context, CgmAlarmType.LOW))
        assertNull(CgmAlarmDelivery.mode(context, CgmAlarmType.HIGH))
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertNull(CgmAlarmDelivery.mode(context, CgmAlarmType.LOW))
    }

    @Test
    fun `selected snooze duration updates notification and controls actual wake time`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.setSnoozeMinutes(context, CgmAlarmType.LOW, 20)
        val notification = shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID)
        assertEquals(
            "Schlummerzeit · 20 Min.",
            notification.actions
                .first()
                .title
                .toString(),
        )
        assertEquals(notification.contentIntent, notification.actions.first().actionIntent)
        CgmAlarmCoordinator.snooze(context, CgmAlarmType.LOW, NOW)
        val snapshot = context.getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE).getString("state_v1", "")!!
        assertTrue(snapshot.contains("\"snoozedUntilEpochMs\":${NOW + 20 * 60_000L}"))
        CgmAlarmDelivery.reset(context, CgmAlarmType.LOW)
        assertEquals(20, CgmAlarmDelivery.snoozeMinutes(context, CgmAlarmType.LOW))
        assertEquals(10, CgmAlarmDelivery.snoozeMinutes(context, CgmAlarmType.HIGH))
    }

    @Test
    fun `snooze duration stays within selectable limits`() {
        CgmAlarmDelivery.setSnoozeMinutes(context, CgmAlarmType.LOW, -5)
        assertEquals(5, CgmAlarmDelivery.snoozeMinutes(context, CgmAlarmType.LOW))
        CgmAlarmDelivery.setSnoozeMinutes(context, CgmAlarmType.LOW, 200)
        assertEquals(120, CgmAlarmDelivery.snoozeMinutes(context, CgmAlarmType.LOW))
    }

    @Test
    fun `unacknowledged notice survives recovery until explicitly acknowledged`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertFalse(CgmAlarmCoordinator.isActive(context, CgmAlarmType.LOW))
        assertTrue(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
        CgmAlarmCoordinator.restore(context, NOW)
        assertTrue(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
        CgmAlarmDelivery.perform(context, CgmAlarmType.LOW, "ack")
        assertFalse(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 120.0), NOW)
        assertFalse(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
    }

    @Test
    fun `muting preserves the overview notice while snoozing removes it`() {
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        CgmAlarmDelivery.perform(context, CgmAlarmType.LOW, "mute")
        assertTrue(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
        CgmAlarmCoordinator.snooze(context, CgmAlarmType.LOW, NOW)
        assertFalse(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))
    }

    @Test
    fun `pausing alarms suppresses non-VERY_LOW alarms but NEVER suppresses VERY_LOW alarm`() {
        CgmAlarmPauseStore.setPause(context, 60, NOW)
        assertTrue(CgmAlarmPauseStore.isPaused(context, CgmAlarmType.HIGH, NOW))
        assertTrue(CgmAlarmPauseStore.isPaused(context, CgmAlarmType.LOW, NOW))
        assertFalse(CgmAlarmPauseStore.isPaused(context, CgmAlarmType.VERY_LOW, NOW))

        // Trigger LOW alarm (65 mg/dL) while pause is active -> suppressed
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertFalse(CgmAlarmCoordinator.isActive(context, CgmAlarmType.LOW))
        assertFalse(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.LOW))

        // Trigger VERY_LOW alarm (45 mg/dL) while pause is active -> MUST NOT BE SUPPRESSED!
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 45.0), NOW)
        assertTrue(CgmAlarmCoordinator.isActive(context, CgmAlarmType.VERY_LOW))
        assertTrue(CgmAlarmCoordinator.isVisible(context, CgmAlarmType.VERY_LOW))

        // Clear pause -> normal operation restored for LOW
        CgmAlarmPauseStore.clearPause(context)
        assertFalse(CgmAlarmPauseStore.isPaused(context, CgmAlarmType.LOW, NOW))
        CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertTrue(CgmAlarmCoordinator.isActive(context, CgmAlarmType.LOW))
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
        val LOW_NOTIFICATION_ID = 7_100 + CgmAlarmType.LOW.ordinal
    }
}
