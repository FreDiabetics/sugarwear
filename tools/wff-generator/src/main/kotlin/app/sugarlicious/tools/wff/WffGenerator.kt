package app.sugarlicious.tools.wff

import app.sugarlicious.tools.cwf.CwfDocument
import app.sugarlicious.tools.cwf.CwfElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun String.escapeXml(): String = replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

data class WffGenerationResult(
    val xml: String,
    val warnings: List<String>,
    val omittedElements: List<String>,
    val slotCount: Int,
)

class WffGenerationException(
    message: String,
) : IllegalArgumentException(message)

class WffGenerator {
    fun generate(
        document: CwfDocument,
        allowDegraded: Boolean = false,
    ): WffGenerationResult {
        val slots =
            mappings
                .mapNotNull { mapping ->
                    mapping.keys.firstNotNullOfOrNull { key -> document.elements[key]?.takeIf { it.visible }?.let { mapping.toSlot(it) } }
                }.take(MAX_SLOTS)
        val consumed = slots.flatMapTo(mutableSetOf()) { it.sourceKeys } + STATIC_KEYS
        val omitted =
            document.elements.values
                .filter { it.visible && it.key !in consumed }
                .map { it.key }
                .sorted()
        val warnings =
            buildList {
                addAll(document.warnings)
                if (omitted.isNotEmpty()) add("Visible CWF elements are not mapped: ${omitted.joinToString()}")
                if (slots.size == MAX_SLOTS) add("WFF platform limit of eight ComplicationSlot instances is fully used")
            }.distinct()
        if (!allowDegraded && warnings.isNotEmpty()) {
            throw WffGenerationException("Generation requires --allow-degraded:\n${warnings.joinToString("\n")}")
        }
        if (slots.none { it.key == "sgv" }) throw WffGenerationException("A visible sgv element is required")

        val scaleX = 450.0 / document.canvasWidth
        val scaleY = 450.0 / document.canvasHeight

        fun sx(value: Int) = (value * scaleX).toInt().coerceIn(0, 450)

        fun sy(value: Int) = (value * scaleY).toInt().coerceIn(0, 450)
        val xml =
            buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<WatchFace width=\"450\" height=\"450\">")
                appendLine("  <Metadata key=\"CLOCK_TYPE\" value=\"DIGITAL\" />")
                appendLine("  <Scene backgroundColor=\"#000000\">")
                document.elements["freetext1"]?.takeIf { it.visible }?.let {
                    val y = sy(it.top)
                    val h = sy(it.height).coerceAtLeast(1)
                    val textValue =
                        it.raw["textvalue"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.takeIf(String::isNotBlank)
                    if (textValue != null) {
                        val color = it.fontColor?.toWffColor() ?: "#FFFFFFFF"
                        appendLine("    <PartText x=\"${sx(it.left)}\" y=\"$y\" width=\"${sx(it.width)}\" height=\"$h\" alpha=\"255\">")
                        appendLine("      <Variant mode=\"AMBIENT\" target=\"alpha\" value=\"0\" />")
                        appendLine(
                            "      <Text align=\"CENTER\" ellipsis=\"TRUE\"><Font family=\"SYNC_TO_DEVICE\" size=\"${scaledFont(
                                it,
                                scaleY,
                                24,
                            )}\" color=\"$color\">${textValue.escapeXml()}</Font></Text>",
                        )
                        appendLine("    </PartText>")
                    } else {
                        appendLine("    <PartDraw x=\"${sx(it.left)}\" y=\"$y\" width=\"${sx(it.width)}\" height=\"$h\" alpha=\"255\">")
                        appendLine("      <Variant mode=\"AMBIENT\" target=\"alpha\" value=\"0\" />")
                        appendLine(
                            "      <Line startX=\"0\" startY=\"${h / 2}\" endX=\"${sx(
                                it.width,
                            )}\" endY=\"${h / 2}\"><Stroke color=\"#FFFFFFFF\" cap=\"BUTT\" thickness=\"$h\" /></Line>",
                        )
                        appendLine("    </PartDraw>")
                    }
                }
                slots.forEachIndexed { index, slot -> append(slot.xml(index, scaleX, scaleY)) }
                val time = document.elements["time"]?.takeIf { it.visible }
                if (time != null) {
                    appendLine(
                        "    <DigitalClock x=\"${sx(
                            time.left,
                        )}\" y=\"${sy(time.top)}\" width=\"${sx(time.width)}\" height=\"${sy(time.height)}\">",
                    )
                    appendLine(
                        "      <TimeText x=\"0\" y=\"0\" width=\"${sx(
                            time.width,
                        )}\" height=\"${sy(
                            time.height,
                        )}\" format=\"hh:mm\" hourFormat=\"SYNC_TO_DEVICE\" align=\"CENTER\"><Font family=\"SYNC_TO_DEVICE\" size=\"${scaledFont(
                            time,
                            scaleY,
                            54,
                        )}\" color=\"#FFFFFFFF\" /></TimeText>",
                    )
                    appendLine("    </DigitalClock>")
                } else {
                    val splitTime =
                        listOf("hour" to "hh", "minute" to "mm", "second" to "ss")
                            .mapNotNull { (key, format) -> document.elements[key]?.takeIf { it.visible }?.let { Triple(it, format, key) } }
                    if (splitTime.isNotEmpty()) {
                        appendLine("    <DigitalClock x=\"0\" y=\"0\" width=\"450\" height=\"450\">")
                        splitTime.forEach { (element, format, _) ->
                            val color = element.fontColor?.toWffColor() ?: "#FFFFFFFF"
                            appendLine(
                                "      <TimeText x=\"${sx(
                                    element.left,
                                )}\" y=\"${sy(
                                    element.top,
                                )}\" width=\"${sx(
                                    element.width,
                                )}\" height=\"${sy(
                                    element.height,
                                )}\" format=\"$format\" hourFormat=\"SYNC_TO_DEVICE\" align=\"CENTER\"><Font family=\"SYNC_TO_DEVICE\" size=\"${scaledFont(
                                    element,
                                    scaleY,
                                    46,
                                )}\" color=\"$color\" /></TimeText>",
                            )
                        }
                        appendLine("    </DigitalClock>")
                    }
                }
                appendLine("  </Scene>")
                appendLine("</WatchFace>")
            }
        return WffGenerationResult(xml, warnings, omitted, slots.size)
    }

    private fun scaledFont(
        element: CwfElement,
        scale: Double,
        fallback: Int,
    ): Int = ((element.textSize ?: fallback) * scale).toInt().coerceIn(12, 72)

    private data class Slot(
        val key: String,
        val sourceKeys: Set<String>,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val displayName: String,
        val provider: String,
        val field: String = "TEXT",
        val image: Boolean = false,
        val ambient: Boolean = true,
        val fontSize: Int = 24,
    ) {
        fun xml(
            id: Int,
            scaleX: Double,
            scaleY: Double,
        ): String {
            fun scaledX(value: Int) = (value * scaleX).toInt().coerceIn(0, 450)

            fun scaledY(value: Int) = (value * scaleY).toInt().coerceIn(0, 450)
            val px = scaledX(x)
            val py = scaledY(y)
            val w = scaledX(width).coerceAtLeast(1)
            val h = scaledY(height).coerceAtLeast(1)
            val type = if (image) "SMALL_IMAGE" else "SHORT_TEXT"
            return buildString {
                appendLine(
                    "    <ComplicationSlot slotId=\"$id\" displayName=\"${displayName.escapeXml()}\" isCustomizable=\"TRUE\" supportedTypes=\"$type EMPTY\" x=\"$px\" y=\"$py\" width=\"$w\" height=\"$h\">",
                )
                if (ambient) appendLine("      <Variant mode=\"AMBIENT\" target=\"alpha\" value=\"0\" />")
                appendLine(
                    "      <DefaultProviderPolicy primaryProvider=\"app.sugarlicious/app.sugarlicious.complications.$provider\" primaryProviderType=\"$type\" defaultSystemProvider=\"EMPTY\" defaultSystemProviderType=\"EMPTY\" />",
                )
                appendLine("      <BoundingBox x=\"0\" y=\"0\" width=\"$w\" height=\"$h\" />")
                if (image) {
                    appendLine(
                        "      <Complication type=\"SMALL_IMAGE\"><PartImage x=\"0\" y=\"0\" width=\"$w\" height=\"$h\"><Image resource=\"[COMPLICATION.SMALL_IMAGE]\" /></PartImage></Complication>",
                    )
                } else {
                    appendLine(
                        "      <Complication type=\"SHORT_TEXT\"><PartText x=\"0\" y=\"0\" width=\"$w\" height=\"$h\"><Text align=\"CENTER\" ellipsis=\"TRUE\"><Font family=\"SYNC_TO_DEVICE\" size=\"$fontSize\" color=\"#FFFFFFFF\"><Template>%s<Parameter expression=\"[COMPLICATION.$field]\" /></Template></Font></Text></PartText></Complication>",
                    )
                }
                appendLine("      <Complication type=\"EMPTY\" />")
                appendLine("    </ComplicationSlot>")
            }
        }
    }

    private data class Mapping(
        val keys: List<String>,
        val factory: (CwfElement) -> Slot,
    ) {
        fun toSlot(element: CwfElement) = factory(element)
    }

    private val mappings =
        listOf(
            Mapping(listOf("sgv")) {
                Slot(
                    "sgv",
                    setOf("sgv", "direction", "delta"),
                    125,
                    24,
                    170,
                    100,
                    "Glucose, trend and delta",
                    "GlucoseTrendDeltaComplication",
                    ambient = false,
                    fontSize = 60,
                )
            },
            Mapping(listOf("timestamp")) {
                Slot(
                    "timestamp",
                    setOf("timestamp"),
                    300,
                    76,
                    60,
                    38,
                    "Glucose age",
                    "GlucoseAgeComplication",
                    field = "TITLE",
                    fontSize = 22,
                )
            },
            Mapping(listOf("loop")) { Slot("loop", setOf("loop"), 60, 55, 75, 55, "Loop status", "LoopComplication", fontSize = 20) },
            Mapping(
                listOf("uploader_battery"),
            ) { e ->
                Slot(
                    "uploader_battery",
                    setOf("uploader_battery", "rig_battery"),
                    e.left,
                    e.top,
                    e.width,
                    e.height,
                    "Phone battery",
                    "PhoneBatteryComplication",
                    fontSize = 20,
                )
            },
            Mapping(
                listOf("basalRate"),
            ) { e ->
                Slot(
                    "basalRate",
                    setOf("basalRate", "bgi"),
                    e.left,
                    e.top,
                    e.width,
                    e.height,
                    "Basal rate",
                    "BasalComplication",
                    fontSize = 20,
                )
            },
            Mapping(
                listOf("cob2", "cob1"),
            ) { e -> Slot("cob", setOf("cob1", "cob2"), e.left, e.top, e.width, e.height, "COB", "CobComplication", fontSize = 22) },
            Mapping(
                listOf("iob2", "iob1"),
            ) { e -> Slot("iob", setOf("iob1", "iob2"), e.left, e.top, e.width, e.height, "IOB", "IobComplication", fontSize = 22) },
            Mapping(
                listOf("chart"),
            ) { e ->
                Slot(
                    "chart",
                    setOf("chart"),
                    e.left,
                    e.top,
                    e.width,
                    e.height,
                    "Glucose graph",
                    "GlucoseGraphComplication",
                    image = true,
                )
            },
        )

    companion object {
        const val MAX_SLOTS = 8
        private val STATIC_KEYS =
            setOf(
                "background",
                "freetext1",
                "time",
                "day",
                "month",
                "cover_chart",
                "cover_plate",
                "hour",
                "minute",
                "second",
                "timePeriod",
                "day_name",
                "week_number",
                "hour_hand",
                "minute_hand",
                "second_hand",
            )
    }
}

private fun String.toWffColor(): String =
    when {
        matches(Regex("#[0-9A-Fa-f]{8}")) -> uppercase()
        matches(Regex("#[0-9A-Fa-f]{6}")) -> "#FF${drop(1).uppercase()}"
        else -> "#FFFFFFFF"
    }
