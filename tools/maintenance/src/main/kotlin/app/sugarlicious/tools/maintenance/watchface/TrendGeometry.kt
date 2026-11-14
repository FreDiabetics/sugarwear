package app.sugarlicious.tools.maintenance.watchface

import java.nio.file.Path

internal fun configureTrendIconGeometry(path: Path) {
    val root = parseXml(path)
    var changed = false
    root.all(".//Complication[@type='RANGED_VALUE']//PartImage").forEach { image ->
        if (image.one(".//Image[@resource='[COMPLICATION.MONOCHROMATIC_IMAGE]']") == null ||
            (0 until image.childNodes.length).any {
                image.childNodes.item(it).nodeType == org.w3c.dom.Node.COMMENT_NODE &&
                    image.childNodes
                        .item(it)
                        .nodeValue
                        .contains("trend-host-canvas")
            }
        ) {
            return@forEach
        }
        val x = image.attr("x").toDouble()
        val y = image.attr("y").toDouble()
        val width = image.attr("width").toDouble()
        val height = image.attr("height").toDouble()
        val enlarged = pyRound(width * 125 / 60).toDouble()
        val shift = (enlarged - width) / 2
        image.appendChild(root.ownerDocument.createComment(" trend-host-canvas: equal ink height for single and double arrows "))
        listOf(Triple("x", x - shift, x), Triple("y", y - shift, y), Triple("width", enlarged, width), Triple("height", enlarged, height)).forEach { (target, large, normal) ->
            image.add("Transform", "target" to target, "value" to "[COMPLICATION.RANGED_VALUE_MIN] == 40 && [COMPLICATION.RANGED_VALUE_MAX] == 260 ? ${general(large)} : ${general(normal)}")
        }
        changed = true
    }
    if (changed) writeXml(root, path)
}
