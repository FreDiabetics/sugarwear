package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphTimeWindowTest {
    @Test
    fun `prediction boundary may leave a manually panned viewport`() {
        val window = GraphTimeWindow(startEpochMs = 0L, liveEdgeEpochMs = 20_000L, endEpochMs = 10_000L)
        assertTrue(window.xFraction(window.liveEdgeEpochMs) > 1f)
    }

    private val minute = 60_000L
    private val history = 3L * 60L * minute

    @Test
    fun `fake clock moves every existing point left before next reading arrives`() {
        val at1445 = time(14, 45)
        val at1450 = time(14, 50)
        val positions =
            (0L..5L).map { elapsed ->
                GraphTimeWindow.live(at1450 + elapsed * minute, history).let { window ->
                    window.xFraction(at1445) to window.xFraction(at1450)
                }
            }

        positions.zipWithNext().forEach { (before, after) ->
            assertTrue(after.first < before.first)
            assertTrue(after.second < before.second)
        }

        val at1455 = time(14, 55)
        val finalWindow = GraphTimeWindow.live(at1455, history)
        assertTrue(finalWindow.xFraction(at1450) < positions[4].second)
        assertTrue(finalWindow.xFraction(at1455) > finalWindow.xFraction(at1450))
    }

    @Test
    fun `clock is part of the render result even when sample count is unchanged`() {
        val sampleTimes = listOf(time(14, 45), time(14, 50))
        val first = GraphTimeWindow.live(time(14, 50), history)
        val second = GraphTimeWindow.live(time(14, 51), history)

        val firstPositions = sampleTimes.map(first::xFraction)
        val secondPositions = sampleTimes.map(second::xFraction)

        assertEquals(firstPositions.size, secondPositions.size)
        assertTrue(firstPositions.zip(secondPositions).all { (before, after) -> after < before })
    }

    @Test
    fun `event time not receive time determines x position`() {
        val window = GraphTimeWindow.live(time(15, 0), history)
        val measuredAt = time(14, 58)
        val receivedAt = time(15, 0)

        assertTrue(window.xFraction(measuredAt) < window.xFraction(receivedAt))
        assertEquals(1f, window.xFraction(receivedAt), 0.000001f)
    }

    @Test
    fun `distinct five minute timestamps retain distinct plot coordinates at twenty four hours`() {
        val now = time(15, 0)
        val window = GraphTimeWindow.live(now, 24L * 60L * minute)
        val previousX = window.plotX(now - 5L * minute, 0f, 840f)
        val currentX = window.plotX(now, 0f, 840f)

        assertTrue(currentX > previousX)
        assertEquals(840f * 5f / (24f * 60f), currentX - previousX, 0.0001f)
    }

    @Test
    fun `mobile widget and combined widget share identical normalized position`() {
        val now = time(15, 0)
        val timestamp = time(14, 55)
        val mobile = GraphTimeWindow.live(now, history).xFraction(timestamp)
        val graphWidget = GraphTimeWindow.live(now, history).xFraction(timestamp)
        val combinedWidget = GraphTimeWindow.live(now, history).xFraction(timestamp)

        assertEquals(mobile, graphWidget, 0f)
        assertEquals(mobile, combinedWidget, 0f)
    }

    private fun time(
        hour: Int,
        minuteOfHour: Int,
    ): Long = (hour * 60L + minuteOfHour) * minute
}
