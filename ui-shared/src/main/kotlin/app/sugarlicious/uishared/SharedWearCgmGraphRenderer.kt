package app.sugarlicious.uishared

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.sugarlicious.model.CgmGraphPolicy
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphAxisLayoutSpec
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.RangeExcursion
import app.sugarlicious.model.RelativeGraphTimeAxis

/** Semantic colors shared by the Sugarlicious Wear and G7 Collector graph adapters. */
data class SharedWearCgmGraphPalette(
    val background: Int,
    val targetArea: Int,
    val highArea: Int,
    val lowArea: Int,
    val highLine: Int,
    val lowLine: Int,
    val dotHigh: Int,
    val dotInRange: Int,
    val dotLow: Int,
    val dotOutline: Int,
    val axisText: Int,
    val axisTick: Int,
    val nowLine: Int,
    val border: Int,
    val predictionIob: Int,
    val predictionCob: Int,
    val predictionUam: Int,
    val predictionZeroTemp: Int,
    val dotVeryHigh: Int = dotHigh,
    val dotVeryLow: Int = dotLow,
    val targetText: Int = axisText,
    val emptyText: Int = axisText,
)

data class SharedWearCgmGraphStyle(
    val dotRadiusDp: Float = 2.4f,
    val dotOutlineWidthDp: Float = 0.95f,
    val dotOutlineEnabled: Boolean = true,
    val historicalDotOutlineEnabled: Boolean = true,
    val currentDotOutlineEnabled: Boolean = true,
    val historicalDotOutlineWidthDp: Float = dotOutlineWidthDp,
    val currentDotOutlineWidthDp: Float = dotOutlineWidthDp,
    val cornerRadiusDp: Float = 20f,
    val borderEnabled: Boolean = true,
    val timeAxisEnabled: Boolean = true,
    val timeAxisVisible: Boolean = true,
    val targetTicksEnabled: Boolean = false,
    val targetLabelsOutsideRange: Boolean = true,
    val targetLabelsInsidePlot: Boolean = false,
    val rangeBackgroundEnabled: Boolean = true,
    val scaleLaneOpacityPercent: Int = 30,
    val axisTextSizeSp: Float = 8.5f,
    val plotToScaleGapDp: Float = 4f,
    val scaleLaneWidthDp: Float = 25f,
    val maximumScaleEnabled: Boolean = true,
    val currentDotContourAtLiveEdge: Boolean = false,
    val timeAxisOuterLabelInsetDp: Float = 3f,
    val timeHoursBold: Boolean = true,
    val timeAxisBottomInsetDp: Float? = null,
    val regularTypeface: android.graphics.Typeface? = null,
    val boldTypeface: android.graphics.Typeface? = null,
)

object DirectToWatchGraphDefaults {
    fun vigilStyle() = style().copy(currentDotOutlineWidthDp = .7f)

    fun style() =
        SharedWearCgmGraphStyle(
            dotOutlineWidthDp = 0.35f,
            historicalDotOutlineWidthDp = 0.35f,
            currentDotOutlineWidthDp = 0.35f,
            borderEnabled = false,
            timeAxisEnabled = true,
            targetTicksEnabled = false,
            targetLabelsOutsideRange = true,
            targetLabelsInsidePlot = true,
            axisTextSizeSp = 12f,
            plotToScaleGapDp = 0f,
            scaleLaneWidthDp = 31f,
            maximumScaleEnabled = false,
            currentDotContourAtLiveEdge = true,
            timeAxisOuterLabelInsetDp = 16f,
        )
}

/** One canonical glucose Y scale for Mobile, SugarWear, Wear, complications and previews. */
object WearCgmGraphScale {
    const val MAXIMUM_MG_DL = 400.0

    fun ratio(valueMgDl: Double): Double = GlucoseGraphScale.ratio(valueMgDl)
}

internal fun effectiveDotOutlineWidthDp(
    style: SharedWearCgmGraphStyle,
    isCurrent: Boolean,
): Float? =
    if (isCurrent) {
        style.currentDotOutlineWidthDp.coerceIn(0.25f, 3f)
    } else if (style.dotOutlineEnabled && style.historicalDotOutlineEnabled) {
        style.historicalDotOutlineWidthDp.coerceIn(0.25f, 3f)
    } else {
        null
    }

data class SharedWearCgmGraphInput(
    val history: List<GlucoseSample>,
    val predictions: List<GlucosePrediction> = emptyList(),
    val timeWindow: GraphTimeWindow,
    val nowEpochMs: Long,
    val thresholds: CgmThresholds,
    val palette: SharedWearCgmGraphPalette,
    val style: SharedWearCgmGraphStyle = SharedWearCgmGraphStyle(),
    val emptyLabel: String = "",
    val topLeftLabel: String = "",
    val outsideClipColor: Int = palette.background,
)

data class SharedWearCgmGraphMetrics(
    val visualBounds: RectF,
    val plot: RectF,
    val axisLeftPx: Float,
    val highY: Float,
    val lowY: Float,
    val liveX: Float,
    val sampleContourOffsetPx: Float = 0f,
) {
    fun xFor(
        window: GraphTimeWindow,
        timestampEpochMs: Long,
    ): Float = window.plotX(timestampEpochMs, plot.left, plot.width())

    fun sampleXFor(window: GraphTimeWindow, timestampEpochMs: Long): Float = xFor(window, timestampEpochMs) - sampleContourOffsetPx

    fun yFor(valueMgDl: Double): Float = plot.bottom - WearCgmGraphScale.ratio(valueMgDl).toFloat() * plot.height()
}

internal data class WearMaximumScaleLayout(
    val labelX: Float,
    val centerY: Float,
)

internal fun wearMaximumScaleLayout(
    metrics: SharedWearCgmGraphMetrics,
    widthPx: Int,
    density: Float,
    textWidth: Float,
    style: SharedWearCgmGraphStyle,
): WearMaximumScaleLayout {
    val spec = GraphAxisLayoutSpec.COMPACT
    val centerY = 7f * density
    val labelX =
        if (style.targetLabelsInsidePlot) {
            metrics.axisLeftPx + 2f * density
        } else {
            minOf(
                metrics.axisLeftPx + spec.plotToTickGapDp * density + 1.5f * density,
                widthPx - spec.outerEdgePaddingDp * density - textWidth,
            )
        }
    return WearMaximumScaleLayout(labelX, centerY)
}

/**
 * One Canvas renderer for both Wear applications. App-specific code only adapts data and colors;
 * geometry, range confirmation, axes, clipping policy and timestamp placement stay identical.
 */
object SharedWearCgmGraphRenderer {
    fun metrics(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        thresholds: CgmThresholds,
        style: SharedWearCgmGraphStyle = SharedWearCgmGraphStyle(),
    ): SharedWearCgmGraphMetrics {
        fun dp(value: Float) = value * density
        val dotInset =
            dp(
                style.dotRadiusDp.coerceAtLeast(0f) +
                    maxOf(
                        if (style.historicalDotOutlineEnabled) style.historicalDotOutlineWidthDp else 0f,
                        style.currentDotOutlineWidthDp,
                    ).coerceIn(0f, 3f),
            )
        val left = dotInset
        // Reserve one explicit label lane so data cannot run through the target values.
        // Keep the target-value lane only as wide as a three-digit value needs. The live edge
        // remains visibly separate from that (invisible) lane boundary.
        val axisLeft = widthPx - dp(style.scaleLaneWidthDp)
        val top = dotInset
        val visualBottom = heightPx - dp(if (style.timeAxisEnabled) 20f else 6f)
        val bottom = visualBottom - dotInset
        // Contour anchoring already subtracts the current radius when drawing the samples.
        // Reserving it here as well left a second gap between the live edge and the scale.
        val rightInset = if (style.currentDotContourAtLiveEdge) 0f else dotInset
        val plot = RectF(left, top, axisLeft - dp(style.plotToScaleGapDp.coerceAtLeast(0f)) - rightInset, bottom)

        fun y(value: Double): Float = plot.bottom - WearCgmGraphScale.ratio(value).toFloat() * plot.height()
        return SharedWearCgmGraphMetrics(
            visualBounds = RectF(0f, 0f, widthPx.toFloat(), visualBottom),
            plot = plot,
            axisLeftPx = axisLeft,
            highY = y(thresholds.highMgDl),
            lowY = y(thresholds.lowMgDl),
            liveX = plot.right,
            sampleContourOffsetPx = if (style.currentDotContourAtLiveEdge) dp(style.dotRadiusDp.coerceIn(1.5f, 6f) + (effectiveDotOutlineWidthDp(style, true) ?: 0f)) else 0f,
        )
    }

    fun render(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        scaledDensity: Float,
        input: SharedWearCgmGraphInput,
    ): SharedWearCgmGraphMetrics? {
        if (widthPx <= 0 || heightPx <= 0 || !input.thresholds.isValid) return null

        fun dp(value: Float) = value * density
        val palette = input.palette
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        val axisText =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.axisText
                textSize = input.style.axisTextSizeSp * scaledDensity
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
                input.style.boldTypeface?.let { typeface = it }
            }
        val emptyText =
            Paint(axisText).apply {
                color = palette.emptyText
                textAlign = Paint.Align.CENTER
                textSize = 10f * scaledDensity
            }
        val metrics = metrics(widthPx, heightPx, density, input.thresholds, input.style)
        val plot = metrics.plot
        val visual = metrics.visualBounds
        if (plot.width() <= 0f || plot.height() <= 0f) return null

        // RGB_565 tile resources have no alpha channel. Matte only the pixels outside the graph
        // contour with the surrounding surface; all pixels inside the contour belong to the graph.
        canvas.drawColor(input.outsideClipColor)
        val cornerRadius = dp(input.style.cornerRadiusDp).coerceAtLeast(0f)
        val canvasState = canvas.save()
        canvas.clipPath(
            Path().apply {
                addRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
            },
        )

        // Paint the complete tile first. Previously the configured background only covered the
        // plot bands, leaving the time-axis strip and parts of the scale lane transparent.
        fill.color = palette.background
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), fill)

        val history =
            input.history
                .asSequence()
                .filter {
                    it.quality == CgmQuality.VALID &&
                        it.valueMgDl.isFinite() &&
                        it.valueMgDl in 20.0..1_000.0 &&
                        it.measuredAtEpochMs in input.timeWindow.startEpochMs..input.timeWindow.endEpochMs
                }.sortedBy(GlucoseSample::measuredAtEpochMs)
                .distinctBy { listOf(it.sensorId, it.sessionId, it.sequenceNumber, it.measuredAtEpochMs, it.source) }
                .toList()
        val predictions =
            input.predictions
                .map { series ->
                    series.copy(
                        samples =
                            series.samples.filter {
                                it.measuredAtEpochMs in input.timeWindow.startEpochMs..input.timeWindow.endEpochMs
                            },
                    )
                }.filter { it.samples.isNotEmpty() }

        fun laneColor(color: Int): Int {
            val alpha = (color ushr 24) * input.style.scaleLaneOpacityPercent.coerceIn(0, 100) / 100
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }

        fun drawSplitArea(
            top: Float,
            bottom: Float,
            color: Int,
        ) {
            fill.color = color
            canvas.drawRect(visual.left, top, metrics.axisLeftPx, bottom, fill)
            fill.color = laneColor(color)
            canvas.drawRect(metrics.axisLeftPx, top, visual.right, bottom, fill)
        }
        fill.color = palette.background
        canvas.drawRect(visual.left, visual.top, metrics.axisLeftPx, visual.bottom, fill)
        fill.color = laneColor(palette.background)
        canvas.drawRect(metrics.axisLeftPx, visual.top, visual.right, metrics.highY, fill)
        canvas.drawRect(metrics.axisLeftPx, metrics.lowY, visual.right, visual.bottom, fill)
        drawSplitArea(metrics.highY, metrics.lowY, palette.targetArea)
        when (CgmGraphPolicy.rangeExcursion(history, input.thresholds).takeIf { input.style.rangeBackgroundEnabled }) {
            RangeExcursion.HIGH -> {
                drawSplitArea(visual.top, metrics.highY, palette.highArea)
            }
            RangeExcursion.LOW -> {
                drawSplitArea(metrics.lowY, visual.bottom, palette.lowArea)
            }
            null -> Unit
        }

        line.pathEffect = null
        line.strokeWidth = dp(0.8f)
        line.strokeCap = Paint.Cap.BUTT
        line.color = palette.highLine
        canvas.drawLine(visual.left, metrics.highY, metrics.axisLeftPx, metrics.highY, line)
        line.color = laneColor(palette.highLine)
        canvas.drawLine(metrics.axisLeftPx, metrics.highY, visual.right, metrics.highY, line)
        line.color = palette.lowLine
        canvas.drawLine(visual.left, metrics.lowY, metrics.axisLeftPx, metrics.lowY, line)
        line.color = laneColor(palette.lowLine)
        canvas.drawLine(metrics.axisLeftPx, metrics.lowY, visual.right, metrics.lowY, line)
        line.strokeCap = Paint.Cap.ROUND

        val targetText = Paint(axisText).apply { color = palette.targetText }
        drawTargetLabel(
            canvas,
            input.thresholds.highMgDl,
            metrics.highY,
            true,
            metrics.axisLeftPx,
            widthPx,
            density,
            targetText,
            line,
            palette.axisTick,
            input.style,
        )
        drawTargetLabel(
            canvas,
            input.thresholds.lowMgDl,
            metrics.lowY,
            false,
            metrics.axisLeftPx,
            widthPx,
            density,
            targetText,
            line,
            palette.axisTick,
            input.style,
        )

        val liveX = metrics.xFor(input.timeWindow, input.timeWindow.liveEdgeEpochMs)
        val radius = input.style.dotRadiusDp.coerceIn(1.5f, 6f) * density
        history.forEachIndexed { index, sample ->
            val isCurrent = index == history.lastIndex
            // "Current" controls styling only. Every point, including the newest one, belongs to
            // its real sensor event time; it must drift left as the wall-clock viewport advances.
            val x = metrics.sampleXFor(input.timeWindow, sample.measuredAtEpochMs)
            val y = metrics.yFor(sample.valueMgDl)
            val outline = effectiveDotOutlineWidthDp(input.style, isCurrent)?.times(density)
            if (outline != null) {
                fill.color = palette.dotOutline
                canvas.drawCircle(x, y, radius + outline, fill)
            }
            fill.color =
                when (input.thresholds.classify(sample.valueMgDl)) {
                    CgmRangeClass.VERY_LOW -> palette.dotVeryLow
                    CgmRangeClass.LOW -> palette.dotLow
                    CgmRangeClass.HIGH -> palette.dotHigh
                    CgmRangeClass.VERY_HIGH -> palette.dotVeryHigh
                    else -> palette.dotInRange
                }
            canvas.drawCircle(x, y, radius, fill)
        }

        predictions.forEach { series ->
            fill.color =
                when (series.kind) {
                    PredictionKind.IOB -> palette.predictionIob
                    PredictionKind.COB, PredictionKind.ACOB -> palette.predictionCob
                    PredictionKind.UAM -> palette.predictionUam
                    PredictionKind.ZERO_TEMP -> palette.predictionZeroTemp
                }
            series.samples.forEach { sample ->
                canvas.drawCircle(
                    metrics.xFor(input.timeWindow, sample.measuredAtEpochMs),
                    metrics.yFor(sample.valueMgDl),
                    dp(1.8f),
                    fill,
                )
            }
        }

        if (input.style.maximumScaleEnabled) drawMaximumScale(canvas, input, metrics, widthPx, dp(1f), axisText)
        if (input.topLeftLabel.isNotBlank()) {
            val area =
                if (7f * density in metrics.highY..metrics.lowY) {
                    palette.targetArea
                } else if (input.style.rangeBackgroundEnabled && CgmGraphPolicy.rangeExcursion(history, input.thresholds) == RangeExcursion.HIGH) {
                    palette.highArea
                } else {
                    android.graphics.Color.TRANSPARENT
                }
            axisText.color = graphTextContrast(palette.background, area)
            axisText.textAlign = Paint.Align.LEFT
            canvas.drawText(input.topLeftLabel, maxOf(3f * density, input.style.cornerRadiusDp * density * 0.7f), 7f * density - (axisText.ascent() + axisText.descent()) / 2f, axisText)
        }
        if (input.style.timeAxisEnabled && input.style.timeAxisVisible) drawTimeAxis(canvas, input, metrics, widthPx, heightPx, dp(1f), axisText, line)
        if (history.isEmpty() && predictions.isEmpty() && input.emptyLabel.isNotBlank()) {
            emptyText.color = palette.emptyText
            canvas.drawText(input.emptyLabel, plot.centerX(), plot.centerY() - (emptyText.ascent() + emptyText.descent()) / 2f, emptyText)
        }

        if (input.style.borderEnabled) {
            val borderInset = dp(0.5f)
            line.color = palette.border
            line.strokeWidth = dp(1f)
            line.pathEffect = null
            canvas.drawRoundRect(
                borderInset,
                borderInset,
                widthPx - borderInset,
                heightPx - borderInset,
                (dp(input.style.cornerRadiusDp) - borderInset).coerceAtLeast(0f),
                (dp(input.style.cornerRadiusDp) - borderInset).coerceAtLeast(0f),
                line,
            )
        }
        canvas.restoreToCount(canvasState)
        return metrics.copy(liveX = liveX)
    }

    private fun drawTargetLabel(
        canvas: Canvas,
        value: Double,
        y: Float,
        isHigh: Boolean,
        plotRight: Float,
        widthPx: Int,
        density: Float,
        text: Paint,
        line: Paint,
        tickColor: Int,
        style: SharedWearCgmGraphStyle,
    ) {
        val spec = GraphAxisLayoutSpec.COMPACT
        val gap = spec.plotToTickGapDp * density
        val tickLength = spec.tickLengthDp * density
        val labelGap = spec.tickToLabelGapDp * density
        line.color = tickColor
        line.strokeWidth = 0.8f * density
        line.pathEffect = null
        val tickStart = plotRight + gap
        val tickEnd = if (style.targetTicksEnabled) tickStart + tickLength else tickStart
        if (style.targetTicksEnabled) canvas.drawLine(tickStart, y, tickEnd, y, line)
        text.textAlign = Paint.Align.LEFT
        val baseline =
            if (style.targetLabelsOutsideRange) {
                if (isHigh) y - 2f * density - text.descent() else y + 2f * density - text.ascent()
            } else {
                y - (text.ascent() + text.descent()) / 2f
            }
        val labelX =
            if (style.targetLabelsInsidePlot) {
                plotRight + 2f * density
            } else {
                minOf(
                    tickEnd + if (style.targetTicksEnabled) labelGap else 1.5f * density,
                    widthPx - spec.outerEdgePaddingDp * density - text.measureText(value.toInt().toString()),
                )
            }
        canvas.drawText(value.toInt().toString(), labelX, baseline, text)
    }

    private fun drawTimeAxis(
        canvas: Canvas,
        input: SharedWearCgmGraphInput,
        metrics: SharedWearCgmGraphMetrics,
        widthPx: Int,
        heightPx: Int,
        oneDp: Float,
        text: Paint,
        line: Paint,
    ) {
        text.color = input.palette.axisText
        line.color = input.palette.axisTick
        line.strokeWidth = 0.8f * oneDp
        line.pathEffect = null
        RelativeGraphTimeAxis
            .ticks(
                input.timeWindow.startEpochMs,
                input.timeWindow.endEpochMs,
                input.nowEpochMs,
            ).forEach { tick ->
                val x = metrics.xFor(input.timeWindow, tick.timestampEpochMs)
                line.color = if (tick.hoursBack == 0) input.palette.nowLine else input.palette.axisTick
                text.textAlign =
                    when {
                        tick.timestampEpochMs <= input.timeWindow.startEpochMs + 30_000L -> Paint.Align.LEFT
                        tick.hoursBack == 0 -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                val labelX =
                    when (text.textAlign) {
                        Paint.Align.LEFT -> maxOf(input.style.timeAxisOuterLabelInsetDp * oneDp, x)
                        Paint.Align.RIGHT -> minOf(widthPx - 3f * oneDp, x)
                        else -> x
                    }
                val spec = GraphAxisLayoutSpec.COMPACT
                val tickStart = metrics.plot.bottom + spec.plotToTickGapDp * oneDp
                val tickEnd = tickStart + spec.tickLengthDp * oneDp
                // The live position is marked like every other axis value: a short solid tick at the
                // exact X of the former full-height Now line. Label alignment must not move the tick.
                val bold = tick.hoursBack == 0 || input.style.timeHoursBold
                SystemTypography.apply(text, bold)
                (if (bold) input.style.boldTypeface else input.style.regularTypeface)?.let { text.typeface = it }
                val baseline = tickEnd + spec.tickToLabelGapDp * oneDp - text.ascent()
                val bottomInset = input.style.timeAxisBottomInsetDp
                val labelBaseline =
                    if (bottomInset == null) {
                        minOf(heightPx - spec.outerEdgePaddingDp * oneDp, baseline)
                    } else {
                        heightPx - bottomInset * oneDp - text.descent()
                    }
                val shift = if (bottomInset == null) 0f else labelBaseline - baseline
                canvas.drawLine(x, tickStart + shift, x, tickEnd + shift, line)
                canvas.drawText(tick.label, labelX, labelBaseline, text)
            }
    }

    private fun drawMaximumScale(
        canvas: Canvas,
        input: SharedWearCgmGraphInput,
        metrics: SharedWearCgmGraphMetrics,
        widthPx: Int,
        oneDp: Float,
        text: Paint,
    ) {
        val label = WearCgmGraphScale.MAXIMUM_MG_DL.toInt().toString()
        val layout = wearMaximumScaleLayout(metrics, widthPx, oneDp, text.measureText(label), input.style)
        val baseline = layout.centerY - (text.ascent() + text.descent()) / 2f
        text.color = input.palette.axisText
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(label, layout.labelX, baseline, text)
    }
}
