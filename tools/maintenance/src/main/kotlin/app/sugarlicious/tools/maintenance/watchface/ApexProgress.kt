package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element
import kotlin.math.*

internal fun configureApexProgress(root: Element, strings: Element) {
    val configs = root.need("UserConfigurations")
    if (configs.one("ListConfiguration[@id='progressBars']") != null) return
    if (configs.one("BooleanConfiguration[@id='progressGap']") == null) {
        val next = configs.need("BooleanConfiguration[@id='progressGradient']").nextSibling
        val item = configs.add("BooleanConfiguration", "id" to "progressGap", "displayName" to "progress_gap", "defaultValue" to "TRUE")
        configs.insertBefore(item, next)
    }
    // Retain the legacy export label byte-for-byte; correcting translations is
    // a separate change from replacing the implementation language.
    label(strings, "progress_gap", "TrennlÃ¼cke")
    val marker = "[CONFIGURATION.progressMarker] == \"TRUE\""
    val valid = "[COMPLICATION.RANGED_VALUE_MAX] > [COMPLICATION.RANGED_VALUE_MIN]"
    val fraction = "([COMPLICATION.RANGED_VALUE_MAX] <= [COMPLICATION.RANGED_VALUE_MIN] ? 0 : (clamp([COMPLICATION.RANGED_VALUE_VALUE], [COMPLICATION.RANGED_VALUE_MIN], [COMPLICATION.RANGED_VALUE_MAX]) - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]))"
    root.all("Scene//ComplicationSlot").forEach { slot ->
        val identity = slot.attr("slotId")
        val ranged = slot.one("Complication[@type='RANGED_VALUE']")
        if (ranged != null && identity != "7") {
            if (identity == "1") ranged.all(".//TextCircular").forEach { it.set("align" to "END") }
            val effective = "($marker && [CONFIGURATION.progressGap] != \"TRUE\"" + if (identity == "1") " && [CONFIGURATION.upperRightMarkers] != \"TRUE\")" else ")"
            ranged.rewriteAttributes { if (marker in it && "[CONFIGURATION.progressGap]" !in it) it.replace(marker, effective) else it }
            val track = ranged.all("PartDraw").first { it.one("Arc") != null }
            val arc = track.need("Arc")
            val start = arc.attr("startAngle").toDouble()
            val end = arc.attr("endAngle").toDouble()
            val radius = arc.attr("width").toDouble() / 2
            val thickness = arc.need("Stroke").attr("thickness").toDouble()
            var gap = 19.5 * (thickness / radius) / ((12 * 512.0 / 450) / (59 * 512.0 / 450))
            if (identity in listOf("0", "1", "2", "3")) gap *= .8
            val active = "[CONFIGURATION.progressGap] == \"TRUE\" && $valid" + if (identity == "1") " && [CONFIGURATION.upperRightMarkers] != \"TRUE\"" else ""
            val transform = track.one("Transform[@target='alpha']") ?: track.add("Transform", "target" to "alpha").also { track.insertBefore(it, track.firstChild) }
            transform.set("value" to "($active) ? 0 : 55")
            ranged.one("Group[@name='progress-gap-$identity']")?.remove()
            val group = ranged.add("Group", "name" to "progress-gap-$identity", "x" to "0", "y" to "0", "width" to track.attr("width"), "height" to track.attr("height"))
            group.add("Transform", "target" to "alpha", "value" to "($active) && $fraction < 1 ? 55 : 0")
            val part = group.add("PartDraw", "x" to "0", "y" to "0", "width" to track.attr("width"), "height" to track.attr("height"))
            val remaining = arc.copy().set("startAngle" to general(end), "endAngle" to general(start), "direction" to "COUNTER_CLOCKWISE")
            remaining.add("Transform", "target" to "endAngle", "value" to "clamp(${general(start)} + ${fixed(gap,6)} + ${general(end - start)} * $fraction, ${general(start)}, ${general(end)}) - ${fixed(gap,6)} * ($fraction == 0)")
            part.appendChild(remaining)
            ranged.insertBefore(group, track.nextSibling)
        }
    }
}

internal fun fitOuterValueText(root: Element) {
    val captions = mapOf("0" to listOf("337", "361", "START"), "1" to listOf("66", "90", "START"), "2" to listOf("181", "155", "END"), "3" to listOf("282", "246", "END"))
    val angles = mapOf("0" to 276, "1" to 6, "2" to 96, "3" to 186)
    val glucose = "[COMPLICATION.RANGED_VALUE_MIN] == 40 && [COMPLICATION.RANGED_VALUE_MAX] == 260"

    fun clearGeometry(image: Element) {
        image.all("Transform").filter { it.attr("target") in listOf("x", "y", "width", "height") }.forEach { it.remove() }
    }
    root.all("Scene/ComplicationSlot").forEach { slot ->
        val identity = slot.attr("slotId")
        if (identity in captions) {
            slot.all(".//Font").forEach { it.set("size" to "29.6") }
            val ranged = slot.need("Complication[@type='RANGED_VALUE']")
            ranged.all(".//TextCircular").forEach { text -> listOf("startAngle", "endAngle", "align").zip(captions.getValue(identity)).forEach { (k, v) -> text.set(k to v) } }
            if (identity == "1") ranged.all(".//Font").forEach { it.set("letterSpacing" to "-0.03") }
            val track = ranged.need(".//Arc")
            val angle = Math.toRadians(angles.getValue(identity).toDouble())
            val radius = track.attr("width").toDouble() / 2
            val cx = track.attr("centerX").toDouble() + radius * sin(angle)
            val cy = track.attr("centerY").toDouble() - radius * cos(angle)
            ranged.all("PartImage").forEach { image ->
                clearGeometry(image)
                val normal = mapOf("x" to cx - 11, "y" to cy - 11, "width" to 22.0, "height" to 22.0)
                normal.forEach { (key, value) -> image.set(key to pyRound(value)) }
                mapOf("x" to cx - 26, "y" to cy - 26, "width" to 52.0, "height" to 52.0).forEach { (key, value) -> image.add("Transform", "target" to key, "value" to "$glucose ? ${fixed(value,4)} : ${fixed(normal.getValue(key),4)}") }
            }
            slot.all("Complication[@type='SHORT_TEXT']//PartImage").forEach { image ->
                val width = image.attr("width").toDouble()
                val height = image.attr("height").toDouble()
                if (width > 25) {
                    listOf("x" to (width - 25) / 2, "y" to (height - 25) / 2).forEach { (axis, delta) ->
                        val position = image.attr(axis).toDouble() + delta
                        image.set(axis to pyRound(position))
                        image.add("Transform", "target" to axis, "value" to fixed(position, 4))
                    }
                    image.set("width" to "25", "height" to "25")
                }
            }
        } else if (identity in listOf("4", "5", "6")) {
            slot.all(".//Font").forEach { font -> font.set("size" to if (font.all(".//Parameter").any { "[COMPLICATION.TITLE]" in it.attr("expression") }) "32" else "39") }
            if (identity == "6") {
                slot.all("Complication[@type='RANGED_VALUE']/PartImage").forEach { image ->
                    clearGeometry(image)
                    val normal = mapOf("x" to 63.5, "y" to 117.0, "width" to 26.0, "height" to 26.0)
                    normal.forEach { (key, value) -> image.set(key to pyRound(value)) }
                    mapOf("x" to 48.0, "y" to 101.5, "width" to 57.0, "height" to 57.0).forEach { (key, value) -> image.add("Transform", "target" to key, "value" to "$glucose ? ${general(value)} : ${general(normal.getValue(key))}") }
                }
            } else {
                slot.all(".//PartText").filter { it.attr("width") in listOf("103", "107", "117") }.forEach { part ->
                    val title = part.all(".//Parameter").any { "[COMPLICATION.TITLE]" in it.attr("expression") }
                    val ranged = part.attr("x") == "10" || part.attr("y") in listOf("8", "50")
                    part.set(
                        "x" to "3",
                        "width" to "117",
                        "height" to "48",
                        "y" to
                            if (ranged) {
                                if (title) {
                                    "8"
                                } else {
                                    "50"
                                }
                            } else if (title) {
                                "15"
                            } else {
                                "60"
                            },
                    )
                    part.one("Transform[@target='y']")?.set("value" to "[COMPLICATION.TITLE] == \"\" ? 37.5 : ${part.attr("y")}")
                }
            }
        }
    }
}

internal fun simplifyApexEditor(root: Element, strings: Element) {
    val configs = root.need("UserConfigurations")
    val scene = root.need("Scene")
    val removed = setOf("nightMode", "dialTheme", "minuteMarkers", "showAmPm", "unreadIndicator", "aodStyle", "upperMode")
    root.all(".//*[@id]").filter { it.attr("id") in removed }.forEach { it.remove() }
    label(strings, "background_pattern", "Hintergrund")
    label(strings, "upper_content", "Graph")
    listOf("seconds_white" to "Weiß", "seconds_theme" to "Themefarbe", "seconds_red" to "Rot").forEach { (key, value) -> label(strings, key, value) }
    val seconds = configs.need("ListConfiguration[@id='secondStyle']")
    seconds.children().forEach { it.remove() }
    listOf("0" to "seconds_white", "1" to "seconds_theme", "3" to "seconds_red", "2" to "option_off").forEach { (id, name) -> seconds.add("ListOption", "id" to id, "displayName" to name) }
    configs
        .need("ListConfiguration[@id='indexStyle']")
        .children()
        .drop(2)
        .forEach { it.remove() }
    val names = setOf("am-pm", "unread-indicator", "minute-lines", "original-weekdays-date", "date-icon-only", "weekday-arc")
    root.all(".//*").forEach { item ->
        if (item.attr("name") in names) {
            item.remove()
        } else if (item.tagName == "Transform" && "[CONFIGURATION.minuteMarkers]" in item.attr("value")) {
            if ("== \"0\"" !in item.attr("value")) (item.parentNode as Element).set("alpha" to "0")
            item.remove()
        }
    }
    root.rewriteAttributes { it.replace("[CONFIGURATION.nightMode]", "#FFFFFFFF").replace("[CONFIGURATION.dialTheme]", "#FFFFFFFF") }
    listOf("background-web", "background-target").forEach { name ->
        scene.one("Group/Group[@name='$name']")?.let { group ->
            group.set("alpha" to "145")
            group.need("Transform").let { it.set("value" to it.attr("value").replace("112", "145")) }
            group.all(".//Stroke").forEach { it.set("thickness" to "1.5") }
        }
    }
    val graph = scene.need("ComplicationSlot[@slotId='7']").set("supportedTypes" to "SMALL_IMAGE EMPTY")
    graph.all("Complication").filter { it.attr("type") !in listOf("SMALL_IMAGE", "EMPTY") }.forEach { it.remove() }
    val content = graph.need("Complication[@type='SMALL_IMAGE']")
    content.one("Group[@name='graph-mode-only']")?.let { wrapper ->
        wrapper.children().filter { it.tagName !in listOf("Transform", "Variant") }.forEach(content::appendChild)
        wrapper.remove()
    }
    root.all(".//ComplicationSlot[@slotId='8']").forEach { it.remove() }
    strings.children().filter { it.attr("name") in setOf("upper_mode", "upper_graph", "upper_date", "upper_icon") }.forEach { it.remove() }
    scene.all("Group[@name='hands-and-indicators']/Group/Group").forEach { style ->
        val group = style.need("Group[@name='seconds-variants']")
        val original = group.need("Group/AnalogClock/SecondHand").copy()
        group.clear()
        group.set("name" to "seconds-variants", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
        listOf("0" to "#FFFFFFFF", "1" to "[CONFIGURATION.materialAccent.1]", "3" to "#FFFF4040").forEach { (option, color) ->
            val variant = group.add("Group", "name" to "seconds-color-$option", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
            variant.add("Transform", "target" to "alpha", "value" to "[CONFIGURATION.secondStyle] == \"$option\" ? 255 : 0")
            val clock = variant.add("AnalogClock", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
            val hand = original.copy().set("tintColor" to color)
            hand.children().forEach { it.remove() }
            hand.add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to "0")
            hand.add("Sweep", "frequency" to "15")
            clock.appendChild(hand)
        }
    }
    scene.all("ComplicationSlot[@slotId='3']/Complication/PartImage").forEach { it.set("y" to "280") }
    val hands = scene.need("Group[@name='hands-and-indicators']")
    val hub =
        hands.one("Group[@name='fixed-center-hub']") ?: hands.add("Group", "name" to "fixed-center-hub", "x" to "0", "y" to "0", "width" to "512", "height" to "512").apply {
            val draw = add("PartDraw", "x" to "0", "y" to "0", "width" to "512", "height" to "512")
            draw.add("Ellipse", "x" to "247", "y" to "247", "width" to "18", "height" to "18").add("Fill", "color" to "#FFFFFFFF")
            draw.add("Ellipse", "x" to "253", "y" to "253", "width" to "6", "height" to "6").add("Fill", "color" to "#FF000000")
        }
    if (hub.one("Transform[@target='alpha']") == null) {
        val visibility = hub.add("Transform", "target" to "alpha", "value" to "[CONFIGURATION.secondStyle] == \"2\" ? 255 : 0")
        hub.insertBefore(visibility, hub.firstChild)
        hub.add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to "255")
    }
    scene.children().forEach { child ->
        val keep = child === hands || child.tagName == "ComplicationSlot" && child.attr("slotId") in listOf("6", "7")
        if (!keep) {
            (child.one("Variant[@mode='AMBIENT'][@target='alpha']") ?: child.add("Variant", "mode" to "AMBIENT", "target" to "alpha")).set("value" to "0")
        } else if (child.tagName == "ComplicationSlot") {
            child.one("Variant[@mode='AMBIENT'][@target='alpha']")?.set("value" to "160")
        }
    }
    configs.all("Flavors/Flavor/ComplicationSlot[@slotId='7']/DefaultProviderPolicy").forEach { it.set("primaryProvider" to "app.sugarlicious/app.sugarlicious.complications.GlucoseGraphComplication", "primaryProviderType" to "SMALL_IMAGE") }
}
