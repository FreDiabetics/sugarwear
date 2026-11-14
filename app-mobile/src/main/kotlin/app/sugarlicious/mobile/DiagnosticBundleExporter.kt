package app.sugarlicious.mobile

import android.content.Context
import android.os.Build
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.SettingsSchemaVersions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object DiagnosticBundleExporter {
    private val json =
        Json {
            prettyPrint = true
            explicitNulls = false
        }
    private val sensitiveParts = setOf("secret", "token", "password", "sharedkey", "gkey", "auth", "credential", "pin", "url")
    private val preferenceFiles = listOf("dashboard_ui", "diagnostics", "mobile_canonical_cgm_resolver")

    fun create(
        context: Context,
        events: List<DiagnosticEvent>,
    ): File {
        val outputDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        outputDir.listFiles()?.filter { it.name.startsWith("sugarlicious-diagnostics-") }?.forEach { it.delete() }
        val output = File(outputDir, "sugarlicious-diagnostics-${System.currentTimeMillis()}.zip")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.textEntry(
                "app_versions.json",
                json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            "package" to JsonPrimitive(context.packageName),
                            "versionName" to JsonPrimitive(packageInfo.versionName ?: "unknown"),
                            "versionCode" to JsonPrimitive(packageVersionCode(packageInfo)),
                        ),
                    ),
                ),
            )
            zip.textEntry(
                "device_info.json",
                json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            "manufacturer" to JsonPrimitive(Build.MANUFACTURER),
                            "model" to JsonPrimitive(Build.MODEL),
                            "sdk" to JsonPrimitive(Build.VERSION.SDK_INT),
                            "release" to JsonPrimitive(Build.VERSION.RELEASE),
                        ),
                    ),
                ),
            )
            zip.textEntry("schema_versions.json", schemaVersionsJson())
            zip.textEntry("events.jsonl", events.toJsonLines())
            zip.textEntry("resolver_events.jsonl", events.filterModules("SOURCE", "RESOLVER", "CANONICAL_CGM").toJsonLines())
            zip.textEntry("collector_events.jsonl", events.filterModules("G7", "COLLECTOR", "BLE").toJsonLines())
            zip.textEntry("alarm_events.jsonl", events.filterModules("ALARM").toJsonLines())
            zip.textEntry("settings_redacted.json", redactedSettings(context))
        }
        return output
    }

    internal fun isSensitive(key: String): Boolean = sensitiveParts.any { key.contains(it, ignoreCase = true) }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: android.content.pm.PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

    private fun schemaVersionsJson(): String =
        json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "appearance" to JsonPrimitive(SettingsSchemaVersions.APPEARANCE),
                    "widget" to JsonPrimitive(SettingsSchemaVersions.WIDGET),
                    "wear" to JsonPrimitive(SettingsSchemaVersions.WEAR),
                    "collector" to JsonPrimitive(SettingsSchemaVersions.COLLECTOR),
                ),
            ),
        )

    private fun redactedSettings(context: Context): String {
        val files =
            preferenceFiles.associateWith { name ->
                val safe =
                    context
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .all
                        .filterKeys { !isSensitive(it) }
                        .mapValues { (_, value) -> JsonPrimitive(value.toString().take(200)) }
                JsonObject(safe)
            }
        return json.encodeToString(JsonObject.serializer(), JsonObject(files))
    }

    private fun List<DiagnosticEvent>.filterModules(vararg terms: String): List<DiagnosticEvent> = filter { event -> terms.any { term -> event.module.contains(term, true) || event.code.contains(term, true) } }

    private fun List<DiagnosticEvent>.toJsonLines(): String = sortedBy(DiagnosticEvent::occurredAtEpochMs).joinToString("\n") { json.encodeToString(it) }

    private fun ZipOutputStream.textEntry(
        name: String,
        content: String,
    ) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
