package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmRetiredSensorSetupTest {
    @Test fun `only addressless setup without previous session permits retired candidates`() {
        val sensor = CgmSensor("new-setup", "new-session")

        assertTrue(allowsRetiredCgmCandidateForSetup(sensor, false, null))
        assertFalse(allowsRetiredCgmCandidateForSetup(sensor.copy(deviceAddress = "AA:BB:CC:DD:EE:01"), false, null))
        assertFalse(allowsRetiredCgmCandidateForSetup(sensor, true, null))
        assertFalse(allowsRetiredCgmCandidateForSetup(sensor, false, "AA:BB:CC:DD:EE:01"))
    }

    @Test fun `fresh discovery eligibility leaves failed or unauthenticated candidates excluded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val retired = CgmRetiredSensors(context)
        val candidate = "AA:BB:CC:DD:EE:01"
        retired.retire(setOf(candidate))

        assertTrue(retired.excludesDiscovery(candidate, freshSetup = false))
        assertFalse(retired.excludesDiscovery(candidate, freshSetup = true))
        // Discovery and rejection without a shared-key callback must not mutate retirement.
        assertTrue(CgmRetiredSensors(context).contains(candidate))
        assertTrue(retired.excludesDiscovery(candidate, freshSetup = false))
    }

    @Test fun `authenticated reactivation removes only selected address and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val retired = CgmRetiredSensors(context)
        val selected = "AA:BB:CC:DD:EE:01"
        val other = "AA:BB:CC:DD:EE:02"
        retired.retire(setOf(selected, other))

        retired.reactivateAuthenticated(selected.lowercase())

        val restored = CgmRetiredSensors(context)
        assertFalse(restored.contains(selected))
        assertTrue(restored.contains(other))
        assertTrue(restored.excludesDiscovery(other, freshSetup = false))
    }
}
