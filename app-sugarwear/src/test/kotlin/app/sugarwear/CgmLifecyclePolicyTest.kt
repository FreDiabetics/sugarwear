package app.sugarwear

import android.content.Intent
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReconnectScheduler
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSessionState
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import app.sugarlicious.sensor.protocol.CollectorOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmLifecyclePolicyTest {
    @Test fun `first live publication retains address established during this connection`() {
        val initial = CgmSensor("sensor", "session")
        val authenticated = initial.copy(deviceAddress = "AA:BB:CC:DD:EE:FF")
        val published = sensorForCgmLivePublication(initial, authenticated, app.sugarlicious.sensor.protocol.CgmSensorState.ACTIVE)
        assertEquals(authenticated.deviceAddress, published.deviceAddress)
        assertEquals(app.sugarlicious.sensor.protocol.CgmSensorState.ACTIVE, published.state)
        assertEquals(null, sensorForCgmLivePublication(initial, authenticated.copy(sessionId = "other"), app.sugarlicious.sensor.protocol.CgmSensorState.ACTIVE).deviceAddress)
    }

    @Test fun `automatic restoration can wait only for the same sessions fresh live phase`() {
        val measuredAt = 1_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, measuredAt, measuredAt)
        val state = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("sensor", "session"), lastReading = reading)
        assertTrue(shouldAwaitCgmSensorWindow(state, measuredAt + 120_000L))
        assertFalse(shouldAwaitCgmSensorWindow(state, measuredAt + 270_000L))
        assertFalse(shouldAwaitCgmSensorWindow(state, measuredAt - 1L))
        assertFalse(shouldAwaitCgmSensorWindow(state.copy(lastReading = null), measuredAt))
        assertFalse(shouldAwaitCgmSensorWindow(state.copy(sensor = CgmSensor("new", "new-session")), measuredAt))
        assertFalse(shouldAwaitCgmSensorWindow(state.copy(lastReading = reading.copy(origin = app.sugarlicious.sensor.protocol.CgmReadingOrigin.BACKFILL)), measuredAt))
        assertFalse(shouldAwaitCgmSensorWindow(state.copy(lastReading = reading.copy(status = app.sugarlicious.sensor.protocol.CgmReadingStatus.INVALID)), measuredAt))
    }

    @Test fun `startup after signal loss preserves the already armed current session window`() {
        val now = 10_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, now - 20 * 60_000L, now - 20 * 60_000L)
        val state = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("sensor", "session"), lastReading = reading)
        val expected = now + 90_000L
        val pending = CollectorCycleTiming(expectedWindowId = expectedWindowId("sensor", "session", expected), expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 10_000L)
        assertFalse(shouldAwaitCgmSensorWindow(state, now))
        assertEquals(pending, cgmStartupSensorWindow(state, pending, now))
        assertEquals(pending, cgmStartupSensorWindow(state, pending, pending.requestedReconnectEpoch!! - 1L))
    }

    @Test fun `stale startup rejects foreign expired and inconsistent scheduled envelopes`() {
        val now = 10_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, now - 20 * 60_000L, now - 20 * 60_000L)
        val state = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("sensor", "session"), lastReading = reading)
        val expected = now + 90_000L
        val pending = CollectorCycleTiming(expectedWindowId = expectedWindowId("sensor", "session", expected), expectedReadingEpoch = expected, requestedReconnectEpoch = expected - 10_000L)
        for (bad in listOf(null, pending.copy(expectedWindowId = "other-session"), pending.copy(requestedReconnectEpoch = now), pending.copy(expectedReadingEpoch = now), pending.copy(requestedReconnectEpoch = expected + 1L))) {
            assertNull(cgmStartupSensorWindow(state, bad, now))
        }
        for (bad in listOf(state.copy(collectorEnabled = false), state.copy(lastReading = null), state.copy(sensor = CgmSensor("other", "session")), state.copy(lastReading = reading.copy(origin = app.sugarlicious.sensor.protocol.CgmReadingOrigin.BACKFILL)), state.copy(lastReading = reading.copy(timestampEpochMs = now + 1L)))) {
            assertNull(cgmStartupSensorWindow(bad, pending, now))
        }
    }

    @Test fun `fresh startup without pending envelope restores its original sensor phase`() {
        val measured = 1_000_000L
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, measured, measured)
        val state = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("sensor", "session"), lastReading = reading)
        val restored = cgmStartupSensorWindow(state, null, measured + 269_500L)
        assertEquals(measured + 300_000L, restored?.expectedReadingEpoch)
        assertEquals(measured + 270_000L, restored?.requestedReconnectEpoch)
    }

    @Test fun `Bluetooth restart recovers only an enabled configured collector`() {
        assertTrue(
            shouldRecoverCgmAfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_ON,
                collectorEnabled = true,
                hasSensor = true,
            ),
        )
        assertFalse(
            shouldRecoverCgmAfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_OFF,
                collectorEnabled = true,
                hasSensor = true,
            ),
        )
        assertFalse(
            shouldRecoverCgmAfterBluetoothState(
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED,
                android.bluetooth.BluetoothAdapter.STATE_ON,
                collectorEnabled = false,
                hasSensor = true,
            ),
        )
    }

    @Test fun `pairing survives restoration but expires without renewing its original budget`() {
        val initial = CgmPersistedState(sensor = CgmSensor("sensor"), collectorEnabled = true)
        val started = startCgmPairingAttempt(initial, 1_000L)
        val restored = ensureCgmPairingAttempt(started, 20_000L)
        val expired = ensureCgmPairingAttempt(started, 1_000L + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS)

        assertEquals(1_000L, started.pairingStartedAtEpochMs)
        assertEquals(1_000L + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS, started.pairingDeadlineEpochMs)
        assertEquals(started.pairingAttemptId, restored.pairingAttemptId)
        assertEquals(started.pairingStartedAtEpochMs, restored.pairingStartedAtEpochMs)
        assertEquals(started.pairingDeadlineEpochMs, restored.pairingDeadlineEpochMs)
        assertFalse(expired.collectorEnabled)
        assertNull(expired.nextReconnectEpochMs)
        assertEquals("G7-PAIRING-TIMEOUT", expired.lastError?.code)
        assertEquals(expired, ensureCgmPairingAttempt(expired, 24L * 60 * 60_000L))
        val explicitResume =
            startCgmPairingAttempt(
                app.sugarlicious.sensor.protocol
                    .CgmSessionManager(expired)
                    .startCollector(),
                24L * 60 * 60_000L,
            )
        assertTrue(explicitResume.collectorEnabled)
        assertEquals(24L * 60 * 60_000L + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS, explicitResume.pairingDeadlineEpochMs)

        val discoveredButNotValidated = started.copy(sensor = CgmSensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertFalse(
            ensureCgmPairingAttempt(
                discoveredButNotValidated,
                1_000L + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
            ).collectorEnabled,
        )
    }

    @Test fun `enabled collector without sensor cannot acquire a scan budget`() {
        val stopped = ensureCgmPairingAttempt(CgmPersistedState(collectorEnabled = true), 10_000L)
        assertFalse(stopped.collectorEnabled)
        assertNull(stopped.pairingAttemptId)
        assertNull(stopped.nextReconnectEpochMs)
    }

    @Test fun `restoration cannot invent setup permission from enabled flag and configured sensor alone`() {
        val abandoned = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("setup"))
        val stopped = ensureCgmPairingAttempt(abandoned, 10_000L)
        assertFalse(stopped.collectorEnabled)
        assertEquals("G7-SETUP-001", stopped.lastError?.code)
    }

    @Test fun `upgrading an old repeatedly extended deadline still honors its original start`() {
        val legacy =
            startCgmPairingAttempt(CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("setup")), 1_000L)
                .copy(pairingDeadlineEpochMs = 1_000L + 2 * CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS)
        assertFalse(ensureCgmPairingAttempt(legacy, 1_000L + CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS).collectorEnabled)
    }

    @Test fun `existing same session reading keeps reconnection beyond an old setup deadline`() {
        val reading = CgmReading("live", DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 123.0, 1_000L, 1_000L)
        val known =
            CgmPersistedState(
                collectorEnabled = true,
                sensor = CgmSensor("sensor", "session", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                lastReading = reading,
                pairingStartedAtEpochMs = 500L,
                pairingDeadlineEpochMs = 1_000L,
            )
        assertEquals(known, ensureCgmPairingAttempt(known, 24L * 60 * 60_000L))
    }

    @Test fun `authenticated bonded session without first glucose is not an abandoned setup`() {
        val paired =
            CgmPersistedState(
                collectorEnabled = true,
                sensor = CgmSensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                authenticationState = app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED,
                pairingDeadlineEpochMs = 1_000L,
            )
        assertEquals(paired, ensureCgmPairingAttempt(paired, 2_000L))
        assertFalse(ensureCgmPairingAttempt(paired.copy(authenticationState = app.sugarlicious.sensor.protocol.CgmAuthenticationState.REQUIRED), 2_000L).collectorEnabled)
    }

    @Test fun `runtime restart retains authenticated session authority before its first valid value`() {
        val paired =
            CgmPersistedState(
                collectorEnabled = true,
                sensor = CgmSensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                authenticationState = app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED,
            )
        val reset = resetCgmRuntimeForRestart(paired)
        assertEquals(paired.authenticationState, reset.authenticationState)
        assertTrue(ensureCgmPairingAttempt(reset, 24L * 60 * 60_000L).collectorEnabled)
    }

    @Test fun `collector repairs missing or expired follow up but preserves future alarm`() {
        val now = 1_000_000L
        assertTrue(needsCgmFollowUpRepair(true, null, now))
        assertTrue(needsCgmFollowUpRepair(true, now, now))
        assertTrue(needsCgmFollowUpRepair(true, now - 1L, now))
        assertFalse(needsCgmFollowUpRepair(false, null, now))
        assertFalse(needsCgmFollowUpRepair(true, now + 1L, now))
    }

    @Test fun `enabled collector restores after boot`() {
        assertTrue(shouldRestoreCgmCollector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = true))
    }

    @Test fun `enabled collector restores after app update`() {
        assertTrue(shouldRestoreCgmCollector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = true))
    }

    @Test fun `disabled collector stays disabled after lifecycle broadcasts`() {
        assertFalse(shouldRestoreCgmCollector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = false))
        assertFalse(shouldRestoreCgmCollector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = false))
    }

    @Test fun `unrelated broadcasts never restore collector`() {
        assertFalse(shouldRestoreCgmCollector(Intent.ACTION_SCREEN_ON, collectorEnabled = true))
        assertFalse(shouldRestoreCgmCollector(null, collectorEnabled = true))
    }

    @Test fun `enabled collector keeps foreground runtime between sensor cycles`() {
        assertTrue(shouldKeepCgmRuntimeForeground(collectorEnabled = true))
        assertFalse(shouldKeepCgmRuntimeForeground(collectorEnabled = false))
    }

    @Test fun `scheduled service start consumes its alarm envelope before runtime repair`() {
        assertFalse(shouldRepairCgmRuntimeOnServiceStart(CgmCollectorService.ACTION_RECONNECT))
        assertTrue(shouldRepairCgmRuntimeOnServiceStart(CgmCollectorService.ACTION_RESTART))
        assertTrue(shouldRepairCgmRuntimeOnServiceStart(null))
    }

    @Test fun `source selection cannot enable a user-disabled collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                cgmSelected = true,
                collectorEnabled = false,
            ),
        )
    }

    @Test fun `leaving direct source never stops or restarts collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                cgmSelected = false,
                collectorEnabled = true,
            ),
        )
    }

    @Test fun `direct source signal may only resume an already enabled collector`() {
        assertTrue(
            shouldResumeEnabledCollectorForSourceSignal(
                cgmSelected = true,
                collectorEnabled = true,
            ),
        )
    }

    @Test fun `scheduled cycle stages next five minute safety slot before BLE work`() {
        val expected = 1_000_000L
        val state = CgmPersistedState(collectorEnabled = true)

        assertEquals(
            expected + CgmReconnectScheduler.EXPECTED_READING_INTERVAL_MS - CgmReconnectScheduler.PRECONNECT_LEAD_MS,
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
                CgmPersistedState(collectorEnabled = false),
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
        val sensor = CgmSensor("sensor", "session", deviceAddress = "AA:BB:CC:DD:EE:FF")
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
            CgmPersistedState(
                sensor = sensor,
                collectorEnabled = true,
                collectorOwner = CollectorOwner.WATCH,
                connectionState = CgmConnectionState.CONNECTED,
                protocolState = CgmProtocolState.RECOVERING,
                sessionState = CgmSessionState.WAITING_FOR_NEXT_READING,
                lastReading = reading,
                lastSuccessfulConnectionEpochMs = 1_001L,
                nextReconnectEpochMs = 2_000L,
                retryCount = 4,
                lastError = CgmCollectorError("G7-BLE-107", true, 1_500L, "recoverable"),
                activeAttemptId = 9L,
                scanStartedAtEpochMs = 1_600L,
                scanTimeoutAtEpochMs = 91_600L,
                pairingAttemptId = "pairing-1",
                pairingStartedAtEpochMs = 1_600L,
                pairingDeadlineEpochMs = 91_600L,
            )

        val restarted = resetCgmRuntimeForRestart(original)

        assertTrue(restarted.collectorEnabled)
        assertEquals(CollectorOwner.WATCH, restarted.collectorOwner)
        assertEquals(sensor, restarted.sensor)
        assertEquals(CgmSessionState.WAITING_FOR_NEXT_READING, restarted.sessionState)
        assertEquals(reading, restarted.lastReading)
        assertEquals(1_001L, restarted.lastSuccessfulConnectionEpochMs)
        assertEquals(0, restarted.retryCount)
        assertEquals(null, restarted.nextReconnectEpochMs)
        assertEquals(null, restarted.lastError)
        assertEquals(CgmConnectionState.DISCONNECTED, restarted.connectionState)
        assertEquals(CgmProtocolState.UNINITIALIZED, restarted.protocolState)
        assertEquals("pairing-1", restarted.pairingAttemptId)
        assertEquals(1_600L, restarted.pairingStartedAtEpochMs)
        assertEquals(91_600L, restarted.pairingDeadlineEpochMs)
        assertEquals(91_600L, restarted.scanTimeoutAtEpochMs)
    }

    @Test fun `terminal collector state clears stale scan timestamps`() {
        val state =
            CgmPersistedState(
                scanStartedAtEpochMs = 1_000L,
                scanTimeoutAtEpochMs = 2_000L,
            )

        val terminal = clearCgmScanRuntime(state)

        assertNull(terminal.scanStartedAtEpochMs)
        assertNull(terminal.scanTimeoutAtEpochMs)
    }
}
