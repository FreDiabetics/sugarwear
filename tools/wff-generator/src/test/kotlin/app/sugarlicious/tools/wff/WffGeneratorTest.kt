package app.sugarlicious.tools.wff

import app.sugarlicious.tools.cwf.CwfDocument
import app.sugarlicious.tools.cwf.CwfElement
import app.sugarlicious.tools.cwf.CwfMetadata
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WffGeneratorTest {
    @Test
    fun `generates well formed WFF with no more than eight slots`() {
        val result = WffGenerator().generate(document(), allowDegraded = true)
        val xml =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(result.xml)))
        assertEquals("WatchFace", xml.documentElement.tagName)
        assertEquals(8, result.slotCount)
        assertEquals(8, xml.getElementsByTagName("ComplicationSlot").length)
        assertTrue(result.xml.contains("GlucoseTrendDeltaComplication"))
        assertTrue(result.xml.contains("GlucoseGraphComplication"))
    }

    @Test
    fun `refuses silent degradation`() {
        assertFailsWith<WffGenerationException> { WffGenerator().generate(document()) }
    }

    @Test
    fun `reports every visible unmapped element`() {
        val result = WffGenerator().generate(document(), allowDegraded = true)
        assertEquals(listOf("unknown_visible"), result.omittedElements)
        assertTrue(result.warnings.any { "unknown_visible" in it })
    }

    @Test
    fun `renders visible free text as text instead of a solid bar`() {
        val base = document()
        val freeText =
            element("freetext1", 20, 20, 100, 30).copy(
                fontColor = "#12AB34",
                raw = JsonObject(mapOf("textvalue" to JsonPrimitive("Change"))),
            )
        val result =
            WffGenerator().generate(
                base.copy(elements = base.elements + ("freetext1" to freeText)),
                allowDegraded = true,
            )
        assertTrue(result.xml.contains(">Change</Font>"))
        assertTrue(result.xml.contains("color=\"#FF12AB34\""))
    }

    @Test
    fun `renders split hour and minute fields when combined time is hidden`() {
        val base = document()
        val split =
            base.elements - "time" +
                mapOf(
                    "hour" to element("hour", 120, 300, 70, 70, 60),
                    "minute" to element("minute", 200, 300, 70, 70, 60),
                )
        val result = WffGenerator().generate(base.copy(elements = split), allowDegraded = true)
        assertTrue(result.xml.contains("format=\"hh\""))
        assertTrue(result.xml.contains("format=\"mm\""))
    }

    private fun document(): CwfDocument {
        val elements =
            listOf(
                element("background", 0, 0, 400, 400),
                element("sgv", 0, 26, 400, 100, 74),
                element("direction", 291, 36, 40, 40),
                element("delta", 7, 127, 56, 32, 23),
                element("timestamp", 285, 79, 52, 34, 25),
                element("loop", 68, 61, 50, 50),
                element("uploader_battery", 120, 127, 60, 32, 24),
                element("basalRate", 242, 127, 90, 32, 24),
                element("cob2", 0, 196, 105, 33, 24),
                element("iob2", 275, 196, 125, 33, 24),
                element("chart", 0, 230, 400, 170),
                element("time", 105, 162, 130, 70, 55),
                element("unknown_visible", 10, 10, 20, 20),
            ).associateBy { it.key }
        return CwfDocument(
            metadata = CwfMetadata("Test", "Tester", null, null, "1.0", null),
            canvasWidth = 400,
            canvasHeight = 400,
            previewEntry = "CustomWatchface.png",
            resourceEntries = listOf("CustomWatchface.json", "CustomWatchface.png"),
            elements = elements,
            featureFlags = setOf("chart", "dynamic_preferences"),
            warnings = listOf("CWF feature requires explicit WFF mapping: dynamic_preferences"),
        )
    }

    private fun element(
        key: String,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        textSize: Int? = null,
    ) = CwfElement(
        key = key,
        width = width,
        height = height,
        top = top,
        left = left,
        visibility = "visible",
        textSize = textSize,
        gravity = "center",
        font = "default",
        fontStyle = "normal",
        fontColor = "#FFFFFF",
        rotation = 0.0,
        raw = JsonObject(emptyMap()),
    )
}
