package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path

internal fun loadPalettes(repositoryRoot: Path = watchfaceRepositoryRoot()): List<Map<String, String>> {
    val rows = Files.readAllLines(repositoryRoot.resolve("tools/watchface-assets/orbit_palettes.csv")).filter { it.isNotBlank() }.map(::csvFields)
    val entries = rows.drop(1).map { rows.first().zip(it).toMap() }
    listOf("id", "apex_id").forEach { key -> require(entries.map { it[key] }.toSet().size == entries.size) { "Duplicate palette $key" } }
    require(entries.size == 78 && entries.count { it["origin"] == "original" } == 76) { "Expected76 original palettes plus preserved Olive and pure white" }

    fun hsv(color: String): DoubleArray {
        val (r, g, b) = listOf(0, 2, 4).map { color.substring(it, it + 2).toInt(16) / 255.0 }
        val maximum = maxOf(r, g, b)
        val minimum = minOf(r, g, b)
        if (minimum == maximum) return doubleArrayOf(0.0, 0.0, maximum)
        val range = maximum - minimum
        val rc = (maximum - r) / range
        val gc = (maximum - g) / range
        val bc = (maximum - b) / range
        val h =
            (
                if (r == maximum) {
                    bc - gc
                } else if (g == maximum) {
                    2.0 + rc - bc
                } else {
                    4.0 + gc - rc
                }
            ) / 6.0
        return doubleArrayOf((h % 1.0 + 1.0) % 1.0, range / maximum, maximum)
    }

    fun order(entry: Map<String, String>): Pair<Int, Double> {
        val vivid = hsv(entry.getValue("vivid"))
        if (entry["kind"] == "combination") return 2 to vivid[0]
        if (listOf("light", "medium", "vivid").maxOf { hsv(entry.getValue(it))[1] } < .15) return 1 to -vivid[2]
        val degrees = vivid[0] * 360.0
        return 0 to if (degrees > 345) degrees - 360 else degrees
    }
    return entries.sortedWith(compareBy<Map<String, String>> { order(it).first }.thenBy { order(it).second }.thenBy { it["name"] })
}

private fun csvFields(line: String): List<String> {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        when (val c = line[i]) {
            '"' ->
                if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    field.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            ',' ->
                if (quoted) {
                    field.append(c)
                } else {
                    fields += field.toString()
                    field.clear()
                }
            else -> field.append(c)
        }
        i++
    }
    require(!quoted) { "Unterminated CSV field" }
    fields += field.toString()
    return fields
}

internal fun shade(color: String, factor: Double): String = listOf(0, 2, 4).joinToString("") { "%02X".format(pyRound(color.substring(it, it + 2).toInt(16) * factor)) }

internal fun applyApexPalettes(root: Element, strings: Element) {
    val entries = loadPalettes()
    val labels = entries.map { "curated_palette_${it.getValue("id")}" }.toSet()
    strings.children().filter { it.attr("name").startsWith("curated_palette_") && it.attr("name") !in labels }.forEach { it.remove() }
    listOf("materialAccent", "materialSubTheme").forEach { identity ->
        val config = requireNotNull(root.one("UserConfigurations/ColorConfiguration[@id='$identity']")) { "Missing configuration $identity" }
        config.children().forEach { it.remove() }
        config.set("defaultValue" to "37")
        entries.forEach { entry ->
            val name = "curated_palette_${entry.getValue("id")}"
            label(strings, name, entry.getValue("name"))
            val tones = listOf("light", "medium", "vivid").map { entry.getValue(it) } + if (identity == "materialAccent") listOf(shade(entry.getValue("medium"), .32)) else emptyList()
            config.add("ColorOption", "id" to entry.getValue("apex_id"), "displayName" to name, "colors" to tones.joinToString(" ") { "#FF$it" })
        }
    }
    configureIndicatorGradient(root, "apex")
    unifyApexProgressSettings(root, strings)
    replaceUserFlavors(root, strings, "apex")
    val lower = root.need("Scene/ComplicationSlot[@slotId='3']")
    lower.all("Complication[@type='SHORT_TEXT']/PartImage").forEach { it.set("x" to "54", "y" to "304", "width" to "32", "height" to "32") }
    val glucose = "[COMPLICATION.RANGED_VALUE_MIN] == 40 && [COMPLICATION.RANGED_VALUE_MAX] == 260"
    lower.all("Complication[@type='RANGED_VALUE']/PartImage").forEach { image ->
        image.set("x" to "221", "y" to "434", "width" to "32", "height" to "32")
        image.all("Transform").forEach { transform ->
            when (transform.attr("target")) {
                "x" -> transform.set("value" to "$glucose ? 211 : 221")
                "y" -> transform.set("value" to "$glucose ? 424 : 434")
                "width", "height" -> transform.set("value" to "$glucose ? 52 : 32")
            }
        }
    }
    listOf("0", "1", "2", "3").forEach { identity ->
        val slot = root.need("Scene/ComplicationSlot[@slotId='$identity']")
        slot.all(".//Group[starts-with(@name,'progress-marker-')]//Stroke").forEach { it.set("thickness" to "5.640898") }
        slot.all("Complication[@type='RANGED_VALUE']/PartImage").forEach { image ->
            val base = image.attr("width").toDouble()
            val size = if (identity == "3") 36 else 32
            val delta = (size - base) / 2
            listOf("x", "y", "width", "height").forEach targetLoop@{ target ->
                val transform = image.one("Transform[@target='$target']") ?: image.add("Transform", "target" to target, "value" to image.attr(target))
                val previous = transform.attr("value")
                if ("[COMPLICATION.RANGED_VALUE_MAX] == 10" in previous) return@targetLoop
                val value = if (target in listOf("width", "height")) "$size" else "($previous) - $delta"
                transform.set("value" to "([COMPLICATION.RANGED_VALUE_MIN] == 0 && [COMPLICATION.RANGED_VALUE_MAX] == 10) ? $value : ($previous)")
            }
        }
    }
    fitOuterValueText(root)
    configureSegmentedProgress(root, "apex")
    restoreNativeHands(root, "apex")
}
