package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmPairingProgressTest {
    @Test fun `recoverable radio retry keeps stable sensor search text`() {
        val state =
            CgmPersistedState(
                collectorEnabled = true,
                protocolState = CgmProtocolState.RECOVERING,
                activeAttemptId = 7L,
                lastError =
                    CgmCollectorError(
                        "G7-GATT-133",
                        true,
                        2_000L,
                        "G7-Kandidat nicht erreichbar · weitere Sensoren werden gesucht",
                    ),
            )
        val retrying = cgmPairingProgress(state, 1_000L)
        assertEquals("Sensor suchen", retrying.message)
        assertFalse(retrying.message.contains("Kandidat"))
        assertTrue(retrying.animate)

        val scanning =
            cgmPairingProgress(
                state.copy(protocolState = CgmProtocolState.SCANNING),
                1_000L,
            )
        assertTrue(scanning.message.contains("Sensor suchen"))
        assertFalse(scanning.message.contains("Kandidat"))
        assertTrue(scanning.animate)
    }

    @Test fun `rejected G7 candidate never replaces the stable search message`() {
        val state =
            CgmPersistedState(
                collectorEnabled = true,
                protocolState = CgmProtocolState.SCANNING,
                activeAttemptId = 7L,
                otherSensorCount = 2,
                lastError = CgmCollectorError("G7-AUTH-MISMATCH", true, 2_000L, "Internal detail never shown"),
            )
        val progress = cgmPairingProgress(state, 1_000L)
        assertTrue(progress.message.contains("Sensor suchen"))
        assertFalse(progress.message.contains("G7 erkannt"))
        assertFalse(progress.message.contains("Kopplungscode"))
        assertFalse(progress.message.contains("Internal detail"))
        assertTrue(progress.animate)
        assertEquals("Weitere Sensoren erkannt: 2", cgmOtherSensorsLabel(state.otherSensorCount))
        assertEquals(null, cgmOtherSensorsLabel(0))
    }

    @Test fun `paused setup and terminal timeout do not animate but explicit preparation does`() {
        val state = CgmPersistedState()
        assertEquals(CgmPairingProgress("Sensorsuche pausiert", false), cgmPairingProgress(state, 1_000L))
        assertTrue(cgmPairingProgress(state, 1_000L, preparing = true).animate)
        val timedOut = state.copy(lastError = CgmCollectorError("G7-PAIRING-TIMEOUT", false, 2_000L, "Verbindung beendet"))
        assertEquals(CgmPairingProgress("Verbindung beendet", false), cgmPairingProgress(timedOut, 1_000L))
    }
}
