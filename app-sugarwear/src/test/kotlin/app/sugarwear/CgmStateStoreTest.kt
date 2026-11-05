package app.sugarwear

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CollectorOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmStateStoreTest {
    @Test
    fun `pairing code is masked outside the pairing editor`() {
        assertEquals("••••", maskedPairingCode("1234"))
        assertEquals("—", maskedPairingCode(null))
    }

    @Test
    fun `pairing code is revealed only for an explicit in-memory reveal state`() {
        assertEquals("••••", pairingCodeDisplayValue("1234", revealed = false))
        assertEquals("1234", pairingCodeDisplayValue("1234", revealed = true))
        assertEquals("—", pairingCodeDisplayValue(null, revealed = true))
    }

    @Test fun `collector state survives process recreation`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        CgmSensorStateStore(context).save(CgmPersistedState(collectorEnabled = true, collectorOwner = CollectorOwner.WATCH))
        val restored = CgmSensorStateStore(context).read()
        assertTrue(restored.collectorEnabled)
        assertEquals(CollectorOwner.WATCH, restored.collectorOwner)
    }

    @Test fun `scanner accepts only current G7 advertising families`() {
        assertTrue(isCgmAdvertisedName("DXCM12"))
        assertTrue(isCgmAdvertisedName("DX01AB"))
        assertTrue(isCgmAdvertisedName("DX02CD"))
        assertFalse(isCgmAdvertisedName("DXCM-OLD-SENSOR-NAME-TOO-LONG"))
        assertFalse(isCgmAdvertisedName("Dexcom"))
    }
}
