package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7Sensor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class G7SensorMetadataStoreTest {
    private val sensor = G7Sensor("local-session", deviceAddress = "AA:BB:CC:DD:EE:01")

    @Test fun `metadata is scoped to both current sensor and address`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        G7CredentialStore(context).clearAll()
        val store = G7SensorMetadataStore(context)
        store.save(sensor, "123456789012")
        assertEquals("123456789012", G7SensorMetadataStore(context).serialCandidate(sensor))
        assertNull(store.serialCandidate(sensor.copy(sensorId = "new-session")))
        assertNull(store.serialCandidate(sensor.copy(deviceAddress = "AA:BB:CC:DD:EE:02")))
        assertEquals("local-session", sensor.sensorId)
    }

    @Test fun `missing metadata does not cause repeated requests every measurement or restart`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        G7CredentialStore(context).clearAll()
        val store = G7SensorMetadataStore(context)
        assertTrue(store.beginRead(sensor, 1000))
        assertFalse(G7SensorMetadataStore(context).beginRead(sensor, 301000))
        assertTrue(store.beginRead(sensor.copy(sensorId = "next"), 301000))
    }

    @Test fun `full sensor unlink removes metadata with credentials`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = G7SensorMetadataStore(context)
        store.save(sensor, "123456789012")
        G7CredentialStore(context).clearAll()
        assertNull(store.serialCandidate(sensor))
        assertTrue(store.beginRead(sensor, 1000))
    }
}
