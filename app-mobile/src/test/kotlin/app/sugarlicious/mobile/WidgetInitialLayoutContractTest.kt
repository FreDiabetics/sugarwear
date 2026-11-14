package app.sugarlicious.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetInitialLayoutContractTest {
    @Test
    fun `widget providers use data neutral type specific initial layouts`() {
        val providers =
            mapOf(
                "glucose_widget_info.xml" to "widget_glucose_initial",
                "graph_widget_info.xml" to "widget_graph_initial",
                "glucose_graph_widget_info.xml" to "widget_glucose_graph_initial",
                "metabolic_widget_info.xml" to "widget_metabolic_initial",
            )

        providers.forEach { (providerFile, initialLayout) ->
            val provider = File("src/main/res/xml/$providerFile").readText()
            assertTrue(provider.contains("android:initialLayout=\"@layout/$initialLayout\""))
            assertFalse(provider.contains("@layout/widget_loading"))

            val layout = File("src/main/res/layout/$initialLayout.xml")
            assertTrue(layout.isFile)
            val layoutSource = layout.readText()
            assertFalse(layoutSource.contains("@string/widget_loading"))
            assertFalse(layoutSource.contains("@string/widget_preview_"))
            assertFalse(layoutSource.contains("TextView"))
        }
    }
}
