package app.sugarwear.debug.playground.demo

import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarwear.CgmOverviewSnapshot
import app.sugarwear.CgmOverviewState

/**
 * Creates isolated home screen overview states for playground preview and test rendering.
 */
object DemoHomeStateFactory {
    internal fun createOverviewState(
        readings: List<CgmReading>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmOverviewState {
        val lastReading = readings.lastOrNull()
        val dummySensor = CgmSensor(
            sensorId = "DEMO-G7-SENSOR",
            sessionId = "DEMO-SESSION-1001",
        )

        val persistedState = CgmPersistedState(
            sensor = dummySensor,
            lastReading = lastReading,
            collectorEnabled = true,
        )

        val snapshot = CgmOverviewSnapshot(
            persisted = persistedState,
            credentialsPresent = true,
            graphReadings = readings,
            transition = null,
        )

        return CgmOverviewState(
            now = nowEpochMs,
            snapshot = snapshot,
            error = null,
        )
    }
}
