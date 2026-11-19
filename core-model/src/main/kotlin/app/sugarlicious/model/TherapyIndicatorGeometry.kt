package app.sugarlicious.model

/** Relative geometry of the therapy core; each surface keeps ownership of its outer size. */
object TherapyIndicatorGeometry {
    const val REFERENCE_DIAMETER_DP = 66f
    const val VALUE_TEXT_SP = 14f
    const val SECONDARY_TEXT_SP = 10f
    const val VALUE_WIDTH_FRACTION = .70f
    const val ICON_CENTER_Y_FRACTION = .885f

    /** Room below the ring for the icon in its open segment. */
    fun iconBottomOverflowDp(diameter: Float, iconSize: Float): Float =
        (diameter * ICON_CENTER_Y_FRACTION + iconSize / 2f - diameter).coerceAtLeast(0f)

    fun scale(diameter: Float): Float = diameter.coerceAtLeast(0f) / REFERENCE_DIAMETER_DP

    /** Convert final component geometry to SP without applying font scaling a second time. */
    fun fontSizeSp(referenceSizeSp: Float, diameter: Float, fontScale: Float): Float =
        referenceSizeSp * scale(diameter) / fontScale.coerceAtLeast(.1f)

    fun iconSizeDp(metric: TherapyProgressMetric, diameter: Float): Float =
        when (metric) {
            TherapyProgressMetric.IOB -> 19f
            TherapyProgressMetric.COB -> 17f
            TherapyProgressMetric.BASAL -> 19f
        } * scale(diameter)
}
