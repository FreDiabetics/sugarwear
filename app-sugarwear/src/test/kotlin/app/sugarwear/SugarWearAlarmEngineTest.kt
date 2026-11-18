package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.junit.Assert.*
import org.junit.Test

class SugarWearAlarmEngineTest {
    @Test
    fun `active alarm retains original glucose trend and measurement across later values`() {
        val first = reading(65.0).copy(trend = app.sugarlicious.model.Trend.SINGLE_DOWN)
        val settings = SugarWearAlarmDefaults.defaultSettings()
        val initial = SugarWearAlarmEngine.evaluate(first, null, 0, 0, emptyMap(), settings, 1_000_000L)
        val later = first.copy(id = "second", glucoseMgDl = 60.0, timestampEpochMs = 1_300_000L, trend = app.sugarlicious.model.Trend.SINGLE_UP)
        val updated = SugarWearAlarmEngine.evaluate(later, null, 0, 0, initial.updatedInstances, settings, 1_300_000L)
        val alarm = updated.updatedInstances.getValue(SugarWearAlarmType.LOW)
        assertEquals(65.0, alarm.triggerGlucoseMgDl!!, 0.0)
        assertEquals(first.timestampEpochMs, alarm.measuredAtEpochMs)
    }

    private fun reading(glucose: Double, trendRate: Double? = null, predicted: Double? = null, status: CgmReadingStatus = CgmReadingStatus.VALID) =
        CgmReading(
            id = "test-1",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = "sensor-1",
            sessionId = "session-1",
            glucoseMgDl = glucose,
            trendRateMgDlPerMinute = trendRate,
            predictedMgDl = predicted,
            status = status,
            timestampEpochMs = 1_000_000L,
            receivedAtEpochMs = 1_000_000L,
        )

    @Test
    fun `urgent low triggers at or below 55 mg dl`() {
        val settings = SugarWearAlarmDefaults.defaultSettings()

        // 56 mg/dL -> no urgent low
        val res56 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(56.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertFalse(res56.newlyActiveTypes.contains(SugarWearAlarmType.URGENT_LOW))

        // 55 mg/dL -> urgent low triggers
        val res55 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(55.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertTrue(res55.newlyActiveTypes.contains(SugarWearAlarmType.URGENT_LOW))

        // 54 mg/dL -> urgent low triggers
        val res54 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(54.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertTrue(res54.newlyActiveTypes.contains(SugarWearAlarmType.URGENT_LOW))
    }

    @Test
    fun `high alarm delay works correctly`() {
        val settings = SugarWearAlarmDefaults.defaultSettings() // High delay default 15 min
        val start = 1_000_000L

        // Initial high reading -> PENDING_DELAY
        val res1 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(160.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = start,
            )
        val high1 = res1.updatedInstances[SugarWearAlarmType.HIGH]!!
        assertEquals(SugarWearAlarmState.PENDING_DELAY, high1.state)
        assertFalse(res1.newlyActiveTypes.contains(SugarWearAlarmType.HIGH))

        // 14 min elapsed -> still PENDING_DELAY
        val res14 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(160.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = res1.updatedInstances,
                settings = settings,
                nowEpochMs = start + 14 * 60_000L,
            )
        val high14 = res14.updatedInstances[SugarWearAlarmType.HIGH]!!
        assertEquals(SugarWearAlarmState.PENDING_DELAY, high14.state)

        // 15 min elapsed -> ACTIVE
        val res15 =
            SugarWearAlarmEngine.evaluate(
                reading = reading(160.0),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = res14.updatedInstances,
                settings = settings,
                nowEpochMs = start + 15 * 60_000L,
            )
        val high15 = res15.updatedInstances[SugarWearAlarmType.HIGH]!!
        assertEquals(SugarWearAlarmState.ACTIVE, high15.state)
        assertTrue(res15.newlyActiveTypes.contains(SugarWearAlarmType.HIGH))
    }

    @Test
    fun `rapid rise and fall check both rate and additional glucose limit`() {
        val rules = SugarWearAlarmDefaults.defaultSettings().rules.toMutableMap()
        rules[SugarWearAlarmType.RAPID_RISE] = SugarWearAlarmDefaults.defaultRule(SugarWearAlarmType.RAPID_RISE).copy(enabled = true)
        val settings = SugarWearAlarmSettings(rules = rules)

        // Rate >= 3.0, but glucose 95 <= 100 -> NO RAPID RISE
        val resLow =
            SugarWearAlarmEngine.evaluate(
                reading = reading(95.0, trendRate = 3.5),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertFalse(resLow.newlyActiveTypes.contains(SugarWearAlarmType.RAPID_RISE))

        // Rate >= 3.0 AND glucose 105 > 100 -> RAPID RISE
        val resHigh =
            SugarWearAlarmEngine.evaluate(
                reading = reading(105.0, trendRate = 3.5),
                technicalError = null,
                signalLossDurationMinutes = 0,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertTrue(resHigh.newlyActiveTypes.contains(SugarWearAlarmType.RAPID_RISE))
    }

    @Test
    fun `signal loss and temporary sensor problem timing`() {
        val rules = SugarWearAlarmDefaults.defaultSettings().rules.toMutableMap()
        rules[SugarWearAlarmType.SIGNAL_LOSS] = SugarWearAlarmDefaults.defaultRule(SugarWearAlarmType.SIGNAL_LOSS).copy(enabled = true, durationMinutes = 20)
        rules[SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM] = SugarWearAlarmDefaults.defaultRule(SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM).copy(enabled = true, durationMinutes = 20)
        val settings = SugarWearAlarmSettings(rules = rules)

        // 19 min signal loss -> no alarm
        val res19 =
            SugarWearAlarmEngine.evaluate(
                reading = null,
                technicalError = null,
                signalLossDurationMinutes = 19,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertFalse(res19.newlyActiveTypes.contains(SugarWearAlarmType.SIGNAL_LOSS))

        // 20 min signal loss -> alarm
        val res20 =
            SugarWearAlarmEngine.evaluate(
                reading = null,
                technicalError = null,
                signalLossDurationMinutes = 20,
                sensorErrorDurationMinutes = 0,
                previousState = emptyMap(),
                settings = settings,
                nowEpochMs = 1_000_000L,
            )
        assertTrue(res20.newlyActiveTypes.contains(SugarWearAlarmType.SIGNAL_LOSS))
    }
}
