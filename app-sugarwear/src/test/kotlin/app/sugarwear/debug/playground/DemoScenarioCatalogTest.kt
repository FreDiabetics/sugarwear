package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.demo.DemoScenarioCatalog
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioCatalogTest {

    @Test
    fun `allScenarios contains stable and hypo scenarios`() {
        val scenarios = DemoScenarioCatalog.allScenarios
        assertTrue(scenarios.isNotEmpty())
        assertNotNull(DemoScenarioCatalog.findById("scenario_stable"))
        assertNotNull(DemoScenarioCatalog.findById("scenario_hypo"))
    }
}
