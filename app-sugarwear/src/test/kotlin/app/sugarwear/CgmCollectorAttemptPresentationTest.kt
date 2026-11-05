package app.sugarwear

import app.sugarlicious.sensor.protocol.*
import org.junit.Assert.*
import org.junit.Test

class CgmCollectorAttemptPresentationTest {
    @Test fun `overview and tiles use identical warning states without progress details`() {
        assertEquals("Datenempfang pausiert", cgmSensorWarning(CgmStatusPillState.PAUSED, true))
        assertEquals("Kein Sensor verbunden", cgmSensorWarning(CgmStatusPillState.NO_ACTIVE_SENSOR, false))
        assertEquals("Keine Daten", cgmSensorWarning(CgmStatusPillState.SIGNAL_LOSS, false))
        assertEquals("Signalverlust", cgmSensorWarning(CgmStatusPillState.SIGNAL_LOSS, true))
        assertNull(cgmSensorWarning(CgmStatusPillState.CONNECTED, true))
    }

    @Test fun `normal connected operation never displays attempt progress`() {
        assertNull(collectorAttemptPresentation(CgmPersistedState(collectorEnabled = true), null, 100_000))
    }

    @Test fun `pause progress expires exactly two minutes after the action`() {
        val action = CgmUiSensorTransition(CgmUiSensorAction.PAUSE, 100_000)
        val state = CgmPersistedState(collectorEnabled = false)
        assertEquals("Datenempfang pausiert", collectorAttemptPresentation(state, action, 219_999)!!.title)
        assertNull(collectorAttemptPresentation(state, action, 220_000))
    }

    @Test fun `resume shows real phases until a new successful sensor connection`() {
        val action = CgmUiSensorTransition(CgmUiSensorAction.RESUME, 100_000)
        val state = CgmPersistedState(collectorEnabled = true, protocolState = CgmProtocolState.SCANNING, lastSuccessfulConnectionEpochMs = 90_000)
        assertEquals("Sensor suchen", collectorAttemptPresentation(state, action, 110_000)!!.phase)
        assertNull(collectorAttemptPresentation(state.copy(lastSuccessfulConnectionEpochMs = 105_000), action, 110_000))
    }
}
