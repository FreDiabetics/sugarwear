package app.sugarlicious.tools.maintenance.watchface

import kotlinx.serialization.json.*
import org.w3c.dom.Element
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class WatchfaceContractsTest {
    private val root = watchfaceRepositoryRoot()

    private fun face(family: String) = parseXml(root.resolve("watchfaces/sugarlicious-${if (family == "apex") "analog" else "orbit"}/src/main/res/raw/watchface.xml"))

    private fun source(family: String) = root.resolve("watchfaces/sugarlicious-${if (family == "apex") "analog" else "orbit"}/src/main")

    private fun slot(face: Element, id: String) = face.need("Scene/ComplicationSlot[@slotId='$id']")

    private fun ranged(face: Element, id: String) = slot(face, id).need("Complication[@type='RANGED_VALUE']")

    private fun snapshot(node: Element): String =
        buildString {
            append(node.tagName)
            append(node.attrs().toSortedMap())
            append(node.children().joinToString("") { snapshot(it) })
            if (node.children().isEmpty()) append(node.textContent.trim())
        }

    @Test fun originalCatalogInventoryAndFingerprint() {
        val entries = loadPalettes()
        assertEquals(78, entries.size)
        listOf("id", "apex_id", "name").forEach { assertEquals(78, entries.map { e -> e[it] }.toSet().size) }
        val original = entries.filter { it["origin"] == "original" }.map { entry -> listOf("name", "light", "medium", "vivid").map { entry.getValue(it) } }.sortedWith(compareBy<List<String>> { it[0] }.thenBy { it[1] }.thenBy { it[2] }.thenBy { it[3] })
        assertEquals(76, original.size)
        // Python json.dumps defaults to ASCII escaping. These catalogue strings are ASCII.
        val bytes = Json.encodeToString(JsonArray.serializer(), JsonArray(original.map { JsonArray(it.map(::JsonPrimitive)) })).toByteArray()
        assertEquals("f2b58d19e5740898a398daa0f7abc2c72b9fef025a0784738959ee81740a445c", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val byId = entries.associateBy { it.getValue("id") }
        assertEquals(listOf("FFCCB1", "FF935C", "FF5F0D"), listOf("light", "medium", "vivid").map { byId.getValue("70")[it] })
        assertEquals(listOf("D4DAB6", "A4AF73", "758544"), listOf("light", "medium", "vivid").map { byId.getValue("14")[it] })
        assertEquals("61", byId.getValue("14")["apex_id"])
        assertEquals(List(3) { "FFFFFF" }, listOf("light", "medium", "vivid").map { byId.getValue("999")[it] })
        val index = entries.indexOfFirst { it["kind"] == "combination" }
        assertEquals(11, entries.size - index)
        assertTrue(entries.drop(index).all { it["kind"] == "combination" })
        assertTrue(entries.none { "Sanft" in it.getValue("name") || "Kräftig" in it.getValue("name") })
    }

    @Test fun bothFacesShareOriginalPalettes() {
        listOf("apex", "orbit").forEach { family ->
            val face = face(family)
            val keys = if (family == "apex") listOf("materialAccent", "materialSubTheme") else listOf("primary", "secondary", "tertiary")
            keys.forEach { key ->
                val options = face.need("UserConfigurations/ColorConfiguration[@id='$key']")
                assertEquals(78, options.children().size)
                loadPalettes().forEach { entry ->
                    val identity = entry.getValue(if (family == "apex") "apex_id" else "id")
                    val tones = options.need("ColorOption[@id='$identity']").attr("colors").split(" ")
                    assertEquals(listOf("light", "medium", "vivid").map { "#FF${entry.getValue(it)}" }, tones.take(3))
                    assertEquals(if (key == "materialAccent") 4 else 3, tones.size)
                }
            }
        }
    }

    @Test fun isolatedCapturesRetainExactNativeLayersAndManifest() {
        listOf("apex", "orbit").forEach { family ->
            val destination = Files.createTempDirectory("slot-capture-test")
            try {
                prepareSlotCapture(source(family), destination, family)
                val original = face(family)
                val isolated = parseXml(destination.resolve("res/raw/watchface.xml"))
                assertNull(isolated.one("UserConfigurations/Flavors"))
                assertTrue(isolated.need("Scene").children().all { it.tagName == "ComplicationSlot" || it.attr("name") == "center-contours" })
                val slots = isolated.all("Scene/ComplicationSlot")
                assertEquals(if (family == "apex") 7 else 6, slots.size)
                slots.forEach { assertEquals(snapshot(slot(original, it.attr("slotId"))), snapshot(it)) }
                val manifest = parseXml(destination.resolve("AndroidManifest.xml"))
                assertEquals("app.sugarlicious.watchfacepush.slotcapture$family", manifest.attr("package"))
                val app = manifest.need("application")
                val android = "http://schemas.android.com/apk/res/android"
                assertEquals("Slot Capture $family", app.getAttributeNS(android, "label"))
                assertFalse(app.hasAttributeNS(android, "dataExtractionRules"))
                assertEquals("36", manifest.need("uses-sdk").getAttributeNS(android, "minSdkVersion"))
                assertEquals("37", manifest.need("uses-sdk").getAttributeNS(android, "targetSdkVersion"))
            } finally {
                destination.toFile().deleteRecursively()
            }
        }
    }

    @Test fun sixProfilesAreValidCompleteAndPreserveCapturedSelections() {
        listOf("apex", "orbit").forEach { family ->
            val face = face(family)
            val strings = parseXml(source(family).resolve("res/values/strings.xml"))
            replaceUserFlavors(face, strings, family)
            val configs = face.need("UserConfigurations")
            val flavors = configs.need("Flavors")
            val expected = listOf("standard", "ultra", "military", "polar", "aurora", "solar").map { "profile_$it" }
            assertEquals(expected, flavors.children().map { it.attr("id") })
            assertEquals(expected[0], flavors.attr("defaultValue"))
            val definitions = configs.children().filter { it.tagName != "Flavors" }.associateBy { it.attr("id") }
            val selections =
                flavors.children().map { flavor ->
                    val selected = flavor.all("Configuration").associate { it.attr("id") to it.attr("optionId") }
                    selected.forEach { (key, value) ->
                        val definition = definitions.getValue(key)
                        val allowed = if (definition.tagName == "BooleanConfiguration") setOf("TRUE", "FALSE") else definition.children().map { it.attr("id") }.toSet()
                        assertTrue(value in allowed, "$family ${flavor.attr("id")} $key=$value")
                    }
                    flavor.all("ComplicationSlot").forEach { s ->
                        val policy = s.need("DefaultProviderPolicy")
                        assertTrue(policy.attr("primaryProviderType", policy.attr("defaultSystemProviderType")) in slot(face, s.attr("slotId")).attr("supportedTypes").split(" "))
                    }
                    if (family == "apex") {
                        assertFalse("arcContent" in selected)
                        assertEquals(face.all("Scene/ComplicationSlot[@isCustomizable='TRUE']").map { it.attr("slotId") }.toSet(), flavor.all("ComplicationSlot").map { it.attr("slotId") }.toSet())
                    }
                    selected
                }
            val styles = selections.map { it.getValue("progressBars").toInt() }
            assertTrue(styles.any { it < 4 })
            assertTrue(styles.any { it >= 4 })
            assertTrue(styles.any { it % 2 == 0 })
            assertTrue(styles.any { it % 2 == 1 })
            assertEquals(definitions.values.filter { it.hasAttribute("defaultValue") }.associate { it.attr("id") to it.attr("defaultValue") }, selections[0])
            if (family == "orbit") {
                assertEquals(setOf("3", "4"), selections.map { it.getValue("arcContent") }.toSet())
            } else {
                listOf("materialAccent", "materialSubTheme").forEach { key ->
                    val id = selections[4].getValue(key)
                    assertEquals("curated_palette_64", configs.need("ColorConfiguration[@id='$key']/ColorOption[@id='$id']").attr("displayName"))
                }
                assertTrue(slot(face, "0").need("DefaultProviderPolicy").attr("primaryProvider").endsWith(".IobRangedValueComplication"))
                assertEquals("RANGED_VALUE", slot(face, "0").need("DefaultProviderPolicy").attr("primaryProviderType"))
            }
            val once = snapshot(face)
            replaceUserFlavors(face, strings, family)
            assertEquals(once, snapshot(face))
        }
    }

    @Test fun apexTicksAreRadialAndOnlyOppositePairIsSegmented() {
        val face = face("apex")
        configureSegmentedProgress(face, "apex")
        listOf("1", "3").forEach { id ->
            val content = ranged(face, id)
            val marks = content.need("Group[@name='radial-value-ticks']")
            assertEquals(30, marks.all("PartDraw").size)
            assertNull(marks.one(".//Ellipse"))
            assertTrue("255.0000" in marks.need("PartDraw[@name='tick-0-light']/Transform").attr("value"))
            assertTrue("0.0000" in marks.need("PartDraw[@name='tick-9-light']/Transform").attr("value"))
            marks.all("PartDraw").filterIndexed { index, _ -> index % 3 == 0 }.forEachIndexed { index, part ->
                val current = part.need("Line/Transform[@target='startX']").attr("value")
                assertTrue("floor(clamp(" in current)
                assertTrue("== $index" in current)
            }
            assertTrue("? 0 : 255" in content.need("Group[@name='continuous-only-progress-marker-$id']/Transform").attr("value"))
        }
        listOf("0", "2", "4", "5", "6").forEach { assertNull(ranged(face, it).one(".//Group[@name='radial-value-ticks']")) }
        val once = snapshot(face)
        configureSegmentedProgress(face, "apex")
        assertEquals(once, snapshot(face))
    }

    @Test fun measuredApexGeometryIsIdempotentAndProtectsTracksPaletteProviders() {
        val face = face("apex")

        fun protected() =
            face.all("descendant-or-self::*").flatMap { e ->
                e
                    .attrs()
                    .filter { (key, _) -> key in setOf("color", "tintColor", "family", "primaryProvider", "primaryProviderType") || e.tagName == "Arc" && key in setOf("startAngle", "endAngle", "width", "height") }
                    .toSortedMap()
                    .map { (key, value) -> Triple(e.tagName, key, value) }
            }
        val before = protected()
        fitOuterValueText(face)
        assertEquals(before, protected())
        val once = snapshot(face)
        fitOuterValueText(face)
        assertEquals(once, snapshot(face))
        listOf(listOf("0", "337", "361", "START"), listOf("1", "66", "90", "START"), listOf("2", "181", "155", "END"), listOf("3", "282", "246", "END")).forEach { row ->
            val content = ranged(face, row[0])
            content.all(".//TextCircular").forEach { assertEquals(row.drop(1), listOf("startAngle", "endAngle", "align").map(it::attr)) }
            val arc = content.need(".//Arc")
            val image = content.need("PartImage")
            val angle = Math.toRadians(mapOf("0" to 276, "1" to 6, "2" to 96, "3" to 186).getValue(row[0]).toDouble())
            val radius = arc.attr("width").toDouble() / 2
            assertEquals(
                arc.attr("centerX").toDouble() + radius * kotlin.math.sin(angle),
                image
                    .need("Transform[@target='x']")
                    .attr("value")
                    .substringAfterLast(':')
                    .trim()
                    .toDouble() + 11,
                .001,
            )
            assertEquals(
                arc.attr("centerY").toDouble() - radius * kotlin.math.cos(angle),
                image
                    .need("Transform[@target='y']")
                    .attr("value")
                    .substringAfterLast(':')
                    .trim()
                    .toDouble() + 11,
                .001,
            )
            assertEquals(listOf("22", "22"), listOf(image.attr("width"), image.attr("height")))
            assertTrue("52.0000" in image.need("Transform[@target='width']").attr("value"))
            assertTrue(slot(face, row[0]).all(".//Font").all { it.attr("size") == "29.6" })
        }
        listOf("4", "5", "6").forEach { id -> slot(face, id).all(".//Font").forEach { font -> assertEquals(if (font.all(".//Parameter").any { "[COMPLICATION.TITLE]" in it.attr("expression") }) "32" else "39", font.attr("size")) } }
        ranged(face, "1").all(".//TextCircular").also { assertEquals(3, it.size) }.forEach {
            assertEquals("START", it.attr("align"))
            assertEquals("90", it.attr("endAngle"))
            assertEquals("CLOCKWISE", it.attr("direction"))
        }
    }

    @Test fun standaloneApexCommandPreservesFixedNativeGraphAndIsByteStable() {
        val destination = Files.createTempDirectory("apex-export")
        try {
            listOf("raw/watchface.xml", "values/strings.xml").forEach { relative ->
                val target = destination.resolve(relative)
                Files.createDirectories(target.parent)
                Files.copy(source("apex").resolve("res/$relative"), target)
            }
            assertEquals(0, runWatchfaceTool("configure-apex-progress", listOf("--resources", destination.toString())))
            val once = Files.readAllBytes(destination.resolve("raw/watchface.xml"))
            runWatchfaceTool("configure-apex-progress", listOf("--resources", destination.toString()))
            val twice = Files.readAllBytes(destination.resolve("raw/watchface.xml"))
            if (!once.contentEquals(twice)) {
                Files.write(root.resolve("tools/maintenance/build/apex-once.xml"), once)
                Files.write(root.resolve("tools/maintenance/build/apex-twice.xml"), twice)
            }

            fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(digest(once), digest(twice), "ApeX command must be byte-idempotent; failure artifacts are apex-once.xml/apex-twice.xml")
            val graph = slot(parseXml(destination.resolve("raw/watchface.xml")), "7")
            assertEquals("LONG_TEXT EMPTY", graph.attr("supportedTypes"))
            assertTrue(graph.need("DefaultProviderPolicy").attr("primaryProvider").endsWith("NativeApexGraphComplication"))
        } finally {
            destination.toFile().deleteRecursively()
        }
    }

    @Test fun apexGraphBoundsMasksAndProviderRemainCanonical() {
        val face = face("apex")
        val graph = slot(face, "7")
        assertTrue(graph.need("DefaultProviderPolicy").attr("primaryProvider").endsWith(".NativeApexGraphComplication"))
        assertEquals(listOf("91", "91", "330", "97"), listOf("x", "y", "width", "height").map(graph.need("BoundingBox")::attr))
        val content = graph.need("Complication[@type='LONG_TEXT']")
        assertNull(content.one(".//RoundRectangle"))
        assertNull(content.one("PartDraw"))
        val clipped = content.need(".//Group[@name='graph-clipped-content']")
        assertEquals("[CONFIGURATION.materialAccent.0]", clipped.attr("tintColor"))
        val mask = clipped.need("PartDraw[@renderMode='MASK']/Arc")
        assertEquals(listOf("256", "256", "165", "165", "270", "450"), listOf("centerX", "centerY", "width", "height", "startAngle", "endAngle").map(mask::attr))
        assertEquals("165", mask.need("Stroke").attr("thickness"))
        val ink = clipped.need("Group[@name='graph-cap-ink']")
        listOf("PartDraw", "PartImage").forEach { tag -> assertEquals(listOf("91", "91", "330", "97"), listOf("x", "y", "width", "height").map(ink.need(tag)::attr)) }
        assertEquals("#FF000000", ink.need("PartDraw/Rectangle/Fill").attr("color"))
    }

    @Test fun apexGapEditorSecondsAndAmbientContracts() {
        val face = face("apex")
        val configs = face.need("UserConfigurations")
        assertEquals((0..7).map(Int::toString), configs.need("ListConfiguration[@id='progressBars']").children().map { it.attr("id") })
        listOf("progressGap", "progressMarker", "progressGradient", "upperRightMarkers").forEach { assertNull(configs.one("BooleanConfiguration[@id='$it']")) }
        repeat(7) { id ->
            val content = ranged(face, "$id")
            val gap = content.need(".//Group[@name='progress-gap-$id']")
            val arc = gap.need("PartDraw/Arc")
            assertEquals("COUNTER_CLOCKWISE", arc.attr("direction"))
            assertEquals("ROUND", arc.need("Stroke").attr("cap"))
            assertTrue("clamp(" in arc.need("Transform[@target='endAngle']").attr("value"))
            val expression = gap.need("Transform[@target='alpha']").attr("value")
            assertTrue("< 1" in expression)
            assertTrue(
                (
                    if (id == 6) {
                        "\"TRUE\" == \"TRUE\""
                    } else if (id in listOf(4, 5)) {
                        "\"FALSE\" == \"TRUE\""
                    } else {
                        "progressBars"
                    }
                ) in expression,
            )
            if (id < 4) {
                val track = content.all("PartDraw/Arc").first()
                val radius = track.attr("width").toDouble() / 2
                val thickness = track.need("Stroke").attr("thickness").toDouble()
                assertTrue(fixed(19.5 * (thickness / radius) / (12.0 / 59) * .8, 6) in arc.need("Transform[@target='endAngle']").attr("value"))
            }
        }
        assertNull(ranged(face, "6").one("Group[@name='progress-marker-6']"))
        assertNull(ranged(face, "6").one(".//Group[@name='indicator-gradient-content']"))
        val ids =
            configs
                .children()
                .filter { it.tagName != "Flavors" }
                .map { it.attr("id") }
                .toSet()
        assertTrue(ids.intersect(setOf("nightMode", "dialTheme", "minuteMarkers", "showAmPm", "unreadIndicator", "aodStyle")).isEmpty())
        val used =
            face
                .all("descendant-or-self::*")
                .flatMap { it.attrs().values }
                .flatMap { Regex("\\[CONFIGURATION\\.([A-Za-z]+)").findAll(it).map { m -> m.groupValues[1] }.toList() }
                .toSet()
        assertTrue((used - ids).isEmpty())
        configs.all("Flavors/Flavor/Configuration").forEach { assertTrue(it.attr("id") in ids) }
        assertEquals(listOf("0", "1"), configs.need("ListConfiguration[@id='indexStyle']").children().map { it.attr("id") })
        val hands = face.all(".//SecondHand")
        assertEquals(9, hands.size)
        assertEquals(setOf("#FFFFFFFF", "[CONFIGURATION.materialAccent.1]", "#FFFF4040"), hands.map { it.attr("tintColor") }.toSet())
        hands.forEach {
            assertNotNull(it.one("Sweep"))
            assertNull(it.one("Tick"))
            assertEquals("0", it.need("Variant[@mode='AMBIENT']").attr("value"))
        }
        assertEquals(listOf("#FFFFFFFF", "#FF000000"), face.all(".//Group[@name='hands-and-indicators']/Group[@name='fixed-center-hub']//Fill").map { it.attr("color") })
        assertEquals(8, face.all("Scene/ComplicationSlot").size)
        assertNull(face.one(".//ComplicationSlot[@slotId='8']"))
        assertNull(configs.one("ListConfiguration[@id='upperMode']"))
        listOf("original-weekdays-date", "date-icon-only", "weekday-arc", "graph-mode-only").forEach { assertNull(face.one(".//Group[@name='$it']")) }
        face
            .need("Scene")
            .children()
            .filterNot { it.attr("name") == "hands-and-indicators" || it.tagName == "ComplicationSlot" && it.attr("slotId") in listOf("6", "7") }
            .forEach { assertEquals("0", it.need("Variant[@mode='AMBIENT'][@target='alpha']").attr("value")) }
    }

    @Test fun upperRightMarkerIsActiveOnlyForContinuousDotStyles() {
        val expression = ranged(face("apex"), "1").need(".//Group[@name='progress-marker-1']/Transform[@target='alpha']").attr("value").substringBeforeLast('?')
        repeat(8) { style ->
            val resolved = expression.replace("[CONFIGURATION.progressBars]", "\"$style\"").replace("[COMPLICATION.RANGED_VALUE_MAX]", "100").replace("[COMPLICATION.RANGED_VALUE_MIN]", "0")
            assertEquals(style in listOf(4, 5), BooleanExpression(resolved).evaluate(), "progressBars=$style")
        }
    }

    @Test fun trendHostCanvasUsesGlucoseOnlyGeometryAndIsIdempotent() {
        val path = Files.createTempFile("trend-host", ".xml")
        try {
            Files.writeString(path, """<WatchFace><Scene><ComplicationSlot><Complication type="RANGED_VALUE"><PartImage x="10" y="20" width="60" height="60"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage><PartImage x="1" y="2" width="10" height="10"><Image resource="other"/></PartImage></Complication><Complication type="SHORT_TEXT"><PartImage x="0" y="0" width="60" height="60"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage></Complication></ComplicationSlot></Scene></WatchFace>""")
            configureTrendIconGeometry(path)
            val root = parseXml(path)
            val image = root.need(".//Complication[@type='RANGED_VALUE']/PartImage")
            assertEquals(4, image.all("Transform").size)
            assertEquals("[COMPLICATION.RANGED_VALUE_MIN] == 40 && [COMPLICATION.RANGED_VALUE_MAX] == 260 ? -22.5 : 10", image.need("Transform[@target='x']").attr("value"))
            assertEquals("[COMPLICATION.RANGED_VALUE_MIN] == 40 && [COMPLICATION.RANGED_VALUE_MAX] == 260 ? 125 : 60", image.need("Transform[@target='width']").attr("value"))
            assertNull(root.one(".//Complication[@type='SHORT_TEXT']//Transform"))
            val once = Files.readString(path)
            configureTrendIconGeometry(path)
            assertEquals(once, Files.readString(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private class BooleanExpression(
        source: String,
    ) {
        private val tokens = Regex("\\s*(\\(|\\)|&&|\\|\\||!=|==|>=|<=|>|<|!|\"[^\"]*\"|\\d+)").findAll(source).map { it.groupValues[1] }.toList()
        private var position = 0

        fun evaluate(): Boolean = or().also { check(position == tokens.size) { "Unconsumed expression tokens" } }

        private fun take(token: String): Boolean = (tokens.getOrNull(position) == token).also { if (it) position++ }

        private fun or(): Boolean {
            var value = and()
            while (take("||")) {
                val right = and()
                value = value || right
            }
            return value
        }

        private fun and(): Boolean {
            var value = atom()
            while (take("&&")) {
                val right = atom()
                value = value && right
            }
            return value
        }

        private fun atom(): Boolean {
            if (take("!")) return !atom()
            if (take("(")) return or().also { check(take(")")) }
            val left = tokens[position++]
            val operator = tokens[position++]
            val right = tokens[position++]
            return when (operator) {
                "==" -> left == right
                "!=" -> left != right
                ">" -> left.toDouble() > right.toDouble()
                "<" -> left.toDouble() < right.toDouble()
                ">=" -> left.toDouble() >= right.toDouble()
                "<=" -> left.toDouble() <= right.toDouble()
                else -> error("Unsupported comparison $operator")
            }
        }
    }
}
