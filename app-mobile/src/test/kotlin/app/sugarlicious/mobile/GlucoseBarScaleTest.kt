package app.sugarlicious.mobile

import app.sugarlicious.model.CgmThresholds
import org.junit.Assert.*
import org.junit.Test

class GlucoseBarScaleTest {
    @Test fun `current interval fills the bar independently of history`() {
        val thresholds = CgmThresholds.DEFAULT
        val low = glucoseBarState(55.0, thresholds)!!
        assertEquals(GlucoseBarRange.LOW, low.range)
        assertEquals(.5f, low.position, .0001f)
        assertEquals(.5f, glucoseBarState(125.0, thresholds)!!.position, .0001f)
        assertEquals(GlucoseBarRange.TARGET, glucoseBarState(125.0, thresholds)!!.range)
        assertEquals(.5f, glucoseBarState(290.0, thresholds)!!.position, .0001f)
        assertEquals(GlucoseBarRange.HIGH, glucoseBarState(290.0, thresholds)!!.range)
    }

    @Test fun `boundaries follow canonical classification and clamp display endpoints`() {
        val thresholds = CgmThresholds.DEFAULT
        assertEquals(GlucoseBarRange.LOW, glucoseBarState(70.0, thresholds)!!.range)
        assertEquals(1f, glucoseBarState(70.0, thresholds)!!.position)
        assertEquals(GlucoseBarRange.HIGH, glucoseBarState(180.0, thresholds)!!.range)
        assertEquals(0f, glucoseBarState(180.0, thresholds)!!.position)
        assertEquals(0f, glucoseBarState(20.0, thresholds)!!.position)
        assertEquals(1f, glucoseBarState(500.0, thresholds)!!.position)
        assertNull(glucoseBarState(null, thresholds))
        assertNull(glucoseBarState(Double.NaN, thresholds))
        assertNull(glucoseBarState(Double.POSITIVE_INFINITY, thresholds))
        assertNull(glucoseBarState(120.0, thresholds.copy(lowMgDl = 500.0)))
    }

    @Test fun `configured thresholds define interval mapping`() {
        val thresholds = CgmThresholds.DEFAULT.copy(lowMgDl = 80.0, highMgDl = 160.0)
        assertEquals(.5f, glucoseBarState(60.0, thresholds)!!.position, .0001f)
        assertEquals(.5f, glucoseBarState(120.0, thresholds)!!.position, .0001f)
        assertEquals(.5f, glucoseBarState(280.0, thresholds)!!.position, .0001f)
    }

    @Test fun `a collapsed outer interval does not suppress other valid intervals`() {
        val low = CgmThresholds.DEFAULT.copy(veryLowMgDl = 30.0, lowMgDl = 40.0)
        assertEquals(GlucoseBarRange.TARGET, glucoseBarState(120.0, low)!!.range)
        assertEquals(GlucoseBarRange.HIGH, glucoseBarState(250.0, low)!!.range)
        assertEquals(0f, glucoseBarState(40.0, low)!!.position)
        val high = CgmThresholds.DEFAULT.copy(highMgDl = 400.0, veryHighMgDl = 450.0)
        assertEquals(GlucoseBarRange.TARGET, glucoseBarState(120.0, high)!!.range)
        assertEquals(1f, glucoseBarState(400.0, high)!!.position)
    }
}
