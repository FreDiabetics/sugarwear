package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorState
import org.junit.Assert.assertEquals
import org.junit.Test

class CgmConnectionsPresentationTest {
    private val now = 1_000_000_000L
    private val day = 24L * 60L * 60_000L

    private fun state(sensorState: CgmSensorState = CgmSensorState.ACTIVE): CgmPersistedState {
        val sensor = CgmSensor(sensorId = "G7", sessionId = "session-A", state = sensorState, sensorStartEpochMs = now - day, sensorEndEpochMs = now + day)
        return CgmPersistedState(sensor = sensor, collectorEnabled = true)
    }

    @Test
    fun `a saved active sensor does not imply a current BLE connection`() {
        val saved = state().copy(connectionState = CgmConnectionState.DISCONNECTED, authenticationState = CgmAuthenticationState.AUTHENTICATED)
        assertEquals("Sensor aktiv", cgmConnectionsSensorStatus(saved, now))
    }

    @Test
    fun `connected is shown only for the authenticated live BLE connection`() {
        val connected = state().copy(connectionState = CgmConnectionState.CONNECTED, authenticationState = CgmAuthenticationState.AUTHENTICATED)
        assertEquals("Sensor verbunden", cgmConnectionsSensorStatus(connected, now))
        val unauthenticated = connected.copy(authenticationState = CgmAuthenticationState.UNKNOWN)
        assertEquals("Sensor aktiv", cgmConnectionsSensorStatus(unauthenticated, now))
    }

    @Test
    fun `grace period and expiry do not masquerade as an active sensor`() {
        val graceState = state(CgmSensorState.GRACE_PERIOD)
        val grace = graceState.copy(sensor = graceState.sensor?.copy(sensorEndEpochMs = now - 1L))
        assertEquals("Nachlaufphase", cgmConnectionsSensorStatus(grace, now))
        val activeState = state()
        val expired = activeState.copy(sensor = activeState.sensor?.copy(sensorEndEpochMs = now - 1L))
        assertEquals("Sensorlaufzeit beendet", cgmConnectionsSensorStatus(expired, now))
    }

    @Test
    fun `no sensor warmup and pause have distinct states`() {
        assertEquals("Kein aktiver Sensor verbunden", cgmConnectionsSensorStatus(CgmPersistedState(), now))
        assertEquals("Sensoraufwärmung", cgmConnectionsSensorStatus(state(CgmSensorState.WARMUP), now))
        assertEquals("Datenempfang pausiert", cgmConnectionsSensorStatus(state().copy(collectorEnabled = false), now))
    }
}
