package app.sugarlicious.mobile

import kotlin.math.sqrt

/** Tests actual ink, never moving an event or its amount to make it fit. Touching hides it. */
internal fun therapyInkClearsUpperContour(
    inkLeft: Float,
    inkRight: Float,
    inkTop: Float,
    contourLeft: Float,
    contourRight: Float,
    contourTop: Float,
    cornerRadius: Float,
): Boolean {
    if (!listOf(inkLeft, inkRight, inkTop, contourLeft, contourRight, contourTop, cornerRadius).all { it.isFinite() }) return false
    val radius = cornerRadius.coerceIn(0f, ((contourRight - contourLeft) / 2f).coerceAtLeast(0f))

    fun topAt(x: Float): Float {
        if (radius == 0f) return contourTop
        val center =
            when {
                x < contourLeft + radius -> contourLeft + radius
                x > contourRight - radius -> contourRight - radius
                else -> return contourTop
            }
        val dx = (x - center).coerceIn(-radius, radius)
        return contourTop + radius - sqrt((radius * radius - dx * dx).coerceAtLeast(0f))
    }
    return inkTop > maxOf(topAt(inkLeft), topAt(inkRight))
}
