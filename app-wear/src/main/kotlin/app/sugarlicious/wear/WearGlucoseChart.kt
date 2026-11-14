package app.sugarlicious.wear

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.protocol.WatchGraphStyle
import app.sugarlicious.storage.PredictionDisplayTimeline
import app.sugarlicious.uishared.SharedWearCgmGraphInput
import app.sugarlicious.uishared.SharedWearCgmGraphPalette
import app.sugarlicious.uishared.SharedWearCgmGraphRenderer
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import kotlin.math.max

@SuppressLint("DrawAllocation")
class WearGlucoseChart
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private val density = resources.displayMetrics.density
        private var state: TherapyDisplayState? = null
        private var durationHours = 3
        private var showPredictions = false
        private var colors = WatchGraphColors()
        private var graphStyle = WatchGraphStyle()
        private var thresholds = CgmThresholds.DEFAULT
        private var stateSignature: List<Any?>? = null

        init {
            outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        if (view.width > 0 && view.height > 0) {
                            outline.setRoundRect(0, 0, view.width, view.height, TILE_RADIUS_DP * density)
                        }
                    }
                }
            clipToOutline = true
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            invalidateOutline()
        }

        fun bind(
            newState: TherapyDisplayState?,
            graphHours: Int,
            showPredictions: Boolean,
            colors: WatchGraphColors,
            style: WatchGraphStyle,
            thresholds: CgmThresholds = CgmThresholds.DEFAULT,
        ) {
            val resolvedDuration = graphHours.takeIf { it in WearDisplayPreferences.allowedGraphHours } ?: 3
            val signature = wearChartStateSignature(newState)
            if (
                stateSignature == signature &&
                durationHours == resolvedDuration &&
                this.showPredictions == showPredictions &&
                this.colors == colors &&
                graphStyle == style &&
                this.thresholds == thresholds
            ) {
                return
            }
            state = newState
            stateSignature = signature
            durationHours = resolvedDuration
            this.showPredictions = showPredictions
            this.colors = colors
            graphStyle = style
            this.thresholds = thresholds
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = System.currentTimeMillis()
            val predictions =
                if (showPredictions) {
                    PredictionDisplayTimeline.anchor(state?.glucosePredictions.orEmpty(), now)
                } else {
                    emptyList()
                }
            val predictionEnd = predictions.flatMap { it.samples }.maxOfOrNull { it.measuredAtEpochMs } ?: now
            val window = wearChartTimeWindow(now, predictionEnd, durationHours, showPredictions)
            val history =
                buildList {
                    addAll(state?.glucoseHistory.orEmpty())
                    state?.glucose?.let { glucose ->
                        add(
                            GlucoseSample(
                                valueMgDl = glucose.valueMgDl,
                                measuredAtEpochMs = glucose.measuredAtEpochMs,
                                source = state?.source ?: glucose.source,
                                sensorId = glucose.sensorId,
                                sessionId = glucose.sessionId,
                                sequenceNumber = glucose.sequenceNumber,
                                receivedAtEpochMs = glucose.receivedAtEpochMs,
                                quality = glucose.quality,
                            ),
                        )
                    }
                }.filter { it.quality == CgmQuality.VALID }

            SharedWearCgmGraphRenderer.render(
                canvas,
                width,
                height,
                density,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics),
                SharedWearCgmGraphInput(
                    history = history,
                    predictions = predictions,
                    timeWindow = window,
                    nowEpochMs = now,
                    thresholds = thresholds,
                    palette = colors.toSharedPalette(),
                    style =
                        SharedWearCgmGraphStyle(
                            dotRadiusDp = graphStyle.cgmDotRadiusDp,
                            dotOutlineWidthDp = graphStyle.cgmDotOutlineWidthDp,
                            historicalDotOutlineWidthDp = graphStyle.cgmHistoricalDotOutlineWidthDp,
                            currentDotOutlineWidthDp = graphStyle.cgmCurrentDotOutlineWidthDp,
                            dotOutlineEnabled = graphStyle.cgmDotOutlineEnabled,
                            historicalDotOutlineEnabled = graphStyle.cgmHistoricalDotOutlineEnabled,
                            currentDotOutlineEnabled = true,
                            cornerRadiusDp = TILE_RADIUS_DP,
                            borderEnabled = graphStyle.borderEnabled,
                            scaleLaneOpacityPercent = graphStyle.scaleLaneOpacityPercent,
                        ),
                ),
            )
        }

        private fun WatchGraphColors.toSharedPalette() =
            SharedWearCgmGraphPalette(
                background = graphBackground,
                targetArea = rangeInRange,
                highArea = rangeHigh,
                lowArea = rangeLow,
                highLine = highLine,
                lowLine = lowLine,
                dotHigh = cgmHigh,
                dotInRange = cgmInRange,
                dotLow = cgmLow,
                dotOutline = outline,
                axisText = axisLabel,
                axisTick = axisTick,
                nowLine = nowLine,
                border = border,
                predictionIob = predictionIob,
                predictionCob = predictionCob,
                predictionUam = predictionUam,
                predictionZeroTemp = predictionZeroTemp,
            )

        private companion object {
            const val TILE_RADIUS_DP = 20f
        }
    }

internal fun wearChartStateSignature(state: TherapyDisplayState?): List<Any?>? =
    state?.let {
        listOf(it.glucose, it.glucoseHistory, it.glucosePredictions, it.target?.lowMgDl, it.target?.highMgDl)
    }

internal fun wearChartTimeWindow(
    timelineNow: Long,
    predictionEnd: Long,
    durationHours: Int,
    showPredictions: Boolean,
): GraphTimeWindow {
    val historyDuration = durationHours.coerceAtLeast(1).toLong() * WEAR_CHART_HOUR_MS
    val end = if (showPredictions) max(timelineNow, predictionEnd) else timelineNow
    return GraphTimeWindow.endingAt(
        viewportEndEpochMs = end.coerceAtLeast(timelineNow),
        historyDurationMs = historyDuration,
        futureDurationMs = end.coerceAtLeast(timelineNow) - timelineNow,
    )
}

private const val WEAR_CHART_HOUR_MS = 60L * 60_000L
