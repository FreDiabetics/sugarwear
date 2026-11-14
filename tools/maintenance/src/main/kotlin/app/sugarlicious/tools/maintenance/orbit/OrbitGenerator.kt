package app.sugarlicious.tools.maintenance.orbit

import app.sugarlicious.tools.maintenance.watchface.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.cos
import kotlin.math.sin

data class MarkerGeometry(
    val x: Double,
    val y: Double,
    val diameter: Double,
    val contourThickness: Double,
)

fun markerGeometry(cx: Double, cy: Double, radius: Double, start: Double, end: Double, value: Double, minimum: Double, maximum: Double, thickness: Double): MarkerGeometry? {
    if (!value.isFinite() || !minimum.isFinite() || !maximum.isFinite() || maximum <= minimum) return null
    val fraction = ((value - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)
    val angle = Math.toRadians(start + fraction * (end - start))
    return MarkerGeometry(cx + radius * sin(angle), cy - radius * cos(angle), thickness, thickness * .3)
}

/** The measured geometry is declarative WFF, not a second executable generator. */
private fun model(name: String): Element =
    OrbitGeneratorResource::class.java.getResourceAsStream("/orbit/$name").use { input ->
        requireNotNull(input) { "Missing Orbit geometry model $name" }
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder()
            .parse(input)
            .documentElement
    }

private object OrbitGeneratorResource

fun generateOrbit(output: Path, repositoryRoot: Path = watchfaceRepositoryRoot()) {
    val root = model("geometry.xml")
    val strings = model("labels.xml")
    val palettes = loadPalettes(repositoryRoot)
    for (identity in listOf("primary", "secondary", "tertiary")) {
        val config = root.need("UserConfigurations/ColorConfiguration[@id='$identity']")
        config.children().forEach { it.remove() }
        for (entry in palettes) {
            val id = entry.getValue("id")
            label(strings, "${identity}_$id", entry.getValue("name"))
            config.add("ColorOption", "id" to id, "displayName" to "${identity}_$id", "colors" to listOf("light", "medium", "vivid").joinToString(" ") { "#FF" + entry.getValue(it) })
        }
    }
    val manifestPath = repositoryRoot.resolve("watchfaces/sugarlicious-orbit/reference/weather/manifest.json")
    val weather = Json.parseToJsonElement(Files.readString(manifestPath)).jsonObject
    configureWeather(root, weather)
    replaceUserFlavors(root, strings, "orbit", repositoryRoot)
    configureIndicatorGradient(root, "orbit")
    configureSegmentedProgress(root, "orbit")
    restoreNativeHands(root, "orbit")
    writeXml(root, output.resolve("raw/watchface.xml"))
    writeXml(strings, output.resolve("values/strings.xml"))
    configureTrendIconGeometry(output.resolve("raw/watchface.xml"))
}

private fun configureWeather(root: Element, manifest: JsonObject) {
    val packs = manifest.getValue("packs").jsonObject
    val assets = manifest.getValue("assets").jsonObject
    val launch = root.need("UserConfigurations/ListConfiguration[@id='weatherLaunch']")
    launch.children().forEachIndexed { index, option ->
        val vendor = listOf("samsung", "google")[index]
        option.set(
            "icon" to
                packs
                    .getValue("${vendor}_day")
                    .jsonObject
                    .getValue("8")
                    .jsonPrimitive.content,
        )
    }
    for (offset in 0..4) {
        val token = if (offset == 0) "WEATHER" else "WEATHER.HOURS.$offset"
        val art = root.need(".//Group[@name='forecast-$offset']//Group[@name='weather-art-0']")
        art.children().forEach { it.remove() }
        for (period in listOf("day", "night")) {
            val day = if (period == "day") "[$token.IS_DAY]" else "![$token.IS_DAY]"
            for ((condition, value) in packs.getValue("samsung_$period").jsonObject) {
                val resource = value.jsonPrimitive.content
                val owner = art.add("Group", "name" to "weather-0-$period-$condition-256", "x" to 0, "y" to 0, "width" to 512, "height" to 512)
                owner.add("Transform", "target" to "alpha", "value" to "($day) && [$token.CONDITION] == $condition ? 255 : 0")
                val canvas =
                    assets
                        .getValue(resource)
                        .jsonObject
                        .getValue("canvas")
                        .jsonArray
                        .map { it.jsonPrimitive.content.toDouble() }
                require(canvas.size == 2 && canvas.all { it > 0 && it.isFinite() }) { "Invalid weather canvas $resource" }
                val width = canvas[0]
                val height = canvas[1]
                val scale = (36.0 * 512 / 450) / maxOf(width, height)
                owner
                    .add("PartImage", "x" to pyRound(256 - width / 2 * scale), "y" to pyRound(140.0 * 512 / 450 - height / 2 * scale), "width" to pyRound(width * scale), "height" to pyRound(height * scale))
                    .add("Image", "resource" to resource)
            }
        }
    }
}

fun runOrbitTool(command: String, args: List<String>): Int? {
    if (command != "generate-orbit") return null
    require(args.size == 2 && args[0] == "--output") { "generate-orbit requires --output DIRECTORY" }
    generateOrbit(Path.of(args[1]))
    return 0
}
