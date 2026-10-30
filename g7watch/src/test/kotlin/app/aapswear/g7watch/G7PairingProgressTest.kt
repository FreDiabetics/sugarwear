package app.aapswear.g7watch

import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7PairingProgressTest {
    @Test fun `radio failure stays visible while retry phase never masquerades as an active scan`() {
        val state =
            G7PersistedState(
                collectorEnabled = true,
                protocolState = G7ProtocolState.RECOVERING,
                lastError = G7CollectorError("G7-GATT-133", true, 2_000L, "Bluetooth-Verbindungsfehler (133)"),
            )
        val waiting = g7PairingProgress(state, 1_000L)
        assertTrue(waiting.message.contains("133"))
        assertFalse(waiting.animate)
        val scanning = g7PairingProgress(state.copy(protocolState = G7ProtocolState.SCANNING, activeAttemptId = 1L), 1_000L)
        assertTrue(scanning.message.contains("Sensor suchen"))
        assertTrue(scanning.animate)
        assertFalse(g7PairingProgress(state, 3_000L).message.contains("133"))
    }

    @Test fun `paused setup and terminal timeout do not animate but explicit preparation does`() {
        val state = G7PersistedState()
        assertEquals(G7PairingProgress("Sensorsuche pausiert", false), g7PairingProgress(state, 1_000L))
        assertTrue(g7PairingProgress(state, 1_000L, preparing = true).animate)
        val timedOut = state.copy(lastError = G7CollectorError("G7-PAIRING-TIMEOUT", false, 2_000L, "Verbindung beendet"))
        assertEquals(G7PairingProgress("Verbindung beendet", false), g7PairingProgress(timedOut, 1_000L))
    }
}
