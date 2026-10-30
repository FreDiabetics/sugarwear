package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SessionManager
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7CollectorPauseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store by lazy { G7SensorStateStore(context) }
    private val active =
        G7PersistedState(
            sensor = G7Sensor("sensor-a", "session-a", "DXCM12", deviceAddress = "AA:BB:CC:DD:EE:FF"),
            collectorEnabled = true,
            nextReconnectEpochMs = 2_000_000L,
        )

    @Before fun reset() {
        store.save(active)
        G7AlertPolicyStore.setPolicy(context, false)
    }

    @Test fun `late collector snapshots cannot reactivate paused sensor`() {
        val beforePause = store.read()
        val paused = G7SessionManager(beforePause).stop()
        store.save(paused)
        assertFalse(G7SensorStateStore(context).saveFromCollector(beforePause))
        assertEquals(paused, store.read())
        assertEquals(beforePause.sensor, store.read().sensor)
        assertEquals(beforePause.lastReading, store.read().lastReading)
        assertEquals(beforePause.authenticationState, store.read().authenticationState)
        assertNull(store.read().nextReconnectEpochMs)
    }

    @Test fun `pre pause callback cannot write after an explicit resume`() {
        val beforePause = store.read()
        val oldGeneration = store.collectorGeneration()
        store.save(G7SessionManager(beforePause).stop())
        val resumed = G7SessionManager(store.read()).startCollector()
        store.save(resumed)
        assertTrue(store.read().collectorEnabled)
        assertFalse(store.saveFromCollector(beforePause, oldGeneration))
        assertEquals(resumed, store.read())
        assertTrue(store.saveFromCollector(resumed, store.collectorGeneration()))
    }

    @Test fun `automatic source and recovery cannot enable intentional pause`() {
        store.save(G7SessionManager(active).stop())
        G7CollectorService.resumeEnabled(context)
        assertFalse(store.read().collectorEnabled)
        assertEquals(G7StatusPillState.PAUSED, deriveG7StatusPillState(store.read(), true))
        org.junit.Assert.assertNull(initialG7PairingScreenStep(store.read()))
    }

    @Test fun `late scheduling is rejected while paused`() {
        store.save(G7SessionManager(active).stop())
        var cancelled = false
        try {
            G7ReconnectAlarmScheduler.scheduleRequested(context, System.currentTimeMillis() + 60_000L)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(store.read().collectorEnabled)
    }

    @Test fun `source alarm policy cannot restore alarms while paused`() {
        G7AlertPolicyStore.setPolicy(context, true)
        assertTrue(G7AlertPolicyStore.alarmsEnabled(context))
        store.save(G7SessionManager(active).stop())
        assertFalse(G7AlertPolicyStore.alarmsEnabled(context))
        store.save(G7SessionManager(store.read()).startCollector())
        assertTrue(G7AlertPolicyStore.alarmsEnabled(context))
    }
}
