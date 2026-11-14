package app.sugarlicious.mobile

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

internal data class NativeSlotBounds(
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val startAngle: Float = 0f,
    val endAngle: Float = 360f,
    val thickness: Float = 0f,
)

internal data class NativeWatchSlot(
    val id: Int,
    val supportedTypes: Set<String>,
    val bounds: NativeSlotBounds,
    val provider: String = "",
    val providerType: String = "",
    val systemProvider: String = "",
    val displayName: String = "",
    val templates: Map<String, NativeSlotTemplate> = emptyMap(),
)

internal data class NativeSlotText(
    val bounds: NativeSlotBounds,
    val size: Float,
    val expression: String,
    val clockwise: Boolean = true,
    val align: String = "CENTER",
    val emptyTitleY: Float? = null,
    val therapyUnitFormatting: Boolean = false,
    val color: Int = 0xFFFFFFFF.toInt(),
    val letterSpacing: Float = 0f,
)

internal data class NativeSlotTemplate(
    val texts: List<NativeSlotText>,
    val icon: NativeSlotBounds?,
    val track: NativeSlotBounds?,
    val iconAngle: Float = 0f,
    val iconColor: Int = 0xFFFFFFFF.toInt(),
    val trackColor: Int = 0xFF454545.toInt(),
    val fillColor: Int = 0xFFFFFFFF.toInt(),
    val trackClockwise: Boolean = true,
    val fillStartAngle: Float? = null,
    val fillClockwise: Boolean = true,
    val trackGapDegrees: Float = 0f,
)

internal fun nativeArcSweep(start: Float, end: Float, clockwise: Boolean): Float {
    val difference = if (clockwise) end - start else start - end
    val normalized = ((difference % 360f) + 360f) % 360f
    val sweep = if (normalized == 0f && difference != 0f) 360f else normalized
    return if (clockwise) sweep else -sweep
}

private fun Element.elements(tag: String): List<Element> = getElementsByTagName(tag).let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }

private fun Element.num(name: String, default: Float = 0f): Float = getAttribute(name).toFloatOrNull() ?: default

private fun nativeLiteralColor(value: String): Int? =
    value
        .removePrefix("#")
        .takeIf { value.startsWith('#') && it.length in setOf(6, 8) }
        ?.toLongOrNull(16)
        ?.let { if (value.length == 7) (it or 0xFF000000L).toInt() else it.toInt() }

internal fun resolveNativeFontColor(value: String, palettes: Map<String, List<Int>>): Int {
    nativeLiteralColor(value)?.let { return it }
    val reference = Regex("\\[CONFIGURATION\\.([A-Za-z0-9_]+)\\.([0-9]+)]").matchEntire(value)
    if (reference != null) return palettes[reference.groupValues[1]]?.getOrNull(reference.groupValues[2].toInt()) ?: 0xFFFFFFFF.toInt()
    if (value == "[COMPLICATION.RANGED_VALUE_COLORS]") return (palettes["materialAccent"] ?: palettes["tertiary"] ?: palettes["secondary"])?.firstOrNull() ?: 0xFFFFFFFF.toInt()
    return 0xFFFFFFFF.toInt()
}

private fun templateFor(element: Element, palettes: Map<String, List<Int>>): NativeSlotTemplate {
    fun artworkColor(node: Element?, attribute: String): Int {
        var owner = node
        var expression = node?.getAttribute(attribute).orEmpty()
        var alpha = 1f
        while (owner != null && owner !== element) {
            if (expression.isBlank()) expression = owner.getAttribute("tintColor")
            alpha *= owner.getAttribute("alpha").toFloatOrNull()?.div(255f) ?: 1f
            owner = owner.parentNode as? Element
        }
        val color = resolveNativeFontColor(expression, palettes)
        return (color and 0x00FFFFFF) or ((((color ushr 24) * alpha).roundToInt().coerceIn(0, 255)) shl 24)
    }
    val texts =
        element
            .elements("PartText")
            .mapNotNull { part ->
                val circular = part.elements("TextCircular").firstOrNull()
                val font = part.elements("Font").firstOrNull() ?: return@mapNotNull null
                val expression = part.elements("Parameter").joinToString(" ") { it.getAttribute("expression") }
                if (!expression.contains("COMPLICATION.")) return@mapNotNull null
                val bounds =
                    if (circular != null) {
                        NativeSlotBounds("BoundingArc", circular.num("centerX") - circular.num("width") / 2, circular.num("centerY") - circular.num("height") / 2, circular.num("width"), circular.num("height"), circular.num("startAngle"), circular.num("endAngle"), font.num("size"))
                    } else {
                        NativeSlotBounds("BoundingBox", part.num("x"), part.num("y"), part.num("width"), part.num("height"))
                    }
                val emptyTitleY =
                    part
                        .elements("Transform")
                        .firstOrNull { it.getAttribute("target") == "y" }
                        ?.getAttribute("value")
                        ?.let { value ->
                            Regex("\\[COMPLICATION\\.TITLE]\\s*==\\s*\"\"\\s*\\?\\s*(-?[0-9.]+)\\s*:")
                                .find(value)
                                ?.groupValues
                                ?.get(1)
                                ?.toFloatOrNull()
                        }
                val contentExpression =
                    when {
                        expression.contains("COMPLICATION.TITLE") && expression.contains("COMPLICATION.TEXT") ->
                            part.elements("Parameter").joinToString(" ") { parameter -> if (parameter.getAttribute("expression").contains("COMPLICATION.TITLE")) "[COMPLICATION.TITLE]" else "[COMPLICATION.TEXT]" }
                        expression.contains("COMPLICATION.TITLE") -> "[COMPLICATION.TITLE]"
                        else -> "[COMPLICATION.TEXT]"
                    }
                NativeSlotText(bounds, font.num("size", 24f), contentExpression, circular?.getAttribute("direction") != "COUNTER_CLOCKWISE", circular?.getAttribute("align") ?: "CENTER", emptyTitleY, expression.contains("numberFormat"), resolveNativeFontColor(font.getAttribute("color"), palettes), font.num("letterSpacing", 0f))
            }.distinctBy { it.bounds to it.expression }
            .distinctBy { it.bounds }
    val image = element.elements("PartImage").firstOrNull { it.elements("Image").any { image -> image.getAttribute("resource").contains("COMPLICATION") } }
    val track = element.elements("Arc").firstOrNull { it.num("startAngle") != it.num("endAngle") }
    val fill = element.elements("Arc").firstOrNull { it.num("startAngle") == it.num("endAngle") }
    return NativeSlotTemplate(
        texts,
        image?.let { NativeSlotBounds("BoundingBox", it.num("x"), it.num("y"), it.num("width"), it.num("height")) },
        track?.let { NativeSlotBounds("BoundingArc", it.num("centerX") - it.num("width") / 2, it.num("centerY") - it.num("height") / 2, it.num("width"), it.num("height"), it.num("startAngle"), it.num("endAngle"), it.elements("Stroke").firstOrNull()?.num("thickness", 10f) ?: 10f) },
        image?.num("angle") ?: 0f,
        iconColor = artworkColor(image, "tintColor"),
        trackColor = artworkColor(track?.elements("Stroke")?.firstOrNull(), "color"),
        fillColor = artworkColor(fill?.elements("Stroke")?.firstOrNull(), "color"),
        trackClockwise = track?.getAttribute("direction") != "COUNTER_CLOCKWISE",
        fillStartAngle = fill?.num("startAngle"),
        fillClockwise = fill?.getAttribute("direction") != "COUNTER_CLOCKWISE",
        trackGapDegrees =
            track
                ?.elements("Transform")
                ?.firstOrNull { it.getAttribute("target") == "endAngle" }
                ?.getAttribute("value")
                ?.let {
                    Regex("clamp\\([0-9.]+\\s*\\+\\s*([0-9.]+)\\s*\\+")
                        .find(it)
                        ?.groupValues
                        ?.get(1)
                        ?.toFloatOrNull()
                } ?: 0f,
    )
}

internal fun nativeSlotPreviewBounds(bounds: NativeSlotBounds): NativeSlotBounds =
    if (bounds.kind == "BoundingArc" && bounds.width >= 400f) bounds.copy(thickness = maxOf(88f, bounds.thickness + 24f)) else bounds

/** Unwrap clockwise angles into this slot's revolution; Kotlin remainder may otherwise be negative. */
internal fun nativePreviewAngle(dx: Float, dy: Float, startAngle: Float): Float =
    ((Math.toDegrees(kotlin.math.atan2(dx, -dy).toDouble()).toFloat() - startAngle + 720f) % 360f) + startAngle

/** Ownership mask may grow into a free angular gap, never into another outer slot. */
internal fun nativeSlotPreviewContentBounds(bounds: NativeSlotBounds, siblings: List<NativeSlotBounds>): NativeSlotBounds {
    if (bounds.kind != "BoundingArc" || bounds.width < 400f) return bounds
    val neighbors = siblings.filter { it != bounds && it.kind == "BoundingArc" && it.width >= 400f }
    val before = neighbors.minOfOrNull { (bounds.startAngle - it.endAngle + 360f) % 360f } ?: 0f
    val after = neighbors.minOfOrNull { (it.startAngle - bounds.endAngle + 360f) % 360f } ?: 0f
    return bounds.copy(thickness = bounds.thickness + 24f, startAngle = bounds.startAngle - minOf(8f, before / 2f), endAngle = bounds.endAngle + minOf(8f, after / 2f))
}

internal fun nativeSlotPreviewEntries(slots: List<NativeWatchSlot>): List<NativeWatchSlot> = slots.filterNot { it.providerType in setOf("SMALL_IMAGE", "LARGE_IMAGE") }

/** Display order only; IDs and their provider assignments remain unchanged. */
internal fun nativeSlotEditorOrder(faceIndex: Int, slots: List<NativeWatchSlot>): List<NativeWatchSlot> {
    val order = if (faceIndex == 0) listOf(0, 1, 4, 5, 3, 2, 6) else listOf(0, 7, 4, 5, 3, 2)
    val entries = nativeSlotPreviewEntries(slots)
    return order.mapNotNull { id -> entries.firstOrNull { it.id == id } } + entries.filter { it.id !in order }
}

internal fun isNativeSlotEditable(slot: NativeWatchSlot): Boolean =
    slot.provider.substringBefore('/') == "app.sugarlicious" &&
        slot.provider.substringAfter('/', "").startsWith("app.sugarlicious.complications.")

internal fun eligibleNativeVariants(slot: NativeWatchSlot): List<ComplicationVariant> =
    if (!isNativeSlotEditable(slot)) {
        emptyList()
    } else {
        SugarliciousComplicationCatalog.flatMap { it.variants }.filter { variant ->
            (if (variant.type == ComplicationVariantType.PHOTO_IMAGE) "LARGE_IMAGE" else variant.type.name) in slot.supportedTypes
        }
    }

internal fun readNativeWatchSlots(input: InputStream): List<NativeWatchSlot> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
    val palettes =
        document.documentElement.elements("ColorConfiguration").associate { configuration ->
            val option = configuration.elements("ColorOption").firstOrNull { it.getAttribute("id") == configuration.getAttribute("defaultValue") }
            configuration.getAttribute("id") to
                option
                    ?.getAttribute("colors")
                    .orEmpty()
                    .split(Regex("\\s+"))
                    .mapNotNull(::nativeLiteralColor)
        }
    val scene = document.getElementsByTagName("Scene").item(0) ?: document.documentElement
    return (0 until scene.childNodes.length).mapNotNull { index ->
        val slot = scene.childNodes.item(index) as? Element ?: return@mapNotNull null
        if (slot.tagName != "ComplicationSlot" || slot.getAttribute("isCustomizable") != "TRUE") return@mapNotNull null
        val shape = (0 until slot.childNodes.length).mapNotNull { slot.childNodes.item(it) as? Element }.firstOrNull { it.tagName.startsWith("Bounding") } ?: return@mapNotNull null

        fun Element.number(name: String, fallback: Float = 0f) = getAttribute(name).toFloatOrNull() ?: fallback
        val width = shape.number("width")
        val height = shape.number("height")
        val arc = shape.tagName == "BoundingArc"
        val policy = slot.getElementsByTagName("DefaultProviderPolicy").item(0) as? Element
        val type = policy?.getAttribute("primaryProviderType").orEmpty().ifEmpty { policy?.getAttribute("defaultSystemProviderType").orEmpty() }
        NativeWatchSlot(
            id = slot.getAttribute("slotId").toInt(),
            supportedTypes = slot.getAttribute("supportedTypes").split(' ').toSet(),
            provider = policy?.getAttribute("primaryProvider").orEmpty(),
            providerType = type,
            systemProvider = policy?.getAttribute("defaultSystemProvider").orEmpty(),
            displayName = slot.getAttribute("displayName").ifBlank { "Slot ${slot.getAttribute("slotId")}" },
            templates =
                slot.elements("Complication").associate { element ->
                    fun NativeSlotBounds.global() = copy(x = x + slot.number("x"), y = y + slot.number("y"))
                    val template = templateFor(element, palettes)
                    element.getAttribute("type") to template.copy(texts = template.texts.map { it.copy(bounds = it.bounds.global(), emptyTitleY = it.emptyTitleY?.plus(slot.number("y"))) }, icon = template.icon?.global(), track = template.track?.global())
                },
            bounds =
                NativeSlotBounds(
                    shape.tagName,
                    slot.number("x") + if (arc) shape.number("centerX") - width / 2f else shape.number("x"),
                    slot.number("y") + if (arc) shape.number("centerY") - height / 2f else shape.number("y"),
                    width,
                    height,
                    shape.number("startAngle"),
                    shape.number("endAngle", 360f),
                    shape.number("thickness"),
                ),
        )
    }
}
