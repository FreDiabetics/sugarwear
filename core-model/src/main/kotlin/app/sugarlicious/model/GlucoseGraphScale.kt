package app.sugarlicious.model

/** Canonical Sugarlicious glucose Y scale shared by phone, watch and complication previews. */
object GlucoseGraphScale {
    private const val ZERO_RATIO = 0.02
    private const val LOW_RATIO = 0.10
    private const val TARGET_HIGH_RATIO = 0.515
    private const val DISPLAY_MIN = 40.0
    private const val DISPLAY_MAX = 400.0

    fun ratio(
        valueMgDl: Double,
        maximumMgDl: Double = DISPLAY_MAX,
    ): Double {
        val maximum = maximumMgDl.coerceAtLeast(180.0)
        val value = valueMgDl.coerceIn(DISPLAY_MIN, maximum)
        return when {
            value <= 80.0 -> ZERO_RATIO + (value - DISPLAY_MIN) / (80.0 - DISPLAY_MIN) * (LOW_RATIO - ZERO_RATIO)
            value <= 160.0 -> LOW_RATIO + (kotlin.math.ln(value / 80.0) / kotlin.math.ln(2.0)) * (TARGET_HIGH_RATIO - LOW_RATIO)
            else -> TARGET_HIGH_RATIO + (kotlin.math.ln(value / 160.0) / kotlin.math.ln(maximum / 160.0)) * (1.0 - TARGET_HIGH_RATIO)
        }.coerceIn(ZERO_RATIO, 1.0)
    }
}
