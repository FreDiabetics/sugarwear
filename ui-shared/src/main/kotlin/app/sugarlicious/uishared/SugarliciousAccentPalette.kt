package app.sugarlicious.uishared

import android.graphics.Color
import kotlin.math.roundToInt

/** One brand seed for public Android controls and Compose menus. Clinical data colors stay independent. */
object SugarliciousAccentPalette {
    const val SEED: Int = 0xFF22EB60.toInt()
    val onPrimary: Int = Color.BLACK
    val secondary: Int = mix(SEED, Color.WHITE, .4f)
    val container: Int = mix(SEED, Color.BLACK, .8f)
    val tertiary: Int = mix(SEED, Color.GRAY, .25f)

    fun primary(dark: Boolean): Int = if (dark) SEED else mix(SEED, Color.BLACK, .08f)

    private fun mix(first: Int, second: Int, fraction: Float): Int =
        Color.rgb(
            (Color.red(first) * (1f - fraction) + Color.red(second) * fraction).roundToInt(),
            (Color.green(first) * (1f - fraction) + Color.green(second) * fraction).roundToInt(),
            (Color.blue(first) * (1f - fraction) + Color.blue(second) * fraction).roundToInt(),
        )
}
