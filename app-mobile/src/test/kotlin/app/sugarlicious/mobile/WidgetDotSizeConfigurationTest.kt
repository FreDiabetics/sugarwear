package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetDotSizeConfigurationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `each graph widget persists its own cgm dot radius`() {
        WidgetInstanceConfigurationStore.save(context, 4101, WidgetInstanceConfiguration(cgmDotRadiusDp = 1.5f))
        WidgetInstanceConfigurationStore.save(context, 4102, WidgetInstanceConfiguration(cgmDotRadiusDp = 6f))

        assertEquals(1.5f, WidgetInstanceConfigurationStore.read(context, 4101).cgmDotRadiusDp, 0.001f)
        assertEquals(6f, WidgetInstanceConfigurationStore.read(context, 4102).cgmDotRadiusDp, 0.001f)
    }

    @Test
    fun `widget graph metrics use configured cgm dot radius`() {
        val layout = responsiveWidgetLayout(240f, 140f).copy(graphDotRadiusDp = 4.5f)

        val metrics = widgetGraphMetrics(480, 280, 2f, layout)

        assertEquals(9f, metrics.dotRadiusPx, 0.001f)
    }
}
