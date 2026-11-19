package app.sugarlicious.tools.maintenance.watchface

import kotlinx.serialization.json.*
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path

internal fun watchfaceRepositoryRoot(): Path {
    System.getProperty("maintenance.sharedRoot")?.let { return Path.of(it) }
    var path = Path.of("").toAbsolutePath()
    while (!Files.exists(path.resolve("tools/watchface-assets/orbit_palettes.csv"))) path = path.parent ?: error("Cannot locate watchface assets; set maintenance.sharedRoot")
    return path
}

internal fun replaceUserFlavors(root: Element, strings: Element, face: String, repositoryRoot: Path = watchfaceRepositoryRoot()) {
    val configs = root.need("UserConfigurations")
    val captured = Json.parseToJsonElement(Files.readString(repositoryRoot.resolve("tools/watchface-assets/captured_native_defaults.json"))).jsonObject[face]?.jsonObject
    captured?.get("configurations")?.jsonObject?.forEach { (identity, value) -> configs.need("*[@id='$identity']").set("defaultValue" to value.jsonPrimitive.content) }
    captured?.get("providers")?.jsonObject?.forEach { (identity, attributes) ->
        root.need("Scene/ComplicationSlot[@slotId='$identity']/DefaultProviderPolicy").apply {
            clear()
            attributes.jsonObject.forEach { (key, value) -> set(key to value.jsonPrimitive.content) }
        }
    }
    val flavors = configs.one("Flavors") ?: configs.add("Flavors")
    val oldLabels = flavors.children().map { it.attr("displayName") }.toSet()
    flavors.clear()
    flavors.set("defaultValue" to "profile_standard")
    strings.children().filter { it.attr("name") in oldLabels }.forEach { it.remove() }
    val profiles =
        listOf(
            listOf("standard", "Standard", null, null, null, null),
            listOf("ultra", "Ultra Orange", "70", "37", "3", "5"),
            listOf("military", "Military", "14", "61", "3", "4"),
            listOf("polar", "Polar", "999", "999", "4", "0"),
            listOf("aurora", "Aurora", "64", "43", "4", "3"),
            listOf("solar", "Solarsturm", "65", "42", "3", "7"),
        )
    profiles.forEach { profile ->
        val identity = profile[0]
        val name = profile[1]
        val orbitColor = profile[2]
        val apexColor = profile[3]
        val arc = profile[4]
        val bars = profile[5]
        label(strings, "native_profile_$identity", name!!)
        val flavor = flavors.add("Flavor", "id" to "profile_$identity", "displayName" to "native_profile_$identity")
        val values = configs.children().filter { it.tagName != "Flavors" && it.hasAttribute("defaultValue") }.associateTo(linkedMapOf()) { it.attr("id") to it.attr("defaultValue") }
        if (face == "orbit" && orbitColor != null) {
            values.putAll(mapOf("primary" to orbitColor, "secondary" to orbitColor, "tertiary" to orbitColor, "arcContent" to arc!!, "progressBars" to bars!!))
        } else if (face == "apex" && apexColor != null) {
            values.putAll(mapOf("materialAccent" to apexColor, "materialSubTheme" to apexColor, "progressBars" to bars!!))
        }
        values.forEach { (key, value) -> flavor.add("Configuration", "id" to key, "optionId" to value) }
        root.all("Scene/ComplicationSlot[@isCustomizable='TRUE']").forEach { actual -> actual.one("DefaultProviderPolicy")?.let { flavor.add("ComplicationSlot", "slotId" to actual.attr("slotId")).appendChild(it.copy()) } }
    }
}
