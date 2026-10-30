package app.aapswear.g7watch

import app.aapswear.g7.G7CollectorError
import org.junit.Assert.assertEquals
import org.junit.Test

class G7ErrorNotifierPolicyTest {
    @Test fun `same collector error has stable notification signature`() {
        val first = G7CollectorError("G7-GATT-133", true, 1_000L, "Temporärer BLE-Verbindungsfehler (133)")
        val second = G7CollectorError("G7-GATT-133", true, 2_000L, "Temporärer BLE-Verbindungsfehler (133)")

        assertEquals(g7ErrorSignature(first), g7ErrorSignature(second))
    }

    @Test fun `different causes produce different notification signatures`() {
        val gatt = G7CollectorError("G7-GATT-133", true, 1_000L, "Temporärer BLE-Verbindungsfehler (133)")
        val auth = G7CollectorError("G7-AUTH-204", false, 1_000L, "Sensor hat die Authentifizierung abgelehnt")

        assert(g7ErrorSignature(gatt) != g7ErrorSignature(auth))
    }
}
