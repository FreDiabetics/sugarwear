package app.sugarlicious.tools.cwf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipFile

class WatchfacePackageParser(
    private val maxEntryBytes: Int = 2 * 1024 * 1024,
    private val maxArchiveBytes: Int = 10 * 1024 * 1024,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    fun parse(zipPath: Path): CwfDocument {
        if (!zipPath.toFile().isFile) throw CwfValidationException("CWF ZIP not found: $zipPath")
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().toList()
            if (entries.isEmpty()) throw CwfValidationException("CWF ZIP is empty")
            validateNames(entries.map { it.name })
            val files = entries.filterNot { it.isDirectory }
            val jsonEntry =
                files.singleOrNull { it.name.equals("CustomWatchface.json", ignoreCase = true) }
                    ?: throw CwfValidationException("Exactly one CustomWatchface.json is required")
            val previews = files.filter { it.name.substringAfterLast('/').lowercase() in PREVIEW_NAMES }
            if (previews.size != 1) throw CwfValidationException("Exactly one CustomWatchface.png, .jpg or .svg preview is required")

            var totalRead = 0

            fun read(entryName: String): ByteArray {
                val entry = files.single { it.name == entryName }
                if (entry.size > maxEntryBytes) throw CwfValidationException("ZIP entry exceeds size limit: ${entry.name}")
                val bytes = zip.getInputStream(entry).use { it.readLimited(maxEntryBytes) }
                totalRead += bytes.size
                if (totalRead > maxArchiveBytes) throw CwfValidationException("CWF ZIP exceeds expanded size limit")
                return bytes
            }

            val root =
                runCatching { json.parseToJsonElement(read(jsonEntry.name).decodeToString()).jsonObject }
                    .getOrElse { throw CwfValidationException("Invalid CustomWatchface.json: ${it.message}") }
            val preview = previews.single()
            read(preview.name)
            files.filter { it != jsonEntry && it != preview }.forEach { read(it.name) }
            return normalize(root, preview.name, files.map { it.name }.sorted())
        }
    }

    fun normalize(
        root: JsonObject,
        previewEntry: String = "CustomWatchface.png",
        resources: List<String> = listOf("CustomWatchface.json", previewEntry),
    ): CwfDocument {
        val metadataObject =
            root["metadata"] as? JsonObject
                ?: throw CwfValidationException("metadata object is required")
        val metadata =
            CwfMetadata(
                name = metadataObject.requiredString("name"),
                author = metadataObject.requiredString("author"),
                createdAt = metadataObject.string("created_at"),
                authorVersion = metadataObject.string("author_version"),
                cwfVersion = metadataObject.string("cwf_version"),
                comment = metadataObject.string("comment"),
            )
        val background =
            root["background"] as? JsonObject
                ?: throw CwfValidationException("background object is required")
        val canvasWidth = background.requiredInt("width", 1..1000)
        val canvasHeight = background.requiredInt("height", 1..1000)
        val warnings = mutableListOf<String>()
        val features = linkedSetOf<String>()
        val elements = linkedMapOf<String, CwfElement>()
        root.forEach { (key, value) ->
            val objectValue = value as? JsonObject ?: return@forEach
            if (!objectValue.containsKey("width") || !objectValue.containsKey("height")) return@forEach
            val element =
                CwfElement(
                    key = key,
                    width = objectValue.requiredInt("width", 0..2000),
                    height = objectValue.requiredInt("height", 0..2000),
                    top = objectValue.int("topmargin") ?: 0,
                    left = objectValue.int("leftmargin") ?: 0,
                    visibility = objectValue.string("visibility") ?: "visible",
                    textSize = objectValue.int("textsize"),
                    gravity = objectValue.string("gravity"),
                    font = objectValue.string("font"),
                    fontStyle = objectValue.string("fontStyle"),
                    fontColor = objectValue.string("fontColor"),
                    rotation = objectValue["rotation"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    raw = objectValue,
                )
            if (element.visibility.lowercase() !in setOf("visible", "gone", "none")) {
                throw CwfValidationException("$key has unsupported visibility '${element.visibility}'")
            }
            if (element.left !in -2000..4000 || element.top !in -2000..4000) {
                throw CwfValidationException("$key position is outside accepted bounds")
            }
            if ("dynPref" in objectValue) features += "dynamic_preferences"
            if ("dynData" in objectValue) features += "dynamic_data"
            if ("twinView" in objectValue) features += "twin_views"
            elements[key] = element
        }
        if ("dynPref" in root) features += "dynamic_preferences"
        if ("dynData" in root) features += "dynamic_data"
        if (elements["chart"]?.visible == true) features += "chart"
        if (listOf("hour_hand", "minute_hand", "second_hand").any { elements[it]?.visible == true }) features += "analog_hands"
        val external =
            resources.filterNot {
                it.equals("CustomWatchface.json", true) ||
                    it.substringAfterLast('/').lowercase() in PREVIEW_NAMES
            }
        if (external.isNotEmpty()) features += "external_resources"
        features.filterNot { it == "chart" }.sorted().forEach { warnings += "CWF feature requires explicit WFF mapping: $it" }
        return CwfDocument(metadata, canvasWidth, canvasHeight, previewEntry, resources, elements, features, warnings)
    }

    private fun validateNames(names: List<String>) {
        val seen = hashSetOf<String>()
        names.forEach { original ->
            val name = original.replace('\\', '/')
            if (name.startsWith('/') || name.matches(Regex("^[A-Za-z]:.*")) || name.split('/').any { it == ".." }) {
                throw CwfValidationException("Unsafe ZIP entry path: $original")
            }
            if (!seen.add(name.lowercase())) throw CwfValidationException("Duplicate ZIP entry: $original")
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw CwfValidationException("ZIP entry exceeds size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun JsonObject.requiredString(key: String): String =
        string(key)?.takeIf { it.isNotBlank() }
            ?: throw CwfValidationException("metadata.$key must be a non-empty string")

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.requiredInt(
        key: String,
        range: IntRange,
    ): Int =
        int(key)?.takeIf { it in range }
            ?: throw CwfValidationException("$key must be an integer in ${range.first}..${range.last}")

    companion object {
        private val PREVIEW_NAMES = setOf("customwatchface.png", "customwatchface.jpg", "customwatchface.svg")
    }
}
