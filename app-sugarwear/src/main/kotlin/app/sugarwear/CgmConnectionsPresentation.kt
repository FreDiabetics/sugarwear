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
    val sensor = state.sensor ?: return "Kein Sensor verbunden"
    val lifetime = cgmSensorLifetime(state, nowEpochMs)
    return when {
        sensor.state == CgmSensorState.ENDED || lifetime?.graceExpired == true -> "Sensorlaufzeit beendet"
        sensor.state == CgmSensorState.ERROR -> "Sensorfehler"
        sensor.state == CgmSensorState.GRACE_PERIOD || lifetime?.inGracePeriod == true -> "Nachlaufphase"
        lifetime?.expired == true -> "Sensorlaufzeit beendet"
        !state.collectorEnabled -> "Datenempfang pausiert"
        sensor.state == CgmSensorState.WARMUP -> "Sensoraufwärmung"
        cgmConnectionsHasSignalLoss(state, nowEpochMs) -> "Signalverlust"
        state.connectionState == CgmConnectionState.CONNECTED &&
            state.authenticationState == CgmAuthenticationState.AUTHENTICATED -> "Sensor verbunden"
        sensor.state == CgmSensorState.ACTIVE -> "Sensor aktiv"
        else -> "Sensor eingerichtet"
    }
}


internal fun cgmConnectionsSensorIcon(
    state: CgmPersistedState,
    nowEpochMs: Long,
    notices: List<SugarWearAlarmType>,
): Int {
    val sensor = state.sensor ?: return R.drawable.ic_status_no_active_sensor
    val lifetime = cgmSensorLifetime(state, nowEpochMs)
    return when {
        sensor.state == CgmSensorState.ENDED || lifetime?.graceExpired == true -> R.drawable.ic_sensorend
        !state.collectorEnabled -> R.drawable.ic_status_paused
        sensor.state == CgmSensorState.ERROR ||
            SugarWearAlarmType.TECHNICAL_WARNING in notices ||
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM in notices -> R.drawable.ic_status_sensor_error
        SugarWearAlarmType.SIGNAL_LOSS in notices ||
            cgmConnectionsHasSignalLoss(state, nowEpochMs) -> R.drawable.ic_status_signal_loss
        notices.any { it.isSensorLifetime } -> R.drawable.ic_sensorend
        else -> R.drawable.ic_sensor_outline
    }
}

private fun cgmConnectionsHasSignalLoss(state: CgmPersistedState, nowEpochMs: Long): Boolean =
    state.lastReading?.takeIf { cgmReadingBelongsToSession(state, it) }
        ?.let { isCgmSignalLoss(it.timestampEpochMs, nowEpochMs) } == true
