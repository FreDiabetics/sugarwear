package app.sugarwear.debug.playground.demo

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarwear.CgmAlarmScreenState
import app.sugarwear.SugarWearAlarmType

/**
 * Creates isolated test alarm instances for visualization in the Alarm Playground.
 * Does not trigger any audio, vibration, or notification side effects.
 */
object DemoAlarmFactory {
    internal fun createTestAlarmState(
        type: SugarWearAlarmType,
        glucoseMgDl: Double = 60.0,
        trend: Trend = Trend.SINGLE_DOWN,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CgmAlarmScreenState {
        val testReading = CgmReading(
            id = "DEMO-ALARM-READING",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = "DEMO-SENSOR",
            sessionId = "DEMO-SESSION",
            glucoseMgDl = glucoseMgDl,
            timestampEpochMs = nowEpochMs - 60_000L,
            receivedAtEpochMs = nowEpochMs,
            deltaMgDl = -3.0,
            trend = trend,
            status = CgmReadingStatus.VALID,
            origin = CgmReadingOrigin.LIVE,
        )

        return CgmAlarmScreenState(
            type = type,
            visible = true,
            active = true,
            triggerGlucose = glucoseMgDl,
            triggeredAtEpochMs = nowEpochMs - 2 * 60_000L,
            currentReading = testReading,
            snoozeMinutes = 10,
        )
    }
}
