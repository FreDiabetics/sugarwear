package app.sugarlicious.tools.maintenance

import app.sugarlicious.tools.maintenance.assets.runAssetTool
import app.sugarlicious.tools.maintenance.migration.runMigrationTool
import app.sugarlicious.tools.maintenance.orbit.runOrbitTool
import app.sugarlicious.tools.maintenance.watchface.runWatchfaceTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

internal val commands =
    listOf(
        "configure-apex-progress",
        "configure-indicator-gradient",
        "configure-trend-icon-geometry",
        "curated-palettes",
        "generate-orbit",
        "prepare-slot-capture",
        "restore-native-hands",
        "segmented-progress",
        "unify-apex-progress-settings",
        "user-flavors",
        "import-app-brand-icons",
        "import-adaptive-brand-icons",
        "refresh-brand-presentations",
        "import-orbit-handset",
        "configure-orbit-seconds-motion",
        "import-orbit-weather",
        "render-orbit-hands",
        "configure-vigil-decoration",
        "import-vigil-markers",
        "migrate-products",
        "verify-codefree-vigil",
    )

private data class CommandOptions(
    val allowed: Set<String>,
    val required: Set<String> = emptySet(),
    val choices: Map<String, Set<String>> = emptyMap(),
)

private val faces = setOf("apex", "orbit")
private val products = setOf("sugarlicious", "sugarwear")
private val commandOptions =
    mapOf(
        "configure-apex-progress" to CommandOptions(setOf("--resources"), setOf("--resources")),
        "curated-palettes" to CommandOptions(setOf("--resources"), setOf("--resources")),
        "unify-apex-progress-settings" to CommandOptions(setOf("--resources"), setOf("--resources")),
        "configure-indicator-gradient" to CommandOptions(setOf("--resources", "--face"), setOf("--resources", "--face"), mapOf("--face" to faces)),
        "restore-native-hands" to CommandOptions(setOf("--resources", "--face"), setOf("--resources", "--face"), mapOf("--face" to faces)),
        "segmented-progress" to CommandOptions(setOf("--resources", "--face"), setOf("--resources", "--face"), mapOf("--face" to faces)),
        "user-flavors" to CommandOptions(setOf("--resources", "--face"), setOf("--resources", "--face"), mapOf("--face" to faces)),
        "configure-trend-icon-geometry" to CommandOptions(setOf("--path")),
        "generate-orbit" to CommandOptions(setOf("--output"), setOf("--output")),
        "prepare-slot-capture" to CommandOptions(setOf("--source", "--destination", "--name"), setOf("--source", "--destination", "--name"), mapOf("--name" to faces)),
        "import-app-brand-icons" to CommandOptions(setOf("--source", "--product", "--repository-root", "--preview-dir"), setOf("--source"), mapOf("--product" to products)),
        "import-adaptive-brand-icons" to CommandOptions(setOf("--source-dir", "--product", "--repository-root", "--preview-dir"), choices = mapOf("--product" to products)),
        "refresh-brand-presentations" to CommandOptions(setOf("--product", "--repository-root"), choices = mapOf("--product" to products)),
        "import-orbit-handset" to CommandOptions(setOf("--source", "--resources")),
        "configure-orbit-seconds-motion" to CommandOptions(emptySet()),
        "import-orbit-weather" to CommandOptions(setOf("--samsung-apk", "--google-reference-apk", "--samsung-resource-table"), setOf("--samsung-apk", "--google-reference-apk", "--samsung-resource-table")),
        "render-orbit-hands" to CommandOptions(setOf("--output")),
        "configure-vigil-decoration" to CommandOptions(emptySet()),
        "import-vigil-markers" to CommandOptions(setOf("--source")),
        "migrate-products" to CommandOptions(setOf("--phone", "--watch", "--product", "--legacy-dir", "--sugarwear-root", "--adb"), setOf("--watch", "--product", "--legacy-dir"), mapOf("--product" to products + "both")),
        "verify-codefree-vigil" to CommandOptions(emptySet()),
    )

/** Match argparse's value options: either spelling, unique prefixes, and last value wins. */
internal fun normalizeCommandArguments(command: String, arguments: List<String>): List<String> {
    val spec = commandOptions.getValue(command)
    // The Kotlin verifier also accepts an explicit checkout path; the Python default stays intact.
    if (command == "verify-codefree-vigil" && arguments.size == 1 && !arguments[0].startsWith('-')) return arguments
    val values = linkedMapOf<String, String>()
    var index = 0
    while (index < arguments.size) {
        val token = arguments[index++]
        val spelling = token.substringBefore('=')
        val matching = if (spelling in spec.allowed) listOf(spelling) else spec.allowed.filter { spelling.startsWith("--") && it.startsWith(spelling) }
        require(matching.size == 1) { if (matching.isEmpty()) "Unrecognized argument $spelling" else "Ambiguous option $spelling: ${matching.joinToString()}" }
        val option = matching.single()
        val value =
            if ('=' in token) {
                token.substringAfter('=')
            } else {
                require(index < arguments.size && !arguments[index].startsWith("--") && arguments[index] !in listOf("-h", "--help")) { "Expected a value for $option" }
                arguments[index++]
            }
        spec.choices[option]?.let { allowed -> require(value in allowed) { "$option must be one of ${allowed.joinToString()}" } }
        values[option] = value
    }
    require(values.keys.containsAll(spec.required)) { "Missing required options: ${(spec.required - values.keys).joinToString()}" }
    return values.flatMap { (key, value) -> listOf(key, value) }
}

fun runMaintenance(arguments: List<String>): Int {
    val actual =
        if (arguments.size == 2 && arguments[0] == "--arguments-file") {
            Json.parseToJsonElement(Files.readString(Path.of(arguments[1]))).jsonArray.map { it.jsonPrimitive.content }
        } else {
            arguments
        }
    if (actual.isEmpty() || actual == listOf("--help") || actual == listOf("-h")) {
        println("Sugarlicious maintenance tools\n" + commands.joinToString("\n"))
        return 0
    }
    val command = actual.first()
    require(command in commands) { "Unknown maintenance command $command" }
    if (actual.drop(1).any { it == "--help" || it == "-h" }) {
        val spec = commandOptions.getValue(command)
        println(
            "Usage: $command " +
                spec.allowed.joinToString(" ") { option ->
                    val usage = "$option ${spec.choices[option]?.joinToString("|", "{", "}") ?: "VALUE"}"
                    if (option in spec.required) usage else "[$usage]"
                },
        )
        return 0
    }
    val args = normalizeCommandArguments(command, actual.drop(1))
    return runOrbitTool(command, args) ?: runWatchfaceTool(command, args) ?: runAssetTool(command, args)
        ?: runMigrationTool(command, args) ?: error("Unregistered maintenance command $command")
}

fun main(args: Array<String>) {
    val result =
        try {
            runMaintenance(args.toList())
        } catch (failure: IllegalArgumentException) {
            System.err.println("Invalid tool input: ${failure.message}")
            2
        } catch (failure: java.io.IOException) {
            System.err.println("Tool I/O failed: ${failure.javaClass.simpleName}")
            1
        }
    exitProcess(result)
}
