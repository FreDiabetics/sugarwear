package app.sugarwear.debug.playground.demo

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus

/**
 * Creates sample readings and snapshots for Tile rendering previews.
 */
object DemoTileFactory {
    fun createTileReadings(nowEpochMs: Long = System.currentTimeMillis()): List<CgmReading> = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE, nowEpochMs = nowEpochMs)

    fun createLatestReading(nowEpochMs: Long = System.currentTimeMillis()): CgmReading =
        CgmReading(
            id = "DEMO-TILE-READING",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = "DEMO-SENSOR",
            sessionId = "DEMO-SESSION",
            glucoseMgDl = 118.0,
            timestampEpochMs = nowEpochMs - 60_000L,
            receivedAtEpochMs = nowEpochMs,
            deltaMgDl = 1.5,
            trend = Trend.FLAT,
            status = CgmReadingStatus.VALID,
            origin = CgmReadingOrigin.LIVE,
        )
}
