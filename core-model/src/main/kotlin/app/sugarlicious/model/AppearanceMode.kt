package app.sugarlicious.model

/** Explicit appearance profile. It is never inferred by a renderer from another profile. */
enum class AppearanceMode(
    val storageKey: String,
) {
    LIGHT("light"),
    DARK("dark"),
}

/**
 * Reference geometry measured from the accepted 2x2 Android widget.
 *
 * The glucose glyphs occupy 58 px and the trend asset render box 37.2 px in the reference
 * render, yielding 0.642. Consumers keep their existing glucose typography and derive only the
 * arrow height from this ratio. The asset aspect ratio remains untouched.
 */
object GlucoseTrendSizing {
    const val MIN_SCALE_PERCENT = 70
    const val DEFAULT_SCALE_PERCENT = 100
    const val MAX_SCALE_PERCENT = 200

    /** Exact visual reference of the accepted 220 x 220 dp combined 2x2 widget. */
    const val REFERENCE_GLUCOSE_TEXT_SP = 40.38f
    const val REFERENCE_GLUCOSE_LINE_HEIGHT_SP = 42.30f
    const val REFERENCE_TREND_HEIGHT_DP = 18.62f
    const val REFERENCE_ARROW_TO_GLUCOSE_HEIGHT = 0.642f

    fun scaleFactor(percent: Int): Float = percent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT) / 100f

    fun arrowHeightForGlucoseHeight(
        glucoseVisualHeight: Float,
        customScale: Float = 1f,
    ): Float =
        (glucoseVisualHeight.coerceAtLeast(0f) * REFERENCE_ARROW_TO_GLUCOSE_HEIGHT * customScale)
            .coerceAtLeast(1f)
}
