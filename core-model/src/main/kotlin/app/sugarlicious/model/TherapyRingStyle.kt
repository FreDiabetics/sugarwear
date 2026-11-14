package app.sugarlicious.model

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared proportions and finite foreground motion for the Mobile/Wear therapy rings. */
object TherapyRingStyle {
    const val CAP_CLEARANCE_STROKE_RATIO = 1.4f
    const val TRACK_ALPHA = .30f
    const val MOTION_DAMPING = .78f
    const val MOTION_STIFFNESS = 180f
    const val MOTION_DURATION_MS = 650L

    /** The same damped spring as Compose, sampled by the native View animator. */
    fun motionFraction(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val damping = MOTION_DAMPING.toDouble()
        val frequency = sqrt(MOTION_STIFFNESS.toDouble())
        val root = sqrt(1.0 - damping * damping)
        val time = fraction * MOTION_DURATION_MS / 1000.0
        val phase = frequency * root * time
        return (1.0 - exp(-damping * frequency * time) * (cos(phase) + damping / root * sin(phase))).toFloat()
    }
}
