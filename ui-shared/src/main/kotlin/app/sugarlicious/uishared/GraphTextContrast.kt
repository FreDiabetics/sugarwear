package app.sugarlicious.uishared

import android.graphics.Color

/** Contrast follows the painted alpha-composited surface, not the uncomposited RGB value. */
fun graphTextContrast(background: Int, overlay: Int = Color.TRANSPARENT): Int {
    fun composite(front: Int, back: Int): Int {
        val alpha = Color.alpha(front) / 255f

        fun blend(foreground: Int, behind: Int) = (foreground * alpha + behind * (1f - alpha)).toInt()
        return Color.rgb(blend(Color.red(front), Color.red(back)), blend(Color.green(front), Color.green(back)), blend(Color.blue(front), Color.blue(back)))
    }
    val surface = composite(overlay, composite(background, Color.BLACK))
    return if (Color.luminance(surface) > .179f) Color.BLACK else Color.WHITE
}
