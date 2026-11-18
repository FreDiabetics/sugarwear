package app.sugarwear.debug.playground.demo

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import kotlin.math.sin

/**
 * Generates deterministic synthetic glucose readings for playground preview and simulation.
 * Purely synthetic data; isolated from actual sensor inputs or database writes.
 */
object DemoGlucoseFactory {
    private const val DUMMY_SENSOR_ID = "DEMO-G7-SENSOR"
    private const val DUMMY_SESSION_ID = "DEMO-SESSION-1001"

    fun createReadingsForScenario(
        scenario: DemoScenario,
        count: Int = 36, // 3 hours at 5-min intervals
        nowEpochMs: Long = System.currentTimeMillis(),
        intervalMs: Long = 5 * 60_000L,
    ): List<CgmReading> {
        if (scenario == DemoScenario.DISCONNECTED) {
            return emptyList()
        }

        val startTimestamp = nowEpochMs - (count - 1) * intervalMs
        val result = mutableListOf<CgmReading>()

        for (i in 0 until count) {
            val timestamp = startTimestamp + i * intervalMs

            if (scenario == DemoScenario.MISSING_VALUES && (i == 10 || i == 11 || i == 22 || i == 23)) {
                // Skip reading to simulate gap
                continue
            }

            if (scenario == DemoScenario.STALE_DATA && i >= count - 4) {
                // Stop adding readings 20 min before now
                break
            }

            val rawGlucose =
                when (scenario) {
                    DemoScenario.STABLE -> scenario.startGlucoseMgDl + sin(i * 0.3) * 3.0
                    DemoScenario.SLOW_RISE -> scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl
                    DemoScenario.FAST_RISE -> scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl
                    DemoScenario.SLOW_FALL -> scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl
                    DemoScenario.FAST_FALL -> scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl
                    DemoScenario.HYPOGLYCEMIA -> (scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl).coerceAtLeast(scenario.minGlucoseMgDl)
                    DemoScenario.HYPERGLYCEMIA -> (scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl).coerceAtMost(scenario.maxGlucoseMgDl)
                    DemoScenario.FLUCTUATING -> 110.0 + sin(i * 0.4) * 55.0
                    DemoScenario.MISSING_VALUES -> scenario.startGlucoseMgDl + i * scenario.deltaPerIntervalMgDl
                    DemoScenario.STALE_DATA -> scenario.startGlucoseMgDl
                    DemoScenario.DISCONNECTED -> 0.0
                }

            val clampedGlucose = rawGlucose.coerceIn(40.0, 400.0)
            val delta = if (result.isNotEmpty()) clampedGlucose - result.last().glucoseMgDl else 0.0

            val trend =
                when {
                    delta > 5.0 -> Trend.DOUBLE_UP
                    delta > 2.5 -> Trend.SINGLE_UP
                    delta > 1.0 -> Trend.FORTY_FIVE_UP
                    delta < -5.0 -> Trend.DOUBLE_DOWN
                    delta < -2.5 -> Trend.SINGLE_DOWN
                    delta < -1.0 -> Trend.FORTY_FIVE_DOWN
                    else -> Trend.FLAT
                }

            val reading =
                CgmReading(
                    id = "DEMO-$timestamp-$i",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = DUMMY_SENSOR_ID,
                    sessionId = DUMMY_SESSION_ID,
                    glucoseMgDl = clampedGlucose,
                    timestampEpochMs = timestamp,
                    receivedAtEpochMs = timestamp + 100L,
                    deltaMgDl = delta,
                    trend = trend,
                    sequenceNumber = (i + 1).toLong(),
                    status = CgmReadingStatus.VALID,
                    origin = CgmReadingOrigin.LIVE,
                )
            result.add(reading)
        }

        return result
    }
}
