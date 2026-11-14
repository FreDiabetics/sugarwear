package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CgmThresholdsTest {
    @Test
    fun `defaults match Sugarlicious CGM policy`() {
        val thresholds = CgmThresholds.DEFAULT
        assertEquals(250.0, thresholds.veryHighMgDl, 0.0)
        assertEquals(180.0, thresholds.highMgDl, 0.0)
        assertEquals(70.0, thresholds.lowMgDl, 0.0)
        assertEquals(50.0, thresholds.veryLowMgDl, 0.0)
        assertTrue(thresholds.isValid)
    }

    @Test
    fun `classification has explicit inclusive threshold semantics`() {
        val thresholds = CgmThresholds.DEFAULT
        val cases =
            mapOf(
                49.0 to CgmRangeClass.VERY_LOW,
                50.0 to CgmRangeClass.VERY_LOW,
                51.0 to CgmRangeClass.LOW,
                70.0 to CgmRangeClass.LOW,
                71.0 to CgmRangeClass.IN_RANGE,
                179.0 to CgmRangeClass.IN_RANGE,
                180.0 to CgmRangeClass.HIGH,
                181.0 to CgmRangeClass.HIGH,
                249.0 to CgmRangeClass.HIGH,
                250.0 to CgmRangeClass.VERY_HIGH,
                251.0 to CgmRangeClass.VERY_HIGH,
            )
        cases.forEach { (value, expected) -> assertEquals(expected, thresholds.classify(value)) }
    }

    @Test
    fun `invalid threshold ordering is rejected without normalization`() {
        val invalid = CgmThresholds(veryHighMgDl = 180.0, highMgDl = 180.0, lowMgDl = 70.0, veryLowMgDl = 50.0)
        assertFalse(invalid.isValid)
        assertNull(invalid.classify(120.0))
        assertEquals(180.0, invalid.veryHighMgDl, 0.0)
        assertEquals(180.0, invalid.highMgDl, 0.0)
    }

    @Test
    fun `non finite values cannot be classified`() {
        val thresholds = CgmThresholds.DEFAULT
        assertNull(thresholds.classify(Double.NaN))
        assertNull(thresholds.classify(Double.POSITIVE_INFINITY))
    }
}
