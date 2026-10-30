package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CollectorCycleTiming
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import app.aapswear.model.DataSourceId
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
class G7UnpairedRuntimeTest {
    @Test fun `expired initial setup stops its armed recovery without deleting code or history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val state =
            G7PersistedState(
                sensor = G7Sensor("new", "new-session"),
                collectorEnabled = true,
                pairingAttemptId = "explicit-request",
                pairingStartedAtEpochMs = now - G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
                pairingDeadlineEpochMs = now,
                nextReconnectEpochMs = now + 60_000L,
            )
        val store = G7SensorStateStore(context)
        store.save(state)
        val preferences = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        preferences.edit().putString("pairing_code", "encrypted-placeholder").commit()
        val old = CgmReading("retained", DataSourceId.DEXCOM_G7_WATCH, "old", "old-session", 123.0, now - 60_000L, now)
        G7ReadingDatabase(context).use { runBlocking { it.insert(old) } }
        G7ReconnectAlarmScheduler.rearmScheduledCycle(
            context,
            CollectorCycleTiming(expectedReadingEpoch = now + 90_000L, requestedReconnectEpoch = now + 60_000L),
            state,
        )

        val result = G7RuntimeReconciler.reconcile(context, G7RuntimeEntryPoint.BOOT, nowEpochMs = now)

        assertEquals(G7RuntimeHealth.INACTIVE, result.restoredHealth)
        assertFalse(store.read().collectorEnabled)
        assertEquals("G7-PAIRING-TIMEOUT", store.read().lastError?.code)
        assertEquals(state.sensor, store.read().sensor)
        assertNull(store.read().nextReconnectEpochMs)
        assertNull(G7CollectorDiagnosticStore(context).pendingScheduledCycle())
        assertEquals("encrypted-placeholder", preferences.getString("pairing_code", null))
        G7ReadingDatabase(context).use { assertTrue(it.query(limit = 300).any { row -> row.id == old.id }) }
        G7RuntimeReconciler.reconcile(context, G7RuntimeEntryPoint.WATCH_APP, nowEpochMs = now + 24 * 60 * 60_000L)
        assertFalse(store.read().collectorEnabled)
        assertNull(G7CollectorDiagnosticStore(context).pendingScheduledCycle())
    }

    @Test fun `enabled state without a configured sensor is quiesced instead of left enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = G7SensorStateStore(context)
        store.save(G7PersistedState(collectorEnabled = true))
        G7RuntimeReconciler.reconcile(context, G7RuntimeEntryPoint.WATCH_APP)
        assertFalse(store.read().collectorEnabled)
        assertNull(store.read().pairingAttemptId)
    }

    @Test fun `temporary credential unavailability does not erase an established receivers enabled state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = G7SensorStateStore(context)
        val paired =
            G7PersistedState(
                sensor = G7Sensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                collectorEnabled = true,
                authenticationState = app.aapswear.g7.G7AuthenticationState.AUTHENTICATED,
            )
        store.save(paired)
        context
            .getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        G7RuntimeReconciler.reconcile(context, G7RuntimeEntryPoint.BOOT)
        assertEquals(paired, store.read())
    }
}
