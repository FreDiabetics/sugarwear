package app.sugarlicious.mobile

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGraphNoStretchTest {
    @Test
    fun `resize recomputes plot geometry instead of scaling old coordinates`() {
        val density = 2f
        val small = WidgetGraphLayoutMetrics.resolve(320, 200, density, density)
        val large = WidgetGraphLayoutMetrics.resolve(680, 420, density, density)
        val wide = WidgetGraphLayoutMetrics.resolve(760, 260, density, density)

        assertNotEquals(small.plotRect.width() / small.plotRect.height(), large.plotRect.width() / large.plotRect.height(), 0.0001f)
        assertNotEquals(large.plotRect.width() / large.plotRect.height(), wide.plotRect.width() / wide.plotRect.height(), 0.0001f)
        assertTrue(small.dotRadiusPx <= large.dotRadiusPx)
        assertTrue(large.dotRadiusPx / density <= CgmGraphVisualPolicy.DOT_RADIUS_DP)
        assertTrue(wide.dotRadiusPx / density <= CgmGraphVisualPolicy.DOT_RADIUS_DP)
    }
}
