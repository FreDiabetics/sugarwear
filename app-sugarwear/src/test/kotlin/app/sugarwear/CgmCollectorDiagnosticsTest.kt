package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CollectorAlarmKind
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import app.sugarlicious.sensor.protocol.CollectorDiagnosticResult
import app.sugarlicious.sensor.protocol.CollectorDiagnosticStage
import app.sugarlicious.sensor.protocol.CollectorSlotStrategy
import app.sugarlicious.sensor.protocol.DirectConnectResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmCollectorDiagnosticsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(
            "g7_collector_attempts",
            "g7_collector_attempt_history",
            "g7_collector_attempt_active",
            "g7_collector_slot_history",
        ).forEach { name ->
            context
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    @Test
    fun `slot summaries retain more than a full day independently from raw event attempts`() {
        assertTrue(cgmSlotRetentionDurationMs() >= 24L * 60L * 60_000L)
        val store = CgmCollectorDiagnosticStore(context)
        val attempt =
            store.begin(
                manual = false,
                restart = false,
                cycle =
                    CollectorCycleTiming(
                        expectedReadingEpoch = 1_300_000L,
                        directConnectResult = DirectConnectResult.SUCCESS,
                        directConnectAttempts = 1,
                        slotStrategy = CollectorSlotStrategy.DIRECT_ONLY_SUCCESS,
                        sensorAgeSeconds = 3,
                    ),
                nowEpochMs = 1_290_000L,
            )
        store.setClassification(attempt.attemptId, CollectorCycleClassification.SUCCESS_FRESH)
        store.record(
            attempt.attemptId,
            CollectorDiagnosticStage.COMPLETE,
            CollectorDiagnosticResult.SUCCESS,
            "SUCCESS_FRESH",
            nowEpochMs = 1_305_000L,
        )

        val summary = CgmCollectorDiagnosticStore(context).slotSnapshot().single()
        assertEquals(1_300_000L, summary.expectedReadingEpoch)
        assertEquals(CollectorSlotStrategy.DIRECT_ONLY_SUCCESS, summary.strategy)
        assertEquals(DirectConnectResult.SUCCESS, summary.directResult)
        assertEquals(3L, summary.readingAgeSeconds)
    }

    @Test
    fun `attempt ids survive recreation while history stays at the overnight bound`() {
        repeat(517) { index ->
            val store = CgmCollectorDiagnosticStore(context)
            val attempt = store.begin(manual = index % 2 == 0, restart = index % 3 == 0, nowEpochMs = index.toLong())
            store.record(
                attempt.attemptId,
                CollectorDiagnosticStage.COMPLETE,
                CollectorDiagnosticResult.SUCCESS,
                "Erfolgreich",
                nowEpochMs = index.toLong() + 1L,
            )
        }

        val restored = CgmCollectorDiagnosticStore(context).snapshot()
        assertEquals(192, restored.size)
        assertEquals(517L, restored.first().attemptId)
        assertEquals(326L, restored.last().attemptId)
        assertTrue(restored.all { it.completedAtEpochMs != null })
    }

    @Test
    fun `stale active attempt is closed as hung and releases active store`() {
        val store = CgmCollectorDiagnosticStore(context)
        store.begin(manual = false, restart = false, nowEpochMs = 1_000L)

        assertTrue(store.hasActiveAttempt())
        assertTrue(store.hasActiveAttempt(1L))
        assertEquals(1, store.expireStaleAttempts(nowEpochMs = 301_000L, maxAgeMs = 240_000L))

        val restored = CgmCollectorDiagnosticStore(context).snapshot().single()
        assertEquals(CollectorCycleClassification.HUNG, restored.classification)
        assertFalse(CgmCollectorDiagnosticStore(context).hasActiveAttempt())
        assertFalse(CgmCollectorDiagnosticStore(context).hasActiveAttempt(1L))
    }

    @Test
    fun `starting a new cycle compacts an expired predecessor as hung`() {
        val store = CgmCollectorDiagnosticStore(context)
        val stale =
            store.begin(
                manual = false,
                restart = false,
                nowEpochMs = 1_000L,
                deadlineEpochMs = 10_000L,
            )

        val current =
            store.begin(
                manual = false,
                restart = false,
                nowEpochMs = 11_000L,
                deadlineEpochMs = 20_000L,
            )

        assertFalse(store.hasActiveAttempt(stale.attemptId))
        assertTrue(store.hasActiveAttempt(current.attemptId))
        assertEquals(CollectorCycleClassification.HUNG, store.snapshot().first { it.attemptId == stale.attemptId }.classification)
    }

    @Test
    fun `incomplete active attempt survives process-style store recreation`() {
        val firstStore = CgmCollectorDiagnosticStore(context)
        val attempt = firstStore.begin(manual = false, restart = false, nowEpochMs = 10_000L)
        firstStore.record(
            attempt.attemptId,
            CollectorDiagnosticStage.SCANNING,
            CollectorDiagnosticResult.INFO,
            "Scan läuft",
            nowEpochMs = 10_500L,
        )

        val restored = CgmCollectorDiagnosticStore(context).snapshot().single()

        assertEquals(attempt.attemptId, restored.attemptId)
        assertNull(restored.completedAtEpochMs)
        assertTrue(restored.events.any { it.stage == CollectorDiagnosticStage.SCANNING })
        assertNull(
            context
                .getSharedPreferences("g7_collector_attempt_history", Context.MODE_PRIVATE)
                .getString("attempts_v2", null),
        )
        assertTrue(
            context
                .getSharedPreferences("g7_collector_attempt_active", Context.MODE_PRIVATE)
                .getString("active_attempts_v1", null)
                ?.isNotBlank() == true,
        )
    }

    @Test
    fun `stale cleanup uses last stage progress rather than attempt start alone`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = false, restart = false, nowEpochMs = 1_000L)
        store.record(
            attempt.attemptId,
            CollectorDiagnosticStage.CONNECT_REQUEST,
            nowEpochMs = 200_000L,
            message = "Fortschritt",
        )

        assertEquals(0, store.expireStaleAttempts(nowEpochMs = 301_000L, maxAgeMs = 240_000L))
        assertTrue(store.hasActiveAttempt(attempt.attemptId))
        assertEquals(1, store.expireStaleAttempts(nowEpochMs = 441_000L, maxAgeMs = 240_000L))
    }

    @Test
    fun `process recreation can immediately close an orphaned active attempt`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = false, restart = false, nowEpochMs = 100_000L)
        store.record(
            attempt.attemptId,
            CollectorDiagnosticStage.CONNECT_REQUEST,
            nowEpochMs = 100_500L,
            message = "Verbindung läuft",
        )

        assertEquals(1, store.expireStaleAttempts(nowEpochMs = 101_000L, maxAgeMs = 0L))
        assertFalse(store.hasActiveAttempt(attempt.attemptId))
        assertEquals(CollectorCycleClassification.HUNG, store.snapshot().single().classification)
    }

    @Test
    fun `terminal attempt moves from active persistence into bounded history`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = false, restart = false, nowEpochMs = 20_000L)
        store.record(
            attempt.attemptId,
            CollectorDiagnosticStage.COMPLETE,
            CollectorDiagnosticResult.SUCCESS,
            "SUCCESS_FRESH",
            nowEpochMs = 21_000L,
        )

        assertNull(
            context
                .getSharedPreferences("g7_collector_attempt_active", Context.MODE_PRIVATE)
                .getString("active_attempts_v1", null),
        )
        assertTrue(
            context
                .getSharedPreferences("g7_collector_attempt_history", Context.MODE_PRIVATE)
                .getString("attempts_v2", null)
                ?.isNotBlank() == true,
        )
        assertEquals(CollectorDiagnosticResult.SUCCESS, CgmCollectorDiagnosticStore(context).snapshot().single().result)
    }

    @Test
    fun `scheduled cycle timing survives alarm receiver service handoff and derives lateness`() {
        val store = CgmCollectorDiagnosticStore(context)
        val scheduled =
            CollectorCycleTiming(
                expectedReadingEpoch = 1_300_000L,
                requestedReconnectEpoch = 1_000_000L,
                alarmKind = CollectorAlarmKind.EXACT,
                canScheduleExactAlarms = true,
                batteryUnrestricted = true,
                deviceIdleMode = true,
                isInteractive = false,
                charging = false,
            )
        store.stageScheduledCycle(scheduled)

        store.markScheduledAlarmReceived(1_012_000L)
        val consumed = store.consumeScheduledCycle(1_014_500L)!!
        val attempt = store.begin(false, false, consumed, 1_014_600L)
        store.updateCycle(attempt.attemptId) {
            it.copy(
                wakeLockAcquiredAt = 1_014_700L,
                scanStartedAt = 1_016_000L,
                advertisementFoundAt = 1_020_000L,
                connectGattStartedAt = 1_020_500L,
                gattConnectedAt = 1_022_000L,
                cycleEndedAt = 1_030_000L,
            )
        }
        store.setClassification(attempt.attemptId, CollectorCycleClassification.SUCCESS_FRESH)

        val restored = CgmCollectorDiagnosticStore(context).snapshot().single()
        val cycle = restored.cycle!!
        assertEquals(12_000L, cycle.alarmLatenessMs)
        assertEquals(2_500L, cycle.serviceStartLatenessMs)
        assertEquals(16_000L, cycle.scanStartLatenessMs)
        assertEquals(4_000L, cycle.advertisementLatencyMs)
        assertEquals(1_500L, cycle.gattLatencyMs)
        assertEquals(18_000L, cycle.totalCycleLatencyMs)
        assertEquals(CollectorCycleClassification.SUCCESS_FRESH, restored.classification)
        assertEquals(CollectorAlarmKind.EXACT, cycle.alarmKind)
        assertTrue(cycle.deviceIdleMode == true)
        assertTrue(cycle.isInteractive == false)
    }

    @Test
    fun `fresh cycle classification rejects aged packets received now`() {
        val now = 2_000_000L
        val fresh = readingAt(now - 5 * 60_000L, now, sensorAgeSeconds = 5 * 60L)
        val aged = readingAt(now - 20 * 60_000L, now, sensorAgeSeconds = 20 * 60L)

        assertTrue(isFreshCgmCycleReading(fresh, now))
        assertFalse(isFreshCgmCycleReading(aged, now))
    }

    @Test
    fun `failure classification separates missed advertisement GATT auth packet and store errors`() {
        assertEquals(
            CollectorCycleClassification.NO_ADVERTISEMENT,
            classifyCgmCycleFailure("G7-BLE-107", CollectorCycleTiming()),
        )
        assertEquals(
            CollectorCycleClassification.GATT_CONNECT_FAILED,
            classifyCgmCycleFailure("G7-GATT-133", CollectorCycleTiming()),
        )
        assertEquals(
            CollectorCycleClassification.AUTH_FAILED,
            classifyCgmCycleFailure("G7-AUTH-205", CollectorCycleTiming()),
        )
        assertEquals(
            CollectorCycleClassification.GLUCOSE_TIMEOUT,
            classifyCgmCycleFailure("G7-BLE-111", CollectorCycleTiming()),
        )
        assertEquals(
            CollectorCycleClassification.INVALID_PACKET,
            classifyCgmCycleFailure("G7-DATA-301", CollectorCycleTiming()),
        )
        assertEquals(
            CollectorCycleClassification.FALLBACK_SCAN_FAILED,
            classifyCgmCycleFailure("G7-BLE-FALLBACK-107", CollectorCycleTiming()),
        )
    }

    @Test
    fun `event history is bounded and secrets and Bluetooth addresses are redacted`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = true, restart = false, nowEpochMs = 0L)
        repeat(105) { index ->
            store.record(
                attempt.attemptId,
                CollectorDiagnosticStage.SCANNING,
                message = "event=$index sharedKey=DEADBEEF pairingCode=1234 address=AA:BB:CC:DD:EE:FF\n",
                nowEpochMs = index.toLong() + 1L,
            )
        }

        val events = store.snapshot().single().events
        assertEquals(40, events.size)
        val text = events.joinToString(" ", transform = { it.message })
        assertFalse(text.contains("DEADBEEF"))
        assertFalse(text.contains("1234"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(text.contains("[REDACTED]"))
        assertTrue(text.contains("••:••:••:••:EE:FF"))
    }

    @Test
    fun `completed attempt keeps compact evidence while active attempt keeps callback detail`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = true, restart = false, nowEpochMs = 0L)
        repeat(30) { index ->
            store.record(attempt.attemptId, CollectorDiagnosticStage.SCANNING, message = "event=$index", nowEpochMs = index + 1L)
        }
        store.record(
            attempt.attemptId,
            CollectorDiagnosticStage.COMPLETE,
            CollectorDiagnosticResult.SUCCESS,
            "SUCCESS_FRESH",
            nowEpochMs = 100L,
        )

        val restored = CgmCollectorDiagnosticStore(context).snapshot().single()
        assertEquals(4, restored.events.size)
        assertEquals("SUCCESS_FRESH", restored.events.last().message)
    }

    @Test
    fun `completed radio failure retains each connection generation and callback`() {
        val store = CgmCollectorDiagnosticStore(context)
        val attempt = store.begin(manual = false, restart = false, nowEpochMs = 0L)
        repeat(4) { generation ->
            store.record(attempt.attemptId, CollectorDiagnosticStage.CONNECT_REQUEST, message = "GATT_GENERATION_CREATED generation=$generation", nowEpochMs = generation * 10L + 1)
            store.record(attempt.attemptId, CollectorDiagnosticStage.CONNECT_REQUEST, CollectorDiagnosticResult.RECOVERABLE_ERROR, "GATT_133_RECEIVED generation=$generation", nowEpochMs = generation * 10L + 2)
            store.record(attempt.attemptId, CollectorDiagnosticStage.SCANNING, message = "noise", nowEpochMs = generation * 10L + 3)
        }
        store.record(attempt.attemptId, CollectorDiagnosticStage.ERROR, CollectorDiagnosticResult.RECOVERABLE_ERROR, "GATT_CONNECT_FAILED", nowEpochMs = 100L)

        val restored = CgmCollectorDiagnosticStore(context).snapshot().single()
        assertEquals(8, restored.events.count { it.stage == CollectorDiagnosticStage.CONNECT_REQUEST })
        assertEquals("GATT_CONNECT_FAILED", restored.events.last().message)
        assertTrue(restored.events.size <= 16)
    }

    @Test
    fun `reading summary counts missing five minute windows in chronological order`() {
        val readings = listOf(reading(20L), reading(0L), reading(5L))
        val summary = summarizeCgmReadings(readings, startOfDayEpochMs = 1L)

        assertEquals(3, summary.count)
        assertEquals(2, summary.todayCount)
        assertEquals(0L, summary.oldestEpochMs)
        assertEquals(20L * 60_000L, summary.latestEpochMs)
        assertEquals(2, summary.missedExpectedWindows)
    }

    @Test
    fun `reading summary does not count a sensor session change as a gap`() {
        val readings =
            listOf(
                reading(0L),
                reading(30L).copy(sensorId = "next-sensor", sessionId = "next-session"),
            )

        assertEquals(0, summarizeCgmReadings(readings, startOfDayEpochMs = 0L).missedExpectedWindows)
    }

    @Test
    fun `reading summary reports only plausible valid glucose successes`() {
        val readings =
            listOf(
                reading(0L),
                reading(5L).copy(status = CgmReadingStatus.SENSOR_ERROR, glucoseMgDl = 0.0),
                reading(10L).copy(glucoseMgDl = Double.NaN),
                reading(15L).copy(glucoseMgDl = 1_001.0),
            )

        val summary = summarizeCgmReadings(readings, startOfDayEpochMs = 0L)

        assertEquals(1, summary.count)
        assertEquals(0L, summary.latestEpochMs)
        assertEquals(0, summary.missedExpectedWindows)
    }

    @Test
    fun `collector graph never mixes readings from another sensor session`() {
        val active = reading(5L)
        val sameSensorOtherSession = reading(10L).copy(sessionId = "other-session")
        val otherSensor = reading(15L).copy(sensorId = "other-sensor")

        val filtered =
            currentCgmSessionReadings(
                listOf(active, sameSensorOtherSession, otherSensor),
                CgmSensor("sensor", "session"),
            )

        assertEquals(listOf(active), filtered)
    }

    private fun reading(minutes: Long) =
        CgmReading(
            id = "reading-$minutes",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = "sensor",
            sessionId = "session",
            glucoseMgDl = 120.0,
            timestampEpochMs = minutes * 60_000L,
            receivedAtEpochMs = minutes * 60_000L,
            sequenceNumber = minutes,
        )

    private fun readingAt(
        measuredAt: Long,
        receivedAt: Long,
        sensorAgeSeconds: Long,
    ) = CgmReading(
        id = "reading-$measuredAt-$receivedAt",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = 120.0,
        timestampEpochMs = measuredAt,
        receivedAtEpochMs = receivedAt,
        sequenceNumber = 1L,
        sensorAgeSeconds = sensorAgeSeconds,
    )
}
