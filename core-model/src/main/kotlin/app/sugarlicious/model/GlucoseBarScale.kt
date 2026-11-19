package app.sugarlicious.model

/**
 * Canonical presentation scale for the overview glucose progress bar.
 *
 * The active interval alone occupies the complete bar: low (40..low threshold),
 * in range (low..high), or high (high..400). A small gap at the normalized
 * glucose position indicates the measured value. This is a display mapping,
 * never a modification of the validated CGM reading.
 */
enum class GlucoseBarRange { LOW, TARGET, HIGH }

data class GlucoseBarState(
    val range: GlucoseBarRange,
    val position: Float,
)

object GlucoseBarScale {
    const val MINIMUM_MG_DL = 40.0
    const val MAXIMUM_MG_DL = 400.0

    fun state(valueMgDl: Double?, thresholds: CgmThresholds): GlucoseBarState? {
        val range =
            when (valueMgDl?.let(thresholds::classify)) {
                CgmRangeClass.VERY_LOW, CgmRangeClass.LOW -> GlucoseBarRange.LOW
                CgmRangeClass.IN_RANGE -> GlucoseBarRange.TARGET
                CgmRangeClass.HIGH, CgmRangeClass.VERY_HIGH -> GlucoseBarRange.HIGH
                null -> return null
            }
        val (minimum, maximum) =
            when (range) {
                GlucoseBarRange.LOW -> MINIMUM_MG_DL to thresholds.lowMgDl
                GlucoseBarRange.TARGET -> thresholds.lowMgDl to thresholds.highMgDl
                GlucoseBarRange.HIGH -> thresholds.highMgDl to MAXIMUM_MG_DL
            }
        val position =
            if (maximum > minimum) {
                ((requireNotNull(valueMgDl) - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0).toFloat()
            } else if (range == GlucoseBarRange.LOW) {
                0f
            } else {
                1f
            }
        return GlucoseBarState(range, position)
    }
}
