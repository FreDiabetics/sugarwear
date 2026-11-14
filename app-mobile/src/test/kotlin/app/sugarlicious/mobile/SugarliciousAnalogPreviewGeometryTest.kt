package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Expectations measured independently from the pinned original 450px WFS. */
class SugarliciousAnalogPreviewGeometryTest {
    @Test fun `selected Apex graph remains available in ambient without a frozen empty frame`() {
        val graph = slot("7")
        val ambient = graph.getElementsByTagName("Variant").item(0) as Element
        assertTrue(ambient.getAttribute("value").toInt() > 0)
        val small =
            (0 until graph.getElementsByTagName("Complication").length)
                .map { graph.getElementsByTagName("Complication").item(it) as Element }
                .single { it.getAttribute("type") == "LONG_TEXT" }
        val policies = small.getElementsByTagName("Variant")
        assertTrue((0 until policies.length).all { (policies.item(it) as Element).getAttribute("value").toInt() > 0 })
    }

    @Test fun `default configuration matches the captured Apex template`() {
        val root = document()
        val expected = mapOf("materialAccent" to "37", "materialSubTheme" to "61", "handStyle" to "1", "secondStyle" to "1", "progressBars" to "5")
        val nodes = root.getElementsByTagName("UserConfigurations").item(0).childNodes
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.forEach { element ->
            expected[element.getAttribute("id")]?.let { assertEquals(it, element.getAttribute("defaultValue")) }
        }
    }

    @Test
    fun `glucose color is opt in and only changes value text not icons or progress`() {
        val configs = document().getElementsByTagName("BooleanConfiguration")
        val toggle = (0 until configs.length).map { configs.item(it) as Element }.singleOrNull { it.getAttribute("id") == "glucoseColor" }
        assertTrue("glucose coloring needs a separate switch", toggle != null)
        assertEquals("FALSE", toggle!!.getAttribute("defaultValue"))
        val fonts = document().getElementsByTagName("Font")
        val colored = (0 until fonts.length).map { fonts.item(it) as Element }.filter { it.getAttribute("color") == "[COMPLICATION.RANGED_VALUE_COLORS]" }
        assertTrue("number must consume the Wear color hint", colored.isNotEmpty())
        colored.forEach { font ->
            assertTrue(font.textContent.contains("%s"))
            assertEquals("[COMPLICATION.TEXT]", (font.getElementsByTagName("Parameter").item(0) as Element).getAttribute("expression"))
        }
        listOf("Stroke", "PartImage").forEach { tag ->
            val nodes = document().getElementsByTagName(tag)
            assertTrue((0 until nodes.length).map { nodes.item(it) as Element }.none { it.getAttribute("color").contains("RANGED_VALUE_COLORS") || it.getAttribute("tintColor").contains("RANGED_VALUE_COLORS") })
        }
    }

    @Test
    fun `every circular progress fill switches gradient and solid visibility directly`() {
        (0..6).forEach { id ->
            val groups = slot(id.toString()).nodes("Group")
            listOf("gradient" to "255 : 0", "solid" to "0 : 255").forEach { (mode, alpha) ->
                val group = groups.single { it.getAttribute("name") == "progress-$mode-$id" }
                val expression = group.nodes("Transform").first().getAttribute("value")
                assertTrue(expression.contains("CONFIGURATION.progressBars"))
                assertTrue(expression.endsWith("? $alpha"))
                assertFalse(expression.contains("CONFIGURATION.progressGradient"))
                assertEquals(if (mode == "gradient") 1 else 0, group.nodes("SweepGradient").size)
            }
        }
    }

    @Test
    fun `hand switching never registers nested configuration listeners in DeviceVendor editor`() {
        val groups = document().getElementsByTagName("Group")
        val hands = (0 until groups.length).map { groups.item(it) as Element }.single { it.getAttribute("name") == "hands-and-indicators" }
        assertEquals("switch visibility without rebuilding the editor listener map", 0, hands.getElementsByTagName("ListConfiguration").length)
        assertEquals(3, hands.getElementsByTagName("HourHand").length)
        assertEquals(3, hands.getElementsByTagName("MinuteHand").length)
        assertEquals(9, hands.getElementsByTagName("SecondHand").length)
    }

    @Test
    fun `smooth seconds use highest supported WFF1 sweep frequency for all hand sets`() {
        val sweeps = document().getElementsByTagName("Sweep")
        assertEquals(9, sweeps.length)
        (0 until sweeps.length).forEach { assertEquals("15", (sweeps.item(it) as Element).getAttribute("frequency")) }
    }

    @Test
    fun `point mode shows the full unchanged colored bar under a hollow black contour`() {
        (0..3).forEach { id ->
            val groups = slot(id.toString()).nodes("Group")
            val fill = groups.single { it.getAttribute("name") == "progress-fill-$id" }
            val direct = (0 until fill.childNodes.length).mapNotNull { fill.childNodes.item(it) as? Element }
            assertTrue(direct.none { it.tagName == "Transform" && it.getAttribute("target") == "alpha" })
            val marker = groups.single { it.getAttribute("name") == "progress-marker-$id" }
            assertEquals(1, marker.nodes("Ellipse").size)
            assertTrue(marker.nodes("Fill").isEmpty())
            assertEquals("#FF000000", marker.nodes("Stroke").single().getAttribute("color"))
            val endpoints = fill.nodes("Transform").filter { it.getAttribute("target") == "endAngle" }
            assertTrue(endpoints.isNotEmpty())
            assertTrue(endpoints.all { it.getAttribute("value").contains("CONFIGURATION.progressBars") })
            if (id == 1) assertEquals(30, slot("1").nodes("Line").size)
        }
        (4..6).forEach { id -> assertFalse(slot(id.toString()).nodes("Group").any { it.getAttribute("name").startsWith("progress-marker-") }) }
    }

    @Test
    fun `point indicator inner diameter matches track thickness in every ranged slot`() {
        (0..3).forEach { id ->
            val marker = slot(id.toString()).nodes("Group").single { it.getAttribute("name") == "progress-marker-$id" }
            val circle = marker.nodes("Ellipse").single()
            val stroke = circle.nodes("Stroke").single()
            val thickness =
                sourceArcs(id.toString())
                    .first()
                    .nodes("Stroke")
                    .first()
                    .getAttribute("thickness")
                    .toDouble()
            assertEquals(5.640898, stroke.getAttribute("thickness").toDouble(), .0001)
            assertEquals(23.845376, circle.getAttribute("width").toDouble(), .0001)
            assertEquals(circle.getAttribute("width"), circle.getAttribute("height"))
            val inside = circle.getAttribute("width").toDouble() - stroke.getAttribute("thickness").toDouble()
            assertTrue(inside >= thickness && inside <= thickness + 1.14)
            assertEquals("#FF000000", stroke.getAttribute("color"))
        }
    }

    @Test
    fun `only image graph receives a pattern knockout behind its transparent artwork`() {
        val content = slot("7").nodes("Complication").single { it.getAttribute("type") == "LONG_TEXT" }
        val group = content.nodes("Group").single { it.getAttribute("name") == "graph-clipped-content" }
        val backing = group.nodes("Rectangle").single()
        val draw = backing.parentNode as Element
        assertEquals("97", draw.getAttribute("height"))
        assertEquals("91", draw.getAttribute("y"))
        assertEquals("#FF000000", backing.nodes("Fill").single().getAttribute("color"))
        val mask = group.nodes("PartDraw").first { it.getAttribute("renderMode") == "MASK" }
        val arc = mask.nodes("Arc").single()
        near(256.0, arc, "centerX")
        near(256.0, arc, "centerY")
        near(165.0, arc, "width")
        near(165.0, arc.nodes("Stroke").single(), "thickness")
        assertEquals("[CONFIGURATION.materialAccent.0]", group.getAttribute("tintColor"))
        assertEquals("[COMPLICATION.MONOCHROMATIC_IMAGE]", group.nodes("Image").single().getAttribute("resource"))
        (0..6).forEach { id -> assertTrue(slot(id.toString()).nodes("Rectangle").isEmpty()) }
    }

    @Test
    fun `lower left text matches the opposite outer complication in each supported text format`() {
        listOf("SHORT_TEXT", "RANGED_VALUE").forEach { type ->
            fun fonts(id: String): Set<String> {
                val variants = slot(id).getElementsByTagName("Complication")
                val variant = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == type }
                val fonts = variant.getElementsByTagName("Font")
                return (0 until fonts.length).map { (fonts.item(it) as Element).getAttribute("size") }.toSet()
            }
            assertEquals("outer lower left must not use a smaller font", fonts("2"), fonts("3"))
        }
    }

    @Test
    fun `outer therapy labels also retain signed values with one decimal and generic fallback`() {
        (0..3).forEach { id ->
            val parameters = slot(id.toString()).getElementsByTagName("Parameter")
            val expressions = (0 until parameters.length).map { (parameters.item(it) as Element).getAttribute("expression") }
            assertTrue("slot $id needs the same IOB precision as the center", expressions.any { it.contains("numberFormat(\"#.#\"") })
            assertTrue(expressions.contains("[COMPLICATION.TEXT]"))
        }
    }

    @Test
    fun `Apex upper content is a fixed graph without Arc alternatives`() {
        val graph = slot("7")
        assertEquals("FALSE", graph.getAttribute("isCustomizable"))
        assertEquals("LONG_TEXT EMPTY", graph.getAttribute("supportedTypes"))
        val groups = document().getElementsByTagName("Group")
        val names = (0 until groups.length).map { (groups.item(it) as Element).getAttribute("name") }
        assertTrue(names.none { it.startsWith("weekday-") || it in setOf("original-weekdays-date", "date-icon-only") })
        assertEquals(8, document().getElementsByTagName("Scene").item(0).let { (it as Element).nodes("ComplicationSlot").size })
    }

    @Test
    fun `upper alternative renders provider title text image and real ranged progress`() {
        val graph = slot("7")
        assertEquals(setOf("LONG_TEXT", "EMPTY"), graph.nodes("Complication").map { it.getAttribute("type") }.toSet())
        assertEquals("app.sugarlicious/app.sugarlicious.complications.NativeApexGraphComplication", graph.nodes("DefaultProviderPolicy").single().getAttribute("primaryProvider"))
        val configs = document().getElementsByTagName("UserConfigurations").item(0) as Element
        val ids = (0 until configs.childNodes.length).mapNotNull { configs.childNodes.item(it) as? Element }.map { it.getAttribute("id") }
        assertFalse(ids.any { it in setOf("upperMode", "arcContent", "weatherLaunch") })
        assertTrue(graph.nodes("Reference").any { it.getAttribute("name") == "nativeHandsOpacity" })
    }

    @Test
    fun `both native color menus expose every measured reference palette within platform limit`() {
        val configs = document().getElementsByTagName("ColorConfiguration")
        listOf("materialAccent", "materialSubTheme").forEach { id ->
            val config = (0 until configs.length).map { configs.item(it) as Element }.single { it.getAttribute("id") == id }
            val options = config.nodes("ColorOption")
            assertEquals(78, options.size)
            assertEquals(78, options.map { it.getAttribute("id") }.toSet().size)
            assertTrue(options.all { it.getAttribute("colors").split(" ").size == if (id == "materialAccent") 4 else 3 })
            val white =
                options
                    .single { it.getAttribute("id") == "999" }
                    .getAttribute("colors")
                    .split(" ")
                    .take(3)
            assertEquals(listOf("#FFFFFFFF", "#FFFFFFFF", "#FFFFFFFF"), white)
            val orange =
                options
                    .single { it.getAttribute("id") == "37" }
                    .getAttribute("colors")
                    .split(" ")
                    .take(3)
            assertEquals(listOf("#FFFFCCB1", "#FFFF935C", "#FFFF5F0D"), orange)
            assertFalse(options.any { it.getAttribute("id") == "537" })
        }
    }

    @Test
    fun `minute markers reuse measured dial gaps rather than drawing over wide hour numerals`() {
        val groups = document().getElementsByTagName("Group")
        val dots = (0 until groups.length).map { groups.item(it) as Element }.single { it.getAttribute("name") == "minute-dots" }
        assertEquals(26, dots.getElementsByTagName("Ellipse").length)
    }

    @Test
    fun `short text uses vivid theme tone while bottom trend uses title accent`() {
        (0..3).forEach { id ->
            val variants = slot(id.toString()).getElementsByTagName("Complication")
            val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
            assertEquals("[CONFIGURATION.materialAccent.2]", (short.getElementsByTagName("Font").item(0) as Element).getAttribute("color"))
        }
        assertEquals("[CONFIGURATION.materialSubTheme.1]", (slot("6").getElementsByTagName("PartImage").item(0) as Element).getAttribute("tintColor"))
    }

    @Test
    fun `ambient retains only lower glucose and fixed graph`() {
        (0..6).forEach { id ->
            val node = slot(id.toString())
            assertEquals("#FFFFFFFF", node.getAttribute("tintColor"))
            val ambient = node.nodes("Variant").first { it.getAttribute("mode") == "AMBIENT" && it.getAttribute("target") == "alpha" }
            assertEquals(if (id == 6) "160" else "0", ambient.getAttribute("value"))
        }
        val source = repoFile("watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml").readText()
        assertFalse(source.contains("CONFIGURATION.nightMode"))
        assertFalse(source.contains("CONFIGURATION.aodStyle"))
    }

    @Test
    fun `opaque decoration cleanup precedes graph and clinical ink`() {
        val root = document()
        val cleanup = root.getElementsByTagName("PartDraw").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("name") == "erase-baked-circles" } }
        assertEquals("Scene", cleanup.parentNode.nodeName)
        assertEquals(3, cleanup.nodes("Ellipse").size)
        assertEquals("0", cleanup.nodes("Variant").single().getAttribute("value"))
        val graph = root.getElementsByTagName("ComplicationSlot").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("slotId") == "7" && it.parentNode.nodeName == "Scene" } }
        assertTrue(cleanup.compareDocumentPosition(graph).toInt() and 4 != 0)
        val clinical = root.getElementsByTagName("ComplicationSlot").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("slotId") == "4" && it.parentNode.nodeName == "Scene" } }
        assertTrue(graph.compareDocumentPosition(clinical).toInt() and 4 != 0)
    }

    @Test
    fun `reference decoration exposes native options without creating new complication slots`() {
        val root = document()
        val user = root.getElementsByTagName("UserConfigurations").item(0) as Element
        val ids = (0 until user.childNodes.length).mapNotNull { user.childNodes.item(it) as? Element }.map { it.getAttribute("id") }.filter(String::isNotEmpty)
        assertEquals(listOf("indexStyle", "handStyle", "secondStyle", "materialAccent", "materialSubTheme", "backgroundPattern", "progressBars", "glucoseColor"), ids)
        assertEquals(8, (root.getElementsByTagName("Scene").item(0) as Element).nodes("ComplicationSlot").size)
        val source = repoFile("watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml").readText()
        listOf("nightMode", "aodStyle", "minuteMarkers", "showAmPm", "unreadIndicator", "dialTheme").forEach { assertFalse(source.contains("CONFIGURATION.$it")) }
    }

    @Test
    fun `outer short text uses native provider images instead of unsupported inline resources`() {
        listOf("0", "1", "2", "3").forEach { id ->
            val variants = slot(id).getElementsByTagName("Complication")
            val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
            assertEquals(0, short.getElementsByTagName("InlineImage").length)
            assertEquals("[COMPLICATION.MONOCHROMATIC_IMAGE]", (short.getElementsByTagName("Image").item(0) as Element).getAttribute("resource"))
        }
    }

    @Test
    fun `reference orange has distinct value title track and vivid end colors`() {
        val configs = document().getElementsByTagName("ColorConfiguration")
        listOf("materialAccent", "materialSubTheme").forEach { id ->
            val config = (0 until configs.length).map { configs.item(it) as Element }.single { it.getAttribute("id") == id }
            val options = config.getElementsByTagName("ColorOption")
            val orange = (0 until options.length).map { options.item(it) as Element }.singleOrNull { it.getAttribute("id") == "37" }
            assertTrue("measured reference palette missing", orange != null)
            assertEquals(
                "#FFFFCCB1 #FFFF935C #FFFF5F0D",
                orange!!
                    .getAttribute("colors")
                    .split(" ")
                    .take(3)
                    .joinToString(" "),
            )
        }
    }

    @Test
    fun `unit spacing uses a thin space instead of a full word space`() {
        val templates = slot("4").getElementsByTagName("Template")
        val units = (0 until templates.length).map { templates.item(it) as Element }.filter { it.getElementsByTagName("Parameter").length == 2 }
        assertTrue(units.isNotEmpty())
        units.forEach { assertTrue(it.textContent.startsWith("%s\u2009%s")) }
    }

    @Test
    fun `bottom icon grows while all three contours use the dark subtheme track`() {
        val images = slot("6").getElementsByTagName("PartImage")
        val icon = images.item(0) as Element
        near(26.0, icon, "width")
        near(26.0, icon, "height")
        near(117.0, icon, "y")
        val draws = document().getElementsByTagName("PartDraw")
        (0 until draws.length).map { draws.item(it) as Element }.filter { it.getAttribute("name").endsWith("-contour") }.forEach {
            assertEquals("55", it.getAttribute("alpha"))
            assertEquals("[CONFIGURATION.materialSubTheme.1]", (it.getElementsByTagName("Stroke").item(0) as Element).getAttribute("color"))
        }
    }

    @Test
    fun `marker endpoint is longer and lower left text grows away from progress`() {
        val lines = slot("1").getElementsByTagName("Line")
        val first = lines.item(0) as Element
        val last = lines.item(lines.length - 1) as Element
        val current = last.nodes("Transform").single { it.getAttribute("target") == "startX" }.getAttribute("value")
        assertTrue(current.contains("floor(clamp("))
        assertTrue(current.contains("== 9"))
        assertTrue(current.contains("?"))
        assertTrue((first.getElementsByTagName("Stroke").item(0) as Element).getAttribute("thickness").toFloat() >= 5f)
        val types = slot("3").getElementsByTagName("Complication")
        val range = (0 until types.length).map { types.item(it) as Element }.single { it.getAttribute("type") == "RANGED_VALUE" }
        val text = range.getElementsByTagName("TextCircular").item(0) as Element
        assertEquals("END", text.getAttribute("align"))
        near(246.0, text, "endAngle")
    }

    @Test
    fun `consolidated progress styles switch gradients and upper right markers`() {
        val user = document().getElementsByTagName("UserConfigurations").item(0) as Element
        val config = user.nodes("ListConfiguration").single { it.getAttribute("id") == "progressBars" }
        assertEquals((0..7).map(Int::toString), config.nodes("ListOption").map { it.getAttribute("id") })
        assertEquals(7, document().getElementsByTagName("SweepGradient").length)
        assertEquals(30, slot("1").nodes("Line").size)
        val shaders = document().getElementsByTagName("SweepGradient")
        (0 until shaders.length).forEach { index ->
            val gradient = shaders.item(index) as Element
            assertEquals(listOf("#FFFFFFFF", "#00FFFFFF"), gradient.getAttribute("colors").split(" "))
            assertTrue(gradient.nodes("Transform").isEmpty())
            assertTrue(gradient.getAttribute("endAngle").toDouble() > gradient.getAttribute("startAngle").toDouble())
        }
    }

    @Test
    fun `progress gradients clamp round caps instead of wrapping vivid color onto the pale start`() {
        val groups = document().getElementsByTagName("Group")
        val fixed = (0 until groups.length).map { groups.item(it) as Element }.filter { it.getAttribute("name") == "fixed-gradient-content" }
        assertEquals(7, fixed.size)
        fixed.forEach { group ->
            val shader = group.nodes("SweepGradient").single()
            val span = shader.getAttribute("endAngle").toDouble() - shader.getAttribute("startAngle").toDouble()
            assertTrue(span == 48.0 || span == 280.0)
            assertTrue(shader.nodes("Transform").isEmpty())
            listOf("start", "end").forEach { endpoint ->
                val cap = group.nodes("PartDraw").single { it.getAttribute("name") == "$endpoint-rounded-cap-base" }
                val ellipse = cap.nodes("Ellipse").single()
                assertEquals(ellipse.getAttribute("width"), ellipse.getAttribute("height"))
                assertTrue(ellipse.nodes("Fill").isNotEmpty())
            }
        }
    }

    @Test
    fun `lower left range places icon toward bottom but short text keeps it on left`() {
        val variants = slot("3").getElementsByTagName("Complication")
        val ranged = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "RANGED_VALUE" }
        val image = ranged.getElementsByTagName("PartImage").item(0) as Element
        assertTrue(image.getAttribute("y").toFloat() > 400)
        val text = ranged.getElementsByTagName("TextCircular").item(0) as Element
        assertTrue(text.getAttribute("startAngle").toFloat() > 250)
        val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
        val shortIcon = short.getElementsByTagName("PartImage").item(0) as Element
        assertTrue(shortIcon.getAttribute("x").toFloat() < 100)
    }

    @Test
    fun `center contour strokes follow the selected theme`() {
        val root = document()
        val draws = root.getElementsByTagName("PartDraw")
        listOf("center-left-contour", "center-right-contour", "center-bottom-contour").forEach { name ->
            val draw = (0 until draws.length).map { draws.item(it) as Element }.singleOrNull { it.getAttribute("name") == name }
            assertTrue("missing themed contour $name", draw != null)
            val stroke = draw!!.getElementsByTagName("Stroke").item(0) as Element
            assertTrue(stroke.getAttribute("color").startsWith("[CONFIGURATION.material"))
        }
    }

    @Test
    fun `center fields style recognized therapy units without changing generic text`() {
        listOf("4", "5", "6").forEach { id ->
            val node = slot(id)
            val fonts = node.getElementsByTagName("Font")
            assertTrue((0 until fonts.length).any { (fonts.item(it) as Element).getElementsByTagName("Parameter").length == 2 })
            val parameters = node.getElementsByTagName("Parameter")
            val expressions = (0 until parameters.length).map { (parameters.item(it) as Element).getAttribute("expression") }
            assertTrue(expressions.any { it.contains("numberFormat(\"#.#\"") })
            assertTrue(expressions.contains("[COMPLICATION.TEXT]"))
            assertTrue(expressions.contains("[COMPLICATION.TITLE]"))
        }
    }

    @Test
    fun `editor outlines isolate quadrants and contain only the visible graph`() {
        val sectors = listOf(270 to 360, 0 to 90, 90 to 180, 180 to 270)
        sectors.forEachIndexed { id, (start, end) ->
            val arc = slot(id.toString()).getElementsByTagName("BoundingArc").item(0) as Element
            near(start.toDouble(), arc, "startAngle")
            near(end.toDouble(), arc, "endAngle")
        }
        val graph = slot("7").getElementsByTagName("BoundingBox").item(0) as Element
        near(91.0, graph, "x")
        near(91.0, graph, "y")
        near(330.0, graph, "width")
        near(97.0, graph, "height")
        val configs = slot("6").ownerDocument.getElementsByTagName("BooleanConfiguration")
        assertTrue((0 until configs.length).none { (configs.item(it) as Element).getAttribute("id") in setOf("bottomIcon", "centerLeftIcon", "centerRightIcon") })
    }

    @Test
    fun `picker has exactly eight slots and excludes unsupported standalone icons and long text`() {
        val nodes = (slot("0").ownerDocument.getElementsByTagName("Scene").item(0) as Element).getElementsByTagName("ComplicationSlot")
        assertEquals(8, nodes.length)
        assertEquals((0..7).map(Int::toString).toSet(), (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("slotId") }.toSet())
        (0..6).forEach { id ->
            assertEquals("RANGED_VALUE SHORT_TEXT EMPTY", slot(id.toString()).getAttribute("supportedTypes"))
        }
        assertEquals("LONG_TEXT EMPTY", slot("7").getAttribute("supportedTypes"))
    }

    @Test
    fun `center short text has no invisible inline icon spacing`() {
        listOf("4", "5", "6").forEach { id ->
            val variants = slot(id).getElementsByTagName("Complication")
            val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
            assertEquals(0, short.getElementsByTagName("PartImage").length)
            assertEquals(0, short.getElementsByTagName("InlineImage").length)
            val parts = short.getElementsByTagName("PartText")
            (0 until parts.length).forEach { index ->
                val part = parts.item(index) as Element
                assertEquals(slot(id).getAttribute("width").toFloat() / 2, part.getAttribute("x").toFloat() + part.getAttribute("width").toFloat() / 2, .01f)
            }
        }
    }

    @Test
    fun `outer short text keeps provider image and centered text within the same slot`() {
        (0..3).forEach { id ->
            val variants = slot(id.toString()).getElementsByTagName("Complication")
            val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
            assertEquals(1, short.getElementsByTagName("PartImage").length)
            val circular = short.getElementsByTagName("TextCircular").item(0) as Element
            assertEquals("CENTER", circular.getAttribute("align"))
            assertTrue(kotlin.math.abs(circular.getAttribute("endAngle").toFloat() - circular.getAttribute("startAngle").toFloat()) >= 70f)
        }
    }

    @Test
    fun `transparent hand pair keeps the new source black translucent center`() {
        listOf("hour", "minute").forEach { kind ->
            val image = javax.imageio.ImageIO.read(repoFile("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/${kind}_hand_transparent.png"))
            val pixel = image.getRGB(image.width / 2, image.height * 140 / 512)
            assertTrue("new $kind center has 70 percent black fill", pixel ushr 24 in 177..180)
            assertEquals(0, pixel and 0xFFFFFF)
        }
    }

    @Test
    fun `all monochromatic complication icons follow their selected theme`() {
        (0..6).forEach { id ->
            val images = slot(id.toString()).getElementsByTagName("PartImage")
            (0 until images.length)
                .map { images.item(it) as Element }
                .filter {
                    it
                        .getElementsByTagName("Image")
                        .item(0)
                        ?.attributes
                        ?.getNamedItem("resource")
                        ?.nodeValue == "[COMPLICATION.MONOCHROMATIC_IMAGE]"
                }.forEach { assertTrue("slot $id icon needs palette tint", it.getAttribute("tintColor").startsWith("[CONFIGURATION.")) }
        }
    }

    @Test
    fun `measured complication typography retains system font and bold weight`() {
        (0..6).forEach { id ->
            slot(id.toString()).nodes("Font").forEach { font ->
                val title = font.nodes("Parameter").any { it.getAttribute("expression").contains("[COMPLICATION.TITLE]") }
                val expected =
                    if (id < 4) {
                        29.6
                    } else if (title) {
                        32.0
                    } else {
                        39.0
                    }
                assertEquals(expected, font.getAttribute("size").toDouble(), .01)
                assertEquals("BOLD", font.getAttribute("weight"))
                assertEquals("SYNC_TO_DEVICE", font.getAttribute("family"))
            }
        }
    }

    @Test
    fun `titles and values use different tones even with identical theme selections`() {
        listOf("4", "5", "6").forEach { id ->
            val fonts = slot(id).getElementsByTagName("Font")
            (0 until fonts.length).forEach {
                val font = fonts.item(it) as Element
                val parameter = font.getElementsByTagName("Parameter").item(0) as Element
                val title = parameter.getAttribute("expression").contains("[COMPLICATION.TITLE]")
                if (font.getAttribute("color") == "[COMPLICATION.RANGED_VALUE_COLORS]") {
                    assertTrue("provider color must not affect the title", !title)
                } else if (font.getAttribute("size") != "24") {
                    assertTrue("slot $id needs distinct semantic palette tones", font.getAttribute("color").endsWith(if (title) ".1]" else ".0]"))
                }
            }
        }
    }

    @Test
    fun `theme palettes are actually bound to text and progress colors`() {
        val root = document()
        val configs = root.getElementsByTagName("ColorConfiguration")
        listOf("materialAccent", "materialSubTheme").forEach { id ->
            val config = (0 until configs.length).map { configs.item(it) as Element }.single { it.getAttribute("id") == id }
            val options = config.getElementsByTagName("ColorOption")
            assertTrue(options.length >= 37)
            (0 until options.length).forEach {
                assertEquals(if (id == "materialAccent") 4 else 3, (options.item(it) as Element).getAttribute("colors").split(" ").size)
            }
            val fonts = root.getElementsByTagName("Font")
            assertTrue((0 until fonts.length).any { (fonts.item(it) as Element).getAttribute("color") == "[CONFIGURATION.$id.0]" })
            val strokes = root.getElementsByTagName("Stroke")
            assertTrue((0 until strokes.length).any { (strokes.item(it) as Element).getAttribute("color") == "[CONFIGURATION.$id.1]" })
            val fills = root.getElementsByTagName("Fill")
            assertTrue((0 until strokes.length).any { (strokes.item(it) as Element).getAttribute("color") == "[CONFIGURATION.$id.2]" } || (0 until fills.length).any { (fills.item(it) as Element).getAttribute("color") == "[CONFIGURATION.$id.2]" })
        }
    }

    @Test
    fun `neutral graph stays in the cap above middle complications`() {
        val variants = slot("7").getElementsByTagName("Complication")
        val image =
            (0 until variants.length)
                .map { variants.item(it) as Element }
                .single { it.getAttribute("type") == "LONG_TEXT" }
                .getElementsByTagName("PartImage")
                .item(0) as Element
        val left = image.getAttribute("x").toInt()
        val top = image.getAttribute("y").toInt()
        val width = image.getAttribute("width").toInt()
        val height = image.getAttribute("height").toInt()
        assertTrue("graph must be larger than the previous 228x80 chart", width * height > 22_000)
        assertEquals("cap ends above middle complications", 188, top + height)
        assertTrue("cap leaves clearance above both middle circles", top + height < SugarliciousAnalogGeometry.middleLeft.y && top + height < SugarliciousAnalogGeometry.middleRight.y)
        val frame = image.parentNode.parentNode as Element
        assertEquals("no rectangular pill frame", 0, frame.getElementsByTagName("RoundRectangle").length)
        assertEquals("curved cap height", 97, height)
        assertEquals(left.toFloat(), SugarliciousAnalogGeometry.graphContent.x)
        assertEquals(top.toFloat(), SugarliciousAnalogGeometry.graphContent.y)
        assertEquals(width.toFloat(), SugarliciousAnalogGeometry.graphContent.width)
        assertEquals(height.toFloat(), SugarliciousAnalogGeometry.graphContent.height)
    }

    @Test
    fun `all outer slots offer ranged arc text and icon including lower left`() {
        listOf("0", "1", "2", "3").forEach { id ->
            val variants = slot(id).getElementsByTagName("Complication")
            listOf("SHORT_TEXT", "RANGED_VALUE").forEach { type ->
                val variant = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == type }
                assertTrue("slot $id $type needs an icon", variant.getElementsByTagName("PartImage").length + variant.getElementsByTagName("InlineImage").length > 0)
                assertEquals("slot $id $type needs exclusive formatted, generic and optional glucose-color paths", if (type == "RANGED_VALUE") 3 else 2, variant.getElementsByTagName("TextCircular").length)
                if (type == "RANGED_VALUE") assertEquals("slot $id needs track, remaining gap arc, opaque gradient base, color overlay and exclusive solid style", 5, variant.getElementsByTagName("Arc").length)
            }
        }
    }

    @Test
    fun `center ranged slots contain curved progress title value and open segment icon`() {
        listOf("4", "5", "6").forEach { id ->
            val variants = slot(id).getElementsByTagName("Complication")
            val ranged = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "RANGED_VALUE" }
            assertEquals("slot $id needs circular rather than linear progress with a separate remainder", 5, ranged.getElementsByTagName("Arc").length)
            assertEquals(0, ranged.getElementsByTagName("Line").length)
            val parameters = ranged.getElementsByTagName("Parameter")
            assertTrue((0 until parameters.length).any { (parameters.item(it) as Element).getAttribute("expression") == "[COMPLICATION.TITLE]" })
            val fonts = ranged.getElementsByTagName("Font")
            (0 until fonts.length).forEach {
                val font = fonts.item(it) as Element
                assertTrue(font.getAttribute("size").toInt() >= 22)
            }
            if (id == "6") {
                val image = ranged.getElementsByTagName("PartImage").item(0) as Element
                assertTrue("icon below text in open segment", image.getAttribute("y").toFloat() > slot(id).getAttribute("height").toFloat() * .75f)
            } else {
                assertEquals(0, ranged.getElementsByTagName("PartImage").length)
            }
        }
    }

    @Test
    fun `source typography and provider bindings remain intact`() {
        listOf("4", "5").forEach { id ->
            val variants = slot(id).getElementsByTagName("Complication")
            val short = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "SHORT_TEXT" }
            val fonts = short.getElementsByTagName("Font")
            assertEquals("BOLD", (fonts.item(0) as Element).getAttribute("weight"))
            assertEquals("BOLD", (fonts.item(1) as Element).getAttribute("weight"))
            val title =
                (0 until fonts.length).map { fonts.item(it) as Element }.single { font ->
                    (font.getElementsByTagName("Parameter").item(0) as Element).getAttribute("expression") == "[COMPLICATION.TITLE]"
                }
            assertEquals("[CONFIGURATION.materialSubTheme.1]", title.getAttribute("color"))
        }
        val graphPolicy = slot("7").getElementsByTagName("DefaultProviderPolicy").item(0) as Element
        assertEquals("app.sugarlicious/app.sugarlicious.complications.NativeApexGraphComplication", graphPolicy.getAttribute("primaryProvider"))
        assertEquals("LONG_TEXT", graphPolicy.getAttribute("primaryProviderType"))
        val bottomPolicy = slot("6").getElementsByTagName("DefaultProviderPolicy").item(0) as Element
        assertEquals("app.sugarlicious/app.sugarlicious.complications.GlucoseTrendRangedValueComplication", bottomPolicy.getAttribute("primaryProvider"))
    }

    @Test
    fun `all hand resources and AOD behavior remain packaged`() {
        val root = document()
        listOf("HourHand", "MinuteHand", "SecondHand").forEach { tag ->
            val nodes = root.getElementsByTagName(tag)
            assertEquals(if (tag == "SecondHand") 9 else 3, nodes.length)
            (0 until nodes.length).forEach { index ->
                val hand = nodes.item(index) as Element
                val resource = hand.getAttribute("resource")
                assertTrue(repoFile("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/$resource.png").isFile)
                if (tag == "SecondHand") {
                    val ambient = hand.getElementsByTagName("Variant").item(0) as Element
                    assertEquals("AMBIENT", ambient.getAttribute("mode"))
                    assertEquals("alpha", ambient.getAttribute("target"))
                    assertEquals("0", ambient.getAttribute("value"))
                }
            }
        }
    }

    @Test
    fun `original dial template keeps transparent graph cutout`() {
        val image = javax.imageio.ImageIO.read(repoFile("design-assets/watchface-templates/apex-original-dial.png"))
        assertEquals(450, image.width)
        assertEquals(450, image.height)
        assertEquals(0, image.getRGB(225, 100) ushr 24)
        assertEquals(255, image.getRGB(225, 225) ushr 24)
        assertEquals(0x4C4C4C, image.getRGB(291, 26) and 0xFFFFFF)
    }

    @Test
    fun `outer progress uses source outside diameter minus stroke`() {
        listOf("0" to 285.0, "1" to 15.0, "2" to 103.0, "3" to 195.0).forEach { (id, start) ->
            val arcs = sourceArcs(id)
            assertEquals(3, arcs.size)
            arcs.forEach { arc ->
                near(391.3956, arc, "width")
                near(if (arc.getAttribute("direction") == "COUNTER_CLOCKWISE") start + 48.0 else start, arc, "startAngle")
                near(17.0667, arc.nodes("Stroke").single(), "thickness")
            }
            val fill = arcs.first { it.getAttribute("direction") == "CLOCKWISE" && it.nodes("Transform").isNotEmpty() }
            assertTrue(
                fill
                    .nodes("Transform")
                    .first()
                    .getAttribute("value")
                    .contains("* 48"),
            )
        }
    }

    @Test
    fun `outer icons stay on measured reference circle while keeping accepted tracks`() {
        listOf(61.37 to 235.54, 276.14 to 61.37, 450.63 to 276.14, 235.54 to 450.63).forEachIndexed { index, (x, y) ->
            val image =
                slot(index.toString())
                    .nodes("Complication")
                    .single { it.getAttribute("type") == "RANGED_VALUE" }
                    .nodes("PartImage")
                    .first()
            assertEquals(x, image.getAttribute("x").toDouble() + image.getAttribute("width").toDouble() / 2, 2.0)
            assertEquals(y, image.getAttribute("y").toDouble() + image.getAttribute("height").toDouble() / 2, 2.0)
        }
    }

    @Test
    fun `curved text paths are independent of progress sweeps`() {
        listOf(337.0, 66.0, 181.0, 282.0).forEachIndexed { index, start ->
            val variants = slot(index.toString()).getElementsByTagName("Complication")
            val ranged = (0 until variants.length).map { variants.item(it) as Element }.single { it.getAttribute("type") == "RANGED_VALUE" }
            val texts = ranged.getElementsByTagName("TextCircular")
            (0 until texts.length).forEach { n ->
                val text = texts.item(n) as Element
                near(start, text, "startAngle")
                near(if (index == 3) 391.3956 else 390.2578, text, "width")
                assertEquals(if (index < 2) "CLOCKWISE" else "COUNTER_CLOCKWISE", text.getAttribute("direction"))
                assertEquals(if (index < 2) "START" else "END", text.getAttribute("align"))
                if (index == 1) near(90.0, text, "endAngle")
            }
        }
    }

    @Test
    fun `middle editor outlines follow the circular complication areas`() {
        listOf("4", "5", "6").forEach { id ->
            assertEquals(1, slot(id).getElementsByTagName("BoundingOval").length)
            assertEquals(0, slot(id).getElementsByTagName("BoundingBox").length)
        }
        val value = slot("4").getElementsByTagName("PartText").item(0) as Element
        near(3.0, value, "x")
        near(60.0, value, "y")
        near(117.0, value, "width")
        near(48.0, value, "height")
    }

    @Test
    fun `bottom ring retains WFS center stroke and open segment icon`() {
        val arcs = sourceArcs("6")
        assertEquals(3, arcs.size)
        arcs.forEach { arc ->
            near(75.0, arc, "centerX")
            near(76.2622, arc, "centerY")
            near(125.1556, arc, "width")
            near(if (arc.getAttribute("direction") == "COUNTER_CLOCKWISE") 500.0 else 220.0, arc, "startAngle")
            near(11.3778, arc.nodes("Stroke").single(), "thickness")
        }
        val ranged = slot("6").nodes("Complication").single { it.getAttribute("type") == "RANGED_VALUE" }
        val image = ranged.nodes("PartImage").first()
        near(64.0, image, "x")
        near(117.0, image, "y")
        near(26.0, image, "width")
        near(26.0, image, "height")
    }

    @Test
    fun `three hand packages retain stable style IDs`() {
        val nodes = document().getElementsByTagName("AnalogClock")
        val clocks = (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.getElementsByTagName("HourHand").length > 0 }
        assertEquals(3, clocks.size)
        listOf("hour_hand", "hour_hand_transparent", "hour_hand_tblack").forEachIndexed { index, resource ->
            val clock = clocks[index]
            assertEquals(resource, (clock.getElementsByTagName("HourHand").item(0) as Element).getAttribute("resource"))
            val visibility = (clock.parentNode as Element).getElementsByTagName("Transform").item(0) as Element
            assertEquals("[CONFIGURATION.handStyle] == \"$index\" ? 255 : 0", visibility.getAttribute("value"))
        }
    }

    @Test
    fun `mobile reuses native template without a duplicate hand renderer`() {
        assertEquals(391f, SugarliciousAnalogGeometry.outerProgressDiameter)
        assertEquals(390f, SugarliciousAnalogGeometry.outerTextDiameter)
        assertEquals(125f, SugarliciousAnalogGeometry.bottomArcDiameter)
        val source = repoFile("app-mobile/src/main/kotlin/app/sugarlicious/mobile/SugarliciousAnalogFacePreview.kt").readText()
        assertTrue(source.contains("R.drawable.apex_template_preview"))
        assertTrue(source.contains("ContentScale.Fit"))
        assertFalse(source.contains("AnalogPreviewHand("))
        val preview = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(repoFile("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/preview.xml"))
        assertEquals("bitmap", preview.documentElement.tagName)
        assertEquals("@drawable/apex_template_preview", preview.documentElement.getAttribute("android:src"))
    }

    private fun Element.nodes(tag: String): List<Element> = getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    /** Original WFS geometry excludes gradient arcs expressed in translated local groups. */
    private fun sourceArcs(id: String): List<Element> {
        val owner = slot(id)
        return owner.nodes("Arc").filter { arc ->
            var parent = arc.parentNode
            var gradient = false
            while (parent !== owner && parent is Element) {
                if (parent.getAttribute("name") == "fixed-gradient-content") gradient = true
                parent = parent.parentNode
            }
            !gradient
        }
    }

    private fun near(expected: Double, element: Element, attribute: String) =
        assertEquals("${element.tagName} $attribute", expected, element.getAttribute(attribute).toDouble(), 0.51)

    private fun document() =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(repoFile("watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml"))

    private fun slot(id: String): Element {
        val nodes = (document().getElementsByTagName("Scene").item(0) as Element).getElementsByTagName("ComplicationSlot")
        return (0 until nodes.length).map { nodes.item(it) as Element }.single { it.getAttribute("slotId") == id }
    }

    private fun repoFile(path: String): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return listOf(File(cwd, path), File(cwd.parentFile, path)).firstOrNull(File::isFile)
            ?: error("Repository file not found: $path")
    }
}
