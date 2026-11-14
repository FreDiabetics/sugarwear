package app.sugarlicious.tools.maintenance.assets

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import app.sugarlicious.tools.maintenance.watchface.add as addNode
import app.sugarlicious.tools.maintenance.watchface.all as selectAll
import app.sugarlicious.tools.maintenance.watchface.need as requireNode
import app.sugarlicious.tools.maintenance.watchface.one as selectOne
import app.sugarlicious.tools.maintenance.watchface.parseXml as parseFace
import app.sugarlicious.tools.maintenance.watchface.set as setAttrs
import app.sugarlicious.tools.maintenance.watchface.writeXml as writeFace

private val handsetFiles = listOf("1hours_hand.svg", "2minutes_hand.svg", "3dots.svg", "4second_hand.svg", "5topdot.svg")

private fun handsetSvg(source: Path, accent: Boolean? = null, white: Boolean = false): Document {
    val doc = parse(source)
    val root = doc.documentElement
    require(root.getAttribute("viewBox") == "0 0 512 512") { "Expected centered 512x512 Orbit artwork" }
    val fills = root.all("style").flatMap { Regex("\\.([\\w-]+)\\s*\\{\\s*fill:\\s*([^;]+);").findAll(it.textContent).map { match -> match.groupValues[1] to match.groupValues[2].trim() }.toList() }.toMap()
    root.children().filter { it.localName == "defs" }.forEach(root::removeChild)
    root.children().forEach { original ->
        val fill = original.getAttribute("fill").ifEmpty { fills[original.getAttribute("class")] ?: "#000000" }
        val isAccent = fill.equals("#f73d45", ignoreCase = true)
        if (accent != null && isAccent != accent) {
            root.removeChild(original)
        } else {
            val element =
                if (original.localName == "polygon") {
                    val points = original.getAttribute("points").trim().split(Regex("[ ,]+"))
                    require(points.size >= 6 && points.size % 2 == 0)
                    val path = doc.createElementNS("http://www.w3.org/2000/svg", "path")
                    path.setAttribute("d", points.chunked(2).mapIndexed { index, pair -> "${if (index == 0) "M" else "L"}${pair[0]},${pair[1]}" }.joinToString(" ") + "Z")
                    root.replaceChild(path, original)
                    path
                } else {
                    original
                }
            element.removeAttribute("class")
            element.setAttribute("fill", if (white || accent == true) "#ffffff" else fill)
        }
    }
    return doc
}

internal fun importOrbitHandset(source: Path, resources: Path) {
    handsetFiles.forEach { require(Files.isRegularFile(source.resolve(it))) { "Missing Orbit layer $it" } }
    val output = resources.resolve("drawable-nodpi")
    Files.createDirectories(output)

    fun render(doc: Document, name: String, size: Int = 512) {
        val temp = Files.createTempFile("orbit-handset", ".svg")
        try {
            writeXml(temp, doc)
            svgPng(Files.readString(temp), output.resolve("$name.png"), size)
        } finally {
            Files.delete(temp)
        }
    }
    listOf("hour", "minute").forEachIndexed { index, hand ->
        render(handsetSvg(source.resolve(handsetFiles[index]), accent = false), "orbit_custom_${hand}_4")
        render(handsetSvg(source.resolve(handsetFiles[index]), accent = true), "orbit_custom_${hand}_4_accent")
    }
    render(handsetSvg(source.resolve(handsetFiles[2])), "orbit_custom_hub_4")
    render(handsetSvg(source.resolve(handsetFiles[3]), white = true), "orbit_custom_second_4")
    render(handsetSvg(source.resolve(handsetFiles[4])), "orbit_custom_top_4")

    val path = resources.resolve("raw/watchface.xml")
    val face = parseFace(path)
    require(face.getAttribute("width") == "512" && face.getAttribute("height") == "512")
    val config = face.requireNode("UserConfigurations/ListConfiguration[@id='apexHands']")
    if (config.selectOne("ListOption[@id='3']") == null) config.addNode("ListOption", "id" to "3", "displayName" to "apexHands_3", "icon" to "orbit_hand_preview_18")
    val hands = face.requireNode("Scene/Group[@name='hands']")
    hands.selectOne("Group[@name='hands-3']")?.let(hands::removeChild)
    val group = hands.requireNode("Group[@name='hands-2']").cloneNode(true) as Element
    group.setAttrs("name" to "hands-3")
    group.requireNode("Transform[@target='alpha']").setAttrs("value" to "[CONFIGURATION.apexHands] == \"3\" ? 255 : 0")
    val clock = group.requireNode("AnalogClock")
    listOf("HourHand" to "hour", "MinuteHand" to "minute").forEach { (tag, name) ->
        val base = clock.requireNode(tag).setAttrs("resource" to "orbit_custom_${name}_4")
        val middle = base.cloneNode(true) as Element
        middle.setAttrs("resource" to "orbit_custom_${name}_4_accent", "tintColor" to "[CONFIGURATION.tertiary.2]")
        clock.insertBefore(middle, base.nextSibling)
    }
    val hub = group.addNode("PartImage", "name" to "custom-hub-4", "x" to 0, "y" to 0, "width" to 512, "height" to 512)
    hub.addNode("Image", "resource" to "orbit_custom_hub_4")
    group.insertBefore(hub, clock.nextSibling)
    group.selectAll(".//Group").forEach { it.setAttrs("name" to it.getAttribute("name").replace("seconds-2-", "seconds-3-")) }
    group.selectAll(".//SecondHand").forEach { it.setAttrs("resource" to "orbit_custom_second_4") }
    group.addNode("PartImage", "name" to "custom-top-4", "x" to 0, "y" to 0, "width" to 512, "height" to 512).addNode("Image", "resource" to "orbit_custom_top_4")
    val ambient = hands.requireNode(".//Group[@name='ambient-seconds-hub']")
    hands.insertBefore(group, hands.selectOne("Group[@name='legacy-ambient-hub-gate']") ?: ambient)
    ambient.requireNode("Transform").setAttrs("value" to "[CONFIGURATION.secondSweep] == \"3\" && [CONFIGURATION.apexHands] != \"3\" ? 255 : 0")
    if (ambient.selectOne("Variant[@mode='AMBIENT']") != null) {
        // An outer visibility gate keeps the existing ambient override from covering the custom hub.
        val gate = hands.selectOne("Group[@name='legacy-ambient-hub-gate']") ?: hands.addNode("Group", "name" to "legacy-ambient-hub-gate", "x" to 0, "y" to 0, "width" to 512, "height" to 512)
        if (gate.selectOne("Transform") == null) gate.addNode("Transform", "target" to "alpha", "value" to "[CONFIGURATION.apexHands] == \"3\" ? 0 : 255")
        gate.appendChild(ambient)
    }
    writeFace(face, path)
    val stringsPath = resources.resolve("values/strings.xml")
    val strings = parseFace(stringsPath)
    if (strings.selectOne("string[@name='apexHands_3']") == null) strings.addNode("string", "name" to "apexHands_3").textContent = "Orbit · 4"
    writeFace(strings, stringsPath)

    val accentConfiguration = face.requireNode("UserConfigurations/ColorConfiguration[@id='tertiary']")
    val palette = accentConfiguration.requireNode("ColorOption[@id='${accentConfiguration.getAttribute("defaultValue")}']")
    val previewAccent =
        palette
            .getAttribute("colors")
            .split(Regex("\\s+"))[2]
            .removePrefix("#FF")
            .let { "#$it" }
    val preview = parseXml("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><rect width=\"512\" height=\"512\" fill=\"#000000\"/></svg>")
    handsetFiles.forEachIndexed { index, name ->
        val layer = handsetSvg(source.resolve(name), white = index == 3)
        val rotation = mapOf(0 to 304, 1 to 48, 3 to 192)[index]
        val target = preview.createElementNS("http://www.w3.org/2000/svg", "g")
        rotation?.let { target.setAttribute("transform", "rotate($it 256 256)") }
        layer.documentElement.children().forEach { shape ->
            if (shape.getAttribute("fill").equals("#f73d45", ignoreCase = true)) shape.setAttribute("fill", previewAccent)
            target.appendChild(preview.importNode(shape, true))
        }
        preview.documentElement.appendChild(target)
    }
    render(preview, "orbit_hand_preview_18", 300)
    require(ImageIO.read(output.resolve("orbit_custom_top_4.png").toFile()).width == 512)
}
