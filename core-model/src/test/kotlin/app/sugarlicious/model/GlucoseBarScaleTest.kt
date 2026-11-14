package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlucoseBarScaleTest {
    private val thresholds = CgmThresholds.DEFAULT

    @Test
    fun `each range uses its own threshold interval and a normalized marker`() {
        val low = requireNotNull(GlucoseBarScale.state(55.0, thresholds))
        val target = requireNotNull(GlucoseBarScale.state(125.0, thresholds))
        val high = requireNotNull(GlucoseBarScale.state(290.0, thresholds))
        assertEquals(GlucoseBarRange.LOW, low.range)
        assertEquals(GlucoseBarRange.TARGET, target.range)
        assertEquals(GlucoseBarRange.HIGH, high.range)
        assertEquals(.5f, low.position, .0001f)
        assertEquals(.5f, target.position, .0001f)
        assertEquals(.5f, high.position, .0001f)
    }

    @Test
    fun `thresholds and endpoints preserve mobile boundary rules`() {
        assertEquals(1f, GlucoseBarScale.state(70.0, thresholds)?.position)
        assertEquals(0f, GlucoseBarScale.state(180.0, thresholds)?.position)
        assertEquals(0f, GlucoseBarScale.state(20.0, thresholds)?.position)
        assertEquals(1f, GlucoseBarScale.state(500.0, thresholds)?.position)
        val modified = thresholds.copy(lowMgDl = 80.0, highMgDl = 160.0)
        assertEquals(.5f, GlucoseBarScale.state(120.0, modified)?.position)
        assertNull(GlucoseBarScale.state(Double.NaN, thresholds))
        assertNull(GlucoseBarScale.state(Double.POSITIVE_INFINITY, thresholds))
        assertNull(GlucoseBarScale.state(null, thresholds))
    }

    @Test
    fun `elapsed age uses the same label on phone and watch`() {
        assertEquals("vor 2 Min.", GlucoseAgeText.fromAgeMinutes("2m"))
        assertEquals("vor 1 Std. 5 Min.", GlucoseAgeText.fromAgeMinutes("65m"))
        assertEquals("vor 1 Tag", GlucoseAgeText.fromAgeMinutes("1440m"))
        assertEquals("—", GlucoseAgeText.fromAgeMinutes("—"))
    }
}
