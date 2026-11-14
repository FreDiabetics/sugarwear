package app.sugarlicious.mobile

internal data class SettingsScrollHeaderState(
    val titleAlpha: Float,
    val titleOffsetDp: Float,
    val backCircleAlpha: Float,
    val titleScale: Float,
    val collapseFraction: Float,
)

internal fun settingsScrollHeaderState(scrollDp: Float, titleHeightDp: Float, firstCardTopDp: Float, compactHeightDp: Float = 56f): SettingsScrollHeaderState {
    val scroll = scrollDp.coerceAtLeast(0f)
    val collapseDistance = (titleHeightDp - compactHeightDp).coerceAtLeast(1f)
    val progress = if (titleHeightDp > compactHeightDp) (scroll / collapseDistance).coerceIn(0f, 1f) else 0f
    return SettingsScrollHeaderState(
        titleAlpha = if (scroll < titleHeightDp) 1f else 0f,
        titleOffsetDp = (titleHeightDp - compactHeightDp).coerceAtLeast(0f) * .5f * progress,
        backCircleAlpha = ((scroll - firstCardTopDp + compactHeightDp) / 24f).coerceIn(0f, 1f),
        titleScale = if (titleHeightDp > 56f) 1f - (1f - 20f / 34f) * progress else 1f,
        collapseFraction = progress,
    )
}

internal data class SettingsCompactTitleLayout(
    val startDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

internal fun settingsCompactTitleLayout(availableWidthDp: Float, textHeightDp: Float, startDp: Float = 52f): SettingsCompactTitleLayout =
    SettingsCompactTitleLayout(startDp, (availableWidthDp - startDp - 16f).coerceAtLeast(1f), (textHeightDp + 16f).coerceAtLeast(56f))

internal data class SettingsSubmenuHeaderState(
    val compactTitleAlpha: Float,
    val expandedTitleAlpha: Float,
    val backCircleAlpha: Float,
    val compactTitleOffsetDp: Float,
    val rowOffsetDp: Float,
    val expandedTitleTopDp: Float,
)

internal const val SETTINGS_SUBMENU_CARD_GAP_DP = 8f

internal fun settingsSubmenuExpansionTarget(expansionDp: Float, maximumDp: Float): Float =
    when {
        expansionDp <= 8f -> 0f
        maximumDp - expansionDp <= 8f -> maximumDp
        else -> expansionDp
    }

/** Back and compact title travel together just above the first card during expansion. */
internal fun settingsSubmenuHeaderState(
    scrollDp: Float,
    expansionDp: Float,
    maximumExpansionDp: Float,
    firstCardTopDp: Float,
    backBottomDp: Float = 52f,
    expandedTitleHeightDp: Float = 0f,
): SettingsSubmenuHeaderState {
    val scroll = scrollDp.coerceAtLeast(0f)
    val expansion = (expansionDp / maximumExpansionDp.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val rowOffset = (expansionDp - scroll).coerceAtLeast(0f)
    val initialGap = (firstCardTopDp - expansionDp - backBottomDp).coerceAtLeast(8f)
    val fadeDistance = minOf(20f, initialGap * .5f)
    val compact = (1f - scroll / fadeDistance).coerceIn(0f, 1f) * (1f - expansion / .4f).coerceIn(0f, 1f)
    val expandedFade =
        ((expansion - .25f) / .5f).coerceIn(0f, 1f) *
            (1f - scroll / (expansionDp * .7f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val availableTitleBottom = (backBottomDp - 52f + rowOffset).coerceAtLeast(0f)
    val fitFade = if (expandedTitleHeightDp > 0f) ((availableTitleBottom - expandedTitleHeightDp) / 24f).coerceIn(0f, 1f) else 1f
    val finalTitleTop = backBottomDp + 4f + SETTINGS_SUBMENU_CARD_GAP_DP + (maximumExpansionDp - backBottomDp - 4f - expandedTitleHeightDp) * .5f
    val titleTop = minOf(finalTitleTop, availableTitleBottom - expandedTitleHeightDp).coerceAtLeast(0f)
    val circleStart = maxOf(fadeDistance, initialGap - 14f)
    val circleEnd = maxOf(circleStart + 1f, initialGap - 4f)
    val circle = ((scroll - circleStart) / (circleEnd - circleStart)).coerceIn(0f, 1f)
    return SettingsSubmenuHeaderState(compact, minOf(expandedFade, fitFade), circle, 0f, rowOffset, titleTop)
}
