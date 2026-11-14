package app.sugarlicious.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlucoseWidgetContractTest {
    @Test
    fun `legacy glucose stays minimal while adaptive family owns the expanded layouts`() {
        val source = File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()
        val start = source.indexOf("private fun GlucoseWidgetContent")
        val end = source.indexOf("private fun GraphWidgetContent", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val body = source.substring(start, end)

        assertTrue(body.contains("renderMinimalGlucoseWidget"))
        assertTrue(body.contains("if (adaptiveFamily)"))
        assertTrue(body.contains("renderAdaptiveGlucoseWidget"))
        assertFalse(body.contains("widgetFreshnessStatus"))
    }
}
