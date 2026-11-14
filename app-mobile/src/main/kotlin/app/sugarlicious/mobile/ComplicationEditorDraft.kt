package app.sugarlicious.mobile

internal data class ComplicationEditorDraft(
    val initialVariantId: Int,
    val initialAppearance: ComplicationAppearanceSettings,
    val variantId: Int = initialVariantId,
    val appearance: ComplicationAppearanceSettings = initialAppearance,
) {
    val hasChanges: Boolean get() = variantId != initialVariantId || appearance != initialAppearance
}
