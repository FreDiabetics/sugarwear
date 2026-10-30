package app.aapswear.g7watch

import app.aapswear.g7.*
import org.junit.Assert.*
import org.junit.Test

class G7CollectorAttemptPresentationTest {
    @Test fun `overview and tiles use identical warning states without progress details`() {
        assertEquals("Datenempfang pausiert", g7SensorWarning(G7StatusPillState.PAUSED, true))
        assertEquals("Kein Sensor verbunden", g7SensorWarning(G7StatusPillState.NO_ACTIVE_SENSOR, false))
        assertEquals("Keine Daten", g7SensorWarning(G7StatusPillState.SIGNAL_LOSS, false))
        assertEquals("Signalverlust", g7SensorWarning(G7StatusPillState.SIGNAL_LOSS, true))
        assertNull(g7SensorWarning(G7StatusPillState.CONNECTED, true))
    }

    @Test fun `normal connected operation never displays attempt progress`() {
        assertNull(collectorAttemptPresentation(G7PersistedState(collectorEnabled = true), null, 100_000))
    }

    @Test fun `pause progress expires exactly two minutes after the action`() {
        val action = G7UiSensorTransition(G7UiSensorAction.PAUSE, 100_000)
        val state = G7PersistedState(collectorEnabled = false)
        assertEquals("Datenempfang pausiert", collectorAttemptPresentation(state, action, 219_999)!!.title)
        assertNull(collectorAttemptPresentation(state, action, 220_000))
    }

    @Test fun `resume shows real phases until a new successful sensor connection`() {
        val action = G7UiSensorTransition(G7UiSensorAction.RESUME, 100_000)
        val state = G7PersistedState(collectorEnabled = true, protocolState = G7ProtocolState.SCANNING, lastSuccessfulConnectionEpochMs = 90_000)
        assertEquals("Sensor suchen", collectorAttemptPresentation(state, action, 110_000)!!.phase)
        assertNull(collectorAttemptPresentation(state.copy(lastSuccessfulConnectionEpochMs = 105_000), action, 110_000))
    }
}
