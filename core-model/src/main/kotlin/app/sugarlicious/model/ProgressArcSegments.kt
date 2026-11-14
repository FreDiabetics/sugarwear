package app.sugarlicious.model

/** Separate round-ended fill/track arcs; the gap includes the two projecting stroke caps. */
data class ProgressArcSegments(
    val fillSweep: Float,
    val trackStartOffset: Float,
    val trackSweep: Float,
)

fun progressArcSegments(progress: Float?, diameter: Float, stroke: Float, sweep: Float = 280f, capClearanceStrokeRatio: Float = 1.25f): ProgressArcSegments {
    val fraction = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val filled = sweep * fraction
    if (fraction <= 0f) return ProgressArcSegments(0f, 0f, sweep)
    if (fraction >= 1f) return ProgressArcSegments(sweep, sweep, 0f)
    val radius = ((diameter - stroke) / 2f).coerceAtLeast(stroke / 2f).coerceAtLeast(.001f)
    val clearanceRatio = capClearanceStrokeRatio.takeIf { it.isFinite() }?.coerceIn(1f, 3f) ?: 1.25f
    val capClearance = Math.toDegrees((stroke * clearanceRatio / radius).toDouble()).toFloat()
    val trackStart = (filled + capClearance).coerceAtMost(sweep)
    return ProgressArcSegments(filled, trackStart, sweep - trackStart)
}
