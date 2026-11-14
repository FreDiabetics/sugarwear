package app.sugarlicious.uishared

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.sugarlicious.model.TherapyRingGeometry
import app.sugarlicious.model.TherapyRingStyle
import app.sugarlicious.model.progressArcSegments
import kotlin.math.roundToInt

/** Canvas counterpart of the Mobile ring, reused by Widgets, Wear Views and Wear Tiles. */
class TherapyRingRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        stroke: Float,
        progress: Float?,
        accent: Int,
        geometry: TherapyRingGeometry = TherapyRingGeometry(),
        trackColor: Int = (accent and 0x00FFFFFF) or ((Color.alpha(accent) * TherapyRingStyle.TRACK_ALPHA).roundToInt() shl 24),
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f || !stroke.isFinite() || stroke <= 0f) return
        val arcs = progressArcSegments(progress, bounds.width() + stroke, stroke, geometry.sweepDegrees, TherapyRingStyle.CAP_CLEARANCE_STROKE_RATIO)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        if (arcs.trackSweep > 0f) {
            paint.color = trackColor
            canvas.drawArc(bounds, geometry.composeStartDegrees + arcs.trackStartOffset, arcs.trackSweep, false, paint)
        }
        if (arcs.fillSweep > 0f) {
            paint.color = accent
            canvas.drawArc(bounds, geometry.composeStartDegrees, arcs.fillSweep, false, paint)
        }
    }
}
