package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorState
import org.junit.Assert.*
import org.junit.Test

class CgmWarmupPresentationTest {

    @Test
    fun `returns NotWarmingUp when sensor is null or collector disabled`() {
        val warmup = CgmWarmupPolicy.evaluate(sensor = null, reading = null, collectorEnabled = false, nowEpochMs = 1_000_000L)
        assertEquals(CgmWarmupPresentation.NotWarmingUp, warmup)
    }

    @Test
    fun `returns NotWarmingUp when sensor is in normal active state`() {
        val sensor = CgmSensor("s1", "ss1", state = CgmSensorState.ACTIVE)
        val warmup = CgmWarmupPolicy.evaluate(sensor = sensor, reading = null, collectorEnabled = true, nowEpochMs = 1_000_000L)
        assertEquals(CgmWarmupPresentation.NotWarmingUp, warmup)
    }

    @Test
    fun `counts down warmup minutes correctly when start time is known`() {
        val now = 1_800_000_000_000L
        val start = now - 10 * 60_000L // 10 minutes elapsed of 30 min warmup -> 20 min remaining
        val sensor = CgmSensor("s1", "ss1", sensorStartEpochMs = start, state = CgmSensorState.WARMUP)

        val warmup = CgmWarmupPolicy.evaluate(sensor = sensor, reading = null, collectorEnabled = true, nowEpochMs = now)
        assertTrue(warmup is CgmWarmupPresentation.CountingDown)
        assertEquals(20L, (warmup as CgmWarmupPresentation.CountingDown).remainingMinutes)
    }

    @Test
    fun `returns AwaitingFirstReading when warmup timer has elapsed but no reading received`() {
        val now = 1_800_000_000_000L
        val start = now - 35 * 60_000L // 35 minutes elapsed -> 0 remaining
        val sensor = CgmSensor("s1", "ss1", sensorStartEpochMs = start, state = CgmSensorState.WARMUP)

        val warmup = CgmWarmupPolicy.evaluate(sensor = sensor, reading = null, collectorEnabled = true, nowEpochMs = now)
        assertEquals(CgmWarmupPresentation.AwaitingFirstReading, warmup)
    }

    @Test
    fun `returns NotWarmingUp as soon as a valid reading arrives`() {
        val now = 1_800_000_000_000L
        val start = now - 10 * 60_000L
        val sensor = CgmSensor("s1", "ss1", sensorStartEpochMs = start, state = CgmSensorState.WARMUP)
        val reading = CgmReading("r1", DataSourceId.DEXCOM_G7_WATCH, "s1", "ss1", 110.0, now, now, status = CgmReadingStatus.VALID)

        val warmup = CgmWarmupPolicy.evaluate(sensor = sensor, reading = reading, collectorEnabled = true, nowEpochMs = now)
        assertEquals(CgmWarmupPresentation.NotWarmingUp, warmup)
    }
}
