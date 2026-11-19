package app.sugarlicious.tools.maintenance.assets

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path

internal fun vigilMarkers(args: List<String>, root: Path) {
    val opts = options(args, setOf("--source"))
    val source = root.resolve("design-assets/vigil")
    Files.createDirectories(source)
    val destination = root.resolve("watchfaces/vigil/src/main/res/drawable-nodpi")
    for (name in listOf("vigil_minutemarkers_close", "vigil_minutemarkers_wide", "vigil_hourmarkers_close", "vigil_hourmarkers_wide")) {
        val path = source.resolve("$name.svg")
        opts["--source"]?.let { copy(Path.of(it).resolve("$name.svg"), path) }
        val doc = parse(path)
        require(doc.documentElement.getAttribute("viewBox") == "0 0 450 450")
        if ("hourmarkers" in name) {
            doc.documentElement
                .all("line")
                .filter {
                    it.getAttribute("x1") == "225" && it.getAttribute("x2") == "225" && (it.getAttribute("y1").toDoubleOrNull() ?: 0.0) > 225
                }.forEach { it.parentNode.removeChild(it) }
        }
        val writer = java.io.StringWriter()
        javax.xml.transform.TransformerFactory
            .newInstance()
            .newTransformer()
            .transform(
                javax.xml.transform.dom
                    .DOMSource(doc),
                javax.xml.transform.stream
                    .StreamResult(writer),
            )
        svgPng(writer.toString().replace(Regex("#[0-9a-fA-F]{6}"), "#ffffff"), destination.resolve("$name.png"), 450)
    }
    for (name in listOf("hour_hand_transparent.png", "minute_hand_transparent.png", "second_hand_transparent.png")) {
        copy(root.resolve("shared/sugarlicious/watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/$name"), destination.resolve(name))
    }
}

internal fun configureVigil(root: Path) {
    val path = root.resolve("watchfaces/vigil/src/main/res/raw/watchface.xml")
    val document = parse(path)
    val face = document.documentElement
    face.children().first { it.tagName == "Metadata" && it.getAttribute("key") == "PREVIEW_TIME" }.setAttribute("value", "10:08:32")
    val configs = face.child("UserConfigurations")
    configs.children().first { it.tagName == "BooleanConfiguration" && it.getAttribute("id") == "showHands" }.setAttribute("defaultValue", "FALSE")
    configs.children().filter { it.tagName == "ColorConfiguration" && it.getAttribute("id") == "secondColor" }.forEach { configs.removeChild(it) }
    configs.add("ColorConfiguration", "id" to "secondColor", "displayName" to "seconds_color", "defaultValue" to "red").apply {
        add("ColorOption", "id" to "red", "displayName" to "seconds_red", "colors" to "#FFFF3B30")
        add("ColorOption", "id" to "gray", "displayName" to "seconds_gray", "colors" to "#FF9E9E9E")
    }

    fun visible(parent: Element): Element {
        val condition = parent.add("Condition")
        condition.add("Expressions").add("Expression", "name" to "handsVisible").textContent = "[CONFIGURATION.showHands] == \"TRUE\""
        return condition.add("Compare", "expression" to "handsVisible")
    }
    val scene = face.child("Scene")

    fun slot(id: String) = scene.children().first { it.tagName == "ComplicationSlot" && it.getAttribute("slotId") == id }

    fun complication(slot: Element): Element =
        slot.children().first { it.tagName == "Complication" && it.getAttribute("type") == "SMALL_IMAGE" }.apply {
            while (hasChildNodes()) removeChild(firstChild)
            val names = (0 until attributes.length).map { attributes.item(it).nodeName }
            names.forEach { removeAttribute(it) }
            setAttribute("type", "SMALL_IMAGE")
        }
    val group =
        visible(complication(slot("7")).add("Group", "name" to "vigil-hands-visibility", "x" to 0, "y" to 0, "width" to 450, "height" to 450))
            .add("Group", "name" to "vigil-native-hands", "x" to 0, "y" to 0, "width" to 450, "height" to 450)
    group.add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to 160)
    group.add("PartImage", "renderMode" to "MASK", "x" to 0, "y" to 0, "width" to 450, "height" to 450).add("Image", "resource" to "[COMPLICATION.SMALL_IMAGE]")
    for (shadow in listOf(true, false)) {
        val clock = group.add("AnalogClock", "x" to 0, "y" to 0, "width" to 450, "height" to 450)
        if (shadow) clock.add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to 0)
        for (kind in listOf("Hour", "Minute")) {
            clock.add(kind + "Hand", "resource" to kind.lowercase() + "_hand_transparent", "x" to if (shadow) 2 else 0, "y" to if (shadow) 3 else 0, "width" to 450, "height" to 450, "pivotX" to "0.5", "pivotY" to "0.5").apply {
                if (shadow) setAttribute("tintColor", "#A0000000")
            }
        }
    }
    val hub = group.add("PartDraw", "x" to 0, "y" to 0, "width" to 450, "height" to 450)
    for ((radius, color) in listOf(8.5 * 450 / 512 to "#FFFFFFFF", 5.0 * 450 / 512 to "#FF000000")) hub.add("Ellipse", "x" to 225 - radius, "y" to 225 - radius, "width" to radius * 2, "height" to radius * 2).add("Fill", "color" to color)
    for (shadow in listOf(true, false)) {
        val clock = group.add("AnalogClock", "x" to 0, "y" to 0, "width" to 450, "height" to 450)
        clock.add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to 0)
        clock.add("SecondHand", "resource" to "second_hand_transparent", "x" to if (shadow) 2 else 0, "y" to if (shadow) 3 else 0, "width" to 450, "height" to 450, "pivotX" to "0.5", "pivotY" to "0.5", "tintColor" to if (shadow) "#A0000000" else "[CONFIGURATION.secondColor.0]").add("Sweep", "frequency" to 15)
    }
    val center = slot("8")
    mapOf("x" to "200", "y" to "200", "width" to "50", "height" to "50").forEach { (k, v) -> center.setAttribute(k, v) }
    mapOf("x" to "0", "y" to "0", "width" to "50", "height" to "50").forEach { (k, v) -> center.child("BoundingOval").setAttribute(k, v) }
    visible(complication(center).add("Group", "name" to "vigil-center-visibility", "x" to 0, "y" to 0, "width" to 50, "height" to 50)).add("PartImage", "x" to 0, "y" to 0, "width" to 50, "height" to 50, "alpha" to 0).add("Image", "resource" to "[COMPLICATION.SMALL_IMAGE]")
    writeXml(path, document)
}
