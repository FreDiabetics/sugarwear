package app.sugarlicious.mobile

import app.sugarlicious.model.GraphTimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCgmViewportTest {
    @Test fun `visible live graph advances on minute ticks without a new reading`() {
        val minute = 60_000L
        val at1445 = 14L * 60L * minute + 45L * minute
        val at1450 = at1445 + 5L * minute
        val history = 3L * 60L * minute
        val positions =
            (0L..4L).map { elapsedMinutes ->
                val window = GraphTimeWindow.live(at1450 + elapsedMinutes * minute, history)
                window.xFraction(at1445) to window.xFraction(at1450)
            }

        positions.zipWithNext().forEach { (before, after) ->
            assertTrue(after.first < before.first)
            assertTrue(after.second < before.second)
        }
    }

    @Test fun `minute tick aligns to next wall clock minute`() {
        assertEquals(60_000L, delayUntilNextGraphMinute(0L).inWholeMilliseconds)
        assertEquals(59_999L, delayUntilNextGraphMinute(1L).inWholeMilliseconds)
        assertEquals(1L, delayUntilNextGraphMinute(59_999L).inWholeMilliseconds)
        assertEquals(60_000L, delayUntilNextGraphMinute(60_000L).inWholeMilliseconds)
    }

    @Test fun `live viewport shifts every existing timestamp left for each new reading`() {
        val minute = 60_000L
        val history = 3L * 60L * minute
        val at1150 = 11L * 60L * minute + 50L * minute
        val at1155 = at1150 + 5L * minute
        val at1200 = at1155 + 5L * minute
        val at1205 = at1200 + 5L * minute

        val renderA = GraphTimeWindow.live(at1155, history)
        val renderB = GraphTimeWindow.live(at1200, history)
        val renderC = GraphTimeWindow.live(at1205, history)

        assertTrue(renderB.xFraction(at1150) < renderA.xFraction(at1150))
        assertTrue(renderB.xFraction(at1155) < renderA.xFraction(at1155))
        assertTrue(renderB.xFraction(at1200) > renderB.xFraction(at1155))
        assertTrue(renderC.xFraction(at1150) < renderB.xFraction(at1150))
        assertTrue(renderC.xFraction(at1155) < renderB.xFraction(at1155))
        assertTrue(renderC.xFraction(at1200) < renderB.xFraction(at1200))
        assertTrue(renderC.xFraction(at1205) > renderC.xFraction(at1200))
        assertTrue(renderC.xFraction(at1200) != renderC.xFraction(at1205))
    }

    @Test fun `irregular missing duplicate and out of order timestamps keep real spacing`() {
        val minute = 60_000L
        val second = 1_000L
        val end = 12L * 60L * minute + 20L * second
        val window = GraphTimeWindow.live(end, 3L * 60L * minute)
        val unordered =
            listOf(
                12L * 60L * minute + 5L * minute + 19L * second,
                11L * 60L * minute + 50L * minute + 8L * second,
                12L * 60L * minute + 4L * second,
                11L * 60L * minute + 55L * minute + 11L * second,
                12L * 60L * minute + 4L * second,
                11L * 60L * minute + 40L * minute + 8L * second,
            )
        val ordered = unordered.distinct().sorted()
        val positions = ordered.map(window::xFraction)

        assertEquals(ordered.size, positions.distinct().size)
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
        ordered.zipWithNext().zip(positions.zipWithNext()).forEach { (times, xs) ->
            val expected = (times.second - times.first).toDouble() / window.durationMs
            assertEquals(expected, (xs.second - xs.first).toDouble(), 0.000001)
        }
    }

    @Test fun `timestamp positions preserve five minute and two minute spacing`() {
        val start = 12L * 60L * 60_000L
        val end = start + 60L * 60_000L
        val points = listOf(0L, 5L, 10L, 15L).map { timeToXFraction(start + it * 60_000L, start, end) }
        assertEquals(points[1] - points[0], points[2] - points[1], 0.00001f)
        assertEquals(points[2] - points[1], points[3] - points[2], 0.00001f)
        val twoMinutes = timeToXFraction(start + 17L * 60_000L, start, end) - points[3]
        assertTrue(twoMinutes < points[3] - points[2])
    }

    @Test fun `user pan remains stable until explicit return to now`() {
        val viewport = ChartViewport(3).apply { setAvailablePastWindow(12L * 60L * 60_000L) }
        viewport.pan(100f, 300f, referenceNow = 1_000_000L)
        val historicalEnd = viewport.endEpochMs(1_000_000L)
        assertEquals(ChartViewport.Mode.USER_NAVIGATING, viewport.mode)
        assertEquals(historicalEnd, viewport.endEpochMs(1_000_000L + 5L * 60_000L))
        viewport.returnToNow()
        assertEquals(ChartViewport.Mode.LIVE_FOLLOW, viewport.mode)
        assertEquals(1_300_000L, viewport.endEpochMs(1_300_000L))
    }
}
