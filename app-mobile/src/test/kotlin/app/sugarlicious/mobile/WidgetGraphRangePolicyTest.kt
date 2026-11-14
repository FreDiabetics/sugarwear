package app.sugarlicious.mobile

import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.RangeExcursion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetGraphRangePolicyTest {
    private val now = 2_000_000_000L

    @Test
    fun `first high immediately activates high`() {
        assertEquals(RangeExcursion.HIGH, sustainedRangeExcursion(samples(120.0, 166.0), 80.0, 160.0))
        assertEquals(RangeExcursion.HIGH, sustainedRangeExcursion(samples(166.0, 171.0), 80.0, 160.0))
    }

    @Test
    fun `first low immediately activates low`() {
        assertEquals(RangeExcursion.LOW, sustainedRangeExcursion(samples(120.0, 77.0), 80.0, 160.0))
        assertEquals(RangeExcursion.LOW, sustainedRangeExcursion(samples(77.0, 73.0), 80.0, 160.0))
    }

    @Test
    fun `first in-range value clears active excursion`() {
        assertNull(sustainedRangeExcursion(samples(166.0, 171.0, 158.0), 80.0, 160.0))
        assertNull(sustainedRangeExcursion(samples(77.0, 73.0, 84.0), 80.0, 160.0))
    }

    private fun samples(vararg values: Double): List<GlucoseSample> =
        values.mapIndexed { index, value ->
            val minutesBack = (values.lastIndex - index) * 5L
            GlucoseSample(valueMgDl = value, measuredAtEpochMs = now - minutesBack * 60_000L)
        }
}
