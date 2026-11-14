package app.sugarlicious.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun nativeSlotPreviewPath(bounds: NativeSlotBounds): Path = nativeWatchSlotPath(nativeSlotPreviewBounds(bounds))

/** Only the isolated picker normalizes the inner ring to its larger sibling's pill size. */
internal fun nativeIsolatedPreviewSlot(faceIndex: Int, slot: NativeWatchSlot, reference: NativeSlotBounds? = null): NativeWatchSlot {
    if (faceIndex == 0 || slot.id != 5 || slot.bounds.kind != "BoundingArc" || reference == null) return slot
    val delta = (reference.width - slot.bounds.width) / 2f

    fun NativeSlotBounds.expanded() = copy(x = x - delta, y = y - delta, width = width + 2 * delta, height = height + 2 * delta)
    return slot.copy(
        bounds = slot.bounds.expanded(),
        templates =
            slot.templates.mapValues { (_, template) ->
                template.copy(
                    texts = template.texts.map { text -> if (text.bounds.kind == "BoundingArc") text.copy(bounds = text.bounds.expanded()) else text },
                    track = template.track?.expanded(),
                    icon =
                        template.icon?.let { icon ->
                            val dx = icon.x + icon.width / 2 - (slot.bounds.x + slot.bounds.width / 2)
                            val dy = icon.y + icon.height / 2 - (slot.bounds.y + slot.bounds.height / 2)
                            val radius = hypot(dx, dy)
                            if (radius > 0f) icon.copy(x = icon.x + delta * dx / radius, y = icon.y + delta * dy / radius) else icon
                        },
                )
            },
    )
}

/** A padded circular/curved backdrop follows native template ink without clipping that ink. */
internal fun nativeDynamicPreviewBackdrop(slot: NativeWatchSlot, variantId: Int?): Path {
    val type = eligibleNativeVariants(slot).firstOrNull { it.id == (variantId ?: nativeDefaultVariant(slot)) }?.type?.name ?: slot.providerType
    val template = slot.templates[type] ?: slot.templates[slot.providerType]
    if (slot.bounds.kind != "BoundingArc") {
        val ink = nativeDynamicPreviewBounds(slot, variantId)
        val radius = maxOf(ink.width, ink.height) / 2f
        return Path().apply { addOval(Rect(ink.center.x - radius, ink.center.y - radius, ink.center.x + radius, ink.center.y + radius)) }
    }
    val center = Offset(slot.bounds.x + slot.bounds.width / 2, slot.bounds.y + slot.bounds.height / 2)
    val points = mutableListOf<Offset>()

    fun addArc(b: NativeSlotBounds, half: Float) {
        val start = minOf(b.startAngle, b.endAngle)
        val end = maxOf(b.startAngle, b.endAngle)
        val cx = b.x + b.width / 2
        val cy = b.y + b.height / 2
        repeat(65) { index ->
            val angle = Math.toRadians((start + (end - start) * index / 64f - 90f).toDouble())
            for (radius in listOf(b.width / 2 - half, b.width / 2 + half)) points += Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
        }
    }
    template?.texts.orEmpty().forEach { text -> if (text.bounds.kind == "BoundingArc") addArc(text.bounds, text.size) }
    template?.track?.let { addArc(it, it.thickness / 2) }
    template?.icon?.let { b ->
        val angle = Math.toRadians(template.iconAngle.toDouble())
        for (x in listOf(-b.width / 2, b.width / 2)) {
            for (y in listOf(-b.height / 2, b.height / 2)) {
                points += Offset(b.x + b.width / 2 + x * cos(angle).toFloat() - y * sin(angle).toFloat(), b.y + b.height / 2 + x * sin(angle).toFloat() + y * cos(angle).toFloat())
            }
        }
    }
    if (points.isEmpty()) return nativeSlotPreviewPath(slot.bounds)
    val radii = points.map { hypot(it.x - center.x, it.y - center.y) }
    val radius = (radii.min() + radii.max()) / 2
    val half = (radii.max() - radii.min()) / 2 + 8f
    val angles = points.map { nativePreviewAngle(it.x - center.x, it.y - center.y, slot.bounds.startAngle - 45f) }
    val fitted = slot.bounds.copy(x = center.x - radius, y = center.y - radius, width = radius * 2, height = radius * 2, thickness = half * 2, startAngle = angles.min(), endAngle = angles.max())
    var path = nativeWatchSlotPath(fitted)
    for (angle in listOf(fitted.startAngle, fitted.endAngle)) {
        val radians = Math.toRadians((angle - 90f).toDouble())
        val x = center.x + radius * cos(radians).toFloat()
        val y = center.y + radius * sin(radians).toFloat()
        path = Path.combine(PathOperation.Union, path, Path().apply { addOval(Rect(x - half, y - half, x + half, y + half)) })
    }
    return path
}

/** Fits complete template ink rather than clipping Orbit's inner curved text to its touch bounds. */
internal fun nativeDynamicPreviewBounds(slot: NativeWatchSlot, variantId: Int?): Rect {
    val type = eligibleNativeVariants(slot).firstOrNull { it.id == (variantId ?: nativeDefaultVariant(slot)) }?.type?.name ?: slot.providerType
    val template = slot.templates[type] ?: slot.templates[slot.providerType]
    val paths =
        listOf(nativeWatchSlotPath(slot.bounds)) +
            template?.texts.orEmpty().map { text ->
                val b = text.bounds
                if (b.kind == "BoundingArc") nativeWatchSlotPath(b.copy(startAngle = minOf(b.startAngle, b.endAngle), endAngle = maxOf(b.startAngle, b.endAngle), thickness = text.size * 2.2f)) else nativeWatchSlotPath(b)
            } + listOfNotNull(template?.icon?.let(::nativeWatchSlotPath))
    val rects = paths.map { it.getBounds() }
    return Rect(rects.minOf { it.left } - 8f, rects.minOf { it.top } - 8f, rects.maxOf { it.right } + 8f, rects.maxOf { it.bottom } + 8f)
}

@Composable
internal fun IsolatedNativeSlotPreview(faceIndex: Int, slot: NativeWatchSlot, selected: Boolean, modifier: Modifier = Modifier, siblings: List<NativeSlotBounds> = emptyList(), variantId: Int? = null) {
    val context = LocalContext.current
    val reference = siblings.filter { it.kind == "BoundingArc" && it.width < 400f }.maxByOrNull { it.width }
    val previewSlot = nativeIsolatedPreviewSlot(faceIndex, slot, reference)
    Canvas(modifier.semantics { contentDescription = "${slot.displayName} · Demo der nativen Complication-Vorlage" }) {
        val backdrop = nativeDynamicPreviewBackdrop(previewSlot, variantId)
        val crop = backdrop.getBounds()
        val factor = size.minDimension / maxOf(crop.width, crop.height)
        withTransform({
            translate((size.width - crop.width * factor) / 2f - crop.left * factor, (size.height - crop.height * factor) / 2f - crop.top * factor)
            scale(factor, factor, Offset.Zero)
        }) {
            drawPath(backdrop, Color.Black)
            drawNativeSlotDemo(previewSlot, variantId, context)
            if (selected) drawPath(backdrop, Color(0xFF6CA9FF), style = Stroke(3f))
        }
    }
}
