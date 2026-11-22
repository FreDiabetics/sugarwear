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

    private fun state(sensorState: CgmSensorState = CgmSensorState.ACTIVE): CgmPersistedState =
        CgmPersistedState(
            sensor = CgmSensor(
                sensorId = "G7",
                sessionId = "session-A",
                state = sensorState,
                sensorStartEpochMs = now - day,
                sensorEndEpochMs = now + day,
            ),
            collectorEnabled = true,
        )

    @Test
    fun `a saved active sensor does not imply a current BLE connection`() {
        val state = state().copy(
            connectionState = CgmConnectionState.DISCONNECTED,
            authenticationState = CgmAuthenticationState.AUTHENTICATED,
        )
        assertEquals("Sensor aktiv", cgmConnectionsSensorStatus(state, now))
    }

    @Test
    fun `connected is shown only for the authenticated live BLE connection`() {
        val state = state().copy(
            connectionState = CgmConnectionState.CONNECTED,
            authenticationState = CgmAuthenticationState.AUTHENTICATED,
        )
        assertEquals("Sensor verbunden", cgmConnectionsSensorStatus(state, now))
        assertEquals(
            "Sensor aktiv",
            cgmConnectionsSensorStatus(state.copy(authenticationState = CgmAuthenticationState.UNKNOWN), now),
        )
    }

    @Test
    fun `grace period and expiry do not masquerade as an active sensor`() {
        assertEquals(
            "Nachlaufphase",
            cgmConnectionsSensorStatus(state(CgmSensorState.GRACE_PERIOD).copy(
                sensor = state(CgmSensorState.GRACE_PERIOD).sensor?.copy(sensorEndEpochMs = now - 1L),
            ), now),
        )
        assertEquals(
            "Sensorlaufzeit beendet",
            cgmConnectionsSensorStatus(state().copy(sensor = state().sensor?.copy(sensorEndEpochMs = now - 1L)), now),
        )
    }

    @Test
    fun `no sensor warmup and pause have distinct states`() {
        assertEquals("Kein aktiver Sensor verbunden", cgmConnectionsSensorStatus(CgmPersistedState(), now))
        assertEquals("Sensoraufwärmung", cgmConnectionsSensorStatus(state(CgmSensorState.WARMUP), now))
        assertEquals("Datenempfang pausiert", cgmConnectionsSensorStatus(state().copy(collectorEnabled = false), now))
    }
}
