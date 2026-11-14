package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element

internal fun restoreNativeHands(root: Element, family: String) {
    val scene = root.need("Scene")
    val identity = if (family == "orbit") "8" else "7"
    val slot = scene.one("ComplicationSlot[@slotId='$identity']") ?: return
    slot.one("Complication[@type='LONG_TEXT']")?.let { content ->
        content.one("Group[@name='center-tap-native-hands']")?.let { wrapper ->
            wrapper.children().filter { it.tagName == "Group" }.forEach(scene::appendChild)
            wrapper.remove()
        }
        content.set("type" to "SMALL_IMAGE")
    }
    slot.set("isCustomizable" to "FALSE", "supportedTypes" to "SMALL_IMAGE EMPTY")
    slot.need("DefaultProviderPolicy").set("primaryProviderType" to "SMALL_IMAGE")
    slot.need("BoundingBox").set("x" to "91", "y" to "91", "width" to "330", "height" to if (family == "apex") "97" else "165")
    val policies = root.all("UserConfigurations/Flavors/Flavor/ComplicationSlot[@slotId='$identity']/DefaultProviderPolicy")
    policies.forEach { it.set("primaryProviderType" to "SMALL_IMAGE") }
    scene.all("Group").filter { it.attr("name") in listOf("hands", "hands-and-indicators") }.forEach(scene::appendChild)
    if (family == "apex") {
        slot.remove()
        scene.insertBeforeOrAppend(slot, scene.one("ComplicationSlot"))
    }
    val content = slot.need("Complication[@type='SMALL_IMAGE']").set("type" to "LONG_TEXT")
    content.all(".//Image").filter { it.attr("resource") == "[COMPLICATION.SMALL_IMAGE]" }.forEach { it.set("resource" to "[COMPLICATION.MONOCHROMATIC_IMAGE]") }
    slot.set("supportedTypes" to "LONG_TEXT EMPTY")
    val provider = "app.sugarlicious/app.sugarlicious.complications.Native${if (family == "orbit") "Orbit" else "Apex"}GraphComplication"
    (policies + slot.need("DefaultProviderPolicy")).forEach { it.set("primaryProviderType" to "LONG_TEXT", "primaryProvider" to provider) }
    if (content.one("Group[@name='native-hands-opacity-state']") == null) {
        val state = content.add("Group", "name" to "native-hands-opacity-state", "x" to "91", "y" to "91", "width" to "1", "height" to "1")
        state.add("Transform", "target" to "alpha", "value" to "[COMPLICATION.TEXT] == \"dim\" ? 77 : 255")
        state.add("Reference", "name" to "nativeHandsOpacity", "source" to "alpha", "defaultValue" to "255")
        state.add("PartDraw", "x" to "0", "y" to "0", "width" to "1", "height" to "1").add("Rectangle", "x" to "0", "y" to "0", "width" to "1", "height" to "1").add("Fill", "color" to "#01000000")
    }
    scene.all("Group").filter { it.attr("name") in listOf("hands", "hands-and-indicators") }.forEach { hands ->
        val transform = hands.one("Transform[@target='alpha']") ?: hands.add("Transform", "target" to "alpha").also { hands.insertBefore(it, hands.firstChild) }
        transform.set("value" to "[REFERENCE.nativeHandsOpacity]")
    }
    val hotspot =
        scene.one("PartDraw[@name='native-hands-center-tap']") ?: scene.add("PartDraw", "name" to "native-hands-center-tap", "x" to "230", "y" to "230", "width" to "52", "height" to "52").apply {
            add("Launch", "target" to "app.sugarlicious/app.sugarlicious.complications.${if (family == "orbit") "Orbit" else "Apex"}HandsOpacityActivity")
            add("Ellipse", "x" to "0", "y" to "0", "width" to "52", "height" to "52").add("Fill", "color" to "#01000000")
            add("Variant", "mode" to "AMBIENT", "target" to "alpha", "value" to "0")
        }
    scene.appendChild(hotspot)
}
