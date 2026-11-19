package app.sugarlicious.complications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.MonochromaticImage
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.uishared.TrendDrawableResources
import kotlin.math.roundToInt

/** Renders the exact Sugarlicious trend vector used by the phone overview. */
internal object TrendComplicationIcon {
    @Suppress("UNUSED_PARAMETER") // Retain provider call compatibility; layout belongs exclusively to the host face.
    fun monochromaticImage(
        context: Context,
        trend: Trend,
        sizePx: Int = 72,
        catalogId: Int? = null,
        stylePreferencesName: String = "watch_display",
    ): MonochromaticImage? {
        val hostBitmap = watchFaceBitmap(context, trend, sizePx) ?: return null
        return MonochromaticImage.Builder(Icon.createWithBitmap(hostBitmap)).build()
    }

    /** Every direction shares one host canvas and one ink height, including the wide double arrows. */
    internal fun watchFaceBitmap(context: Context, trend: Trend, sizePx: Int): Bitmap? {
        val glyph = render(context, trend, sizePx) ?: return null
        return watchFaceCanvas(glyph, sizePx)
    }

    /** Plain glucose ranged values share the same host geometry as glucose + trend. */
    internal fun glucoseValueImage(context: Context, sizePx: Int = 72): MonochromaticImage? {
        val drawable = context.getDrawable(R.drawable.ic_complication_glucose)?.mutate() ?: return null
        drawable.setTint(Color.WHITE)
        val glyph = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(glyph))
        return MonochromaticImage.Builder(Icon.createWithBitmap(watchFaceCanvas(glyph, sizePx))).build()
    }

    private fun watchFaceCanvas(glyph: Bitmap, sizePx: Int): Bitmap {
        val widest = requireNotNull(TrendVisuals.spec(Trend.DOUBLE_UP)).aspectRatio
        val side = (sizePx * widest).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(glyph, (side - glyph.width) / 2f, (side - glyph.height) / 2f, null)
        }
    }

    /**
     * Keeps a stable canvas so every direction retains identical geometry. The percentage is
     * calibrated directly: 100% equals [referenceHeightPx], 70% is 0.7x and 200% is 2x.
     * The stable 2x canvas prevents clipping at the maximum while making every slider step visible.
     */
    internal fun renderScaled(
        context: Context,
        trend: Trend,
        referenceHeightPx: Int,
        scalePercent: Int,
        offsetXPercent: Int = 0,
        offsetYPercent: Int = 0,
        style: TrendArrowStyle = TrendArrowStyle.defaults(AppearanceMode.DARK, Color.WHITE),
    ): Bitmap? {
        val spec = TrendVisuals.spec(trend) ?: return null
        val canvasHeight = referenceHeightPx * 2
        val canvasWidth = (canvasHeight * spec.aspectRatio).roundToInt().coerceAtLeast(1)
        val targetHeight = (canvasHeight * glyphFillFraction(scalePercent)).roundToInt().coerceAtLeast(1)
        val targetWidth = (targetHeight * spec.aspectRatio).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val drawable = context.getDrawable(TrendDrawableResources.forAsset(spec.asset))?.mutate() ?: return null
        val render = style.copy(sizePercent = scalePercent).renderSpec()
        drawable.setTint(render.fillColor)
        val left = ((canvasWidth - targetWidth) / 2f + referenceHeightPx * offsetXPercent.coerceIn(-50, 50) / 100f).roundToInt()
        val top = ((canvasHeight - targetHeight) / 2f + referenceHeightPx * offsetYPercent.coerceIn(-50, 50) / 100f).roundToInt()
        drawable.setBounds(left, top, left + targetWidth, top + targetHeight)
        val canvas = Canvas(bitmap)
        if (render.outlineThicknessDp > 0f) {
            val outline = context.getDrawable(TrendDrawableResources.forAsset(spec.asset))?.mutate()
            outline?.setTint(render.outlineColor)
            val px = render.outlineThicknessDp * context.resources.displayMetrics.density
            listOf(-px to 0f, px to 0f, 0f to -px, 0f to px).forEach { (x, y) ->
                val save = canvas.save()
                canvas.translate(x, y)
                outline?.bounds = drawable.bounds
                outline?.draw(canvas)
                canvas.restoreToCount(save)
            }
        }
        drawable.draw(canvas)
        return bitmap
    }

    internal fun glyphFillFraction(scalePercent: Int): Float = GlucoseTrendSizing.scaleFactor(scalePercent) / 2f

    /** Removes the stable host canvas padding when a glyph participates in a text layout. */
    internal fun cropTransparentPadding(bitmap: Bitmap): Bitmap {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < left || bottom < top) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        }
    }

    fun render(
        context: Context,
        trend: Trend,
        sizePx: Int,
    ): Bitmap? {
        val spec = TrendVisuals.spec(trend) ?: return null
        val drawable = context.getDrawable(TrendDrawableResources.forAsset(spec.asset))?.mutate() ?: return null
        drawable.setTint(Color.WHITE)
        val width = (sizePx * spec.aspectRatio).roundToInt().coerceAtLeast(1)
        val base = Bitmap.createBitmap(width, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(base)
        drawable.setBounds(0, 0, width, sizePx)
        drawable.draw(canvas)
        return base
    }
}
