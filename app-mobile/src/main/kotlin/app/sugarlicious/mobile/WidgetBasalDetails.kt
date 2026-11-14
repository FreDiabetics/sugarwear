package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import app.sugarlicious.model.BasalDetailPresentation
import app.sugarlicious.uishared.SystemTypography
import kotlin.math.roundToInt
import app.sugarlicious.uishared.R as SharedR

internal fun drawTherapyWidgetIcon(context: Context, canvas: Canvas, resource: Int, left: Float, top: Float, size: Float, color: Int) {
    ContextCompat.getDrawable(context, resource)?.mutate()?.also { icon ->
        if (icon is LayerDrawable) {
            icon.findDrawableByLayerId(SharedR.id.therapy_icon_background).setTint(color)
            icon.findDrawableByLayerId(SharedR.id.therapy_icon_symbol).setTint(Color.BLACK)
        } else {
            icon.setTint(color)
        }
        icon.setBounds(left.roundToInt(), top.roundToInt(), (left + size).roundToInt(), (top + size).roundToInt())
        icon.draw(canvas)
    }
}

/** Draw only within the spare column beside Basal; never overlap the chart or another ring. */
internal fun drawWidgetBasalDetails(context: Context, canvas: Canvas, details: BasalDetailPresentation, bounds: RectF, tempIcon: Int, palette: WidgetPalette, density: Float, bold: Boolean) {
    if (bounds.width() <= 0f || bounds.height() <= 0f) return
    canvas.withClip(bounds) {
        val iconSize = minOf(17f * density, bounds.width() * .26f, bounds.height() * .3f)
        val textLeft = bounds.left + iconSize + 3f * density
        val available = (bounds.right - textLeft).coerceAtLeast(1f)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.argb(WidgetColorRole.BASAL_VALUE)
                textSize = minOf(10f * density * context.resources.configuration.fontScale, bounds.height() * .2f)
                SystemTypography.apply(this, bold)
            }
        listOf(details.base to SharedR.drawable.ic_basal_bckgrnd, details.temporary to tempIcon).forEachIndexed { index, (text, icon) ->
            val centerY = bounds.top + bounds.height() * (if (index == 0) .25f else .75f)
            drawTherapyWidgetIcon(context, canvas, icon, bounds.left, centerY - iconSize / 2f, iconSize, palette.argb(WidgetColorRole.BASAL_ICON))
            paint.textSize = minOf(10f * density * context.resources.configuration.fontScale, bounds.height() * .2f)
            val parts = if (paint.measureText(text) > available && text.contains(' ')) listOf(text.substringBefore(' '), text.substringAfter(' ')) else listOf(text)
            val longest = parts.maxOf(paint::measureText)
            if (longest > available) paint.textSize *= available / longest
            val lineHeight = paint.fontSpacing
            val firstBaseline = centerY - (parts.size - 1) * lineHeight / 2f - (paint.ascent() + paint.descent()) / 2f
            parts.forEachIndexed { line, value -> canvas.drawText(value, textLeft, firstBaseline + line * lineHeight, paint) }
        }
    }
}
