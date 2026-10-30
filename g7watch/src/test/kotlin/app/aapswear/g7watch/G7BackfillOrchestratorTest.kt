package app.aapswear.g7watch

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorCycleClassification
import app.aapswear.g7.CollectorExpectedWindow
import app.aapswear.g7.CollectorWindowTerminalState
import app.aapswear.g7.G7GapRecoveryState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class G7BackfillOrchestratorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context
            .getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("g7_collector_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        G7SensorStateStore(context).save(G7PersistedState(sensor = G7Sensor("sensor-a", "session-a")))
    }

    @Test fun `initial discovery scan is not counted as recovery fallback`() {
        val ledger = G7ExpectedWindowLedger(context)
        val window = ledger.create(300_000L, 290_000L)
        ledger.markScan(window.expectedWindowId, 290_000L, 295_000L, 2, 294_000L)

        val recorded = requireNotNull(ledger.window(window.expectedWindowId))
        assertFalse(recorded.fallbackScanUsed)
        assertEquals(290_000L, recorded.scanStartedAt)
        assertEquals(2, recorded.scanResultCount)
        assertEquals(294_000L, recorded.advertisementSeenAt)
    }

    @Test fun `scan after direct connection attempt remains a fallback`() {
        val ledger = G7ExpectedWindowLedger(context)
        val window = ledger.create(300_000L, 290_000L)
        ledger.markGattStarted(window.expectedWindowId, 290_000L, 1L)
        ledger.markScan(window.expectedWindowId, 310_000L, 315_000L, 0, null)

        assertTrue(requireNotNull(ledger.window(window.expectedWindowId)).fallbackScanUsed)
    }

    @Test fun `ledger callback decodes retained history only once`() {
        var historyReads = 0
        val delegate = context.getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
        val observed =
            java.lang.reflect.Proxy.newProxyInstance(
                android.content.SharedPreferences::class.java.classLoader,
                arrayOf(android.content.SharedPreferences::class.java),
            ) { _, method, args ->
                if (method.name == "getString" && args?.firstOrNull() == "windows_v1") historyReads += 1
                method.invoke(delegate, *(args ?: emptyArray()))
            } as android.content.SharedPreferences
        val countingContext =
            object : android.content.ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                    if (name == "g7_expected_window_ledger") observed else super.getSharedPreferences(name, mode)
            }
        val ledger = G7ExpectedWindowLedger(countingContext)
        val window = ledger.create(300_000L, 290_000L)
        historyReads = 0

        ledger.markPrimaryTriggered(window.expectedWindowId, 300_000L)

        assertEquals(1, historyReads)
        assertEquals(300_000L, ledger.window(window.expectedWindowId)?.primaryAlarmTriggeredAt)
    }

    @Test fun `oldest same-session gap is selected and legacy detection time is repaired`() {
        val ledger = G7ExpectedWindowLedger(context)
        val newer = ledger.create(600_000L, 590_000L)
        ledger.markFinal(newer.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val older = ledger.create(300_000L, 290_000L)
        ledger.markFinal(older.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)

        val selected = ledger.oldestOpenGap(older.sensorId, older.sessionId, nowEpochMs = 700_000L)

        assertEquals(older.expectedWindowId, selected?.expectedWindowId)
        assertNotNull(ledger.window(older.expectedWindowId)?.gapDetectedAt)
    }

    @Test fun `latest expected timestamp is available without decoding the window ledger`() {
        val ledger = G7ExpectedWindowLedger(context)
        ledger.create(300_000L, 290_000L)
        ledger.create(600_000L, 590_000L)

        context
            .getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
            .edit()
            .putString("windows_v1", "not valid json")
            .commit()

        assertEquals(600_000L, G7ExpectedWindowLedger(context).latestExpectedAt("sensor-a", "session-a"))
        assertNull(G7ExpectedWindowLedger(context).latestExpectedAt("sensor-a", "another-session"))
    }

    @Test fun `ledger retains only technically recoverable open windows and recent closed evidence`() {
        val values =
            (0 until 400).map { index ->
                CollectorExpectedWindow(
                    expectedWindowId = "open-$index",
                    expectedAt = index.toLong(),
                    recoveryRequired = true,
                )
            } +
                (0 until 250).map { index ->
                    CollectorExpectedWindow(
                        expectedWindowId = "closed-$index",
                        expectedAt = (1_000 + index).toLong(),
                        recoveryRequired = false,
                    )
                }

        val retained = retainExpectedWindows(values)

        assertEquals(MAX_RECOVERABLE_OPEN_WINDOWS, retained.count(CollectorExpectedWindow::recoveryRequired))
        assertEquals(MAX_CLOSED_WINDOWS, retained.count { !it.recoveryRequired })
        assertNull(retained.firstOrNull { it.expectedWindowId == "open-0" })
        assertNotNull(retained.firstOrNull { it.expectedWindowId == "open-399" })
    }

    @Test fun `closed gap and another sensor session are never selected`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = gap.sensorId ?: "unknown"
        val sessionId = gap.sessionId ?: "unknown"
        ledger.markNextLiveAndBackfill(sensorId, sessionId, 600_000L, 601_000L, 602_000L, 601_500L, 601_700L, listOf(300_000L))

        assertNull(ledger.oldestOpenGap(sensorId, sessionId))
        assertNull(ledger.oldestOpenGap("other", "other"))
    }

    @Test fun `request attempts persist across ledger instances and incomplete responses keep gap open`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = gap.sensorId ?: "unknown"
        val sessionId = gap.sessionId ?: "unknown"

        ledger.markRecoveryRequestStarted(sensorId, sessionId, 600_000L, 601_500L)
        ledger.markNextLiveAndBackfill(sensorId, sessionId, 600_000L, 601_000L, 602_000L, 601_500L, 601_700L, emptyList())

        val afterRestart = G7ExpectedWindowLedger(context).oldestOpenGap(gap.sensorId, gap.sessionId, nowEpochMs = 700_000L)
        assertNotNull(afterRestart)
        assertEquals(1, afterRestart?.recoveryAttemptCount)
        assertEquals("RESPONSE_DID_NOT_CONTAIN_GAP", afterRestart?.lastRecoveryOutcome)

        ledger.markRecoveryRequestStarted(sensorId, sessionId, 900_000L, 901_500L)
        assertEquals(2, ledger.window(gap.expectedWindowId)?.recoveryAttemptCount)
    }

    @Test fun `gap older than sensor history is terminalized and no longer blocks a recent gap`() {
        val ledger = G7ExpectedWindowLedger(context)
        val expired = ledger.create(300_000L, 290_000L)
        ledger.markFinal(expired.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val recent = ledger.create(G7CollectorBackfillProtocol.MAX_WINDOW_MS + 600_000L, 590_000L)
        ledger.markFinal(recent.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)

        val selected =
            ledger.oldestOpenGap(
                expired.sensorId,
                expired.sessionId,
                nowEpochMs = G7CollectorBackfillProtocol.MAX_WINDOW_MS + 900_000L,
            )

        assertEquals(recent.expectedWindowId, selected?.expectedWindowId)
        val terminal = requireNotNull(ledger.window(expired.expectedWindowId))
        assertFalse(terminal.recoveryRequired)
        assertEquals(G7GapRecoveryState.UNRECOVERABLE, terminal.gapRecoveryState)
        assertEquals("outside Dexcom G7 history window", terminal.terminalGapReason)
    }

    @Test fun `three complete responses without target terminalize gap and unblock next one`() {
        val ledger = G7ExpectedWindowLedger(context)
        val missing = ledger.create(300_000L, 290_000L)
        ledger.markFinal(missing.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val next = ledger.create(2_000_000L, 1_990_000L)
        ledger.markFinal(next.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = missing.sensorId ?: "unknown"
        val sessionId = missing.sessionId ?: "unknown"

        repeat(MAX_COMPLETE_BACKFILL_MISSES) { attempt ->
            val liveAt = 900_000L + attempt * 300_000L
            ledger.markRecoveryRequestStarted(sensorId, sessionId, liveAt, liveAt + 1_000L)
            ledger.markNextLiveAndBackfill(
                sensorId,
                sessionId,
                liveAt,
                liveAt + 500L,
                liveAt + 1_500L,
                liveAt + 1_000L,
                liveAt + 1_400L,
                emptyList(),
            )
        }

        val selected = ledger.oldestOpenGap(sensorId, sessionId, nowEpochMs = 2_100_000L)

        assertEquals(next.expectedWindowId, selected?.expectedWindowId)
        val terminal = requireNotNull(ledger.window(missing.expectedWindowId))
        assertFalse(terminal.recoveryRequired)
        assertEquals(G7GapRecoveryState.UNRECOVERABLE, terminal.gapRecoveryState)
        assertEquals("complete backfill responses did not contain gap", terminal.terminalGapReason)
    }

    @Test fun `failed backfill requests remain retryable`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = gap.sensorId ?: "unknown"
        val sessionId = gap.sessionId ?: "unknown"

        repeat(MAX_COMPLETE_BACKFILL_MISSES + 1) { attempt ->
            ledger.markRecoveryRequestStarted(sensorId, sessionId, 600_000L, 601_000L + attempt)
            ledger.markNextLiveAndBackfill(
                sensorId,
                sessionId,
                600_000L,
                600_500L,
                601_500L,
                601_000L + attempt,
                null,
                emptyList(),
            )
        }

        assertEquals(
            gap.expectedWindowId,
            ledger.oldestOpenGap(sensorId, sessionId, nowEpochMs = 700_000L)?.expectedWindowId,
        )
    }

    @Test fun `complete response with failed storage does not consume miss budget`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = gap.sensorId ?: "unknown"
        val sessionId = gap.sessionId ?: "unknown"

        repeat(MAX_COMPLETE_BACKFILL_MISSES) { attempt ->
            ledger.markRecoveryRequestStarted(sensorId, sessionId, 600_000L, 601_000L + attempt)
            ledger.markNextLiveAndBackfill(
                sensorId,
                sessionId,
                600_000L,
                600_500L,
                601_500L,
                601_000L + attempt,
                601_400L + attempt,
                emptyList(),
                responseProcessedSuccessfully = false,
            )
        }

        val persisted = requireNotNull(ledger.window(gap.expectedWindowId))
        assertEquals(0, persisted.completeRecoveryMissCount)
        assertEquals(
            gap.expectedWindowId,
            ledger.oldestOpenGap(sensorId, sessionId, nowEpochMs = 700_000L)?.expectedWindowId,
        )
    }

    @Test fun `one coalesced response closes every matching open window but never another session`() {
        val ledger = G7ExpectedWindowLedger(context)
        val first = ledger.create(300_000L, 290_000L)
        ledger.markFinal(first.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val second = ledger.create(600_000L, 590_000L)
        ledger.markFinal(second.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = first.sensorId ?: "unknown"
        val sessionId = first.sessionId ?: "unknown"

        ledger.markRecoveryRequestStarted(sensorId, sessionId, 900_000L, 901_500L)
        ledger.markNextLiveAndBackfill(sensorId, sessionId, 900_000L, 901_000L, 902_000L, 901_500L, 901_700L, listOf(300_000L, 600_000L))

        assertNull(ledger.oldestOpenGap(first.sensorId, first.sessionId))
        assertTrue(
            ledger
                .snapshot()
                .filter {
                    it.expectedWindowId in setOf(first.expectedWindowId, second.expectedWindowId)
                }.all { !it.recoveryRequired },
        )
    }

    @Test fun `near-identical lifecycle windows canonicalize to one record`() {
        val ledger = G7ExpectedWindowLedger(context)
        val first = ledger.create(300_000L, 290_000L)
        val duplicate = ledger.create(320_000L, 310_000L)

        assertEquals(first.expectedWindowId, duplicate.expectedWindowId)
        assertEquals(1, ledger.snapshot().size)
        assertEquals(300_000L, ledger.snapshot().single().expectedAt)
    }

    @Test fun `scheduled cycle uses ledger canonical id when requested time drifts by milliseconds`() {
        val state = G7SensorStateStore(context).read().copy(collectorEnabled = true)
        G7SensorStateStore(context).save(state)
        val ledger = G7ExpectedWindowLedger(context)
        val existing = ledger.create(300_000L, 290_000L)

        val cycle =
            canonicalCollectorCycle(
                ledger = ledger,
                requestedReconnectEpochMs = 290_323L,
                expectedReadingEpochMs = 300_323L,
                exactScheduled = true,
            )

        assertEquals(existing.expectedWindowId, cycle.expectedWindowId)
        assertEquals(existing.expectedAt, cycle.expectedReadingEpoch)
        assertEquals(1, ledger.snapshot().size)
    }

    @Test fun `unfinished historical windows reconcile from durable readings or become recoverable gaps`() {
        val ledger = G7ExpectedWindowLedger(context)
        val stored = ledger.create(300_000L, 290_000L)
        val missing = ledger.create(600_000L, 590_000L)

        val result =
            ledger.reconcileUnfinished(
                sensorId = "sensor-a",
                sessionId = "session-a",
                nowEpochMs = 1_000_000L,
                readingNear = { window -> if (window.expectedWindowId == stored.expectedWindowId) 300_500L else null },
            )

        assertEquals(1, result.satisfiedByReading)
        assertEquals(1, result.recoverableGaps)
        assertEquals(false, ledger.window(stored.expectedWindowId)?.recoveryRequired)
        assertEquals(CollectorCycleClassification.SUCCESS_FRESH, ledger.window(stored.expectedWindowId)?.finalResult)
        assertEquals(true, ledger.window(missing.expectedWindowId)?.recoveryRequired)
        assertEquals(CollectorCycleClassification.MISSED_SENSOR_WINDOW, ledger.window(missing.expectedWindowId)?.finalResult)
    }

    @Test fun `terminal updates report unknown cycle ids instead of failing silently`() {
        val ledger = G7ExpectedWindowLedger(context)
        val existing = ledger.create(300_000L, 290_000L)

        assertFalse(ledger.markReading("unknown-id", 301_000L))
        assertFalse(ledger.markFinal("unknown-id", CollectorCycleClassification.MISSED_SENSOR_WINDOW, true))
        assertTrue(ledger.markReading(existing.expectedWindowId, 301_000L))
    }

    @Test fun `second successful contact retries and closes gap inside ten minute SLA`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        val sensorId = gap.sensorId ?: "unknown"
        val sessionId = gap.sessionId ?: "unknown"

        ledger.markRecoveryRequestStarted(sensorId, sessionId, 600_000L, 601_000L)
        ledger.markNextLiveAndBackfill(sensorId, sessionId, 600_000L, 600_500L, 601_500L, 601_000L, 601_400L, emptyList())
        ledger.markRecoveryRequestStarted(sensorId, sessionId, 900_000L, 901_000L)
        ledger.markNextLiveAndBackfill(sensorId, sessionId, 900_000L, 900_500L, 901_500L, 901_000L, 901_400L, listOf(300_000L))

        val recovered = requireNotNull(ledger.window(gap.expectedWindowId))
        assertEquals(2, recovered.recoveryAttemptCount)
        assertEquals(G7GapRecoveryState.RECOVERED, recovered.gapRecoveryState)
        assertTrue(requireNotNull(recovered.backfillInsertedAt) - requireNotNull(recovered.firstRecoveryOpportunityAt) <= 10 * 60_000L)
    }

    @Test fun `ending session prevents old gaps contaminating replacement sensor`() {
        val ledger = G7ExpectedWindowLedger(context)
        val gap = ledger.create(300_000L, 290_000L)
        ledger.markFinal(gap.expectedWindowId, CollectorCycleClassification.MISSED_SENSOR_WINDOW, true)
        ledger.markSessionEnded(gap.sensorId, gap.sessionId, 400_000L)

        assertNull(ledger.oldestOpenGap(gap.sensorId, gap.sessionId))
        assertEquals(G7GapRecoveryState.SESSION_ENDED, ledger.window(gap.expectedWindowId)?.gapRecoveryState)
    }

    @Test fun `long outage reconstruction persists the complete ledger once`() {
        val preferences = context.getSharedPreferences("g7_expected_window_ledger", Context.MODE_PRIVATE)
        val writes = AtomicInteger()
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "windows_v1") writes.incrementAndGet()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        try {
            val inserted =
                G7ExpectedWindowLedger(context).reconstructMissed(
                    sensorId = "sensor-a",
                    sessionId = "session-a",
                    fromExpectedAt = 300_000L,
                    untilExclusive = 3_300_000L,
                    sensorStartAt = 0L,
                    sensorEndAt = 9_000_000L,
                    nowEpochMs = 3_400_000L,
                )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(10, inserted)
            assertEquals(10, G7ExpectedWindowLedger(context).snapshot().size)
            assertEquals(1, writes.get())
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test fun `reconstruction across a watch reboot records backfillable device downtime`() {
        val ledger = G7ExpectedWindowLedger(context)
        ledger.reconstructMissed(
            sensorId = "sensor-a",
            sessionId = "session-a",
            fromExpectedAt = 1_000L,
            untilExclusive = 2_000L,
            sensorStartAt = null,
            sensorEndAt = null,
            nowEpochMs = 2_000L,
            activeBootId = "10",
        )

        val inserted =
            ledger.reconstructMissed(
                sensorId = "sensor-a",
                sessionId = "session-a",
                fromExpectedAt = 301_000L,
                untilExclusive = 901_000L,
                sensorStartAt = null,
                sensorEndAt = null,
                nowEpochMs = 901_000L,
                activeBootId = "11",
            )

        assertEquals(2, inserted)
        ledger.snapshot().filter { it.expectedAt > 1_000L }.forEach { window ->
            assertEquals(CollectorCycleClassification.DEVICE_OFF_OR_REBOOT_GAP, window.finalResult)
            assertEquals(CollectorWindowTerminalState.DEVICE_UNAVAILABLE, window.terminalState)
            assertTrue(window.recoveryRequired)
        }
    }
}
