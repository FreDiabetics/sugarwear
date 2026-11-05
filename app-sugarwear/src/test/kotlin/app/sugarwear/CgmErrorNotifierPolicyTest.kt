package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorError
import org.junit.Assert.assertEquals
import org.junit.Test

class CgmErrorNotifierPolicyTest {
    @Test fun `same collector error has stable notification signature`() {
        val first = CgmCollectorError("G7-GATT-133", true, 1_000L, "Temporärer BLE-Verbindungsfehler (133)")
        val second = CgmCollectorError("G7-GATT-133", true, 2_000L, "Temporärer BLE-Verbindungsfehler (133)")

        assertEquals(cgmErrorSignature(first), cgmErrorSignature(second))
    }

    @Test fun `different causes produce different notification signatures`() {
        val gatt = CgmCollectorError("G7-GATT-133", true, 1_000L, "Temporärer BLE-Verbindungsfehler (133)")
        val auth = CgmCollectorError("G7-AUTH-204", false, 1_000L, "Sensor hat die Authentifizierung abgelehnt")

        assert(cgmErrorSignature(gatt) != cgmErrorSignature(auth))
    }
}
