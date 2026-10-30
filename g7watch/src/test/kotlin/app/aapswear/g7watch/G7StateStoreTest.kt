package app.aapswear.g7watch

import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorOwner
import app.aapswear.g7.G7PersistedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7StateStoreTest {
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
        G7SensorStateStore(context).save(G7PersistedState(collectorEnabled = true, collectorOwner = CollectorOwner.WATCH))
        val restored = G7SensorStateStore(context).read()
        assertTrue(restored.collectorEnabled)
        assertEquals(CollectorOwner.WATCH, restored.collectorOwner)
    }

    @Test fun `scanner accepts only current G7 advertising families`() {
        assertTrue(isG7AdvertisedName("DXCM12"))
        assertTrue(isG7AdvertisedName("DX01AB"))
        assertTrue(isG7AdvertisedName("DX02CD"))
        assertFalse(isG7AdvertisedName("DXCM-OLD-SENSOR-NAME-TOO-LONG"))
        assertFalse(isG7AdvertisedName("Dexcom"))
    }
}
