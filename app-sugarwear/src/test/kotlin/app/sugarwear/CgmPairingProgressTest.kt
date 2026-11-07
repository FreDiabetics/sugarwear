package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmPairingProgressTest {
    @Test fun `radio failure stays visible while retry phase never masquerades as an active scan`() {
        val state =
            CgmPersistedState(
                collectorEnabled = true,
                protocolState = CgmProtocolState.RECOVERING,
                lastError = CgmCollectorError("G7-GATT-133", true, 2_000L, "Bluetooth-Verbindungsfehler (133)"),
            )
        val waiting = cgmPairingProgress(state, 1_000L)
        assertTrue(waiting.message.contains("133"))
        assertFalse(waiting.animate)
        val scanning = cgmPairingProgress(state.copy(protocolState = CgmProtocolState.SCANNING, activeAttemptId = 1L), 1_000L)
        assertTrue(scanning.message.contains("Sensor suchen"))
        assertTrue(scanning.animate)
        assertFalse(cgmPairingProgress(state, 3_000L).message.contains("133"))
    }

    @Test fun `rejected G7 candidate is visible without claiming Bluetooth scan failure`() {
        val state =
            CgmPersistedState(
                collectorEnabled = true,
                protocolState = CgmProtocolState.SCANNING,
                activeAttemptId = 7L,
                lastError = CgmCollectorError("G7-AUTH-MISMATCH", true, 2_000L, "Internal detail never shown"),
            )
        val progress = cgmPairingProgress(state, 1_000L)
        assertTrue(progress.message.contains("G7 erkannt"))
        assertTrue(progress.message.contains("Kopplungscode prüfen"))
        assertFalse(progress.message.contains("Internal detail"))
        assertTrue(progress.animate)
        assertFalse(cgmPairingProgress(state, 3_000L).message.contains("Kopplungscode"))
    }

    @Test fun `paused setup and terminal timeout do not animate but explicit preparation does`() {
        val state = CgmPersistedState()
        assertEquals(CgmPairingProgress("Sensorsuche pausiert", false), cgmPairingProgress(state, 1_000L))
        assertTrue(cgmPairingProgress(state, 1_000L, preparing = true).animate)
        val timedOut = state.copy(lastError = CgmCollectorError("G7-PAIRING-TIMEOUT", false, 2_000L, "Verbindung beendet"))
        assertEquals(CgmPairingProgress("Verbindung beendet", false), cgmPairingProgress(timedOut, 1_000L))
    }
}
