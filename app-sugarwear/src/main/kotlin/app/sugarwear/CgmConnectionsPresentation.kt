package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmConnectionState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSensorState

/**
 * Sensor identity, active session and a live BLE connection are different states.
 * In particular, a saved sensor alone never proves an active connection.
 */
internal fun cgmConnectionsSensorStatus(
    state: CgmPersistedState,
    nowEpochMs: Long,
): String {
    val sensor = state.sensor ?: return "Kein aktiver Sensor verbunden"
    return when {
        sensor.state == CgmSensorState.GRACE_PERIOD -> "Nachlaufphase"
        sensor.state == CgmSensorState.ENDED || cgmSensorLifetime(state, nowEpochMs)?.expired == true -> "Sensorlaufzeit beendet"
        sensor.state == CgmSensorState.ERROR -> "Sensorfehler"
        !state.collectorEnabled -> "Datenempfang pausiert"
        sensor.state == CgmSensorState.WARMUP -> "Sensoraufwärmung"
        state.connectionState == CgmConnectionState.CONNECTED &&
            state.authenticationState == CgmAuthenticationState.AUTHENTICATED -> "Sensor verbunden"
        sensor.state == CgmSensorState.ACTIVE -> "Sensor aktiv"
        else -> "Sensor eingerichtet"
    }
}
