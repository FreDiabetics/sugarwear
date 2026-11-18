package app.sugarwear.debug.playground

import androidx.test.core.app.ApplicationProvider
import app.sugarwear.debug.playground.data.PlaygroundDataMapper
import app.sugarwear.debug.playground.demo.DemoGlucoseFactory
import app.sugarwear.debug.playground.demo.DemoScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaygroundDataMapperTest {
    @Test
    fun `toGraphInput creates valid SharedWearCgmGraphInput from synthetic readings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE, count = 20)

        val input = PlaygroundDataMapper.toGraphInput(context, readings, graphHours = 3)
        assertNotNull(input)
        assertEquals(3 * 60 * 60_000L, input.timeWindow.durationMs)
    }
}
