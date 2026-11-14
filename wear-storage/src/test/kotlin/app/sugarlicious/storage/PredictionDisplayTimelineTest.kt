package app.sugarlicious.storage

import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.PredictionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionDisplayTimelineTest {
    @Test
    fun `prediction timestamps keep their real spacing after now`() {
        val now = 1_000_000L
        val firstFuture = now + 5 * 60_000L
        val predictions =
            listOf(
                GlucosePrediction(
                    PredictionKind.IOB,
                    listOf(
                        GlucoseSample(118.0, now - 5 * 60_000L),
                        GlucoseSample(120.0, now),
                        GlucoseSample(125.0, firstFuture),
                        GlucoseSample(130.0, firstFuture + 5 * 60_000L),
                    ),
                ),
            )

        val visible = PredictionDisplayTimeline.anchor(predictions, now).single().samples

        assertEquals(now, visible.first().measuredAtEpochMs)
        assertEquals(5 * 60_000L, visible[1].measuredAtEpochMs - visible[0].measuredAtEpochMs)
    }

    @Test
    fun `boundary value is interpolated once when source straddles now`() {
        val now = 1_000_000L
        val visible =
            PredictionDisplayTimeline
                .anchor(
                    listOf(
                        GlucosePrediction(
                            PredictionKind.IOB,
                            listOf(
                                GlucoseSample(100.0, now - 5 * 60_000L),
                                GlucoseSample(120.0, now + 5 * 60_000L),
                            ),
                        ),
                    ),
                    now,
                ).single()
                .samples

        assertEquals(listOf(now, now + 5 * 60_000L), visible.map { it.measuredAtEpochMs })
        assertEquals(110.0, visible.first().valueMgDl, 0.0001)
    }

    @Test
    fun `series without a real future point is hidden instead of moved across now`() {
        val now = 1_000_000L
        val predictions =
            listOf(
                GlucosePrediction(
                    PredictionKind.UAM,
                    listOf(
                        GlucoseSample(120.0, now - 5 * 60_000L),
                        GlucoseSample(121.0, now),
                    ),
                ),
            )

        assertTrue(PredictionDisplayTimeline.anchor(predictions, now).isEmpty())
    }

    @Test
    fun `future window uses the actual latest future timestamp`() {
        val now = 1_000_000L
        val predictions =
            listOf(
                GlucosePrediction(
                    PredictionKind.IOB,
                    listOf(
                        GlucoseSample(120.0, now),
                        GlucoseSample(125.0, now + 5 * 60_000L),
                        GlucoseSample(130.0, now + 10 * 60_000L),
                    ),
                ),
            )

        assertEquals(
            10 * 60_000L,
            PredictionDisplayTimeline.futureWindowMs(predictions, now),
        )
    }
}
