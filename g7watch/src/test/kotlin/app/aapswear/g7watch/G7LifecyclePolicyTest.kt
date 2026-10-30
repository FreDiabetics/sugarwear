package app.aapswear.g7watch

import android.content.Intent
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.CollectorOwner
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7ReconnectScheduler
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SessionState
import app.aapswear.model.DataSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class G7LifecyclePolicyTest {
    @Test fun `first live publication retains address established during this connection`() {
        val initial = G7Sensor("sensor", "session")
        val authenticated = initial.copy(deviceAddress = "AA:BB:CC:DD:EE:FF")
        val published = sensorForG7LivePublication(initial, authenticated, app.aapswear.g7.G7SensorState.ACTIVE)
        assertEquals(authenticated.deviceAddress, published.deviceAddress)
        assertEquals(app.aapswear.g7.G7SensorState.ACTIVE, published.state)
        assertEquals(null, sensorForG7LivePublication(initial, authenticated.copy(sessionId = "other"), app.aapswear.g7.G7SensorState.ACTIVE).deviceAddress)
    }

    @Test fun `automatic restoration can wait only for the same sessions fresh live phase`() {
        val measuredAt = 1_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, measuredAt, measuredAt)
        val state = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("sensor", "session"), lastReading = reading)
        assertTrue(shouldAwaitG7SensorWindow(state, measuredAt + 120_000L))
        assertFalse(shouldAwaitG7SensorWindow(state, measuredAt + 270_000L))
        assertFalse(shouldAwaitG7SensorWindow(state, measuredAt - 1L))
        assertFalse(shouldAwaitG7SensorWindow(state.copy(lastReading = null), measuredAt))
        assertFalse(shouldAwaitG7SensorWindow(state.copy(sensor = G7Sensor("new", "new-session")), measuredAt))
        assertFalse(shouldAwaitG7SensorWindow(state.copy(lastReading = reading.copy(origin = app.aapswear.g7.CgmReadingOrigin.BACKFILL)), measuredAt))
        assertFalse(shouldAwaitG7SensorWindow(state.copy(lastReading = reading.copy(status = app.aapswear.g7.CgmReadingStatus.INVALID)), measuredAt))
    }

    @Test fun `startup after signal loss preserves the already armed current session window`() {
        val now = 10_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, now - 20 * 60_000L, now - 20 * 60_000L)
        val state = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("sensor", "session"), lastReading = reading)
        val expected = now + 90_000L
        val pending = CollectorCycleTiming(expectedWindowId = expectedWindowId("sensor", "session", expected), expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 10_000L)
        assertFalse(shouldAwaitG7SensorWindow(state, now))
        assertEquals(pending, g7StartupSensorWindow(state, pending, now))
        assertEquals(pending, g7StartupSensorWindow(state, pending, pending.requestedReconnectEpoch!! - 1L))
    }

    @Test fun `stale startup rejects foreign expired and inconsistent scheduled envelopes`() {
        val now = 10_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, now - 20 * 60_000L, now - 20 * 60_000L)
        val state = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("sensor", "session"), lastReading = reading)
        val expected = now + 90_000L
        val pending = CollectorCycleTiming(expectedWindowId = expectedWindowId("sensor", "session", expected), expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 10_000L)
        for (bad in listOf(null, pending.copy(expectedWindowId = "other-session"), pending.copy(requestedReconnectEpoch = now), pending.copy(expectedReadingEpoch = now), pending.copy(requestedReconnectEpoch = expected + 1L))) {
            assertNull(g7StartupSensorWindow(state, bad, now))
        }
        for (bad in listOf(state.copy(collectorEnabled = false), state.copy(lastReading = null), state.copy(sensor = G7Sensor("other", "session")), state.copy(lastReading = reading.copy(origin = app.aapswear.g7.CgmReadingOrigin.BACKFILL)), state.copy(lastReading = reading.copy(timestampEpochMs = now + 1L)))) {
            assertNull(g7StartupSensorWindow(bad, pending, now))
        }
    }

    @Test fun `fresh startup without pending envelope restores its original sensor phase`() {
        val measured = 1_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, measured, measured)
        val state = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("sensor", "session"), lastReading = reading)
        val restored = g7StartupSensorWindow(state, null, measured + 269_500L)
        assertEquals(measured + 300_000L, restored?.expectedReadingEpoch)
        assertEquals(measured + 270_000L, restored?.requestedReconnectEpoch)
    }

    @Test fun `Bluetooth restart recovers only an enabled configured collector`() {
        assertTrue(
            shouldRecoverG7AfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_ON,
                collectorEnabled = true,
                hasSensor = true,
            ),
        )
        assertFalse(
            shouldRecoverG7AfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_OFF,
                collectorEnabled = true,
                hasSensor = true,
            ),
        )
        assertFalse(
            shouldRecoverG7AfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_ON,
                collectorEnabled = false,
                hasSensor = true,
            ),
        )
    }

    @Test fun `pairing survives restoration but expires without renewing its original budget`() {
        val initial = G7PersistedState(sensor = G7Sensor("sensor"), collectorEnabled = true)
        val started = startG7PairingAttempt(initial, 1_000L)
        val restored = ensureG7PairingAttempt(started, 20_000L)
        val expired = ensureG7PairingAttempt(started, 1_000L + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS)

        assertEquals(1_000L, started.pairingStartedAtEpochMs)
        assertEquals(1_000L + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS, started.pairingDeadlineEpochMs)
        assertEquals(started.pairingAttemptId, restored.pairingAttemptId)
        assertEquals(started.pairingStartedAtEpochMs, restored.pairingStartedAtEpochMs)
        assertEquals(started.pairingDeadlineEpochMs, restored.pairingDeadlineEpochMs)
        assertFalse(expired.collectorEnabled)
        assertNull(expired.nextReconnectEpochMs)
        assertEquals("G7-PAIRING-TIMEOUT", expired.lastError?.code)
        assertEquals(expired, ensureG7PairingAttempt(expired, 24L * 60 * 60_000L))
        val explicitResume =
            startG7PairingAttempt(
                app.aapswear.g7
                    .G7SessionManager(expired)
                    .startCollector(),
                24L * 60 * 60_000L,
            )
        assertTrue(explicitResume.collectorEnabled)
        assertEquals(24L * 60 * 60_000L + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS, explicitResume.pairingDeadlineEpochMs)

        val discoveredButNotValidated = started.copy(sensor = G7Sensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertFalse(
            ensureG7PairingAttempt(
                discoveredButNotValidated,
                1_000L + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
            ).collectorEnabled,
        )
    }

    @Test fun `enabled collector without sensor cannot acquire a scan budget`() {
        val stopped = ensureG7PairingAttempt(G7PersistedState(collectorEnabled = true), 10_000L)
        assertFalse(stopped.collectorEnabled)
        assertNull(stopped.pairingAttemptId)
        assertNull(stopped.nextReconnectEpochMs)
    }

    @Test fun `restoration cannot invent setup permission from enabled flag and configured sensor alone`() {
        val abandoned = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("setup"))
        val stopped = ensureG7PairingAttempt(abandoned, 10_000L)
        assertFalse(stopped.collectorEnabled)
        assertEquals("G7-SETUP-001", stopped.lastError?.code)
    }

    @Test fun `upgrading an old repeatedly extended deadline still honors its original start`() {
        val legacy =
            startG7PairingAttempt(G7PersistedState(collectorEnabled = true, sensor = G7Sensor("setup")), 1_000L)
                .copy(pairingDeadlineEpochMs = 1_000L + 2 * G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS)
        assertFalse(ensureG7PairingAttempt(legacy, 1_000L + G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS).collectorEnabled)
    }

    @Test fun `existing same session reading keeps reconnection beyond an old setup deadline`() {
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, 1_000L, 1_000L)
        val known =
            G7PersistedState(
                collectorEnabled = true,
                sensor = G7Sensor("sensor", "session", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                lastReading = reading,
                pairingStartedAtEpochMs = 500L,
                pairingDeadlineEpochMs = 1_000L,
            )
        assertEquals(known, ensureG7PairingAttempt(known, 24L * 60 * 60_000L))
    }

    @Test fun `authenticated bonded session without first glucose is not an abandoned setup`() {
        val paired =
            G7PersistedState(
                collectorEnabled = true,
                sensor = G7Sensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                authenticationState = app.aapswear.g7.G7AuthenticationState.AUTHENTICATED,
                pairingDeadlineEpochMs = 1_000L,
            )
        assertEquals(paired, ensureG7PairingAttempt(paired, 2_000L))
        assertFalse(ensureG7PairingAttempt(paired.copy(authenticationState = app.aapswear.g7.G7AuthenticationState.REQUIRED), 2_000L).collectorEnabled)
    }

    @Test fun `runtime restart retains authenticated session authority before its first valid value`() {
        val paired =
            G7PersistedState(
                collectorEnabled = true,
                sensor = G7Sensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                authenticationState = app.aapswear.g7.G7AuthenticationState.AUTHENTICATED,
            )
        val reset = resetG7RuntimeForRestart(paired)
        assertEquals(paired.authenticationState, reset.authenticationState)
        assertTrue(ensureG7PairingAttempt(reset, 24L * 60 * 60_000L).collectorEnabled)
    }

    @Test fun `collector repairs missing or expired follow up but preserves future alarm`() {
        val now = 1_000_000L
        assertTrue(needsG7FollowUpRepair(true, null, now))
        assertTrue(needsG7FollowUpRepair(true, now, now))
        assertTrue(needsG7FollowUpRepair(true, now - 1L, now))
        assertFalse(needsG7FollowUpRepair(false, null, now))
        assertFalse(needsG7FollowUpRepair(true, now + 1L, now))
    }

    @Test fun `enabled collector restores after boot`() {
        assertTrue(shouldRestoreG7Collector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = true))
    }

    @Test fun `enabled collector restores after app update`() {
        assertTrue(shouldRestoreG7Collector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = true))
    }

    @Test fun `disabled collector stays disabled after lifecycle broadcasts`() {
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = false))
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = false))
    }

    @Test fun `unrelated broadcasts never restore collector`() {
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_SCREEN_ON, collectorEnabled = true))
        assertFalse(shouldRestoreG7Collector(null, collectorEnabled = true))
    }

    @Test fun `enabled collector keeps foreground runtime between sensor cycles`() {
        assertTrue(shouldKeepG7RuntimeForeground(collectorEnabled = true))
        assertFalse(shouldKeepG7RuntimeForeground(collectorEnabled = false))
    }

    @Test fun `scheduled service start consumes its alarm envelope before runtime repair`() {
        assertFalse(shouldRepairG7RuntimeOnServiceStart(G7CollectorService.ACTION_RECONNECT))
        assertTrue(shouldRepairG7RuntimeOnServiceStart(G7CollectorService.ACTION_RESTART))
        assertTrue(shouldRepairG7RuntimeOnServiceStart(null))
    }

    @Test fun `source selection cannot enable a user-disabled collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = true,
                collectorEnabled = false,
            ),
        )
    }

    @Test fun `leaving direct source never stops or restarts collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = false,
                collectorEnabled = true,
            ),
        )
    }

    @Test fun `direct source signal may only resume an already enabled collector`() {
        assertTrue(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = true,
                collectorEnabled = true,
            ),
        )
    }

    @Test fun `scheduled cycle stages next five minute safety slot before BLE work`() {
        val expected = 1_000_000L
        val state = G7PersistedState(collectorEnabled = true)

        assertEquals(
            expected + G7ReconnectScheduler.EXPECTED_READING_INTERVAL_MS - G7ReconnectScheduler.PRECONNECT_LEAD_MS,
            nextSafetyReconnectEpoch(
                CollectorCycleTiming(expectedReadingEpoch = expected),
                state,
                nowEpochMs = expected - 30_000L,
            ),
        )
    }

    @Test fun `disabled collector never stages safety reconnect`() {
        assertNull(
            nextSafetyReconnectEpoch(
                CollectorCycleTiming(expectedReadingEpoch = 1_000_000L),
                G7PersistedState(collectorEnabled = false),
                nowEpochMs = 900_000L,
            ),
        )
    }

    @Test fun `direct reconnect wakes close to the expected reading while scan strategy keeps its lead`() {
        val requested = 970_000L
        val expected = 1_000_000L

        assertEquals(
            990_000L,
            alignReconnectRequestToStrategy(requested, expected, directReconnect = true),
        )
        assertEquals(
            requested,
            alignReconnectRequestToStrategy(requested, expected, directReconnect = false),
        )
    }

    @Test fun `failed scheduled cycle retains only the staged next real sensor slot`() {
        val current = CollectorCycleTiming(expectedReadingEpoch = 1_000_000L)
        val safety =
            CollectorCycleTiming(
                expectedReadingEpoch = 1_300_000L,
                requestedReconnectEpoch = 1_295_000L,
            )

        assertEquals(safety, stagedSafetyCycle(current, safety))
        assertNull(
            stagedSafetyCycle(
                current,
                safety.copy(expectedReadingEpoch = 1_100_000L),
            ),
        )
    }

    @Test fun `rearming direct cycle from the same requested and expected times does not drift`() {
        val expected = 1_000_000L
        val firstRequested = alignReconnectRequestToStrategy(970_000L, expected, directReconnect = true)
        val rearmedRequested = alignReconnectRequestToStrategy(firstRequested, expected, directReconnect = true)

        assertEquals(990_000L, firstRequested)
        assertEquals(firstRequested, rearmedRequested)
    }

    @Test fun `restart resets only volatile runtime and retains enabled sensor session and history`() {
        val sensor = G7Sensor("sensor", "session", deviceAddress = "AA:BB:CC:DD:EE:FF")
        val reading =
            CgmReading(
                id = "reading",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor",
                sessionId = "session",
                glucoseMgDl = 123.0,
                timestampEpochMs = 1_000L,
                receivedAtEpochMs = 1_001L,
                sequenceNumber = 7L,
            )
        val original =
            G7PersistedState(
                sensor = sensor,
                collectorEnabled = true,
                collectorOwner = CollectorOwner.WATCH,
                connectionState = G7ConnectionState.CONNECTED,
                protocolState = G7ProtocolState.RECOVERING,
                sessionState = G7SessionState.WAITING_FOR_NEXT_READING,
                lastReading = reading,
                lastSuccessfulConnectionEpochMs = 1_001L,
                nextReconnectEpochMs = 2_000L,
                retryCount = 4,
                lastError = G7CollectorError("G7-BLE-107", true, 1_500L, "recoverable"),
                activeAttemptId = 9L,
                scanStartedAtEpochMs = 1_600L,
                scanTimeoutAtEpochMs = 91_600L,
                pairingAttemptId = "pairing-1",
                pairingStartedAtEpochMs = 1_600L,
                pairingDeadlineEpochMs = 91_600L,
            )

        val restarted = resetG7RuntimeForRestart(original)

        assertTrue(restarted.collectorEnabled)
        assertEquals(CollectorOwner.WATCH, restarted.collectorOwner)
        assertEquals(sensor, restarted.sensor)
        assertEquals(G7SessionState.WAITING_FOR_NEXT_READING, restarted.sessionState)
        assertEquals(reading, restarted.lastReading)
        assertEquals(1_001L, restarted.lastSuccessfulConnectionEpochMs)
        assertEquals(0, restarted.retryCount)
        assertEquals(null, restarted.nextReconnectEpochMs)
        assertEquals(null, restarted.lastError)
        assertEquals(G7ConnectionState.DISCONNECTED, restarted.connectionState)
        assertEquals(G7ProtocolState.UNINITIALIZED, restarted.protocolState)
        assertEquals("pairing-1", restarted.pairingAttemptId)
        assertEquals(1_600L, restarted.pairingStartedAtEpochMs)
        assertEquals(91_600L, restarted.pairingDeadlineEpochMs)
        assertEquals(91_600L, restarted.scanTimeoutAtEpochMs)
    }

    @Test fun `terminal collector state clears stale scan timestamps`() {
        val state =
            G7PersistedState(
                scanStartedAtEpochMs = 1_000L,
                scanTimeoutAtEpochMs = 2_000L,
            )

        val terminal = clearG7ScanRuntime(state)

        assertNull(terminal.scanStartedAtEpochMs)
        assertNull(terminal.scanTimeoutAtEpochMs)
    }
}
