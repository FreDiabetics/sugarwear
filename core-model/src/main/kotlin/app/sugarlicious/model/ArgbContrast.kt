package app.sugarlicious.model

import kotlin.math.pow

/** Android-independent WCAG relative luminance for shared Mobile/Wear color decisions. */
object ArgbContrast {
    fun relativeLuminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }

        val red = linear(argb ushr 16 and 0xFF)
        val green = linear(argb ushr 8 and 0xFF)
        val blue = linear(argb and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    fun isLight(
        argb: Int,
        threshold: Double = 0.56,
    ): Boolean = relativeLuminance(argb) >= threshold
}
