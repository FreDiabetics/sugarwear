package app.aapswear.g7watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmAlarmSettings
import app.aapswear.g7.CgmAlarmType
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import app.aapswear.model.DataSourceId
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
class G7CgmAlarmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }

    @Suppress("DEPRECATION")
    @Test
    fun `alarm routes bundled sound to own service without system notification chime`() {
        val app = shadowOf(context as android.app.Application)
        while (app.nextStartedService != null) { /* drain prior requests */ }
        val settings = G7AlarmSettingsStore.read(context).copy(soundEnabled = true)
        G7CgmAlarmNotifier.showTest(context, CgmAlarmType.VERY_LOW, settings)
        val service = app.nextStartedService
        assertEquals(G7AlarmAudioService::class.java.name, service.component?.className)
        assertEquals(CgmAlarmType.VERY_LOW.name, service.getStringExtra("type"))
        assertTrue(service.getBooleanExtra("test", false))
        val notification = shadowOf(notificationManager).getNotification(G7CgmAlarmNotifier.testNotificationId(CgmAlarmType.VERY_LOW))
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
        assertEquals(expected, CgmAlarmType.entries.associateWith(::g7AlarmSoundAsset))
        expected.values.forEach { asset ->
            context.assets.openFd(asset).use { descriptor -> assertTrue(descriptor.length > 0L) }
        }
    }

    @Test
    fun `settings preparation registers channels without posting or changing alarm preferences`() {
        val settings = G7AlarmSettingsStore.read(context).copy(soundEnabled = false, vibrationEnabled = false)
        G7AlarmSettingsStore.write(context, settings)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)

        G7CgmAlarmNotifier.prepareCurrentChannels(context)

        val deniedIds = CgmAlarmType.entries.map { G7CgmAlarmNotifier.channelId(context, it, settings) }
        deniedIds.forEach { id ->
            val channel = notificationManager.getNotificationChannel(id)
            assertNotNull(channel)
            assertFalse(channel.canBypassDnd())
            assertFalse(channel.shouldVibrate())
            assertNull(channel.sound)
        }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(settings, G7AlarmSettingsStore.read(context))

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        G7CgmAlarmNotifier.prepareCurrentChannels(context)

        CgmAlarmType.entries.forEach { type ->
            val channel = notificationManager.getNotificationChannel(G7CgmAlarmNotifier.channelId(context, type, settings))
            assertTrue(channel.canBypassDnd())
        }
        deniedIds.forEach { assertNotNull(notificationManager.getNotificationChannel(it)) }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertEquals(settings, G7AlarmSettingsStore.read(context))
    }

    @Test
    fun `all eight alarm channels are silent because the alarm service owns sound`() {
        val settings = G7AlarmSettingsStore.read(context)
        G7CgmAlarmNotifier.ensureAllChannels(context, settings)

        CgmAlarmType.entries.forEach { type ->
            val channel = notificationManager.getNotificationChannel(G7CgmAlarmNotifier.channelId(context, type, settings))
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertTrue(channel.shouldVibrate())
            assertNull(channel.sound)
            assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        }
        assertEquals(8, notificationManager.notificationChannels.count { it.id.startsWith("g7_cgm_alarm_v7_") })
    }

    @Test
    fun `alarm sound provider exposes every bundled sound to the notification service`() {
        CgmAlarmType.entries.forEach { type ->
            context.contentResolver.openAssetFileDescriptor(g7AlarmSoundUri(context, type), "r")!!.use {
                assertTrue(it.length > 0L)
            }
        }
    }

    @Test
    fun `collector error channel migrates away from removed raw sound resource`() {
        notificationManager.createNotificationChannel(
            NotificationChannel("direct_watch_collector_errors_v2", "legacy", NotificationManager.IMPORTANCE_HIGH),
        )

        G7ErrorNotifier.ensureChannel(context)

        assertNull(notificationManager.getNotificationChannel("direct_watch_collector_errors_v2"))
        assertNull(notificationManager.getNotificationChannel("direct_watch_collector_errors_v3"))
        assertNotNull(notificationManager.getNotificationChannel(G7ErrorNotifier.channelId(context)).sound)
    }

    @Test
    fun `test alarm uses dedicated notification without mutating real alarm state`() {
        val statePreferences = context.getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE)
        val before = statePreferences.all.toMap()
        val settings = G7AlarmSettingsStore.read(context)

        G7CgmAlarmNotifier.showTest(context, CgmAlarmType.RAPID_FALL, settings)

        assertNotNull(shadowOf(notificationManager).getNotification(G7CgmAlarmNotifier.testNotificationId(CgmAlarmType.RAPID_FALL)))
        assertEquals(before, statePreferences.all.toMap())
    }

    @Test
    fun `alarm helpers cover every alarm class without changing unrelated flags`() {
        val initial = G7AlarmSettingsStore.read(context)
        CgmAlarmType.entries.forEach { type ->
            val disabled = withAlarmEnabled(initial, type, false)
            assertFalse(alarmEnabled(disabled, type))
            CgmAlarmType.entries.filterNot { it == type }.forEach { other ->
                assertEquals(alarmEnabled(initial, other), alarmEnabled(disabled, other))
            }
            assertTrue(g7AlarmTitle(type).isNotBlank())
            assertTrue(g7AlarmSoundName(type).isNotBlank())
        }
    }

    @Before
    fun setUp() {
        G7SensorStateStore(context).save(app.aapswear.g7.G7PersistedState(collectorEnabled = true))
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
        G7AlertPolicyStore.setPolicy(context, true)
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        G7CgmAlarmCoordinator.clearSuppressed(context)
        G7AlertPolicyStore.setPolicy(context, false)
    }

    @Test
    fun `active notification is restored after process restart`() {
        G7CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        notificationManager.cancel(LOW_NOTIFICATION_ID)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))

        G7CgmAlarmCoordinator.restore(context, NOW + 1_000L)

        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `sensor session change cancels alarms from the previous sensor`() {
        G7CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)
        assertNotNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))

        G7CgmAlarmCoordinator.onReading(
            context,
            reading("sensor-b", "session-b", 110.0),
            NOW + 5 * 60_000L,
        )

        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
    }

    @Test
    fun `invalid packet does not resolve an alarm from the last valid reading`() {
        G7CgmAlarmCoordinator.onReading(context, reading("sensor-a", "session-a", 65.0), NOW)

        G7CgmAlarmCoordinator.onReading(
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

        assertEquals(50.0, G7AlarmSettingsStore.read(context).veryLowThreshold, 0.0)
    }

    @Test
    fun `alarm preferences round trip without changing fixed safety boundaries`() {
        G7AlarmSettingsStore.write(
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

        val restored = G7AlarmSettingsStore.read(context)
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
            G7AlarmSettingsStore
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

        val restored = G7AlarmSettingsStore.read(context)
        assertEquals(true, restored.veryHighThreshold > restored.highThreshold)
        assertEquals(true, restored.lowThreshold > restored.veryLowThreshold)
        assertEquals(0.5, restored.rapidRiseThreshold, 0.0)
    }

    @Test
    fun `automatic suppression expires when the canonical Mobile timeout is reached`() {
        G7AlertPolicyStore.setPolicy(context, false, NOW + 1_000L)

        assertEquals(false, G7AlertPolicyStore.alarmsEnabled(context, NOW))
        assertEquals(NOW + 1_000L, G7AlertPolicyStore.nextAutomaticEnableAt(context, NOW))
        assertEquals(true, G7AlertPolicyStore.alarmsEnabled(context, NOW + 1_000L))
        assertNull(G7AlertPolicyStore.nextAutomaticEnableAt(context, NOW + 1_000L))
    }

    @Test
    fun `enabling Watch alarms evaluates the reading that caused source failover`() {
        val transitionReading =
            reading("sensor-a", "session-a", 65.0).copy(
                timestampEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
            )
        G7AlertPolicyStore.setPolicy(context, false)
        G7SensorStateStore(context).save(
            G7PersistedState(
                sensor = G7Sensor(sensorId = "sensor-a", sessionId = "session-a"),
                collectorEnabled = true,
                lastReading = transitionReading,
            ),
        )

        applySourceControl(
            context = context,
            g7Selected = false,
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
        G7SensorStateStore(context).save(
            G7PersistedState(
                sensor = G7Sensor(sensorId = "sensor-a", sessionId = "session-a"),
                collectorEnabled = false,
                lastReading = transitionReading,
            ),
        )
        applySourceControl(context, g7Selected = true, alarmsEnabled = true, automaticEnableAt = null)
        assertNull(shadowOf(notificationManager).getNotification(LOW_NOTIFICATION_ID))
        assertEquals(false, G7SensorStateStore(context).read().collectorEnabled)
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
