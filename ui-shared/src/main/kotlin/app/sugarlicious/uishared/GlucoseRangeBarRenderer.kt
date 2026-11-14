package app.sugarlicious.uishared

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import app.sugarlicious.model.GlucoseBarRange
import app.sugarlicious.model.GlucoseBarState

/** Matches the Mobile overview wave colors, including its translucent inactive track. */
data class GlucoseRangeBarPalette(
    val low: Int = 0xFFF73D45.toInt(),
    val target: Int = 0xFF22EB60.toInt(),
    val high: Int = 0xFFFCC93B.toInt(),
    val underlay: Int = 0x4DB0B0B0,
) {
    fun colorFor(range: GlucoseBarRange): Int =
        when (range) {
            GlucoseBarRange.LOW -> low
            GlucoseBarRange.TARGET -> target
            GlucoseBarRange.HIGH -> high
        }
}

/**
 * Android Canvas equivalent of Mobile's GlucoseRangeBar: round track, two
 * round-ended colored sections and a clear gap exactly at the CGM position.
 * The owner provides a canonical GlucoseBarState; invalid/stale data is never
 * converted to an apparent current position.
 */
class GlucoseRangeBarRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bounds = RectF()

    fun draw(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        state: GlucoseBarState?,
        palette: GlucoseRangeBarPalette = GlucoseRangeBarPalette(),
        gapPx: Float = 7f,
    ) {
        if (widthPx <= 0f || heightPx <= 0f) return
        val radius = heightPx / 2f
        paint.color = palette.underlay
        bounds.set(0f, 0f, widthPx, heightPx)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (state == null) return

        val marker = widthPx * state.position.coerceIn(0f, 1f)
        val gap = gapPx.coerceIn(0f, widthPx)
        paint.color = palette.colorFor(state.range)
        drawSegment(canvas, 0f, (marker - gap / 2f).coerceAtLeast(0f), heightPx, radius)
        drawSegment(canvas, (marker + gap / 2f).coerceAtMost(widthPx), widthPx, heightPx, radius)
    }

    private fun drawSegment(canvas: Canvas, left: Float, right: Float, heightPx: Float, radius: Float) {
        if (right <= left) return
        val saveCount = canvas.save()
        canvas.clipRect(left, 0f, right, heightPx)
        bounds.set(left, 0f, right, heightPx)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        canvas.restoreToCount(saveCount)
    }
}
