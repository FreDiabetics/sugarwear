package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressArcSegmentsTest {
    @Test fun `unknown and endpoints retain honest progress without negative arcs`() {
        assertEquals(0f, progressArcSegments(null, 80f, 7f).fillSweep, .001f)
        assertEquals(280f, progressArcSegments(Float.NaN, 80f, 7f).trackSweep, .001f)
        assertEquals(280f, progressArcSegments(0f, 80f, 7f).trackSweep, .001f)
        assertEquals(280f, progressArcSegments(1f, 80f, 7f).fillSweep, .001f)
        assertEquals(0f, progressArcSegments(1f, 80f, 7f).trackSweep, .001f)
    }

    @Test fun `larger therapy separation changes only the remaining arc not progress or endpoints`() {
        val original = progressArcSegments(.5f, 80f, 7f)
        val wider = progressArcSegments(.5f, 80f, 7f, capClearanceStrokeRatio = 1.4f)
        assertEquals(original.fillSweep, wider.fillSweep, .001f)
        assertTrue(wider.trackStartOffset > original.trackStartOffset)
        assertEquals(280f, wider.trackStartOffset + wider.trackSweep, .001f)
        assertEquals(0f, progressArcSegments(1f, 80f, 7f, capClearanceStrokeRatio = 1.4f).trackSweep, .001f)
    }

    @Test fun `middle gap exceeds both round caps and preserves the filled value angle`() {
        val arcs = progressArcSegments(.5f, 80f, 7f)
        assertEquals(140f, arcs.fillSweep, .001f)
        val capAngle = Math.toDegrees((7.0 / ((80.0 - 7.0) / 2.0))).toFloat()
        assertTrue(arcs.trackStartOffset - arcs.fillSweep > capAngle)
        assertEquals(280f, arcs.trackStartOffset + arcs.trackSweep, .001f)
        assertTrue(progressArcSegments(.999f, 80f, 7f).trackSweep >= 0f)
    }
}
