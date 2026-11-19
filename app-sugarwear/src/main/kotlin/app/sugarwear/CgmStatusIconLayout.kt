package app.sugarwear

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.ceil

/** Original 66-unit SVG ink bounds; only transparent outer space is removed from layout. */
internal data class CgmStatusIconLayout(
    val ink: RectF,
) {
    val scale = 1.75f

    fun width(density: Float) = ceil(ink.width() * scale * density).toInt() + 2

    fun height(density: Float) = ceil(ink.height() * scale * density).toInt() + 2

    fun matrix(density: Float, intrinsicWidth: Int) =
        Matrix().apply {
            val pixelsPerUnit = scale * density
            setScale(66f * pixelsPerUnit / intrinsicWidth, 66f * pixelsPerUnit / intrinsicWidth)
            postTranslate(1f - ink.left * pixelsPerUnit, 1f - ink.top * pixelsPerUnit)
        }
}

internal fun cgmStatusIconLayout(resource: Int): CgmStatusIconLayout? =
    when (resource) {
        R.drawable.ic_status_bluetooth -> CgmStatusIconLayout(RectF(23.47047f, 14.2437f, 44.84f, 50.25f))
        R.drawable.ic_status_paused -> CgmStatusIconLayout(RectF(19.38f, 16.5f, 46.59f, 49.49439f))
        R.drawable.ic_status_sensor_error -> CgmStatusIconLayout(RectF(20.03f, 16.49f, 45.98f, 49.51206f))
        R.drawable.ic_status_signal_loss -> CgmStatusIconLayout(RectF(18.82f, 16.49f, 47.17f, 49.48439f))
        else -> null
    }
