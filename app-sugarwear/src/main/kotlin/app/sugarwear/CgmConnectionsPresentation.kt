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
    val lifetime = cgmSensorLifetime(state, nowEpochMs)
    return when {
        sensor.state == CgmSensorState.ENDED || lifetime?.graceExpired == true -> "Sensorlaufzeit beendet"
        sensor.state == CgmSensorState.ERROR -> "Sensorfehler"
        sensor.state == CgmSensorState.GRACE_PERIOD || lifetime?.inGracePeriod == true -> "Nachlaufphase"
        lifetime?.expired == true -> "Sensorlaufzeit beendet"
        !state.collectorEnabled -> "Datenempfang pausiert"
        sensor.state == CgmSensorState.WARMUP -> "Sensoraufwärmung"
        state.connectionState == CgmConnectionState.CONNECTED &&
            state.authenticationState == CgmAuthenticationState.AUTHENTICATED -> "Sensor verbunden"
        sensor.state == CgmSensorState.ACTIVE -> "Sensor aktiv"
        else -> "Sensor eingerichtet"
    }
}
