package app.sugarlicious.model

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyTimeInRangeTest {
    @Test
    fun `endpoint readings cannot hide a day long signal gap`() {
        val midnight = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val samples = listOf(GlucoseSample(100.0, midnight), GlucoseSample(100.0, midnight + 86_100_000L))
        val day = DailyTimeInRange.calculate(samples, midnight + 86_400_000L, ZoneId.of("UTC"), CgmThresholds.DEFAULT)[3]
        assertEquals(600_000L, day.coveredMillis)
        assertNull(day.percent)
    }

    @Test
    fun `neighbouring days cannot make a midday fragment look complete`() {
        val midnight = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val samples =
            listOf(
                GlucoseSample(100.0, midnight - 3_600_000),
                GlucoseSample(100.0, midnight + 12 * 3_600_000),
                GlucoseSample(100.0, midnight + 25 * 3_600_000),
            )
        assertNull(DailyTimeInRange.calculate(samples, midnight + 26 * 3_600_000, ZoneId.of("UTC"), CgmThresholds.DEFAULT)[3].percent)
    }

    @Test
    fun `today uses observed minutes since midnight and the selected target range`() {
        val midnight = Instant.parse("2026-10-24T00:00:00Z").toEpochMilli()
        val samples = listOf(GlucoseSample(100.0, midnight), GlucoseSample(170.0, midnight + 300_000))
        val thresholds = CgmThresholds(highMgDl = 160.0, lowMgDl = 80.0)
        assertEquals(50, DailyTimeInRange.calculate(samples, midnight + 600_000, ZoneId.of("UTC"), thresholds).last().percent)
    }

    @Test
    fun `late archive start must not masquerade as a daily percentage`() {
        val midnight = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val days =
            DailyTimeInRange.calculate(
                (1..5).map { GlucoseSample(100.0, midnight - it * 300_000L) },
                midnight + 3_600_000,
                ZoneId.of("UTC"),
                CgmThresholds.DEFAULT,
            )
        assertNull(days[3].percent)
        assertEquals(1_500_000L, days[3].coveredMillis)
    }

    @Test
    fun `five calendar days retain missing days and ignore duplicates invalid and future readings`() {
        val now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
        val first = GlucoseSample(100.0, now - 600_000)
        val samples = listOf(first, first, GlucoseSample(200.0, now - 300_000), first.copy(quality = CgmQuality.INVALID), GlucoseSample(100.0, now + 60_000))
        val days = DailyTimeInRange.calculate(samples, now, ZoneId.of("UTC"), CgmThresholds.DEFAULT)
        assertEquals(5, days.size)
        assertEquals("2026-10-19", days.first().date.toString())
        assertNull(days.first().percent)
        assertNull(days.last().percent)
        assertEquals(600_000L, days.last().coveredMillis)
    }

    @Test
    fun `gaps are not filled and midnight splits observed minutes across local days`() {
        val midnight = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val days = DailyTimeInRange.calculate(listOf(GlucoseSample(100.0, midnight - 120_000)), midnight + 3_600_000, ZoneId.of("UTC"), CgmThresholds.DEFAULT)
        assertEquals(120_000L, days[3].coveredMillis)
        assertEquals(180_000L, days[4].coveredMillis)
        assertNull(days[4].percent)
    }
}
