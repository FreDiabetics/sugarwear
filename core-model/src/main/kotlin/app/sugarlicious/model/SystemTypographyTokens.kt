package app.sugarlicious.model

/** Shared semantic weights; an emphasized value is real bold, not medium. */
object SystemTypographyTokens {
    const val NORMAL_WEIGHT = 400
    const val BOLD_WEIGHT = 700

    fun weight(bold: Boolean): Int = if (bold) BOLD_WEIGHT else NORMAL_WEIGHT

    @Suppress("UNUSED_PARAMETER") // Keep the caller contract while following the device-selected default.
    fun fontFamily(manufacturer: String): String = "sans-serif"
}
