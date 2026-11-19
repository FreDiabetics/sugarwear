package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.editor.PlaygroundStyleExporter
import app.sugarwear.debug.playground.editor.PlaygroundStyleOverrides
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaygroundStyleExporterTest {
    @Test
    fun `exportToJson contains target production file paths`() {
        val overrides = PlaygroundStyleOverrides()
        val json = PlaygroundStyleExporter.exportToJson(overrides)

        assertTrue(json.contains("target_production_files"))
        assertTrue(json.contains("GraphVisualStyle.kt"))
        assertTrue(json.contains("HomeVisualStyle.kt"))
    }

    @Test
    fun `exportToKotlin contains Kotlin style instantiation code`() {
        val overrides = PlaygroundStyleOverrides()
        val kotlinCode = PlaygroundStyleExporter.exportToKotlin(overrides)

        assertTrue(kotlinCode.contains("val customGraphStyle = GraphVisualStyle("))
        assertTrue(kotlinCode.contains("val customHomeStyle = HomeVisualStyle("))
    }
}
