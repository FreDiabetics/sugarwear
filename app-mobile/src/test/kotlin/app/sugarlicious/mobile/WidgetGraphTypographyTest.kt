package app.sugarlicious.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGraphTypographyTest {
    @Test
    fun `axis typography is responsive but bounded`() {
        val density = 2f
        val scaledDensity = 2f
        listOf(
            220 to 140,
            320 to 200,
            440 to 280,
            680 to 420,
            760 to 260,
            960 to 620,
            360 to 560,
        ).forEach { (width, height) ->
            val metrics = WidgetGraphLayoutMetrics.resolve(width, height, density, scaledDensity)
            val axisSp = metrics.axisTextPx / scaledDensity
            val yAxisSp = metrics.yAxisTextPx / scaledDensity
            assertTrue(axisSp in 7.5f..9.5f)
            assertTrue(yAxisSp in 8.0f..10.0f)
        }
    }
}
