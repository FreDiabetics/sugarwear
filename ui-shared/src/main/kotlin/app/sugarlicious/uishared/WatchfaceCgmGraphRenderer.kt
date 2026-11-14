package app.sugarlicious.uishared

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withSave
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphTimeWindow
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Transparent, monochrome complication artwork. The receiving watchface owns color and framing. */
object WatchfaceCgmGraphRenderer {
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        history: List<GlucoseSample>,
        window: GraphTimeWindow,
        thresholds: CgmThresholds,
        color: Int = Color.WHITE,
        targetLines: Boolean = true,
        semicircle: Boolean = false,
        showThresholdLabels: Boolean = semicircle,
    ) {
        if (width <= 0 || height <= 0 || !thresholds.isValid) return
        val displayWindow = GraphTimeWindow.live(window.endEpochMs, 5_400_000L)
        val samples =
            history
                .filter {
                    it.quality == CgmQuality.VALID &&
                        it.valueMgDl.isFinite() &&
                        it.valueMgDl in 20.0..1_000.0 &&
                        it.measuredAtEpochMs in displayWindow.startEpochMs..displayWindow.endEpochMs
                }.sortedBy(GlucoseSample::measuredAtEpochMs)
                .distinctBy { listOf(it.sensorId, it.sessionId, it.source, it.measuredAtEpochMs) }
        if (samples.isEmpty()) return

        // Include the target interval and round outward: no per-pixel auto-zoom or fabricated smoothing.
        val minimum = minOf(thresholds.lowMgDl, samples.minOf(GlucoseSample::valueMgDl))
        val maximum = maxOf(thresholds.highMgDl, samples.maxOf(GlucoseSample::valueMgDl))
        val margin = maxOf(20.0, (maximum - minimum) * .15)
        val lower = floor((minimum - margin) / 20) * 20
        val upper = ceil((maximum + margin) / 20) * 20
        val unit = minOf(width / 200f, height / 70f)
        // Keep genuine samples inside the semicircle's inscribed plot rectangle. Clipping an
        // ordinary edge-to-edge rectangular plot would silently hide the oldest/newest dots.
        // Both axes remain linear; never bend equal glucose levels to follow the curved mask.
        val top = if (semicircle) height * .36f else 0f
        val inset =
            if (semicircle) {
                val radius = width / 2f
                val safeTop = (top - 4.4f * unit).coerceAtLeast(0f)
                maxOf(width * .16f, radius - sqrt((radius * radius - (radius - safeTop) * (radius - safeTop)).coerceAtLeast(0f)) + 4.4f * unit)
            } else {
                4.5f * unit
            }
        val left = inset
        val right = width - inset
        val bottom = if (semicircle) height * .92f else height.toFloat()
        if (right <= left || bottom <= top) return

        fun x(sample: GlucoseSample) = displayWindow.plotX(sample.measuredAtEpochMs, left, right - left)

        fun y(value: Double) = bottom - ((value - lower) / (upper - lower)).toFloat() * (bottom - top)

        fun toneColor(level: Int): Int {
            val tone = level / 255f
            return Color.argb(Color.alpha(color), (Color.red(color) * tone).roundToInt(), (Color.green(color) * tone).roundToInt(), (Color.blue(color) * tone).roundToInt())
        }
        if (targetLines) {
            val guide =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 1.4f * unit
                }
            guide.color = toneColor(184)
            canvas.drawLine(0f, y(thresholds.highMgDl), width.toFloat(), y(thresholds.highMgDl), guide)
            guide.color = toneColor(120)
            canvas.drawLine(0f, y(thresholds.lowMgDl), width.toFloat(), y(thresholds.lowMgDl), guide)
            if (semicircle && showThresholdLabels) {
                val label =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 12f * unit
                        textAlign = Paint.Align.RIGHT
                        SystemTypography.apply(this, emphasized = true)
                    }
                listOf(thresholds.highMgDl to 184, thresholds.lowMgDl to 120).forEach { (value, tone) ->
                    label.color = toneColor(tone)
                    val baseline =
                        if (value == thresholds.lowMgDl) {
                            minOf(y(value) + 2f * unit - label.fontMetrics.ascent, height - label.fontMetrics.descent - unit)
                        } else {
                            y(value) - 2f * unit
                        }
                    val outerY = (baseline + label.fontMetrics.ascent).coerceAtLeast(0f)
                    val edge =
                        if (semicircle) {
                            val radius = width / 2f
                            radius + sqrt((radius * radius - (height - outerY) * (height - outerY)).coerceAtLeast(0f))
                        } else {
                            width.toFloat()
                        }
                    canvas.drawText(value.roundToInt().toString(), edge - 2f * unit, baseline, label)
                }
            }
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        fun pointColor(value: Double): Int {
            val tone =
                when {
                    value < thresholds.lowMgDl -> 120
                    value > thresholds.highMgDl -> 184
                    else -> 255
                }
            return toneColor(tone)
        }
        val current = samples.last()
        canvas.withSave {
            canvas.clipOutPath(Path().apply { addCircle(x(current), y(current.valueMgDl), 4.4f * unit, Path.Direction.CW) })
            samples.forEachIndexed { index, sample ->
                if (index != samples.lastIndex) {
                    dot.color = pointColor(sample.valueMgDl)
                    canvas.drawCircle(x(sample), y(sample.valueMgDl), 3.1f * unit, dot)
                }
            }
        }
        // Orbit's opaque center hides guide labels beneath the newest sample; preserve its ring.
        if (semicircle) {
            dot.color = Color.BLACK
            canvas.drawCircle(x(current), y(current.valueMgDl), 4.4f * unit, dot)
        }
        // The ring marks the newest real sample, never the wall-clock edge.
        val stroke =
            Paint(dot).apply {
                this.color = pointColor(current.valueMgDl)
                style = Paint.Style.STROKE
                strokeWidth = 1.25f * unit
            }
        canvas.drawCircle(x(current), y(current.valueMgDl), 4.4f * unit, stroke)
    }
}
