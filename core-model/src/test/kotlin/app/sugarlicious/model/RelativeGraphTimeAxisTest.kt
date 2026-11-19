package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RelativeGraphTimeAxisTest {
    private val now = 100L * RelativeGraphTimeAxis.HOUR_MS

    @Test
    fun `standard graph windows use relative hour labels`() {
        val cases =
            mapOf(
                3 to listOf("3h", "2h", "1h", "jetzt"),
                6 to listOf("6h", "4h", "2h", "jetzt"),
                12 to listOf("12h", "8h", "4h", "jetzt"),
                24 to listOf("24h", "18h", "12h", "6h", "jetzt"),
            )

        cases.forEach { (hours, expected) ->
            val ticks = RelativeGraphTimeAxis.ticks(now - hours * RelativeGraphTimeAxis.HOUR_MS, now, now)
            assertEquals(expected, ticks.map { it.label })
        }
    }

    @Test
    fun `reading anchored live edge keeps now label at visible right edge`() {
        val readingAge = 5L * 60_000L
        val end = now - readingAge
        val start = end - 3L * RelativeGraphTimeAxis.HOUR_MS
        val ticks = RelativeGraphTimeAxis.ticks(start, end, now)

        assertEquals(listOf("3h", "2h", "1h", "jetzt"), ticks.map { it.label })
        assertEquals(end, ticks.last().timestampEpochMs)
    }

    @Test
    fun `panned history stays relative to now and never renders clock text`() {
        val ticks =
            RelativeGraphTimeAxis.ticks(
                startEpochMs = now - 7 * RelativeGraphTimeAxis.HOUR_MS,
                endEpochMs = now - 3 * RelativeGraphTimeAxis.HOUR_MS,
                nowEpochMs = now,
            )

        assertEquals(listOf("6h", "4h"), ticks.map { it.label })
        assertFalse(ticks.any { ':' in it.label })
        assertFalse(ticks.any { it.label == "jetzt" })
    }

    @Test
    fun `prediction future space keeps now as last history label`() {
        val ticks =
            RelativeGraphTimeAxis.ticks(
                startEpochMs = now - 3 * RelativeGraphTimeAxis.HOUR_MS,
                endEpochMs = now + RelativeGraphTimeAxis.HOUR_MS,
                nowEpochMs = now,
            )

        assertEquals(listOf("3h", "2h", "1h", "jetzt"), ticks.map { it.label })
    }
}
