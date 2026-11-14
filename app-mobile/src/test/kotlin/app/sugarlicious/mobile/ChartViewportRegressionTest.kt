package app.sugarlicious.mobile

import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.RelativeGraphTimeAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportRegressionTest {
    private val hour = 60L * 60_000L
    private val now = 48L * hour

    @Test fun `zero pixel fling frames keep coasting while a blocked nonzero step stops`() {
        val viewport = ChartViewport(6)
        val now = 1_800_000_000_000L
        // At the live edge, a no-motion animation frame is not a blocked pan.
        assertTrue(viewport.panFlingFrame(0f, 1000f, now))
        assertFalse(viewport.panFlingFrame(-10f, 1000f, now))
        assertTrue(viewport.panFlingFrame(20f, 1000f, now))
        val end = viewport.snapshot(now).endEpochMs
        assertTrue(viewport.panFlingFrame(0f, 1000f, now))
        assertEquals(end, viewport.snapshot(now).endEpochMs)
        assertTrue(viewport.panFlingFrame(10f, 1000f, now))
    }

    @Test
    fun `prediction viewport ends at last supplied prediction instead of a fixed window`() {
        val predictions =
            listOf(
                GlucosePrediction(
                    kind = PredictionKind.IOB,
                    samples =
                        listOf(
                            GlucoseSample(120.0, now),
                            GlucoseSample(135.0, now + 37L * 60_000L),
                        ),
                ),
            )

        assertEquals(37L * 60_000L, predictionFutureWindowMs(true, predictions, now))
        assertEquals(0L, predictionFutureWindowMs(false, predictions, now))
    }

    @Test
    fun `prediction axis changes side only while a prediction is in the visible viewport`() {
        val predictions =
            listOf(
                GlucosePrediction(
                    kind = PredictionKind.IOB,
                    samples = listOf(GlucoseSample(valueMgDl = 130.0, measuredAtEpochMs = now + 30L * 60_000L)),
                ),
            )
        val live = GraphViewportSnapshot(now - 6L * hour, now, now + hour)
        val history = GraphViewportSnapshot(now - 12L * hour, now, now - 6L * hour)

        assertTrue(hasVisiblePredictions(predictions, now, live))
        assertTrue(targetScaleOnRight(hasVisiblePredictions(predictions, now, history)))
    }

    @Test
    fun `prediction scale crossfades through the final eight percent of the viewport`() {
        val start = now
        val end = now + hour
        val transition = (hour * 0.08).toLong()

        fun prediction(at: Long) =
            listOf(GlucosePrediction(PredictionKind.IOB, listOf(GlucoseSample(130.0, at))))

        assertEquals(1f, predictionScaleRightBlend(prediction(start - 1L), now, start, end), 0.001f)
        assertEquals(1f, predictionScaleRightBlend(prediction(start), now, start, end), 0.001f)
        assertEquals(0.5f, predictionScaleRightBlend(prediction(start + transition / 2L), now, start, end), 0.03f)
        assertEquals(0f, predictionScaleRightBlend(prediction(start + transition), now, start, end), 0.001f)
        assertEquals(0.5f, predictionScaleRightBlend(prediction(end - transition / 2L), now, start, end), 0.03f)
        assertEquals(1f, predictionScaleRightBlend(prediction(end + 1L), now, start, end), 0.001f)
    }

    @Test
    fun `full history keeps twenty four past hours and appends prediction space`() {
        val viewport = ChartViewport(23)
        viewport.setAvailablePastWindow(48L * hour, now)
        assertEquals(23f, viewport.visibleHours, 0.0001f)

        viewport.setHours(24f)
        assertEquals(24f, viewport.visibleHours, 0.0001f)

        viewport.setHours(100f)
        assertEquals(24f, viewport.visibleHours, 0.0001f)

        viewport.setFutureWindow(2L * hour, now)
        assertEquals(26f, viewport.visibleHours, 0.0001f)
        assertEquals(24f, viewport.hours, 0.0001f)
        assertEquals(now - 24L * hour, viewport.snapshot(now).startEpochMs)
        assertEquals(now + 2L * hour, viewport.snapshot(now).endEpochMs)
    }

    @Test
    fun `pinch to full history keeps predictions beyond the live divider`() {
        val viewport = ChartViewport(12)
        viewport.setAvailablePastWindow(24L * hour, now)
        viewport.setFutureWindow(hour, now)

        viewport.zoom(0.01f, 1f, now)

        assertEquals(24f, viewport.hours, 0.0001f)
        assertEquals(25f, viewport.visibleHours, 0.0001f)
        assertEquals(now - 24L * hour, viewport.snapshot(now).startEpochMs)
        assertEquals(now + hour, viewport.snapshot(now).endEpochMs)
    }

    @Test
    fun `near full history retains the existing combined duration limit`() {
        val viewport = ChartViewport(12)
        viewport.setAvailablePastWindow(24L * hour, now)
        viewport.setHours(23.99f)
        viewport.setFutureWindow(hour, now)

        assertTrue(viewport.visibleHours <= MAX_VISIBLE_GRAPH_HOURS)
    }

    @Test
    fun `full history prediction policy pins the scale to the left`() {
        assertEquals(
            0f,
            resolvePredictionViewportPolicy(24f, predictionsEnabled = true, futureWindowMs = hour)
                .scaleRightBlendOverride,
        )
        assertEquals(
            null,
            resolvePredictionViewportPolicy(24f, predictionsEnabled = false, futureWindowMs = hour)
                .scaleRightBlendOverride,
        )
        assertEquals(
            null,
            resolvePredictionViewportPolicy(24f, predictionsEnabled = true, futureWindowMs = 0L)
                .scaleRightBlendOverride,
        )
    }

    @Test
    fun `invalid restored duration is normalized before rendering`() {
        val viewport = ChartViewport(36)
        viewport.setAvailablePastWindow(48L * hour, now)
        assertEquals(MAX_VISIBLE_GRAPH_HOURS, viewport.visibleHours, 0.0001f)
        assertTrue(viewport.snapshot(now).durationMs <= 24L * hour)
    }

    @Test
    fun `pinch keeps its focal timestamp while constraints permit`() {
        val viewport = ChartViewport(12)
        viewport.setAvailablePastWindow(24L * hour, now)
        viewport.pan(120f, 600f, now)
        val before = viewport.snapshot(now)
        val focal = 0.35f
        val focalBefore = before.startEpochMs + (before.durationMs * focal).toLong()

        viewport.zoom(2f, focal, now)

        val after = viewport.snapshot(now)
        val focalAfter = after.startEpochMs + (after.durationMs * focal).toLong()
        assertTrue(kotlin.math.abs(focalBefore - focalAfter) <= 2L)
        assertTrue(after.visibleHours <= MAX_VISIBLE_GRAPH_HOURS)
    }

    @Test
    fun `extreme pan zoom and fling sized steps never escape viewport bounds`() {
        val viewport = ChartViewport(3)
        viewport.setAvailablePastWindow(36L * hour, now)
        viewport.setFutureWindow(hour, now)
        repeat(12) {
            viewport.pan(if (it % 2 == 0) 100_000f else -100_000f, 400f, now)
            viewport.zoom(if (it % 2 == 0) 0.01f else 4f, 0.4f, now)
            val maximumDuration =
                if (viewport.hours >= MAX_VISIBLE_GRAPH_HOURS) 25L * hour else 24L * hour
            assertTrue(viewport.snapshot(now).durationMs <= maximumDuration)
            assertTrue(viewport.snapshot(now).endEpochMs <= now + hour)
        }
    }

    @Test
    fun `axis and divider derive from each current canonical snapshot`() {
        val viewport = ChartViewport(6)
        viewport.setAvailablePastWindow(24L * hour, now)
        val first = viewport.snapshot(now)
        val firstTicks = RelativeGraphTimeAxis.ticks(first.startEpochMs, first.endEpochMs, first.liveEdgeEpochMs)

        viewport.pan(180f, 360f, now)
        val second = viewport.snapshot(now)
        val secondTicks = RelativeGraphTimeAxis.ticks(second.startEpochMs, second.endEpochMs, second.liveEdgeEpochMs)

        assertNotEquals(first.startEpochMs, second.startEpochMs)
        assertNotEquals(firstTicks.map { it.timestampEpochMs }, secondTicks.map { it.timestampEpochMs })
        assertEquals(now, second.liveEdgeEpochMs)
        assertNotEquals(
            first.xFor(now, 600f),
            second.xFor(now, 600f),
        )
    }

    @Test
    fun `clock boundary scrolls out of the viewport and returns only on explicit follow now`() {
        val viewport = ChartViewport(6).apply { setAvailablePastWindow(24L * hour, now) }

        viewport.pan(100_000f, 600f, now)
        val historical = viewport.snapshot(now)
        assertTrue(historical.xFor(now, 600f) > 600f)
        assertEquals(ChartViewport.Mode.USER_NAVIGATING, viewport.mode)

        viewport.returnToNow()
        val following = viewport.snapshot(now)
        assertEquals(600f, following.xFor(now, 600f), 0.001f)
        assertEquals(ChartViewport.Mode.LIVE_FOLLOW, viewport.mode)
    }

    @Test
    fun `scroll zoom scroll and return preserve one shared time transform`() {
        val viewport = ChartViewport(12).apply { setAvailablePastWindow(24L * hour, now) }
        viewport.pan(120f, 600f, now)
        viewport.zoom(2f, 0.4f, now)
        viewport.pan(-45f, 600f, now)

        val navigated = viewport.snapshot(now)
        val boundaryX = navigated.xFor(now, 600f)
        assertTrue(boundaryX.isFinite())
        assertEquals(boundaryX, navigated.xFor(navigated.liveEdgeEpochMs, 600f), 0f)

        viewport.returnToNow()
        viewport.zoom(0.5f, 0.5f, now)
        val restored = viewport.snapshot(now)
        assertEquals(now, restored.liveEdgeEpochMs)
        assertEquals(ChartViewport.Mode.LIVE_FOLLOW, viewport.mode)
    }

    private fun GraphViewportSnapshot.xFor(
        timestamp: Long,
        width: Float,
    ): Float = (timestamp - startEpochMs).toFloat() / durationMs.toFloat() * width

    @Test
    fun `new data keeps a historical viewport stable`() {
        val viewport = ChartViewport(6)
        viewport.setAvailablePastWindow(24L * hour, now)
        viewport.pan(120f, 360f, now)
        val historicalEnd = viewport.snapshot(now).endEpochMs

        viewport.setAvailablePastWindow(24L * hour, now + 5L * 60_000L)

        assertEquals(historicalEnd, viewport.snapshot(now + 5L * 60_000L).endEpochMs)
    }

    @Test
    fun `semantic restore preserves historical time window and clamps duration`() {
        val original =
            ChartViewport(8).apply {
                setAvailablePastWindow(24L * hour, now)
                pan(150f, 600f, now)
            }
        val saved = original.savedState(now)
        val restored =
            ChartViewport(3).apply {
                setAvailablePastWindow(24L * hour, now)
                restore(saved, now)
            }

        assertEquals(original.snapshot(now).startEpochMs, restored.snapshot(now).startEpochMs)
        assertEquals(original.snapshot(now).endEpochMs, restored.snapshot(now).endEpochMs)
        assertEquals(ChartViewport.Mode.USER_NAVIGATING, restored.mode)

        restored.restore(GraphViewportSavedState(48f, now + 12L * hour), now)
        assertTrue(restored.snapshot(now).durationMs <= 24L * hour)
        assertTrue(restored.snapshot(now).endEpochMs <= now)
    }

    @Test
    fun `saved state retains requested duration while history is temporarily short`() {
        val viewport =
            ChartViewport(6).apply {
                setAvailablePastWindow(1L * hour, now)
            }

        val restored =
            ChartViewport(1).apply {
                restore(viewport.savedState(now), now)
                setAvailablePastWindow(8L * hour, now)
            }

        assertEquals(6f, restored.hours, 0.0001f)
    }
}
