package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element
import kotlin.math.*

internal fun configureIndicatorGradient(root: Element, family: String) {
    root.all("Scene//ComplicationSlot").forEach { slot ->
        slot.all("Complication").forEach contentLoop@{ content ->
            if (content.attr("type") !in listOf("RANGED_VALUE", "GOAL_PROGRESS")) return@contentLoop
            val track = content.one(".//Arc") ?: return@contentLoop
            var start = track.attr("startAngle").toDouble()
            var end = track.attr("endAngle").toDouble()
            if (family == "orbit" && slot.attr("slotId") == "3") {
                val lo = min(start, end)
                end = max(start, end)
                start = lo
            }
            val cx = track.attr("centerX").toDouble()
            val cy = track.attr("centerY").toDouble()
            val radius = track.attr("width").toDouble() / 2
            val thickness = track.need("Stroke").attr("thickness").toDouble()
            val span = abs(end - start)
            if (span <= 0 || span > 350) return@contentLoop
            val fraction = if (content.attr("type") == "GOAL_PROGRESS") "([COMPLICATION.GOAL_PROGRESS_TARGET_VALUE] <= 0 ? 0 : clamp([COMPLICATION.GOAL_PROGRESS_VALUE] / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1))" else RANGE_FRACTION
            content.all(".//Group").forEach gradientLoop@{ gradient ->
                val name = gradient.attr("name")
                if (!Regex("(?:(?:outer|inner)-\\d+|round|round-goal|progress-gradient-\\d+)(?:-gradient)?").matches(name) || (!name.endsWith("gradient") && !name.startsWith("progress-gradient-"))) return@gradientLoop
                val prefix =
                    gradient
                        .all("descendant-or-self::*")
                        .asSequence()
                        .flatMap { it.attrs().values.asSequence() }
                        .mapNotNull { Regex("\\[CONFIGURATION\\.(primary|secondary|tertiary|materialAccent|materialSubTheme)\\.").find(it)?.groupValues?.get(1) }
                        .firstOrNull() ?: return@gradientLoop
                val visibility = gradient.all("Transform[@target='alpha']").map { it.copy() }
                gradient.clear()
                val box = 2 * ceil(radius + thickness / 2 + 2).toInt()
                val center = box / 2.0
                gradient.set("name" to name, "x" to pyRound(cx - center), "y" to pyRound(cy - center), "width" to box, "height" to box, "angle" to pyFloat(min(start, end) - 8))
                visibility.forEach(gradient::appendChild)
                var marker = if (family == "orbit") "(" + (4..7).joinToString(" || ") { "[CONFIGURATION.progressBars] == \"$it\"" } + ")" else "[CONFIGURATION.progressMarker] == \"TRUE\""
                val bottom = family == "orbit" && name in listOf("round-gradient", "round-goal-gradient") || family == "apex" && slot.attr("slotId") in listOf("4", "5", "6")
                if (bottom) {
                    marker = "\"FALSE\" == \"TRUE\""
                } else if (family == "apex") {
                    marker += " && [CONFIGURATION.progressGap] != \"TRUE\""
                    if (slot.attr("slotId") == "1") marker += " && [CONFIGURATION.upperRightMarkers] != \"TRUE\""
                }
                val actualStart = if (end >= start) "8" else pyFloat(8 + span)
                val actualEnd = if (end >= start) "8 + $fraction * $span" else "${8 + span} - $fraction * $span"

                fun group(parent: Element, groupName: String) = parent.add("Group", "name" to groupName, "x" to "0", "y" to "0", "width" to box, "height" to box)
                val fixed = group(gradient, "fixed-gradient-content")
                val visibleEnd = "($marker) ? ${pyFloat(actualStart.toDouble() + if (end >= start) span else -span)} : ($actualEnd)"
                listOf(false, true).forEach { overlay ->
                    val draw = fixed.add("PartDraw", "x" to "0", "y" to "0", "width" to box, "height" to box)
                    if (overlay) draw.set("tintColor" to "[CONFIGURATION.$prefix.0]")
                    val arc = draw.add("Arc", "centerX" to center, "centerY" to center, "width" to radius * 2, "height" to radius * 2, "startAngle" to actualStart, "endAngle" to actualStart, "direction" to if (end >= start) "CLOCKWISE" else "COUNTER_CLOCKWISE")
                    arc.add("Transform", "target" to "endAngle", "value" to visibleEnd)
                    val stroke = arc.add("Stroke", "color" to if (overlay) "#FFFFFFFF" else "[CONFIGURATION.$prefix.2]", "thickness" to thickness, "cap" to "ROUND")
                    if (overlay) stroke.add("SweepGradient", "centerX" to center, "centerY" to center, "startAngle" to "8", "endAngle" to 8 + span, "colors" to if (end >= start) "#FFFFFFFF #00FFFFFF" else "#00FFFFFF #FFFFFFFF", "positions" to "0 1")
                }
                val fullFraction = "($marker) ? 1 : ($fraction)"
                listOf("start" to "0", "end" to "($fullFraction)").forEach { (endpoint, f) ->
                    val angle = "($actualStart + ($f) * ${end - start}) * 0.017453292519943295"
                    listOf(false, true).forEach { overlay ->
                        val draw = fixed.add("PartDraw", "name" to "$endpoint-rounded-cap-${if (overlay) "light" else "base"}", "x" to "0", "y" to "0", "width" to box, "height" to box)
                        if (overlay) draw.add("Transform", "target" to "alpha", "value" to "255 * (1 - ($f))")
                        val circle = draw.add("Ellipse", "x" to center - thickness / 2, "y" to center - radius - thickness / 2, "width" to thickness, "height" to thickness)
                        circle.add("Transform", "target" to "x", "value" to "${center - thickness / 2} + $radius * sin($angle)")
                        circle.add("Transform", "target" to "y", "value" to "${center - thickness / 2} - $radius * cos($angle)")
                        circle.add("Fill", "color" to "[CONFIGURATION.$prefix.${if (overlay) 0 else 2}]")
                    }
                }
            }
        }
    }
}
