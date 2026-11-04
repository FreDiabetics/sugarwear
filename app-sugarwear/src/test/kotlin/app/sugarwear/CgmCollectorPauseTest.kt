package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSessionManager
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
class CgmCollectorPauseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store by lazy { CgmSensorStateStore(context) }
    private val active =
        CgmPersistedState(
            sensor = CgmSensor("sensor-a", "session-a", "DXCM12", deviceAddress = "AA:BB:CC:DD:EE:FF"),
            collectorEnabled = true,
            nextReconnectEpochMs = 2_000_000L,
        )

    @Before fun reset() {
        store.save(active)
        CgmAlertPolicyStore.setPolicy(context, false)
    }

    @Test fun `late collector snapshots cannot reactivate paused sensor`() {
        val beforePause = store.read()
        val paused = CgmSessionManager(beforePause).stop()
        store.save(paused)
        assertFalse(CgmSensorStateStore(context).saveFromCollector(beforePause))
        assertEquals(paused, store.read())
        assertEquals(beforePause.sensor, store.read().sensor)
        assertEquals(beforePause.lastReading, store.read().lastReading)
        assertEquals(beforePause.authenticationState, store.read().authenticationState)
        assertNull(store.read().nextReconnectEpochMs)
    }

    @Test fun `pre pause callback cannot write after an explicit resume`() {
        val beforePause = store.read()
        val oldGeneration = store.collectorGeneration()
        store.save(CgmSessionManager(beforePause).stop())
        val resumed = CgmSessionManager(store.read()).startCollector()
        store.save(resumed)
        assertTrue(store.read().collectorEnabled)
        assertFalse(store.saveFromCollector(beforePause, oldGeneration))
        assertEquals(resumed, store.read())
        assertTrue(store.saveFromCollector(resumed, store.collectorGeneration()))
    }

    @Test fun `automatic source and recovery cannot enable intentional pause`() {
        store.save(CgmSessionManager(active).stop())
        CgmCollectorService.resumeEnabled(context)
        assertFalse(store.read().collectorEnabled)
        assertEquals(CgmStatusPillState.PAUSED, deriveCgmStatusPillState(store.read(), true))
        org.junit.Assert.assertNull(initialCgmPairingScreenStep(store.read()))
    }

    @Test fun `late scheduling is rejected while paused`() {
        store.save(CgmSessionManager(active).stop())
        var cancelled = false
        try {
            CgmReconnectAlarmScheduler.scheduleRequested(context, System.currentTimeMillis() + 60_000L)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(store.read().collectorEnabled)
    }

    @Test fun `source alarm policy cannot restore alarms while paused`() {
        CgmAlertPolicyStore.setPolicy(context, true)
        assertTrue(CgmAlertPolicyStore.alarmsEnabled(context))
        store.save(CgmSessionManager(active).stop())
        assertFalse(CgmAlertPolicyStore.alarmsEnabled(context))
        store.save(CgmSessionManager(store.read()).startCollector())
        assertTrue(CgmAlertPolicyStore.alarmsEnabled(context))
    }
}
