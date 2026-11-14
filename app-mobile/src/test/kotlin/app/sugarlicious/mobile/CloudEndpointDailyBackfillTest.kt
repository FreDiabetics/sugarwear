package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEndpointDailyBackfillTest {
    private val now =
        java.time.Instant
            .parse("2026-10-30T18:00:00Z")
            .toEpochMilli()
    private val start = localDayStartEpochMs(now)

    @Test fun `empty cache fetches only today including midnight`() {
        assertEquals(listOf((start - 1) to null), cloudEndpointDailyWindows(emptyList(), now))
        assertEquals(listOf((start - 1) to null), cloudEndpointDailyWindows(listOf(start - 300_000), now))
    }

    @Test fun `existing intervals are preserved and only prefix gap and suffix requested`() {
        val first = start + 300_000
        val second = first + 300_000
        val third = second + 900_000
        assertEquals(
            listOf((start - 1) to first, second to third, third to null),
            cloudEndpointDailyWindows(listOf(third, start - 1, first, second, first, now + 1), now),
        )
    }

    @Test fun `minute cadence backfills a missing minute without fetching populated intervals`() {
        assertEquals(
            listOf((start + 60_000) to (start + 180_000), (start + 240_000) to null),
            cloudEndpointDailyWindows(listOf(start, start + 60_000, start + 180_000, start + 240_000), now),
        )
    }

    @Test fun `midnight value is not requested again and no previous day gap leaks`() {
        assertEquals(listOf(start to null), cloudEndpointDailyWindows(listOf(start - 3_600_000, start), now))
        val tomorrow =
            java.time.Instant
                .ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        assertEquals(listOf((tomorrow - 1) to null), cloudEndpointDailyWindows(listOf(start, now), tomorrow + 60_000))
        assertTrue(cloudEndpointDailyWindows(listOf(start, now), now).all { it.first >= start - 1 })
    }
}
