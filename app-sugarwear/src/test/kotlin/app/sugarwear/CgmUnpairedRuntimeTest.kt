package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CollectorCycleTiming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmUnpairedRuntimeTest {
    @Test fun `expired initial setup stops its armed recovery without deleting code or history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val state =
            CgmPersistedState(
                sensor = CgmSensor("new", "new-session"),
                collectorEnabled = true,
                pairingAttemptId = "explicit-request",
                pairingStartedAtEpochMs = now - CGM_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
                pairingDeadlineEpochMs = now,
                nextReconnectEpochMs = now + 60_000L,
            )
        val store = CgmSensorStateStore(context)
        store.save(state)
        val preferences = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        preferences.edit().putString("pairing_code", "encrypted-placeholder").commit()
        val old = CgmReading("retained", DataSourceId.DEXCOM_G7_WATCH, "old", "old-session", 123.0, now - 60_000L, now)
        CgmReadingDatabase(context).use { runBlocking { it.insert(old) } }
        CgmReconnectAlarmScheduler.rearmScheduledCycle(
            context,
            CollectorCycleTiming(expectedReadingEpoch = now + 90_000L, requestedReconnectEpoch = now + 60_000L),
            state,
        )

        val result = CgmRuntimeReconciler.reconcile(context, CgmRuntimeEntryPoint.BOOT, nowEpochMs = now)

        assertEquals(CgmRuntimeHealth.INACTIVE, result.restoredHealth)
        assertFalse(store.read().collectorEnabled)
        assertEquals("G7-PAIRING-TIMEOUT", store.read().lastError?.code)
        assertEquals(state.sensor, store.read().sensor)
        assertNull(store.read().nextReconnectEpochMs)
        assertNull(CgmCollectorDiagnosticStore(context).pendingScheduledCycle())
        assertEquals("encrypted-placeholder", preferences.getString("pairing_code", null))
        CgmReadingDatabase(context).use { assertTrue(it.query(limit = 300).any { row -> row.id == old.id }) }
        CgmRuntimeReconciler.reconcile(context, CgmRuntimeEntryPoint.WATCH_APP, nowEpochMs = now + 24 * 60 * 60_000L)
        assertFalse(store.read().collectorEnabled)
        assertNull(CgmCollectorDiagnosticStore(context).pendingScheduledCycle())
    }

    @Test fun `enabled state without a configured sensor is quiesced instead of left enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CgmSensorStateStore(context)
        store.save(CgmPersistedState(collectorEnabled = true))
        CgmRuntimeReconciler.reconcile(context, CgmRuntimeEntryPoint.WATCH_APP)
        assertFalse(store.read().collectorEnabled)
        assertNull(store.read().pairingAttemptId)
    }

    @Test fun `temporary credential unavailability does not erase an established receivers enabled state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CgmSensorStateStore(context)
        val paired =
            CgmPersistedState(
                sensor = CgmSensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                collectorEnabled = true,
                authenticationState = app.sugarlicious.sensor.protocol.CgmAuthenticationState.AUTHENTICATED,
            )
        store.save(paired)
        context
            .getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmRuntimeReconciler.reconcile(context, CgmRuntimeEntryPoint.BOOT)
        assertEquals(paired, store.read())
    }
}
