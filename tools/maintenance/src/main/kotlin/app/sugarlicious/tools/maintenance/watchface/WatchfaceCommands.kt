package app.sugarlicious.tools.maintenance.watchface

import java.nio.file.Path

/** Returns null only when the command belongs to another maintenance tool group. */
fun runWatchfaceTool(command: String, args: List<String>): Int? {
    if (command !in setOf("configure-apex-progress", "configure-indicator-gradient", "configure-trend-icon-geometry", "curated-palettes", "prepare-slot-capture", "restore-native-hands", "segmented-progress", "unify-apex-progress-settings", "user-flavors")) return null
    val options = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index++]
        require(key.startsWith("--") && index < args.size) { "Expected --option value, got $key" }
        options[key] = args[index++]
    }

    fun required(key: String): String = requireNotNull(options[key]) { "Missing $key" }

    fun family(): String = required("--face").also { require(it in listOf("apex", "orbit")) { "--face must be apex or orbit" } }
    val allowed =
        when (command) {
            "prepare-slot-capture" -> setOf("--source", "--destination", "--name")
            "configure-trend-icon-geometry" -> setOf("--path")
            "configure-apex-progress", "curated-palettes", "unify-apex-progress-settings" -> setOf("--resources")
            else -> setOf("--resources", "--face")
        }
    require(options.keys.all { it in allowed }) { "Unknown options: ${options.keys - allowed}" }
    // Validate all choices before opening or changing resources, as argparse does.
    val selectedFamily = if (command in setOf("user-flavors", "configure-indicator-gradient", "segmented-progress", "restore-native-hands")) family() else null
    if (command == "prepare-slot-capture") {
        val name = required("--name")
        require(name in listOf("apex", "orbit")) { "--name must be apex or orbit" }
        prepareSlotCapture(Path.of(required("--source")), Path.of(required("--destination")), name)
    } else if (command == "configure-trend-icon-geometry") {
        options["--path"]?.let { configureTrendIconGeometry(Path.of(it)) } ?: listOf("sugarlicious-orbit", "sugarlicious-analog").forEach { configureTrendIconGeometry(watchfaceRepositoryRoot().resolve("watchfaces/$it/src/main/res/raw/watchface.xml")) }
    } else {
        val resources = Path.of(required("--resources"))
        val path = resources.resolve("raw/watchface.xml")
        val stringsPath = resources.resolve("values/strings.xml")
        val root = parseXml(path)
        val strings = if (command in listOf("configure-apex-progress", "curated-palettes", "unify-apex-progress-settings", "user-flavors")) parseXml(stringsPath) else null
        when (command) {
            "configure-apex-progress" -> {
                configureApexProgress(root, strings!!)
                if (root.one("Scene/ComplicationSlot[@slotId='7']/Complication[@type='LONG_TEXT']") == null) simplifyApexEditor(root, strings)
                fitOuterValueText(root)
                restoreNativeHands(root, "apex")
            }
            "curated-palettes" -> applyApexPalettes(root, strings!!)
            "unify-apex-progress-settings" -> unifyApexProgressSettings(root, strings!!)
            "user-flavors" -> replaceUserFlavors(root, strings!!, selectedFamily!!)
            "configure-indicator-gradient" -> configureIndicatorGradient(root, selectedFamily!!)
            "segmented-progress" -> configureSegmentedProgress(root, selectedFamily!!)
            "restore-native-hands" -> restoreNativeHands(root, selectedFamily!!)
        }
        writeXml(root, path)
        if (strings != null) writeXml(strings, stringsPath)
    }
    return 0
}
