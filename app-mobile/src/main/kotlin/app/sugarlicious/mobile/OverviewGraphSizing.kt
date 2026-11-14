package app.sugarlicious.mobile

/** Fit the first three enabled cards, including the metabolic graph; TIR remains below the fold. */
internal fun overviewFittedCgmHeight(preferred: Float, viewport: Float, header: Float, visibleCardHeights: List<Float?>): Float {
    if (header <= 0f || null !in visibleCardHeights) return preferred
    val cards = visibleCardHeights.take(3)
    val spacing = 6f + (cards.size - 1).coerceAtLeast(0) * 12f
    return (viewport - header - spacing - cards.filterNotNull().sum()).coerceAtLeast(96f)
}

/** Keep IOB/COB readable; additional content is reached by scrolling. */
internal fun overviewMetabolicGraphHeight(profileHeight: Int): Int = profileHeight.coerceIn(120, 145)

/** Floor in pixels so three rounded child widths cannot exceed a FlowRow by one pixel. */
internal fun overviewProgressColumnWidthPx(available: Int, gap: Int, minimum: Int): Int =
    maxOf(minimum, ((available - 2 * gap).coerceAtLeast(0) / 3)).coerceIn(1, available.coerceAtLeast(1))
