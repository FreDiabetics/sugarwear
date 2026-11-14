package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element

internal fun unifyApexProgressSettings(root: Element, strings: Element) {
    val configs = root.need("UserConfigurations")
    val expressions = linkedMapOf("progressMarker" to choices(4, 5, 6, 7), "progressGradient" to choices(1, 3, 5, 7), "upperRightMarkers" to choices(2, 3, 6, 7))
    expressions["progressGap"] = "!${expressions.getValue("progressMarker")}"

    fun removeMarkers(content: Element) {
        content.all(".//*[starts-with(@name,'progress-marker-')]").forEach { it.remove() }
    }
    root.one("Scene//ComplicationSlot[@slotId='6']/Complication[@type='RANGED_VALUE']")?.let { bottom ->
        removeMarkers(bottom)
        bottom.rewriteAttributes { original ->
            var value = original
            listOf("progressMarker" to "FALSE", "progressGap" to "TRUE").forEach { (key, literal) -> value = Regex("\\[CONFIGURATION\\.$key\\]\\s*(==|!=)\\s*\"TRUE\"").replace(value) { "\"$literal\" ${it.groupValues[1]} \"TRUE\"" } }
            value
        }
    }

    fun rewrite(original: String): String {
        var value = original
        expressions.forEach { (key, expression) -> value = Regex("\\[CONFIGURATION\\.$key\\]\\s*(==|!=)\\s*\"(TRUE|FALSE)\"").replace(value) { m -> if ((m.groupValues[1] == "==") == (m.groupValues[2] == "TRUE")) expression else "!$expression" } }
        return value
    }
    root.rewriteAttributes(::rewrite)
    root.rewriteText(::rewrite)
    listOf("4", "5", "6").forEach { identity ->
        root.one("Scene//ComplicationSlot[@slotId='$identity']/Complication[@type='RANGED_VALUE']")?.let { content ->
            removeMarkers(content)
            content.rewriteAttributes { it.replace(expressions.getValue("progressMarker"), "(\"FALSE\" == \"TRUE\")") }
            content.rewriteText { it.replace(expressions.getValue("progressMarker"), "(\"FALSE\" == \"TRUE\")") }
        }
    }
    configs.children().filter { it.attr("id") in expressions }.forEach { it.remove() }
    val config = configs.one("ListConfiguration[@id='progressBars']") ?: configs.add("ListConfiguration")
    config.clear()
    config.set("id" to "progressBars", "displayName" to "progress_bars", "defaultValue" to "7")
    label(strings, "progress_bars", "Progress Bars")
    listOf("Einfarbig", "Farbverlauf", "Segmente", "Segmente · Verlauf", "Einfarbig · Dot", "Farbverlauf · Dot", "Segmente · Dot", "Segmente · Verlauf · Dot").forEachIndexed { index, title ->
        label(strings, "progress_bars_$index", title)
        config.add("ListOption", "id" to index, "displayName" to "progress_bars_$index")
    }
    configs.all("Flavors/Flavor/Configuration").filter { it.attr("id") in expressions }.forEach { it.remove() }
    val order = listOf("indexStyle", "handStyle", "secondStyle", "materialAccent", "materialSubTheme", "backgroundPattern", "progressBars", "glucoseColor")
    configs.children().filter { it.attr("id") in order }.sortedBy { order.indexOf(it.attr("id")) }.forEach { item ->
        item.remove()
        configs.insertBefore(item, configs.children().getOrNull(order.indexOf(item.attr("id"))))
    }
}

private fun choices(vararg values: Int) = "(" + values.joinToString(" || ") { "[CONFIGURATION.progressBars] == \"$it\"" } + ")"
