package app.sugarlicious.wear

import app.sugarlicious.model.ResolvedTherapyIndicator
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapySlot
import app.sugarlicious.model.resolveRoundTherapyLayout
import kotlin.math.hypot

/** Size the visible circles against the round display, rather than their empty bitmap corners. */
internal fun centeredTherapyTileLayout(side: Float, appearance: TherapyProgressAppearance): List<ResolvedTherapyIndicator> {
    val original = resolveRoundTherapyLayout(side, appearance, baseDiameterDp = side * .58f)
    val center = side / 2f

    fun extent(shift: Float): Float =
        original.maxOf {
            hypot(it.slot.centerXDp - center, it.slot.centerYDp - center + shift) + it.slot.diameterDp / 2f
        }
    // Balance radial edge clearance, not the rectangular bounds of a triangular group.
    var lowShift = -side / 4f
    var highShift = side / 4f
    repeat(32) {
        val left = lowShift + (highShift - lowShift) / 3f
        val right = highShift - (highShift - lowShift) / 3f
        if (extent(left) < extent(right)) highShift = right else lowShift = left
    }
    val shift = (lowShift + highShift) / 2f
    val scale = minOf(1.10f, center / extent(shift).coerceAtLeast(1f))
    return original.map {
        val slot = it.slot
        it.copy(
            slot = TherapySlot(center + (slot.centerXDp - center) * scale, center + (slot.centerYDp - center + shift) * scale, slot.diameterDp * scale),
            strokeWidthDp = it.strokeWidthDp * scale,
            primaryTextSizeSp = it.primaryTextSizeSp * scale,
            secondaryTextSizeSp = it.secondaryTextSizeSp * scale,
            iconSizeDp = it.iconSizeDp * scale,
        )
    }
}
