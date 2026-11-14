package app.sugarlicious.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal data class SettingsRestoreResult(
    val preferenceFileCount: Int,
    val valueCount: Int,
)

/**
 * Portable backup of user-controlled settings only.
 *
 * Runtime state, diagnostics, Health Connect data and G7 credentials deliberately remain local
 * and are never included in the document.
 */
internal object SettingsBackup {
    private const val FORMAT = "sugarlicious-settings"
    private const val VERSION = 1
    private const val MAX_BACKUP_BYTES = 1_048_576
    private const val MAX_VALUES = 5_000
    private const val MAX_KEY_LENGTH = 256
    private const val MAX_STRING_LENGTH = 65_536

    private val preferenceFiles =
        listOf(
            "dashboard_ui",
            "complication_setup",
            "sugarlicious_watchface_presets",
        )

    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    fun write(
        context: Context,
        output: OutputStream,
        exportedAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        val preferences =
            buildJsonObject {
                preferenceFiles.forEach { name ->
                    put(name, encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
                }
            }
        val document =
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("exportedAtEpochMs", exportedAtEpochMs)
                put("preferences", preferences)
            }
        output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(JsonElement.serializer(), document))
        }
    }

    fun restore(
        context: Context,
        input: InputStream,
    ): SettingsRestoreResult {
        val document = Json.parseToJsonElement(readBoundedUtf8(input)).jsonObject
        require(document["format"]?.jsonPrimitive?.contentOrNull == FORMAT) {
            "Keine Sugarlicious-Einstellungsdatei"
        }
        require(document["version"]?.jsonPrimitive?.int == VERSION) {
            "Nicht unterstützte Sicherungsversion"
        }

        val encodedFiles =
            document["preferences"]?.jsonObject
                ?: throw IllegalArgumentException("Einstellungen fehlen")
        require(encodedFiles.keys == preferenceFiles.toSet()) {
            "Die Sicherung ist unvollständig oder enthält unbekannte Bereiche"
        }

        var valueCount = 0
        val decoded =
            preferenceFiles.associateWith { name ->
                decodePreferences(encodedFiles.getValue(name).jsonObject).also { values ->
                    valueCount += values.size
                    require(valueCount <= MAX_VALUES) { "Die Sicherung enthält zu viele Einstellungen" }
                }
            }
        val snapshots =
            preferenceFiles.associateWith { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all.copyPreferenceValues()
            }

        try {
            decoded.forEach { (name, values) ->
                val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                require(replace(preferences, values)) { "Einstellungen konnten nicht gespeichert werden" }
            }
        } catch (error: Throwable) {
            snapshots.forEach { (name, values) ->
                replace(context.getSharedPreferences(name, Context.MODE_PRIVATE), values)
            }
            throw error
        }

        return SettingsRestoreResult(
            preferenceFileCount = preferenceFiles.size,
            valueCount = valueCount,
        )
    }

    private fun encodePreferences(preferences: SharedPreferences): JsonObject =
        buildJsonObject {
            preferences.all.toSortedMap().forEach { (key, value) ->
                put(key, encodeValue(value))
            }
        }

    private fun encodeValue(value: Any?): JsonObject =
        buildJsonObject {
            when (value) {
                is Boolean -> {
                    put("type", "boolean")
                    put("value", value)
                }

                is Int -> {
                    put("type", "int")
                    put("value", value)
                }

                is Long -> {
                    put("type", "long")
                    put("value", value)
                }

                is Float -> {
                    require(value.isFinite()) { "Ungültiger Zahlenwert" }
                    put("type", "float")
                    put("value", value)
                }

                is String -> {
                    require(value.length <= MAX_STRING_LENGTH) { "Einstellungswert ist zu lang" }
                    put("type", "string")
                    put("value", value)
                }

                is Set<*> -> {
                    val strings =
                        value
                            .map {
                                require(it is String) { "Nicht unterstützter Einstellungswert" }
                                require(it.length <= MAX_STRING_LENGTH) { "Einstellungswert ist zu lang" }
                                it
                            }.sorted()
                    put("type", "stringSet")
                    put("value", JsonArray(strings.map(::JsonPrimitive)))
                }

                else -> throw IllegalArgumentException("Nicht unterstützter Einstellungswert")
            }
        }

    private fun decodePreferences(encoded: JsonObject): Map<String, Any> =
        buildMap {
            encoded.forEach { (key, value) ->
                require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) { "Ungültiger Einstellungsschlüssel" }
                put(key, decodeValue(value.jsonObject))
            }
        }

    private fun decodeValue(encoded: JsonObject): Any {
        val type =
            encoded["type"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Einstellungstyp fehlt")
        val value = encoded["value"] ?: throw IllegalArgumentException("Einstellungswert fehlt")
        return when (type) {
            "boolean" -> value.jsonPrimitive.boolean
            "int" -> value.jsonPrimitive.int
            "long" -> value.jsonPrimitive.long
            "float" ->
                value.jsonPrimitive.float.also {
                    require(it.isFinite()) { "Ungültiger Zahlenwert" }
                }

            "string" ->
                value.jsonPrimitive.content.also {
                    require(it.length <= MAX_STRING_LENGTH) { "Einstellungswert ist zu lang" }
                }

            "stringSet" ->
                value.jsonArray.mapTo(linkedSetOf()) {
                    it.jsonPrimitive.content.also { item ->
                        require(item.length <= MAX_STRING_LENGTH) { "Einstellungswert ist zu lang" }
                    }
                }

            else -> throw IllegalArgumentException("Unbekannter Einstellungstyp")
        }
    }

    @SuppressLint("UseKtx") // The restore API must return the synchronous commit result to its caller.
    private fun replace(
        preferences: SharedPreferences,
        values: Map<String, Any>,
    ): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> ->
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                else -> throw IllegalArgumentException("Nicht unterstützter Einstellungswert")
            }
        }
        return editor.commit()
    }

    private fun Map<String, *>.copyPreferenceValues(): Map<String, Any> =
        entries.associate { (key, value) ->
            key to
                when (value) {
                    is Set<*> -> value.filterIsInstance<String>().toSet()
                    null -> throw IllegalArgumentException("Leerer Einstellungswert")
                    else -> value
                }
        }

    private fun readBoundedUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "Die Sicherungsdatei ist zu groß" }
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }
}
