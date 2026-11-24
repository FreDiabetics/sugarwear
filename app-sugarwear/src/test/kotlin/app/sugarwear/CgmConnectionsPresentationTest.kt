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
    fun `sensor with verified grace end remains in grace rather than being marked ended`() {
        val current = state()
        val graceStart = now - 60_000L
        val graceState =
            current.copy(
                sensor =
                    current.sensor?.copy(
                        sensorStartEpochMs = graceStart - 10L * day,
                        sensorEndEpochMs = graceStart,
                        graceEndEpochMs = graceStart + 12L * 60L * 60_000L,
                    ),
            )
        assertEquals("Nachlaufphase", cgmConnectionsSensorStatus(graceState, now))
        assertEquals("Sensorlaufzeit beendet", cgmConnectionsSensorStatus(graceState, graceStart + 12L * 60L * 60_000L))
    }

    @Test
    fun `no sensor warmup and pause have distinct states`() {
        assertEquals("Kein Sensor verbunden", cgmConnectionsSensorStatus(CgmPersistedState(), now))
        assertEquals("Sensoraufwärmung", cgmConnectionsSensorStatus(state(CgmSensorState.WARMUP), now))
        assertEquals("Datenempfang pausiert", cgmConnectionsSensorStatus(state().copy(collectorEnabled = false), now))
    }

    @Test
    fun `sensor icon ignores glucose alarms and reflects sensor notices and state`() {
        val active = state()
        assertEquals(R.drawable.ic_sensor_outline, cgmConnectionsSensorIcon(active, now, listOf(SugarWearAlarmType.LOW, SugarWearAlarmType.HIGH)))
        assertEquals(R.drawable.ic_status_signal_loss, cgmConnectionsSensorIcon(active, now, listOf(SugarWearAlarmType.URGENT_LOW, SugarWearAlarmType.SIGNAL_LOSS)))
        assertEquals(R.drawable.ic_status_sensor_error, cgmConnectionsSensorIcon(active, now, listOf(SugarWearAlarmType.TECHNICAL_WARNING)))
        assertEquals(R.drawable.ic_sensorend, cgmConnectionsSensorIcon(active, now, listOf(SugarWearAlarmType.SENSOR_END_24H)))
        assertEquals(R.drawable.ic_status_paused, cgmConnectionsSensorIcon(active.copy(collectorEnabled = false), now, emptyList()))
        assertEquals(R.drawable.ic_status_no_active_sensor, cgmConnectionsSensorIcon(CgmPersistedState(), now, emptyList()))
    }
}
