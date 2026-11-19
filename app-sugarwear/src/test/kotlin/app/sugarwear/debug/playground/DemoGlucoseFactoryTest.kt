package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.demo.DemoGlucoseFactory
import app.sugarwear.debug.playground.demo.DemoScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGlucoseFactoryTest {
    @Test
    fun `createReadingsForScenario produces deterministic glucose list`() {
        val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE, count = 10)
        assertEquals(10, readings.size)
        assertTrue(readings.all { it.glucoseMgDl in 40.0..400.0 })
    }

    @Test
    fun `disconnected scenario returns empty list`() {
        val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.DISCONNECTED)
        assertTrue(readings.isEmpty())
    }

    @Test
    fun `hypo scenario produces low glucose values`() {
        val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.HYPOGLYCEMIA, count = 20)
        val lowest = readings.minOf { it.glucoseMgDl }
        assertTrue("Lowest glucose should be below 80 mg/dL", lowest < 80.0)
    }
}
