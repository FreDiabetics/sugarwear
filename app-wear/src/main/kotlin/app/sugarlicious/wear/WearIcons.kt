package app.sugarlicious.wear

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.withTranslation
import app.sugarlicious.model.ArgbContrast
import app.sugarlicious.model.TrendArrowStyle
import kotlin.math.ceil

internal fun shouldOutlineWearIcon(
    backgroundArgb: Int,
    colored: Boolean,
): Boolean = colored && ArgbContrast.isLight(backgroundArgb)

/** Menu/branding padding only; therapy symbols and canonical trend glyphs keep their geometry. */
internal fun wearUiIconInsetFraction(
    @DrawableRes drawableRes: Int,
): Float =
    if (drawableRes == R.drawable.ic_settings || drawableRes == R.drawable.ic_foreground) .08f else 0f

/** Shared classic-View icon renderer for the Wear app's user-configurable light surfaces. */
internal fun ImageView.renderSugarliciousWearIcon(
    @DrawableRes drawableRes: Int,
    tintArgb: Int?,
    backgroundArgb: Int,
    colored: Boolean = true,
    trendStyle: TrendArrowStyle? = null,
) {
    val insetFraction = if (trendStyle == null) wearUiIconInsetFraction(drawableRes) else 0f

    fun nativeIcon(): Drawable =
        requireNotNull(context.getDrawable(drawableRes)).mutate().let { drawable ->
            if (insetFraction > 0f) InsetDrawable(drawable, insetFraction) else drawable
        }
    val backgroundIcon = context.getDrawable(drawableRes)?.mutate() as? android.graphics.drawable.LayerDrawable
    if (backgroundIcon?.findDrawableByLayerId(app.sugarlicious.uishared.R.id.therapy_icon_background) != null) {
        imageTintList = null
        setImageDrawable(backgroundIcon)
        // ImageView clears a previous whole-image tint while attaching the drawable. Apply the
        // independent layer colors afterwards, otherwise both layers lose their chosen colors.
        backgroundIcon.findDrawableByLayerId(app.sugarlicious.uishared.R.id.therapy_icon_background).setTint(tintArgb ?: android.graphics.Color.WHITE)
        backgroundIcon.findDrawableByLayerId(app.sugarlicious.uishared.R.id.therapy_icon_symbol).setTint(android.graphics.Color.BLACK)
        return
    }
    val renderSpec = trendStyle?.renderSpec()
    val outlined = renderSpec?.outlineThicknessDp?.let { it > 0f } ?: shouldOutlineWearIcon(backgroundArgb, colored)
    if (outlined) {
        imageTintList = null
        val foreground =
            nativeIcon().apply {
                setTint(renderSpec?.fillColor ?: tintArgb ?: 0xFFFFFFFF.toInt())
            }
        val outline =
            nativeIcon().apply {
                setTint(renderSpec?.outlineColor ?: 0xB2000000.toInt())
            }
        setImageDrawable(
            SilhouetteOutlineDrawable(
                foreground = foreground,
                outline = outline,
                widthPx = context.resources.displayMetrics.density * (renderSpec?.outlineThicknessDp ?: 0.70f),
            ),
        )
    } else {
        setImageDrawable(nativeIcon())
        imageTintList = (renderSpec?.fillColor ?: tintArgb)?.let(ColorStateList::valueOf)
    }
}

private class SilhouetteOutlineDrawable(
    private val foreground: Drawable,
    private val outline: Drawable,
    private val widthPx: Float,
) : Drawable() {
    private val offsets =
        listOf(
            -1f to 0f,
            1f to 0f,
            0f to -1f,
            0f to 1f,
            -0.7f to -0.7f,
            0.7f to -0.7f,
            -0.7f to 0.7f,
            0.7f to 0.7f,
        )

    override fun onBoundsChange(bounds: Rect) {
        val inset = ceil(widthPx).toInt().coerceAtLeast(1)
        val content = Rect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        foreground.bounds = content
        outline.bounds = content
    }

    override fun draw(canvas: Canvas) {
        offsets.forEach { (x, y) ->
            canvas.withTranslation(x * widthPx, y * widthPx) {
                outline.draw(canvas)
            }
        }
        foreground.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        foreground.alpha = alpha
        outline.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        foreground.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in the Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = foreground.intrinsicWidth

    override fun getIntrinsicHeight(): Int = foreground.intrinsicHeight
}
