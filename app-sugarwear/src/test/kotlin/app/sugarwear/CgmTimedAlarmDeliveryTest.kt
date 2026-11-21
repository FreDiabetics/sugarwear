package app.sugarwear

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PausedOverviewCredentialShadow::class])
@Suppress("DEPRECATION") // Robolectric exposes the PendingIntent only through ScheduledAlarm.operation.
class CgmTimedAlarmDeliveryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private var start = 0L

    @Before fun prepare() {
        // Match a watch where the user allowed alarm notifications.
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        start = System.currentTimeMillis()
        app
            .getSharedPreferences("g7_cgm_alarm_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmAlarmPauseStore.clearPause(app)
        CgmAlertPolicyStore.setLocalEnabled(app, true)
        val settings = SugarWearAlarmDefaults.defaultSettings()
        CgmAlarmSettingsStore.write(app, settings.copy(rules = settings.rules.mapValues { (_, rule) -> rule.copy(enabled = rule.type in setOf(SugarWearAlarmType.SIGNAL_LOSS, SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM), durationMinutes = 20, deliveryMode = SugarWearAlertDeliveryMode.SOUND) }))
        save(sample(0))
        manager.cancelAll()
        drainServices()
    }

    @Test fun `repeated fresh sensor errors trigger sound and notification at the original deadline`() {
        for (minute in listOf(0, 5, 10, 15, 20)) {
            val error = sample(minute, CgmReadingStatus.SENSOR_ERROR)
            save(error)
            CgmAlarmCoordinator.onReading(app, error, start + minute * 60_000L)
            if (minute < 20) assertNull(shadowOf(manager).getNotification(7108))
        }
        assertDelivered(SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM, 7108, CgmAlarmArtwork.SENSOR_ERROR)
    }

    @Test fun `signal loss uses the same notification and alarm audio pipeline as glucose alerts`() {
        val last = sample(0)
        CgmAlarmCoordinator.onReading(app, last, start)
        CgmAlarmCoordinator.onSignalLoss(app, last, start + 20 * 60_000L - 1)
        assertNull(shadowOf(manager).getNotification(7107))
        CgmAlarmCoordinator.onSignalLoss(app, last, start + 20 * 60_000L)
        assertDelivered(SugarWearAlarmType.SIGNAL_LOSS, 7107, CgmAlarmArtwork.SIGNAL_LOSS)
    }

    @Test fun `error deadline survives restoring before another fresh error packet`() {
        val first = sample(0, CgmReadingStatus.SENSOR_ERROR)
        save(first)
        CgmAlarmCoordinator.onReading(app, first, start)
        CgmAlarmCoordinator.restore(app, start + 10 * 60_000L)
        val next = sample(20, CgmReadingStatus.SENSOR_ERROR)
        save(next)
        CgmAlarmCoordinator.onReading(app, next, start + 20 * 60_000L)
        assertDelivered(SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM, 7108, CgmAlarmArtwork.SENSOR_ERROR)
    }

    @Test fun `valid recovery starts a new full error interval`() {
        for ((minute, status) in listOf(0 to CgmReadingStatus.SENSOR_ERROR, 15 to CgmReadingStatus.VALID, 20 to CgmReadingStatus.SENSOR_ERROR, 35 to CgmReadingStatus.SENSOR_ERROR)) {
            val value = sample(minute, status)
            save(value)
            CgmAlarmCoordinator.onReading(app, value, start + minute * 60_000L)
            assertNull(shadowOf(manager).getNotification(7108))
        }
        val next = sample(40, CgmReadingStatus.SENSOR_ERROR)
        save(next)
        CgmAlarmCoordinator.onReading(app, next, start + 40 * 60_000L)
        assertNotNull(shadowOf(manager).getNotification(7108))
    }

    @Test fun `replacement session cannot inherit the previous sensor error interval`() {
        val first = sample(0, CgmReadingStatus.SENSOR_ERROR)
        save(first)
        CgmAlarmCoordinator.onReading(app, first, start)
        val replacement = sample(20, CgmReadingStatus.SENSOR_ERROR).copy(sessionId = "replacement")
        save(replacement)
        CgmAlarmCoordinator.onReading(app, replacement, start + 20 * 60_000L)
        assertNull(shadowOf(manager).getNotification(7108))
    }

    @Test fun `monitor schedules a twenty minute alarm rather than waiting until minute thirty one`() {
        val last = sample(0)
        CgmAlarmCoordinator.onReading(app, last, start)
        CgmSignalLossMonitor.scheduleFromState(app, CgmSensorStateStore(app).read(), start + 16 * 60_000L)
        val scheduled = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { shadowOf(it.operation).savedIntent.component?.className == CgmSignalLossReceiver::class.java.name }
        assertEquals(start + 20 * 60_000L, scheduled.triggerAtMs)
    }

    @Test fun `five minute sensor error deadline precedes the signal loss status boundary`() {
        val settings = CgmAlarmSettingsStore.read(app)
        val type = SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM
        CgmAlarmSettingsStore.write(app, settings.copy(rules = settings.rules + (type to settings.ruleFor(type).copy(durationMinutes = 5))))
        val first = sample(0, CgmReadingStatus.SENSOR_ERROR)
        save(first)
        CgmAlarmCoordinator.onReading(app, first, start)
        val next = sample(4, CgmReadingStatus.SENSOR_ERROR)
        save(next)
        CgmAlarmCoordinator.onReading(app, next, start + 4 * 60_000L)
        CgmSignalLossMonitor.scheduleFromState(app, CgmSensorStateStore(app).read(), start + 4 * 60_000L)
        val scheduled = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { shadowOf(it.operation).savedIntent.component?.className == CgmSignalLossReceiver::class.java.name }
        assertEquals(start + 5 * 60_000L, scheduled.triggerAtMs)
    }

    @Test fun `an open watch activity still receives sensor fault notification and sound without an automatic alarm activity`() {
        app
            .getSharedPreferences("g7_setup", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("complete", true)
            .commit()
        shadowOf(app).grantPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.POST_NOTIFICATIONS)
        val controller =
            org.robolectric.Robolectric
                .buildActivity(CgmWatchActivity::class.java)
                .setup()
                .visible()
        try {
            while (shadowOf(app).nextStartedActivity != null) { /* discard startup destinations */ }
            for (minute in listOf(0, 20)) {
                val error = sample(minute, CgmReadingStatus.SENSOR_ERROR)
                save(error)
                CgmAlarmCoordinator.onReading(app, error, start + minute * 60_000L)
            }
            assertDelivered(SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM, 7108, CgmAlarmArtwork.SENSOR_ERROR)
            val recovered = sample(20)
            save(recovered)
            CgmAlarmCoordinator.onReading(app, recovered, start + 20 * 60_000L)
            CgmAlarmCoordinator.onSignalLoss(app, recovered, start + 40 * 60_000L)
            assertDelivered(SugarWearAlarmType.SIGNAL_LOSS, 7107, CgmAlarmArtwork.SIGNAL_LOSS)
            var next = shadowOf(app).nextStartedActivity
            while (next != null) {
                assertFalse(next.component?.className == CgmAlarmActivity::class.java.name)
                next = shadowOf(app).nextStartedActivity
            }
        } finally {
            controller.pause().stop().destroy()
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test fun `broadcast evaluates a due sensor error before sixteen minutes even without another packet`() {
        start = System.currentTimeMillis() - 5 * 60_000L
        val settings = CgmAlarmSettingsStore.read(app)
        val type = SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM
        CgmAlarmSettingsStore.write(app, settings.copy(rules = settings.rules + (type to settings.ruleFor(type).copy(durationMinutes = 5))))
        val first = sample(0, CgmReadingStatus.SENSOR_ERROR)
        save(first)
        CgmAlarmCoordinator.onReading(app, first, start)
        app.sendBroadcast(android.content.Intent(app, CgmSignalLossReceiver::class.java))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val finished = java.util.concurrent.CountDownLatch(1)
        CgmReceiverWork.dispatcher { _, _ -> }.dispatch(app, "test-barrier", onFinished = { finished.countDown() })
        assertTrue("Existing receiver worker must finish", finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertDelivered(type, 7108, CgmAlarmArtwork.SENSOR_ERROR)
    }

    @Test fun `an already delivered error does not rearm an immediate monitor loop`() {
        for (minute in listOf(0, 20)) {
            val error = sample(minute, CgmReadingStatus.SENSOR_ERROR)
            save(error)
            CgmAlarmCoordinator.onReading(app, error, start + minute * 60_000L)
        }
        CgmSignalLossMonitor.scheduleFromState(app, CgmSensorStateStore(app).read(), start + 20 * 60_000L)
        val scheduled = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { shadowOf(it.operation).savedIntent.component?.className == CgmSignalLossReceiver::class.java.name }
        assertTrue(scheduled.triggerAtMs >= start + 21 * 60_000L)
    }

    @Test fun `disabled technical alarm and intentional sensor pause remain silent`() {
        val settings = CgmAlarmSettingsStore.read(app)
        val type = SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM
        CgmAlarmSettingsStore.write(app, settings.copy(rules = settings.rules + (type to settings.ruleFor(type).copy(enabled = false))))
        for (minute in listOf(0, 20)) {
            val error = sample(minute, CgmReadingStatus.SENSOR_ERROR)
            save(error)
            CgmAlarmCoordinator.onReading(app, error, start + minute * 60_000L)
        }
        assertNull(shadowOf(manager).getNotification(7108))
        assertFalse(CgmAlarmSettingsStore.read(app).ruleFor(type).enabled)
        val state = CgmSensorStateStore(app).read()
        CgmSensorStateStore(app).save(state.copy(collectorEnabled = false))
        CgmAlarmCoordinator.onSignalLoss(app, state.lastReading, start + 40 * 60_000L)
        assertNull(shadowOf(manager).getNotification(7107))
        assertTrue(drainServices().none { it.component?.className == CgmAlarmAudioService::class.java.name })
    }

    @Test fun `automatic repeat uses the alarm rule instead of the quick snooze option`() {
        val type = SugarWearAlarmType.LOW
        val settings = CgmAlarmSettingsStore.read(app)
        val rule = settings.ruleFor(type).copy(enabled = true, snoozeEnabled = true, snoozeMinutes = 25)
        CgmAlarmSettingsStore.write(app, settings.copy(rules = settings.rules + (type to rule)))
        CgmAlarmDelivery.setSnoozeMinutes(app, type, 10)
        val reading = sample(0).copy(glucoseMgDl = 65.0)
        save(reading)
        val beforeScheduling = System.currentTimeMillis()
        CgmAlarmCoordinator.onReading(app, reading, start)
        val afterScheduling = System.currentTimeMillis()

        val repeat =
            shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single {
                shadowOf(it.operation).savedIntent.getStringExtra(CgmAlarmRepeatReceiver.EXTRA_TYPE) == type.name
            }
        val interval = 25 * 60_000L
        assertTrue(repeat.triggerAtMs in (beforeScheduling + interval)..(afterScheduling + interval))
    }

    @Test fun `scheduled profile discards old high delay and checks the first new measurement`() {
        start =
            LocalDate
                .of(2026, 11, 18)
                .atTime(21, 50)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val type = SugarWearAlarmType.HIGH
        val settings = CgmAlarmSettingsStore.read(app)
        val day = settings.ruleFor(type).copy(enabled = true, thresholdMgDl = 180.0, highDelayMinutes = 15)
        val night = day.copy(thresholdMgDl = 220.0, highDelayMinutes = 0)
        SugarWearAlarmMigration.writeSettings(
            app,
            settings.copy(rules = settings.rules + (type to day), nightRules = settings.rules + (type to night), scheduleEnabled = true),
        )

        fun deliver(minute: Int, glucose: Double) {
            val reading = sample(minute).copy(glucoseMgDl = glucose)
            save(reading)
            CgmAlarmCoordinator.onReading(app, reading, start + minute * 60_000L)
        }

        deliver(0, 230.0)
        deliver(5, 230.0)
        assertNull(shadowOf(manager).getNotification(7104))

        val boundary = start + 10 * 60_000L
        val current = CgmSensorStateStore(app).read()
        assertEquals(boundary, CgmAlarmCoordinator.nextTimedEvaluationAt(app, current, start + 5 * 60_000L))
        CgmAlarmCoordinator.restore(app, boundary)
        CgmAlarmCoordinator.onSignalLoss(app, CgmSensorStateStore(app).read().lastReading, boundary)
        assertNull(shadowOf(manager).getNotification(7104))

        deliver(10, 200.0)
        assertNull(shadowOf(manager).getNotification(7104))
        deliver(15, 230.0)
        assertDelivered(type, 7104, CgmAlarmArtwork.HIGH)
        deliver(20, 240.0)
        val text =
            shadowOf(manager)
                .getNotification(7104)
                .extras
                .getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?.toString()
        assertTrue(text.orEmpty().contains("230 mg/dL"))
        assertFalse(text.orEmpty().contains("240 mg/dL"))
    }

    private fun sample(minute: Int, status: CgmReadingStatus = CgmReadingStatus.VALID) = CgmReading("reading-$minute-$status", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 120.0, start + minute * 60_000L, start + minute * 60_000L, status = status)

    private fun save(reading: CgmReading) = CgmSensorStateStore(app).save(CgmPersistedState(sensor = CgmSensor(reading.sensorId, reading.sessionId, deviceAddress = "AA:BB:CC:DD:EE:FF"), collectorEnabled = true, authenticationState = CgmAuthenticationState.AUTHENTICATED, lastReading = reading))

    private fun drainServices(): List<android.content.Intent> {
        val services = mutableListOf<android.content.Intent>()
        while (true) services += shadowOf(app).nextStartedService ?: break
        return services
    }

    private fun assertDelivered(type: SugarWearAlarmType, notificationId: Int, artwork: CgmAlarmArtwork) {
        val notification = shadowOf(manager).getNotification(notificationId)
        assertNotNull("$type must post its notification", notification)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(CgmAlarmActivity::class.java.name, intent.component?.className)
        assertEquals(type.name, intent.getStringExtra("type"))
        assertNotNull(intent.getStringExtra("episode"))
        assertEquals(artwork, cgmAlarmArtwork(type))
        assertTrue("$type must request its bundled alarm sound", drainServices().any { it.component?.className == CgmAlarmAudioService::class.java.name && it.getStringExtra("type") == type.name })
    }
}
