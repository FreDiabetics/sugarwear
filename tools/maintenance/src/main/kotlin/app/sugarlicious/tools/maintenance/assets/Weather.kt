package app.sugarlicious.tools.maintenance.assets

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.imageio.ImageIO

internal fun weatherTool(args: List<String>, root: Path) {
    val opts = options(args, setOf("--samsung-apk", "--google-reference-apk", "--samsung-resource-table"))

    fun required(key: String) = opts[key] ?: error("Missing $key")
    val resources = Files.readString(Path.of(required("--samsung-resource-table"))).removePrefix("\uFEFF")
    val output = root.resolve("watchfaces/sugarlicious-orbit/src/main/res/drawable-nodpi")
    val assets = linkedMapOf<String, JsonElement>()
    val packs = linkedMapOf<String, JsonElement>()
    ZipFile(required("--samsung-apk")).use { deviceVendor ->
        ZipFile(required("--google-reference-apk")).use { google ->
            fun copy(pack: String, name: String, zip: ZipFile, entry: String): String {
                val resourcePack = if (pack == "samsung") "device_vendor" else pack
                val resource = "orbit_weather_${resourcePack}_$name"
                val data = zip.getInputStream(zip.getEntry(entry) ?: error("Missing archive entry $entry")).use { it.readBytes() }
                val filename = resource + entry.substringAfterLast('/').substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
                val image = ImageIO.read(data.inputStream()) ?: error("Unsupported image $entry")
                var left = image.width
                var top = image.height
                var right = 0
                var bottom = 0
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        if ((image.getRGB(x, y) ushr 24) != 0) {
                            left = minOf(left, x)
                            top = minOf(top, y)
                            right = maxOf(right, x + 1)
                            bottom = maxOf(bottom, y + 1)
                        }
                    }
                }
                if (right == 0) {
                    left = 0
                    top = 0
                    right = image.width
                    bottom = image.height
                }
                Files.write(output.resolve(filename), data)
                assets[resource] =
                    buildJsonObject {
                        put("file", filename)
                        put("source", entry)
                        put("sha256", MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
                        put("canvas", JsonArray(listOf(image.width, image.height).map(::JsonPrimitive)))
                        put("bounds", JsonArray(listOf(left, top, right, bottom).map(::JsonPrimitive)))
                    }
                return resource
            }
            val names = listOf("sunny", "cloudy", "fog", "heavy_rain", "heavy_snow", "rain", "snow", "sunny", "thunderstorm", "rain_and_snow", "light_snow", "shower", "fog", "partly_cloud", "wind")
            for (period in listOf("day", "night")) {
                packs["samsung_$period"] =
                    JsonObject(
                        names
                            .mapIndexed { index, original ->
                                val name = if (period == "night") mapOf("sunny" to "clear", "partly_cloud" to "partly_cloud_night")[original] ?: original else original
                                val entry = Regex("drawable/" + Regex.escape(name) + "\\s+\\(\\) \\(file\\) (\\S+)").find(resources)?.groupValues?.get(1) ?: error("Missing original Samsung resource: $name")
                                (index + 1).toString() to JsonPrimitive(copy("samsung", name, deviceVendor, entry))
                            }.toMap(),
                    )
            }
            val entries =
                google
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            val reference = parseXml(google.getInputStream(google.getEntry(entries.first { it.endsWith("/watchface.xml") })).use { it.readBytes().toString(Charsets.UTF_8) }).documentElement
            for (period in listOf("day", "night")) {
                val font = reference.child("BitmapFonts").children().first { it.tagName == "BitmapFont" && it.getAttribute("name") == "weather_icons_pack_1_$period" }
                val mapping = linkedMapOf<String, JsonElement>()
                font.children().forEach { item ->
                    val condition = item.getAttribute("name").toInt()
                    if (condition in 1..15) {
                        val name = item.getAttribute("resource")
                        mapping[condition.toString()] = JsonPrimitive(copy("google", name, google, entries.first { it.endsWith("/$name.png") }))
                    }
                }
                require(mapping.size == 15) { "Incomplete reference Google Weather mapping" }
                packs["google_$period"] = JsonObject(mapping)
            }
        }
    }
    val target = root.resolve("watchfaces/sugarlicious-orbit/reference/weather/manifest.json")
    Files.createDirectories(target.parent)
    Files.writeString(
        target,
        Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("packs", JsonObject(packs))
                put("assets", JsonObject(assets))
            },
        ) + "\n",
    )
}
