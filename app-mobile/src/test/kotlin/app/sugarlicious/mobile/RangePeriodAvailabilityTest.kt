package app.sugarlicious.mobile

import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.GlucoseSample
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RangePeriodAvailabilityTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 2)
    private val now =
        today
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun history(days: Int): List<GlucoseSample> =
        (0 until days).flatMap { offset ->
            val time =
                today
                    .minusDays(offset.toLong())
                    .atTime(10, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            listOf(GlucoseSample(120.0, time), GlucoseSample(120.0, time + 300_000))
        }

    @Test fun `thirty days stays disabled with twenty nine measured days then unlocks`() {
        assertEquals(listOf(1, 2, 3, 7, 14), availableRangePeriods(null, history(29), now, zone))
        assertEquals(listOf(1, 2, 3, 7, 14, 30), availableRangePeriods(null, history(30), now, zone))
        assertEquals(OverviewRangePeriodStore.periods, availableRangePeriods(null, history(90), now, zone))
    }

    @Test fun `missing whole day blocks larger periods without fabricating coverage`() {
        val missingYesterday =
            history(30).filterNot {
                java.time.Instant
                    .ofEpochMilli(it.measuredAtEpochMs)
                    .atZone(zone)
                    .toLocalDate() == today.minusDays(1)
            }
        assertEquals(listOf(1), availableRangePeriods(null, missingYesterday, now, zone))
        assertEquals(emptyList<Int>(), availableRangePeriods(null, emptyList(), now, zone))
        assertEquals(emptyList<Int>(), availableRangePeriods(null, history(1).take(1), now, zone))
    }

    @Test fun `invalid samples and future samples never unlock periods`() {
        assertEquals(emptyList<Int>(), availableRangePeriods(null, history(30).map { it.copy(quality = CgmQuality.INVALID) }, now, zone))
        assertEquals(emptyList<Int>(), availableRangePeriods(null, history(30).map { it.copy(measuredAtEpochMs = now + 300_000) }, now, zone))
        val boundary = history(1).first().copy(measuredAtEpochMs = history(1).first().measuredAtEpochMs + 60_000, quality = CgmQuality.INVALID)
        assertEquals(emptyList<Int>(), availableRangePeriods(null, history(1) + boundary, now, zone))
    }

    @Test fun `cycling skips disabled options and wraps only within available periods`() {
        assertEquals(2, nextAvailableRangePeriod(1, listOf(1, 2, 3)))
        assertEquals(1, nextAvailableRangePeriod(3, listOf(1, 2, 3)))
        assertEquals(7, nextAvailableRangePeriod(3, listOf(1, 7)))
        assertEquals(1, nextAvailableRangePeriod(30, listOf(1)))
        assertEquals(1, nextAvailableRangePeriod(1, emptyList()))
    }
}
