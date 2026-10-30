package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7Sensor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7RetiredSensorSetupTest {
    @Test fun `only addressless setup without previous session permits retired candidates`() {
        val sensor = G7Sensor("new-setup", "new-session")

        assertTrue(allowsRetiredG7CandidateForSetup(sensor, false, null))
        assertFalse(allowsRetiredG7CandidateForSetup(sensor.copy(deviceAddress = "AA:BB:CC:DD:EE:01"), false, null))
        assertFalse(allowsRetiredG7CandidateForSetup(sensor, true, null))
        assertFalse(allowsRetiredG7CandidateForSetup(sensor, false, "AA:BB:CC:DD:EE:01"))
    }

    @Test fun `fresh discovery eligibility leaves failed or unauthenticated candidates excluded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val retired = G7RetiredSensors(context)
        val candidate = "AA:BB:CC:DD:EE:01"
        retired.retire(setOf(candidate))

        assertTrue(retired.excludesDiscovery(candidate, freshSetup = false))
        assertFalse(retired.excludesDiscovery(candidate, freshSetup = true))
        // Discovery and rejection without a shared-key callback must not mutate retirement.
        assertTrue(G7RetiredSensors(context).contains(candidate))
        assertTrue(retired.excludesDiscovery(candidate, freshSetup = false))
    }

    @Test fun `authenticated reactivation removes only selected address and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val retired = G7RetiredSensors(context)
        val selected = "AA:BB:CC:DD:EE:01"
        val other = "AA:BB:CC:DD:EE:02"
        retired.retire(setOf(selected, other))

        retired.reactivateAuthenticated(selected.lowercase())

        val restored = G7RetiredSensors(context)
        assertFalse(restored.contains(selected))
        assertTrue(restored.contains(other))
        assertTrue(restored.excludesDiscovery(other, freshSetup = false))
    }
}
