package app.sugarlicious.wear

import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearGlucoseChartTest {
    @Test
    fun `wear graph advances a fixed reading with the wall clock`() {
        val measuredAt = 1_786_889_891_000L
        val first = wearChartTimeWindow(measuredAt, measuredAt, 3, false)
        val later = wearChartTimeWindow(measuredAt + 60_000L, measuredAt, 3, false)

        assertEquals(1f, first.xFraction(measuredAt), 0.0001f)
        assertTrue(later.xFraction(measuredAt) < first.xFraction(measuredAt))
    }

    @Test
    fun `prediction horizon extends graph without removing cgm history`() {
        val current = 1_786_889_891_000L
        val predictionEnd = current + 120L * 60_000L + 15_144L

        val window =
            wearChartTimeWindow(
                timelineNow = current,
                predictionEnd = predictionEnd,
                durationHours = 2,
                showPredictions = true,
            )

        assertEquals(current - 2L * 60L * 60_000L, window.startEpochMs)
        assertEquals(current, window.liveEdgeEpochMs)
        assertEquals(predictionEnd, window.endEpochMs)
        assertTrue(window.xFraction(current) in 0f..1f)
    }

    @Test
    fun `disabled predictions keep current measurement at graph end`() {
        val current = 1_786_889_891_000L

        val window =
            wearChartTimeWindow(
                timelineNow = current,
                predictionEnd = current + 3L * 60L * 60_000L,
                durationHours = 2,
                showPredictions = false,
            )

        assertEquals(current - 2L * 60L * 60_000L, window.startEpochMs)
        assertEquals(current, window.endEpochMs)
    }

    @Test
    fun `timeline continues moving after the cached prediction horizon`() {
        val lastPrediction = 1_786_889_891_000L
        val later = lastPrediction + 20L * 60_000L

        val window =
            wearChartTimeWindow(
                timelineNow = later,
                predictionEnd = lastPrediction,
                durationHours = 2,
                showPredictions = true,
            )

        assertEquals(later - 2L * 60L * 60_000L, window.startEpochMs)
        assertEquals(later, window.endEpochMs)
    }

    @Test
    fun `wear graph ignores target value and target history but still reacts to display range`() {
        val now = 1_786_889_891_000L
        val first =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                target = TargetState(lowMgDl = 80.0, highMgDl = 160.0, valueMgDl = 100.0),
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 100.0,
                            startedAtEpochMs = now - 60L * 60_000L,
                            endsAtEpochMs = now,
                        ),
                    ),
            )
        val targetOnlyChanged =
            first.copy(
                target = first.target?.copy(valueMgDl = 130.0, temporary = true),
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 130.0,
                            startedAtEpochMs = now - 30L * 60_000L,
                            endsAtEpochMs = now + 30L * 60_000L,
                            temporary = true,
                        ),
                    ),
            )
        val rangeChanged =
            targetOnlyChanged.copy(
                target = targetOnlyChanged.target?.copy(lowMgDl = 85.0),
            )

        assertEquals(wearChartStateSignature(first), wearChartStateSignature(targetOnlyChanged))
        assertNotEquals(wearChartStateSignature(first), wearChartStateSignature(rangeChanged))
    }
}
