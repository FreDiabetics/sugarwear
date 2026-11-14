package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element
import kotlin.math.*

internal fun configureSegmentedProgress(root: Element, family: String) {
    val identities = if (family == "apex") listOf("1", "3") else listOf("7")
    if (family == "apex") {
        listOf("0", "2").forEach { identity ->
            root.one("Scene/ComplicationSlot[@slotId='$identity']/Complication[@type='RANGED_VALUE']")?.let { content ->
                content.children().forEach { child ->
                    if (child.attr("name").startsWith("continuous-only-")) {
                        child.children().filter { it.tagName != "Transform" }.forEach { content.insertBefore(it, child) }
                        child.remove()
                    } else if (child.attr("name") == "radial-value-ticks") {
                        child.remove()
                    }
                }
            }
        }
    }
    identities.forEach identityLoop@{ identity ->
        val content = root.one("Scene/ComplicationSlot[@slotId='$identity']/Complication[@type='RANGED_VALUE']") ?: return@identityLoop
        if (family == "orbit") {
            content.one("Group[@name='outer-1-track']/Transform[@target='alpha']")?.let { background ->
                if (!background.attr("value").startsWith("$SEGMENTED ?")) background.set("value" to "$SEGMENTED ? 255 : (${background.attr("value")})")
            }
        }
        content.all("descendant-or-self::*").forEach { parent -> parent.children().filter { it.attr("name").endsWith("-segments") || it.attr("name") == "radial-value-ticks" }.forEach { it.remove() } }
        content.all("descendant-or-self::*").forEach { parent ->
            parent.all("Condition").forEach { condition ->
                val branches = condition.all("Compare")
                if (branches.isNotEmpty() && branches.all { branch -> branch.children().all { it.attr("name") == "retired-static-ticks" } }) {
                    condition.one("Default")?.let { default ->
                        default.children().forEach { parent.insertBefore(it, condition) }
                        condition.remove()
                    }
                }
            }
        }
        content.children().forEach { child ->
            val name = child.attr("name")
            if (child.tagName == "Group" && !name.startsWith("continuous-only-") && listOf("progress-fill-", "progress-gap-", "progress-marker-", "-solid", "-gradient", "-marker", "-fill").any(name::contains)) {
                val wrapper = content.ownerDocument.createElement("Group").set("name" to "continuous-only-$name", "x" to "0", "y" to "0", "width" to content.attr("width", "512"), "height" to content.attr("height", "512"))
                wrapper.add("Transform", "target" to "alpha", "value" to "$SEGMENTED ? 0 : 255")
                content.insertBefore(wrapper, child)
                wrapper.appendChild(child)
            }
        }
        val track = content.need(".//Arc")
        val cx = track.attr("centerX").toDouble()
        val cy = track.attr("centerY").toDouble()
        val radius = track.attr("width").toDouble() / 2
        val thickness = track.need("Stroke").attr("thickness").toDouble()
        val start = track.attr("startAngle").toDouble()
        val end = track.attr("endAngle").toDouble()
        val prefix = requireNotNull(Regex("CONFIGURATION\\.([A-Za-z]+)\\.").find(track.need("Stroke").attr("color"))).groupValues[1]
        val marks = content.add("Group", "name" to "radial-value-ticks", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
        marks.add("Transform", "target" to "alpha", "value" to "$SEGMENTED && [COMPLICATION.RANGED_VALUE_MAX] > [COMPLICATION.RANGED_VALUE_MIN] ? 255 : 0")
        val count = if (family == "apex") 10 else 12
        val selected = "floor(clamp($RANGE_FRACTION, 0, 0.99) * $count)"
        val full = "([CONFIGURATION.progressBars] == \"6\" || [CONFIGURATION.progressBars] == \"7\")"
        val gradient = "([CONFIGURATION.progressBars] == \"3\" || [CONFIGURATION.progressBars] == \"7\")"
        repeat(count) { index ->
            val position = index.toDouble() / (count - 1)
            val angle = Math.toRadians(start + 1 + (end - start - 2) * position)
            val current = "($selected) == $index"
            listOf("base", "light", "solid").forEach { tone ->
                val part = marks.add("PartDraw", "name" to "tick-$index-$tone", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
                val alpha =
                    when (tone) {
                        "light" -> "($gradient) ? ${fixed(255 * (1 - position),4)} : 0"
                        "base" -> "($gradient) ? 255 : 0"
                        else -> "($gradient) ? 0 : 255"
                    }
                part.add("Transform", "target" to "alpha", "value" to "($full || $index <= ($selected)) ? ($alpha) : 0")
                val width = thickness * if (family == "apex") .3 else 1.0 / 3
                val outer = radius + thickness / 2 - if (family == "orbit") width / 2 else 0.0
                val normalInner = radius - thickness / 2 + if (family == "orbit") width / 2 else 0.0
                val longInner = if (family == "apex") radius - thickness * 1.1 else radius - thickness * .9 + width / 2
                val line = part.add("Line", "startX" to cx + normalInner * sin(angle), "startY" to cy - normalInner * cos(angle), "endX" to cx + outer * sin(angle), "endY" to cy - outer * cos(angle))
                val inner = "($current ? $longInner : $normalInner)"
                line.add("Transform", "target" to "startX", "value" to "$cx + $inner * ${sin(angle)}")
                line.add("Transform", "target" to "startY", "value" to "$cy - $inner * ${cos(angle)}")
                line
                    .add(
                        "Stroke",
                        "color" to "[CONFIGURATION.$prefix.${if (tone == "light") {
                            0
                        } else if (tone == "solid") {
                            1
                        } else {
                            2
                        }}]",
                        "thickness" to width,
                        "cap" to "ROUND",
                    ).add("Transform", "target" to "thickness", "value" to if (family == "apex") "$current ? ${thickness * .36} : $width" else "$width")
            }
        }
    }
}
