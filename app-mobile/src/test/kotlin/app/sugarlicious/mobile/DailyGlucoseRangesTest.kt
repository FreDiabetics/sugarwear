package app.sugarlicious.mobile

import app.sugarlicious.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyGlucoseRangesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val midnight =
        LocalDate
            .of(2026, 10, 30)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private fun sample(minute: Long, value: Double) = GlucoseSample(value, midnight + minute * 60_000)

    private fun stats(vararg samples: GlucoseSample, nowMinute: Long = 60): DailyGlucoseRanges = dailyGlucoseRanges(TherapyDisplayState(receivedAtEpochMs = midnight, glucoseHistory = samples.toList()), midnight + nowMinute * 60_000, zone)

    @Test fun `elapsed coverage and exact crossings replace votes per sample`() {
        // 1 minute at 100, then 4-minute ramp to 200, then 4 minutes at 200.
        val result = stats(sample(0, 100.0), sample(1, 100.0), sample(5, 200.0), sample(9, 200.0))
        assertEquals(9 * 60_000L, result.tir.coveredDurationMs)
        assertEquals(47, result.tir.inRange) // 1 + 3.2 of 9 minutes
        assertEquals(29, result.titr.inRange) // 1 + 1.6 of 9 minutes
    }

    @Test fun `long gaps and time since last point never receive a range`() {
        val result = stats(sample(0, 100.0), sample(5, 100.0), sample(35, 200.0), sample(40, 200.0), nowMinute = 120)
        assertEquals(600_000L, result.tir.coveredDurationMs)
        assertEquals(50, result.tir.inRange)
        assertEquals(50, result.tir.above)
        assertNull(stats(sample(5, 100.0)).tir.inRange)
        assertNull(dailyGlucoseRanges(null, midnight, zone).tir.inRange)
    }

    @Test fun `midnight clips real segments and fixed inclusive ranges ignore custom targets`() {
        val result = stats(sample(-2, 60.0), sample(3, 85.0), nowMinute = 3)
        assertEquals(180_000L, result.tir.coveredDurationMs)
        assertEquals(100, result.tir.inRange) // value is exactly 70 at midnight
        assertEquals(100, result.titr.inRange) // lower bound remains 70
        val state = TherapyDisplayState(receivedAtEpochMs = midnight, target = TargetState(lowMgDl = 90.0, highMgDl = 130.0), glucoseHistory = listOf(sample(0, 70.0), sample(5, 70.0)))
        assertEquals(100, dailyGlucoseRanges(state, midnight + 300_000, zone).titr.inRange)
        assertEquals(100, stats(sample(0, 180.0), sample(5, 180.0)).tir.inRange)
        assertEquals(100, stats(sample(0, 140.0), sample(5, 140.0)).titr.inRange)
    }

    @Test fun `invalid measurement and session transition split coverage`() {
        val invalid = sample(2, 120.0).copy(quality = CgmQuality.INVALID)
        assertEquals(0L, stats(sample(0, 100.0), invalid, sample(5, 100.0)).tir.coveredDurationMs)
        assertNull(stats(sample(0, 100.0).copy(sessionId = "a"), sample(5, 100.0).copy(sessionId = "b")).tir.inRange)
        assertNull(stats(sample(0, 100.0), sample(5, Double.NaN)).tir.inRange)
    }

    @Test fun `minute feed detects missed cycles and retains more than 300 samples`() {
        val samples = (0L..600L).filterNot { it in 200..209 }.map { sample(it, 100.0) }
        val result = stats(*samples.toTypedArray(), nowMinute = 600)
        assertEquals(601 - 10, result.tir.sampleCount)
        assertEquals((600 - 11) * 60_000L, result.tir.coveredDurationMs)
        assertEquals(100, result.tir.inRange)
    }

    @Test fun `daylight saving uses calendar midnight and excludes future values`() {
        val day =
            LocalDate
                .of(2026, 10, 25)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val end =
            LocalDate
                .of(2026, 10, 26)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli() - 1
        val state = TherapyDisplayState(receivedAtEpochMs = end, glucoseHistory = listOf(GlucoseSample(100.0, day), GlucoseSample(100.0, day + 300_000), GlucoseSample(200.0, end + 1000)))
        val result = dailyGlucoseRanges(state, end, zone)
        assertEquals(300_000L, result.tir.coveredDurationMs)
        assertEquals(100, result.tir.inRange)
    }

    @Test fun `invalid measurements from another stream do not erase valid coverage`() {
        val unrelated = sample(2, 120.0).copy(source = DataSourceId.OTHER, sessionId = "other", quality = CgmQuality.INVALID)
        val result = stats(sample(0, 100.0), unrelated, sample(5, 100.0))
        assertEquals(300_000L, result.tir.coveredDurationMs)
        assertEquals(100, result.tir.inRange)
    }

    @Test fun `period integrates archive durations rather than averaging daily percentages`() {
        val older = listOf(sample(-2 * 1440, 200.0), sample(-2 * 1440 + 5, 200.0))
        val state = TherapyDisplayState(receivedAtEpochMs = midnight, glucoseHistory = listOf(sample(0, 100.0), sample(5, 100.0), sample(10, 100.0)))
        val result = dailyGlucoseRanges(state, midnight + 600_000, zone, days = 3, archive = older)
        assertEquals(900_000L, result.tir.coveredDurationMs)
        assertEquals(67, result.tir.inRange)
        assertEquals(33, result.tir.above)
        assertEquals(100, dailyGlucoseRanges(state, midnight + 600_000, zone, days = 1, archive = older).tir.inRange)
    }

    @Test fun `ninety day window includes oldest selected calendar day only`() {
        val archived = listOf(sample(-89 * 1440, 100.0), sample(-89 * 1440 + 5, 100.0), sample(-90 * 1440, 200.0), sample(-90 * 1440 + 5, 200.0))
        val result = dailyGlucoseRanges(null, midnight + 600_000, zone, days = 90, archive = archived)
        assertEquals(300_000L, result.tir.coveredDurationMs)
        assertEquals(100, result.tir.inRange)
    }

    @Test fun `persisted invalid archive boundary prevents interpolation through sensor errors`() {
        val archived = listOf(sample(-1440, 100.0), sample(-1438, Double.NaN).copy(quality = CgmQuality.INVALID), sample(-1435, 100.0))
        val result = dailyGlucoseRanges(null, midnight + 600_000, zone, days = 3, archive = archived)
        assertEquals(0L, result.tir.coveredDurationMs)
        assertNull(result.tir.inRange)
    }
}
