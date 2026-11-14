package app.sugarlicious.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.core.content.edit
import androidx.core.graphics.withClip
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.derivedTargetValueArgb
import app.sugarlicious.model.CgmGraphPolicy
import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.CgmGraphYScale
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphAxis
import app.sugarlicious.model.GraphAxisScale
import app.sugarlicious.model.GraphBounds
import app.sugarlicious.model.GraphScaleSession
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.RangeExcursion
import app.sugarlicious.model.RelativeGraphTimeAxis
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetStepTimeline
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyHistorySample
import app.sugarlicious.storage.PredictionDisplayTimeline
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal fun applyGraphBoundaryPaint(paint: Paint, configuredArgb: Int) {
    paint.color = configuredArgb
    // Paint is reused by overlays that alter alpha. Restore the exact configured ARGB each time.
    paint.alpha = Color.alpha(configuredArgb)
}

internal fun mobileBasalMaximum(points: List<TherapyHistorySample>, start: Long, end: Long, current: Boolean, base: Boolean): Double =
    windowedStepSamples(points.filter { it.baseBasalUnitsPerHour != null || it.basalUnitsPerHour != null }.sortedBy { it.measuredAtEpochMs }, start, end)
        .flatMap { sample -> listOfNotNull(if (base) sample.baseBasalUnitsPerHour ?: sample.basalUnitsPerHour else null, if (current) effectiveBasal(sample) else null) }
        .maxOrNull()
        ?.coerceAtLeast(0.1) ?: 0.1

/** Compact top-down BR/TBR overlay shared by the Mobile CGM and IOB graphs. */
internal fun drawMobileBasalOverlay(
    canvas: Canvas,
    plot: RectF,
    start: Long,
    end: Long,
    points: List<TherapyHistorySample>,
    showCurrent: Boolean,
    showBase: Boolean,
    density: Float,
    cyan: Int,
    linePaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        },
    fillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
    maximumOverride: Double? = null,
) {
    val sorted =
        points
            .filter {
                it.baseBasalUnitsPerHour != null || it.basalUnitsPerHour != null
            }.sortedBy { it.measuredAtEpochMs }
    val visible = windowedStepSamples(sorted, start, end)
    if (visible.size < 2) return
    val selectedValues =
        visible.flatMap { sample ->
            buildList {
                if (showBase) add(sample.baseBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: 0.0)
                if (showCurrent) add(effectiveBasal(sample))
            }
        }
    val maxBasal =
        maximumOverride
            ?: max(
                0.1,
                selectedValues.maxOrNull() ?: 0.1,
            )

    fun basalY(value: Double): Float = plot.top + (value.coerceIn(0.0, maxBasal) / maxBasal).toFloat() * plot.height() * BASAL_HEIGHT_FRACTION
    val effective = visible.map { it.measuredAtEpochMs to effectiveBasal(it) }
    val base = visible.map { it.measuredAtEpochMs to (it.baseBasalUnitsPerHour ?: it.basalUnitsPerHour ?: 0.0) }
    val clip = Path().apply { addRoundRect(plot, 14f * density, 14f * density, Path.Direction.CW) }
    canvas.withClip(clip) {
        if (showCurrent) {
            val area = stepPath(effective, start, end, plot, ::basalY, closeAt = plot.top)
            fillPaint.color = withAlpha(cyan, 76)
            drawPath(area, fillPaint)
            linePaint.color = cyan
            linePaint.strokeWidth = 1.2f * density
            linePaint.pathEffect = null
            drawPath(stepPath(effective, start, end, plot, ::basalY), linePaint)
        }
        if (showBase) {
            linePaint.color = cyan
            linePaint.strokeWidth = 1f * density
            linePaint.pathEffect = DashPathEffect(floatArrayOf(1f * density, 2f * density), 0f)
            drawPath(stepPath(base, start, end, plot, ::basalY), linePaint)
        }
        linePaint.pathEffect = null
    }
}

private const val HOUR_MS = 60L * 60_000L
internal const val MAX_VISIBLE_GRAPH_HOURS = 24f
internal const val MIN_VISIBLE_HISTORY_HOURS = 1f
internal const val BASAL_HEIGHT_FRACTION = 0.10f
private const val ACTIVITY_HEIGHT_FRACTION = 0.80f
internal const val GRAPH_CORNER_RADIUS_DP = 18f
private const val GLUCOSE_DISPLAY_MAX = 400.0
private const val VALUE_AXIS_WIDTH_DP = 29f
private const val TIME_AXIS_HEIGHT_DP = 24f

internal fun sustainedRangeExcursion(
    samples: List<GlucoseSample>,
    lowMgDl: Double,
    highMgDl: Double,
): RangeExcursion? = CgmGraphPolicy.rangeExcursion(samples, lowMgDl, highMgDl)

internal class StaticGraphScaleStore(
    private val preferences: SharedPreferences,
) {
    fun restore(session: GraphScaleSession) {
        listOf(GraphAxis.IOB, GraphAxis.COB).forEach { axis ->
            val minimumKey = key(axis, "minimum")
            val maximumKey = key(axis, "maximum")
            if (!preferences.contains(minimumKey) || !preferences.contains(maximumKey)) return@forEach
            val minimum = Double.fromBits(preferences.getLong(minimumKey, 0L))
            val maximum = Double.fromBits(preferences.getLong(maximumKey, 0L))
            runCatching { GraphBounds(minimum, maximum) }.getOrNull()?.let { session.useConfiguredBounds(axis, it) }
        }
    }

    fun persist(
        axis: GraphAxis,
        scale: GraphAxisScale,
    ) {
        if (scale.mode == CgmGraphScaleMode.DYNAMIC || scale.mode == CgmGraphScaleMode.LOGARITHMIC_DYNAMIC) return
        val bounds = scale.bounds
        preferences.edit {
            putLong(key(axis, "minimum"), bounds.minimum.toBits())
            putLong(key(axis, "maximum"), bounds.maximum.toBits())
        }
    }

    private fun key(
        axis: GraphAxis,
        suffix: String,
    ) = "graph.static.${axis.name.lowercase(Locale.ROOT)}.$suffix"
}

internal class ChartViewport(
    initialHours: Int,
) {
    enum class Mode { LIVE_FOLLOW, USER_NAVIGATING }

    private val listeners = LinkedHashSet<() -> Unit>()
    private var availablePastWindowMs = 24L * HOUR_MS
    private var requestedHours = initialHours.toFloat().coerceIn(MIN_VISIBLE_HISTORY_HOURS, MAX_VISIBLE_GRAPH_HOURS)
    var hours = requestedHours
        private set
    var panMs = 0L
        private set
    var futureWindowMs = 0L
        private set
    var mode: Mode = Mode.LIVE_FOLLOW
        private set
    private var navigationEndEpochMs: Long? = null
    var axisIntervalHours: Int? = null
        private set

    private val gestureOwners = mutableSetOf<Any>()
    val isNavigatingGesture: Boolean get() = gestureOwners.isNotEmpty()

    fun setNavigatingGesture(owner: Any, active: Boolean) {
        val changed = if (active) gestureOwners.add(owner) else gestureOwners.remove(owner)
        if (changed) notifyChanged()
    }

    val visibleHours: Float get() = hours + futureWindowMs.toFloat() / HOUR_MS

    fun setHours(
        value: Float,
        resetPan: Boolean = false,
    ) {
        requestedHours = value.coerceIn(MIN_VISIBLE_HISTORY_HOURS, MAX_VISIBLE_GRAPH_HOURS)
        val next = requestedHours.coerceAtMost(maximumHours())
        val changed = next != hours || (resetPan && panMs != 0L)
        hours = next
        if (resetPan) {
            panMs = 0L
            mode = Mode.LIVE_FOLLOW
            navigationEndEpochMs = null
        }
        clampNavigation(System.currentTimeMillis())
        if (changed) notifyChanged()
    }

    fun setFutureWindow(
        valueMs: Long,
        referenceNow: Long = System.currentTimeMillis(),
    ) {
        val next = valueMs.coerceIn(0L, 2L * HOUR_MS)
        if (next == futureWindowMs) return
        futureWindowMs = next
        hours = requestedHours.coerceAtMost(maximumHours())
        clampNavigation(referenceNow)
        notifyChanged()
    }

    fun setAvailablePastWindow(
        valueMs: Long,
        referenceNow: Long = System.currentTimeMillis(),
    ) {
        val next = valueMs.coerceIn(0L, MAX_VISIBLE_GRAPH_HOURS.toLong() * HOUR_MS)
        if (next == availablePastWindowMs) return
        availablePastWindowMs = next
        hours = requestedHours.coerceAtMost(maximumHours())
        clampNavigation(referenceNow)
        notifyChanged()
    }

    fun endEpochMs(now: Long): Long = snapshot(now).endEpochMs

    fun snapshot(now: Long): GraphViewportSnapshot {
        val liveEnd = now + futureWindowMs
        val earliestEnd = now - availablePastWindowMs + (hours * HOUR_MS).toLong() + futureWindowMs
        val requestedEnd = if (mode == Mode.USER_NAVIGATING) navigationEndEpochMs ?: liveEnd else liveEnd
        val end = requestedEnd.coerceIn(minOf(earliestEnd, liveEnd), liveEnd)
        val start = end - (visibleHours * HOUR_MS).toLong()
        return GraphViewportSnapshot(start, now, end)
    }

    fun beginScale() {
        axisIntervalHours = RelativeGraphTimeAxis.intervalHours(visibleHours.toDouble())
    }

    fun endScale() {
        axisIntervalHours = null
        notifyChanged()
    }

    fun zoom(
        scaleFactor: Float,
        focalFraction: Float = 1f,
        referenceNow: Long = System.currentTimeMillis(),
    ) {
        val oldSnapshot = snapshot(referenceNow)
        val oldHours = hours
        requestedHours = (hours / scaleFactor.coerceAtLeast(0.05f)).coerceIn(MIN_VISIBLE_HISTORY_HOURS, MAX_VISIBLE_GRAPH_HOURS)
        hours = requestedHours.coerceAtMost(maximumHours())
        if (hours != oldHours) {
            val focal = focalFraction.coerceIn(0f, 1f)
            val focalEpochMs = oldSnapshot.startEpochMs + (oldSnapshot.durationMs * focal).toLong()
            val desiredEnd = focalEpochMs + ((visibleHours * HOUR_MS) * (1f - focal)).toLong()
            navigateToEnd(desiredEnd, referenceNow)
            notifyChanged()
        }
    }

    fun pan(
        deltaPixels: Float,
        width: Float,
        referenceNow: Long = System.currentTimeMillis(),
    ): Boolean {
        if (width <= 0f) return false
        val oldEnd = snapshot(referenceNow).endEpochMs
        val deltaMs = (deltaPixels / width * visibleHours * HOUR_MS).toLong()
        navigateToEnd(oldEnd - deltaMs, referenceNow)
        if (snapshot(referenceNow).endEpochMs != oldEnd) {
            notifyChanged()
            return true
        }
        return false
    }

    /** Subpixel fling frames must keep running; only a nonzero blocked step hits an edge. */
    fun panFlingFrame(deltaPixels: Float, width: Float, referenceNow: Long = System.currentTimeMillis()): Boolean =
        deltaPixels == 0f || pan(deltaPixels, width, referenceNow)

    fun returnToNow() {
        if (panMs == 0L && mode == Mode.LIVE_FOLLOW) return
        panMs = 0L
        mode = Mode.LIVE_FOLLOW
        navigationEndEpochMs = null
        notifyChanged()
    }

    fun savedState(referenceNow: Long = System.currentTimeMillis()): GraphViewportSavedState =
        GraphViewportSavedState(
            historyHours = requestedHours,
            navigationEndEpochMs = snapshot(referenceNow).endEpochMs.takeIf { mode == Mode.USER_NAVIGATING },
        )

    fun restore(
        savedState: GraphViewportSavedState,
        referenceNow: Long = System.currentTimeMillis(),
    ) {
        requestedHours = savedState.historyHours.coerceIn(MIN_VISIBLE_HISTORY_HOURS, MAX_VISIBLE_GRAPH_HOURS)
        hours = requestedHours.coerceAtMost(maximumHours())
        savedState.navigationEndEpochMs?.let { navigateToEnd(it, referenceNow) } ?: returnToNow()
        clampNavigation(referenceNow)
        notifyChanged()
    }

    internal fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    internal fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    private fun maximumHours(): Float =
        minOf(
            availablePastWindowMs.toFloat() / HOUR_MS,
            if (requestedHours >= MAX_VISIBLE_GRAPH_HOURS) {
                MAX_VISIBLE_GRAPH_HOURS
            } else {
                MAX_VISIBLE_GRAPH_HOURS - futureWindowMs.toFloat() / HOUR_MS
            },
        ).coerceAtLeast(MIN_VISIBLE_HISTORY_HOURS)

    private fun navigateToEnd(
        desiredEndEpochMs: Long,
        referenceNow: Long,
    ) {
        val liveEnd = referenceNow + futureWindowMs
        val earliestEnd = referenceNow - availablePastWindowMs + (hours * HOUR_MS).toLong() + futureWindowMs
        val resolvedEnd = desiredEndEpochMs.coerceIn(minOf(earliestEnd, liveEnd), liveEnd)
        panMs = resolvedEnd - liveEnd
        navigationEndEpochMs = resolvedEnd.takeIf { it < liveEnd }
        mode = if (navigationEndEpochMs == null) Mode.LIVE_FOLLOW else Mode.USER_NAVIGATING
    }

    private fun clampNavigation(referenceNow: Long) {
        val desiredEnd = navigationEndEpochMs ?: (referenceNow + futureWindowMs + panMs)
        navigateToEnd(desiredEnd, referenceNow)
    }
}

internal abstract class InteractiveChartView(
    context: Context,
    attrs: AttributeSet? = null,
    protected val viewport: ChartViewport,
) : View(context, attrs) {
    private val basalScale = SettledGraphScale<Double> { a, b, f -> a + (b - a) * f }

    protected fun settledBasalMaximum(points: List<TherapyHistorySample>, start: Long, end: Long, current: Boolean, base: Boolean): Double {
        val result = basalScale.resolve(mobileBasalMaximum(points, start, end, current, base), viewport.isNavigatingGesture, android.os.SystemClock.uptimeMillis())
        if (basalScale.animating) postInvalidateOnAnimation()
        return result
    }

    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var moving = false
    private var touching = false
    private var velocityTracker: VelocityTracker? = null
    private val flingConfiguration = ViewConfiguration.get(context)
    private val flingScroller =
        OverScroller(context).apply {
            setFriction(ViewConfiguration.getScrollFriction() * .72f)
        }
    private var flingX = 0
    private val viewportListener: () -> Unit = { invalidate() }
    private val scaleDetector by lazy {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    viewport.beginScale()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    viewport.zoom(detector.scaleFactor, detector.focusX / width.coerceAtLeast(1), System.currentTimeMillis())
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    viewport.endScale()
                }
            },
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewport.addListener(viewportListener)
    }

    override fun onDetachedFromWindow() {
        viewport.removeListener(viewportListener)
        if (touching || !flingScroller.isFinished) viewport.setNavigatingGesture(this, false)
        touching = false
        flingScroller.forceFinished(true)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingScroller.forceFinished(true)
                touching = true
                viewport.setNavigatingGesture(this, true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                lastX = event.x
                downX = event.x
                downY = event.y
                moving = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val delta = event.x - lastX
                    val horizontalGesture = abs(event.x - downX) > abs(event.y - downY) + 4f
                    if (horizontalGesture && abs(delta) > 0.4f) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        viewport.pan(delta, width.toFloat())
                        moving = true
                    }
                    lastX = event.x
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP && moving) {
                    velocityTracker?.apply {
                        addMovement(event)
                        computeCurrentVelocity(1000, flingConfiguration.scaledMaximumFlingVelocity.toFloat())
                        val velocity = xVelocity.toInt()
                        if (abs(velocity) >= flingConfiguration.scaledMinimumFlingVelocity) {
                            flingX = 0
                            flingScroller.fling(0, 0, velocity, 0, Int.MIN_VALUE, Int.MAX_VALUE, 0, 0)
                            postInvalidateOnAnimation()
                        }
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                touching = false
                viewport.setNavigatingGesture(this, !flingScroller.isFinished)
                if (!moving) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun computeScroll() {
        if (!flingScroller.computeScrollOffset()) {
            if (!touching) viewport.setNavigatingGesture(this, false)
            return
        }
        val nextX = flingScroller.currX
        val moved = viewport.panFlingFrame((nextX - flingX).toFloat(), width.toFloat())
        flingX = nextX
        if (moved) {
            postInvalidateOnAnimation()
        } else {
            flingScroller.forceFinished(true)
            if (!touching) viewport.setNavigatingGesture(this, false)
        }
    }
}

@SuppressLint("DrawAllocation")
internal class GlucoseDashboardChart
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        sharedViewport: ChartViewport = ChartViewport(6),
    ) : InteractiveChartView(context, attrs, sharedViewport) {
        private val density = resources.displayMetrics.density
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private var state: TherapyDisplayState? = null
        private var unit = GlucoseUnit.MG_DL
        private var showPredictions = false
        private var showTargetRange = true
        private var showTargetValue = false
        private var showCurrentBasal = false
        private var showBaseBasal = false
        private var showActivity = false
        private var showPredictionIob = false
        private var showPredictionCob = false
        private var showPredictionUam = false
        private var showPredictionZeroTemp = false
        private var cgmDotRadiusDp = 2.4f
        private var cgmDotOutlineEnabled = true
        private var cgmDotOutlineWidthDp = 0.95f
        private var cgmHistoricalDotOutlineEnabled = true
        private var cgmHistoricalDotOutlineWidthDp = 0.95f
        private var cgmCurrentDotOutlineWidthDp = 0.95f
        private var predictionDotRadiusDp = 1.75f
        private var predictionDotOutlineWidthDp = 0.70f
        private var graphMaximumMgDl = GLUCOSE_DISPLAY_MAX
        private var graphMinimumMgDl = CgmGraphYScale.DEFAULT_MINIMUM_MG_DL
        private var graphScaleMode = CgmGraphScaleMode.LOGARITHMIC
        private var renderedYScale: CgmGraphYScale? = null
        private val smoothCgmScale =
            SettledGraphScale<CgmGraphYScale> { a, b, f ->
                b.copy(minimumMgDl = a.minimumMgDl + (b.minimumMgDl - a.minimumMgDl) * f, maximumMgDl = a.maximumMgDl + (b.maximumMgDl - a.maximumMgDl) * f)
            }
        private val staticScaleStore = StaticGraphScaleStore(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
        private val axisScaleSession = GraphScaleSession().also(staticScaleStore::restore)
        private var stateSignature: List<Any?>? = null
        private var clockBucket: Long = Long.MIN_VALUE
        private var renderNowEpochMs: Long = System.currentTimeMillis()
        private var boundDurationHours: Int? = null
        private var boundPalette: app.sugarlicious.mobile.ui.theme.SugarliciousPalette? = null
        private var boundThresholds: CgmThresholds? = null

        fun bind(
            state: TherapyDisplayState?,
            unit: GlucoseUnit,
            showPredictions: Boolean,
            durationHours: Int,
            showTargetRange: Boolean = true,
            showTargetValue: Boolean = false,
            showCurrentBasal: Boolean = false,
            showBaseBasal: Boolean = false,
            showActivity: Boolean = false,
            showPredictionIob: Boolean = false,
            showPredictionCob: Boolean = false,
            showPredictionUam: Boolean = false,
            showPredictionZeroTemp: Boolean = false,
            cgmDotRadiusDp: Float = 2.4f,
            cgmDotOutlineEnabled: Boolean = true,
            cgmDotOutlineWidthDp: Float = 0.95f,
            cgmHistoricalDotOutlineEnabled: Boolean = cgmDotOutlineEnabled,
            cgmHistoricalDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
            cgmCurrentDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
            graphMaximumMgDl: Double = GLUCOSE_DISPLAY_MAX,
            graphMinimumMgDl: Double = CgmGraphYScale.DEFAULT_MINIMUM_MG_DL,
            graphScaleMode: CgmGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC,
            clockEpochMs: Long = System.currentTimeMillis(),
        ) {
            val resolvedRadius = cgmDotRadiusDp.coerceIn(1.5f, 6.0f)
            val resolvedOutlineWidth = cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f)
            val resolvedHistoricalOutlineWidth = cgmHistoricalDotOutlineWidthDp.coerceIn(0.25f, 3.0f)
            val resolvedCurrentOutlineWidth = cgmCurrentDotOutlineWidthDp.coerceIn(0.25f, 3.0f)
            val stylePreferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            val resolvedPalette = SugarliciousColors.palette
            val resolvedThresholds = CgmThresholdPreferences.read(stylePreferences)
            val resolvedDurationHours = resolveOverviewGraphHoursPreference(stylePreferences, durationHours)
            val resolvedPredictionRadius = readMobilePredictionDotRadius(stylePreferences)
            val resolvedPredictionOutlineWidth = readMobilePredictionDotOutlineWidth(stylePreferences)
            val resolvedGraphMaximum = graphMaximumMgDl.coerceIn(180.0, 600.0)
            val resolvedGraphMinimum = 0.0
            val resolvedClockBucket = clockEpochMs / CLOCK_REFRESH_MS
            viewport.setAvailablePastWindow(
                availableOverviewHistoryWindowMs(state, clockEpochMs, resolvedDurationHours),
                clockEpochMs,
            )
            val newStateSignature =
                state?.let {
                    buildList {
                        add(it.source)
                        add(
                            it.glucose?.let { glucose ->
                                listOf(
                                    glucose.measuredAtEpochMs,
                                    glucose.receivedAtEpochMs,
                                    glucose.valueMgDl,
                                    glucose.source,
                                    glucose.sensorId,
                                    glucose.sessionId,
                                    glucose.sequenceNumber,
                                )
                            },
                        )
                        add(
                            it.glucoseHistory.map { sample ->
                                listOf(
                                    sample.measuredAtEpochMs,
                                    sample.receivedAtEpochMs,
                                    sample.valueMgDl,
                                    sample.source,
                                    sample.sensorId,
                                    sample.sessionId,
                                    sample.sequenceNumber,
                                )
                            },
                        )
                        add(
                            it.glucosePredictions.map { series ->
                                series.kind to series.samples.map { sample -> sample.measuredAtEpochMs to sample.valueMgDl }
                            },
                        )
                        add(it.target)
                        add(it.targetHistory)
                        if (showCurrentBasal || showBaseBasal || showActivity) add(it.therapyHistory)
                    }
                }
            val durationChanged = boundDurationHours != resolvedDurationHours
            val changed =
                boundPalette != resolvedPalette ||
                    boundThresholds != resolvedThresholds ||
                    stateSignature != newStateSignature ||
                    durationChanged ||
                    this.unit != unit ||
                    this.showPredictions != showPredictions ||
                    this.showTargetValue != showTargetValue ||
                    this.showCurrentBasal != showCurrentBasal ||
                    this.showBaseBasal != showBaseBasal ||
                    this.showActivity != showActivity ||
                    this.showPredictionIob != showPredictionIob ||
                    this.showPredictionCob != showPredictionCob ||
                    this.showPredictionUam != showPredictionUam ||
                    this.showPredictionZeroTemp != showPredictionZeroTemp ||
                    this.cgmDotRadiusDp != resolvedRadius ||
                    this.cgmDotOutlineEnabled != cgmDotOutlineEnabled ||
                    this.cgmDotOutlineWidthDp != resolvedOutlineWidth ||
                    this.cgmHistoricalDotOutlineEnabled != cgmHistoricalDotOutlineEnabled ||
                    this.cgmHistoricalDotOutlineWidthDp != resolvedHistoricalOutlineWidth ||
                    this.cgmCurrentDotOutlineWidthDp != resolvedCurrentOutlineWidth ||
                    this.predictionDotRadiusDp != resolvedPredictionRadius ||
                    this.predictionDotOutlineWidthDp != resolvedPredictionOutlineWidth ||
                    this.graphMaximumMgDl != resolvedGraphMaximum ||
                    this.graphMinimumMgDl != resolvedGraphMinimum ||
                    this.graphScaleMode != graphScaleMode ||
                    clockBucket != resolvedClockBucket

            if (!changed) return

            boundPalette = resolvedPalette
            boundThresholds = resolvedThresholds
            this.state = state
            stateSignature = newStateSignature
            boundDurationHours = resolvedDurationHours
            this.unit = unit
            this.showPredictions = showPredictions
            // The CGM target range is a permanent part of the Sugarlicious graph. The parameter is
            // retained for binary/source compatibility with older callers, but is intentionally ignored.
            this.showTargetRange = true
            this.showTargetValue = showTargetValue
            this.showCurrentBasal = showCurrentBasal
            this.showBaseBasal = showBaseBasal
            this.showActivity = showActivity
            this.showPredictionIob = showPredictionIob
            this.showPredictionCob = showPredictionCob
            this.showPredictionUam = showPredictionUam
            this.showPredictionZeroTemp = showPredictionZeroTemp
            this.cgmDotRadiusDp = resolvedRadius
            this.cgmDotOutlineEnabled = cgmDotOutlineEnabled
            this.cgmDotOutlineWidthDp = resolvedOutlineWidth
            this.cgmHistoricalDotOutlineEnabled = cgmHistoricalDotOutlineEnabled
            this.cgmHistoricalDotOutlineWidthDp = resolvedHistoricalOutlineWidth
            this.cgmCurrentDotOutlineWidthDp = resolvedCurrentOutlineWidth
            this.predictionDotRadiusDp = resolvedPredictionRadius
            this.predictionDotOutlineWidthDp = resolvedPredictionOutlineWidth
            this.graphMaximumMgDl = resolvedGraphMaximum
            this.graphMinimumMgDl = resolvedGraphMinimum
            this.graphScaleMode = graphScaleMode
            clockBucket = resolvedClockBucket
            renderNowEpochMs = clockEpochMs

            if (durationChanged) viewport.setHours(resolvedDurationHours.toFloat(), resetPan = true)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val outlineInset = 0.5f.dp
            val now = renderNowEpochMs
            val predictions =
                if (showPredictions) {
                    state?.glucosePredictions.orEmpty().filter { predictionEnabled(it.kind) }
                } else {
                    emptyList()
                }
            val viewportSnapshot = viewport.snapshot(now)
            val timeWindow =
                GraphTimeWindow(
                    startEpochMs = viewportSnapshot.startEpochMs,
                    liveEdgeEpochMs = viewportSnapshot.liveEdgeEpochMs,
                    endEpochMs = viewportSnapshot.endEpochMs,
                )
            val start = timeWindow.startEpochMs
            val end = timeWindow.endEpochMs
            val anchoredPredictions = PredictionDisplayTimeline.anchor(predictions, now)
            val visiblePredictions =
                anchoredPredictions
                    .map { series -> series.copy(samples = series.samples.filter { it.measuredAtEpochMs in start..end }) }
                    .filter { it.samples.isNotEmpty() }
            val scaleRightBlend = predictionScaleRightBlend(predictions, now, start, end)
            val bounds =
                mobileCgmGraphBounds(
                    width.toFloat(),
                    height.toFloat(),
                    outlineInset,
                    TIME_AXIS_HEIGHT_DP.dp,
                    VALUE_AXIS_WIDTH_DP.dp,
                    scaleRightBlend,
                )
            val scaleContainer = bounds.tile
            val plot = bounds.plot
            val contentBounds = bounds.content
            if (plot.width() <= 24f || plot.height() <= 24f) return
            val radius = GRAPH_CORNER_RADIUS_DP.dp
            val maximumPointExtent =
                maxOf(
                    cgmDotRadiusDp.dp + maxOf(if (cgmHistoricalDotOutlineEnabled) cgmHistoricalDotOutlineWidthDp else 0f, cgmCurrentDotOutlineWidthDp).dp / 2f,
                    predictionDotRadiusDp.dp + predictionDotOutlineWidthDp.dp / 2f,
                )
            val timeBounds = graphTimeBounds(plot, maximumPointExtent, 0f)
            val dataPlot =
                RectF(
                    timeBounds.left,
                    roundedPlotTopTangentY(plot.top, radius),
                    timeBounds.right,
                    plot.bottom,
                )
            val contentClip = Path().apply { addRoundRect(contentBounds, radius + outlineInset, radius + outlineInset, Path.Direction.CW) }

            canvas.withClip(contentClip) {
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.SURFACE)
                canvas.drawRect(contentBounds, fillPaint)
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
                canvas.drawRoundRect(plot, radius, radius, fillPaint)

                val thresholds = CgmThresholdPreferences.read(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
                val targetVeryLow = thresholds.veryLowMgDl
                val targetLow = thresholds.lowMgDl
                val targetHigh = thresholds.highMgDl
                val targetVeryHigh = thresholds.veryHighMgDl
                val freshness = FreshnessPolicy.classify(state?.glucose?.measuredAtEpochMs, now)
                val signalLost = freshness == Freshness.SIGNAL_LOSS
                // Like AAPS, the viewport is tied to real current time. A new CGM therefore advances
                // the same time axis instead of pinning the latest point while neighbours get squeezed.
                val allHistory =
                    canonicalMobileGraphHistory(
                        samples =
                            buildList {
                                addAll(state?.glucoseHistory.orEmpty())
                                state?.glucose?.let { glucose ->
                                    add(
                                        GlucoseSample(
                                            valueMgDl = glucose.valueMgDl,
                                            measuredAtEpochMs = glucose.measuredAtEpochMs,
                                            source = glucose.source,
                                            sensorId = glucose.sensorId,
                                            sessionId = glucose.sessionId,
                                            sequenceNumber = glucose.sequenceNumber,
                                            receivedAtEpochMs = glucose.receivedAtEpochMs,
                                            quality = glucose.quality,
                                        ),
                                    )
                                }
                            },
                        nowEpochMs = now,
                        preferredSource = state?.source,
                    )
                val history = allHistory.filter { it.measuredAtEpochMs in start..min(end, now) }
                // Range classification is a Y-axis background, not a time series. AndroidAPS keeps
                // the complete visible axis classified, including the prediction side.
                val targetLeft = plot.left
                val targetRight = plot.right
                val proposedYScale =
                    resolveMobileCgmScale(
                        mode = graphScaleMode,
                        measuredValues = history.map(GlucoseSample::valueMgDl),
                        configuredMinimum = graphMinimumMgDl,
                        configuredMaximum = graphMaximumMgDl,
                        thresholds = thresholds,
                    )
                val desiredScale = stableCgmScale(renderedYScale, proposedYScale)
                if (!viewport.isNavigatingGesture) renderedYScale = desiredScale
                val yScale = smoothCgmScale.resolve(desiredScale, viewport.isNavigatingGesture, android.os.SystemClock.uptimeMillis())
                if (smoothCgmScale.animating) postInvalidateOnAnimation()
                val targetVeryTop = mapGlucoseY(targetVeryHigh, dataPlot, yScale)
                val targetTop = mapGlucoseY(targetHigh, dataPlot, yScale)
                val targetBottom = mapGlucoseY(targetLow, dataPlot, yScale)
                val targetVeryBottom = mapGlucoseY(targetVeryLow, dataPlot, yScale)
                // Signal loss changes freshness only. The last confirmed range excursion remains
                // active until a new validated CGM value performs a real range transition.
                val excursion = sustainedRangeExcursion(allHistory, targetLow, targetHigh)
                val graphSave = canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(plot, radius, radius, Path.Direction.CW) })

                if (showTargetRange && targetLeft < targetRight) {
                    // Adjacent fractional rectangle edges must not independently blend with the
                    // background: two half-covered edges otherwise leave a dark one-pixel seam
                    // even when both bands have the same color and their boundary is transparent.
                    fillPaint.isAntiAlias = false
                    if (excursion == RangeExcursion.HIGH) {
                        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.RANGE_VERY_HIGH)
                        canvas.drawRect(targetLeft, plot.top, targetRight, targetVeryTop, fillPaint)
                        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.RANGE_HIGH)
                        canvas.drawRect(targetLeft, targetVeryTop, targetRight, targetTop, fillPaint)
                    }
                    fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.RANGE_IN_RANGE)
                    canvas.drawRect(targetLeft, targetTop, targetRight, targetBottom, fillPaint)
                    if (excursion == RangeExcursion.LOW) {
                        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.RANGE_LOW)
                        canvas.drawRect(targetLeft, targetBottom, targetRight, targetVeryBottom, fillPaint)
                        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.RANGE_VERY_LOW)
                        canvas.drawRect(targetLeft, targetVeryBottom, targetRight, plot.bottom, fillPaint)
                    }
                    fillPaint.isAntiAlias = true
                }

                if (signalLost) {
                    val signalStart =
                        state
                            ?.glucose
                            ?.measuredAtEpochMs
                            ?.let { mapGraphTimeX(it, start, end, timeBounds) }
                            ?.coerceIn(plot.left, plot.right) ?: plot.left
                    if (signalStart < plot.right) {
                        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_SIGNAL_LOSS)
                        canvas.drawRect(signalStart, plot.top, plot.right, plot.bottom, fillPaint)
                        drawText(
                            canvas,
                            "SIGNALVERLUST",
                            (signalStart + plot.right) / 2f,
                            plot.top + 15f.dp,
                            9f,
                            luminousTargetValueColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_SIGNAL_LOSS)),
                            Paint.Align.CENTER,
                        )
                    }
                }

                if (showTargetRange && targetLeft < targetRight) {
                    linePaint.strokeWidth = 1f.dp
                    linePaint.strokeCap = Paint.Cap.BUTT
                    linePaint.pathEffect = null
                    applyGraphBoundaryPaint(linePaint, graphBoundaryColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_VERY_HIGH_LINE)))
                    if (targetVeryHigh in yScale.minimumMgDl..yScale.maximumMgDl) canvas.drawLine(targetLeft, targetVeryTop, targetRight, targetVeryTop, linePaint)
                    applyGraphBoundaryPaint(linePaint, graphBoundaryColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_HIGH_LINE)))
                    if (targetHigh in yScale.minimumMgDl..yScale.maximumMgDl) canvas.drawLine(targetLeft, targetTop, targetRight, targetTop, linePaint)
                    applyGraphBoundaryPaint(linePaint, graphBoundaryColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LOW_LINE)))
                    if (targetLow in yScale.minimumMgDl..yScale.maximumMgDl) canvas.drawLine(targetLeft, targetBottom, targetRight, targetBottom, linePaint)
                    applyGraphBoundaryPaint(linePaint, graphBoundaryColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_VERY_LOW_LINE)))
                    if (targetVeryLow in yScale.minimumMgDl..yScale.maximumMgDl) canvas.drawLine(targetLeft, targetVeryBottom, targetRight, targetVeryBottom, linePaint)
                    linePaint.strokeCap = Paint.Cap.ROUND
                }

                if (showTargetValue) {
                    val currentTarget = state?.target?.valueMgDl?.takeIf { it.isFinite() && it in 20.0..1_000.0 }
                    val targetSegments =
                        state
                            ?.targetHistory
                            .orEmpty()
                            .asSequence()
                            .filter {
                                it.valueMgDl.isFinite() &&
                                    it.valueMgDl in 20.0..1_000.0 &&
                                    it.startedAtEpochMs <= end &&
                                    it.endsAtEpochMs >= start
                            }.map { sample ->
                                val isCurrentObservedTarget =
                                    sample == state?.targetHistory?.lastOrNull() &&
                                        currentTarget == sample.valueMgDl &&
                                        sample.temporary == (state?.target?.temporary == true) &&
                                        freshness in setOf(Freshness.CURRENT, Freshness.DELAYED)
                                sample.copy(
                                    endsAtEpochMs =
                                        targetDisplayEndEpochMs(
                                            sourceEnd = sample.endsAtEpochMs,
                                            temporary = sample.temporary,
                                            isCurrent = isCurrentObservedTarget,
                                            now = now,
                                            viewportEnd = end,
                                        ),
                                )
                            }.toList()
                            .ifEmpty {
                                currentTarget
                                    ?.let { value ->
                                        val observedAt = state?.glucose?.measuredAtEpochMs ?: state?.receivedAtEpochMs ?: now
                                        val firstObservedAt = history.minOfOrNull { it.measuredAtEpochMs } ?: observedAt
                                        val temporary = state?.target?.temporary == true
                                        val explicitEnd = state?.target?.endsAtEpochMs?.takeIf { temporary }
                                        if (explicitEnd != null && explicitEnd <= observedAt) {
                                            emptyList()
                                        } else {
                                            listOf(
                                                app.sugarlicious.model.TargetSample(
                                                    valueMgDl = value,
                                                    // Keep the fallback target attached to actual data time. Using the
                                                    // viewport start makes the dash pattern a sticky screen overlay.
                                                    startedAtEpochMs = minOf(firstObservedAt, observedAt),
                                                    endsAtEpochMs =
                                                        explicitEnd
                                                            ?: if (temporary) now else end,
                                                    temporary = temporary,
                                                ),
                                            )
                                        }
                                    }.orEmpty()
                            }

                    val targetValueColor = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.TARGET_VALUE), 190)
                    linePaint.color = targetValueColor
                    linePaint.strokeWidth = 1.35f.dp
                    targetStepPaths(targetSegments, start, end).forEach { points ->
                        val dashLength = 3f.dp
                        linePaint.pathEffect =
                            DashPathEffect(
                                floatArrayOf(dashLength, dashLength),
                                contentAnchoredDashPhase(
                                    viewportStartEpochMs = start,
                                    viewportDurationMs = end - start,
                                    plotWidthPx = timeBounds.width(),
                                    periodPx = dashLength * 2f,
                                ),
                            )
                        canvas.drawPath(
                            valuePath(points, start, end, timeBounds) { value -> mapGlucoseY(value, dataPlot, yScale) },
                            linePaint,
                        )
                    }
                    linePaint.pathEffect = null
                }

                if (showCurrentBasal || showBaseBasal) {
                    drawBasal(
                        canvas,
                        cgmBasalOverlayBounds(plot),
                        start,
                        end,
                        state?.therapyHistory.orEmpty(),
                        showCurrentBasal,
                        showBaseBasal,
                    )
                }
                if (showActivity) {
                    drawInsulinActivity(
                        canvas,
                        cgmInsulinActivityBand(dataPlot, targetTop, targetBottom),
                        start,
                        end,
                        now,
                        state?.therapyHistory.orEmpty(),
                    )
                }

                // The live marker belongs to the clock, not to the last packet. A delayed packet must
                // remain at its measurement timestamp while the whole history keeps moving left.
                val liveTimestamp = timeWindow.liveEdgeEpochMs
                val liveX = mapGraphTimeX(liveTimestamp, start, end, timeBounds)
                // This is a data-space timestamp, not a sticky viewport overlay. It may leave the plot.
                val futureLaneVisible = end > now && now in start..end

                history.forEachIndexed { index, point ->
                    // A fixed viewport may exclude real values; never pretend they equal an edge value.
                    if (point.valueMgDl !in yScale.minimumMgDl..yScale.maximumMgDl) return@forEachIndexed
                    val mappedX = mapGraphTimeX(point.measuredAtEpochMs, start, end, timeBounds)
                    val y = mapGlucoseY(point.valueMgDl, dataPlot, yScale)
                    val current = index == history.lastIndex
                    val dotRadius = (cgmDotRadiusDp + if (current) 0.1f else 0f).dp
                    val outlineEnabled = current || cgmHistoricalDotOutlineEnabled
                    val outlineWidth = (if (current) cgmCurrentDotOutlineWidthDp else cgmHistoricalDotOutlineWidthDp).dp
                    // Every time-based layer uses the same inset data viewport. The inset is derived
                    // from the largest rendered dot, so the current value remains fully visible.
                    val x = mappedX
                    fillPaint.color = dotColor(point.valueMgDl, thresholds)
                    canvas.drawCircle(x, y, dotRadius, fillPaint)
                    if (outlineEnabled) {
                        dotOutlinePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
                        dotOutlinePaint.strokeWidth = outlineWidth
                        canvas.drawCircle(x, y, dotRadius + outlineWidth / 2f, dotOutlinePaint)
                    }
                }

                if (visiblePredictions.isNotEmpty() && futureLaneVisible) {
                    visiblePredictions.forEach {
                        drawPrediction(canvas, it, dataPlot, start, end, yScale)
                    }
                }

                if (showPredictions && futureLaneVisible) {
                    linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_NOW_LINE)
                    linePaint.strokeWidth = 1f.dp
                    linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
                    canvas.drawLine(liveX, plot.top, liveX, plot.bottom, linePaint)
                    linePaint.pathEffect = null
                }

                canvas.restoreToCount(graphSave)
                drawGrid(canvas, plot, timeBounds, scaleContainer.bottom, start, end, liveTimestamp, liveX)

                if (showTargetRange && targetLeft < targetRight) {
                    val scaleValues =
                        listOf(
                            targetVeryHigh to targetVeryTop,
                            targetHigh to targetTop,
                            targetLow to targetBottom,
                            targetVeryLow to targetVeryBottom,
                        ).filter { (value, _) -> value in yScale.minimumMgDl..yScale.maximumMgDl }
                            .map { (value, y) -> glucoseLabel(value) to y }
                    drawTargetScale(canvas, scaleValues, plot, onRight = false, alpha = 1f - scaleRightBlend, edgeTop = dataPlot.top, primaryLabels = setOf(glucoseLabel(targetHigh), glucoseLabel(targetLow)))
                    drawTargetScale(canvas, scaleValues, plot, onRight = true, alpha = scaleRightBlend, edgeTop = dataPlot.top, primaryLabels = setOf(glucoseLabel(targetHigh), glucoseLabel(targetLow)))
                }
                drawGraphEdgeScale(canvas, plot, dataPlot.top, onRight = false, yScale.maximumMgDl, alpha = 1f - scaleRightBlend)
                drawGraphEdgeScale(canvas, plot, dataPlot.top, onRight = true, yScale.maximumMgDl, alpha = scaleRightBlend)
                drawGraphEdgeScale(canvas, plot, dataPlot.bottom, onRight = false, yScale.minimumMgDl, alpha = 1f - scaleRightBlend)
                drawGraphEdgeScale(canvas, plot, dataPlot.bottom, onRight = true, yScale.minimumMgDl, alpha = scaleRightBlend)

                if (history.size < 2) {
                    drawText(
                        canvas,
                        "Noch kein Verlauf",
                        plot.centerX(),
                        plot.centerY(),
                        10f,
                        SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED),
                        Paint.Align.CENTER,
                    )
                }
            }
            drawRoundedBorder(canvas, scaleContainer, radius)
        }

        private fun drawGrid(
            canvas: Canvas,
            plot: RectF,
            timeBounds: RectF,
            axisBottom: Float,
            start: Long,
            end: Long,
            now: Long,
            nowLineX: Float,
        ) {
            val ticks = RelativeGraphTimeAxis.ticks(start, end, now, viewport.axisIntervalHours)
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_AXIS_TICK)
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = null
            ticks.forEach { tick ->
                val isNow = tick.hoursBack == 0
                val x = if (isNow) nowLineX else mapGraphTimeX(tick.timestampEpochMs, start, end, timeBounds)
                if (x < plot.left || x > plot.right) return@forEach
                val align =
                    when {
                        tick.timestampEpochMs <= start + 30_000L -> Paint.Align.LEFT
                        isNow -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                val labelX =
                    when (align) {
                        Paint.Align.LEFT -> plot.left + 3f.dp
                        Paint.Align.RIGHT -> currentTimeLabelAnchor(x, plot.right, 3f.dp)
                        else -> x
                    }
                val labelSize = 11.5f
                val tickX = x
                canvas.drawLine(tickX, plot.bottom + 2f.dp, tickX, plot.bottom + 8f.dp, linePaint)
                drawText(
                    canvas,
                    tick.label,
                    labelX,
                    axisBottom - 4f.dp,
                    labelSize,
                    SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL),
                    align,
                    bold = false,
                )
            }
        }

        private fun drawTargetScale(
            canvas: Canvas,
            values: List<Pair<String, Float>>,
            plot: RectF,
            onRight: Boolean,
            alpha: Float = 1f,
            edgeTop: Float = plot.top,
            primaryLabels: Set<String> = emptySet(),
        ) {
            if (alpha <= 0f) return
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
                    color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL)
                    textAlign = if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, true)
                    this.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
                }
            val metrics = paint.fontMetrics
            val tickGap = 2f.dp
            val tickLength = 5f.dp
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_AXIS_TICK)
            linePaint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = null
            val x = if (onRight) plot.right + tickGap + tickLength + 2f.dp else plot.left - tickGap - tickLength - 2f.dp
            val minimumSpacing = metrics.descent - metrics.ascent + 2f.dp
            var previousY = Float.NEGATIVE_INFINITY
            values.sortedBy { it.second }.forEach { (label, y) ->
                if (abs(y - edgeTop) < minimumSpacing || abs(y - plot.bottom) < minimumSpacing || y - previousY < minimumSpacing) return@forEach
                previousY = y
                val primary = label in primaryLabels
                paint.color = mobileScaleLabelColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), primary)
                paint.alpha = (Color.alpha(paint.color) * alpha.coerceIn(0f, 1f)).toInt()
                app.sugarlicious.uishared.SystemTypography
                    .apply(paint, primary)
                if (onRight) {
                    canvas.drawLine(plot.right + tickGap, y, plot.right + tickGap + tickLength, y, linePaint)
                } else {
                    canvas.drawLine(plot.left - tickGap, y, plot.left - tickGap - tickLength, y, linePaint)
                }
                val baseline = y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                canvas.drawText(label, x, baseline, paint)
            }
        }

        private fun drawGraphEdgeScale(
            canvas: Canvas,
            plot: RectF,
            tickY: Float,
            onRight: Boolean,
            maximumMgDl: Double,
            alpha: Float = 1f,
        ) {
            if (alpha <= 0f) return
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
                    color = mobileScaleLabelColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), false)
                    textAlign = if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, false)
                    this.alpha = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
                }
            val tickGap = 2f.dp
            val tickLength = 5f.dp
            val baseline = tickY - paint.fontMetrics.ascent / 2f - paint.fontMetrics.descent / 2f
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_AXIS_TICK)
            linePaint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = null
            if (onRight) {
                canvas.drawLine(plot.right + tickGap, tickY, plot.right + tickGap + tickLength, tickY, linePaint)
            } else {
                canvas.drawLine(plot.left - tickGap, tickY, plot.left - tickGap - tickLength, tickY, linePaint)
            }
            val x = if (onRight) plot.right + tickGap + tickLength + 2f.dp else plot.left - tickGap - tickLength - 2f.dp
            canvas.drawText(glucoseLabel(maximumMgDl), x, baseline, paint)
        }

        private fun drawBasal(
            canvas: Canvas,
            plot: RectF,
            start: Long,
            end: Long,
            points: List<TherapyHistorySample>,
            showCurrent: Boolean,
            showBase: Boolean,
        ) = drawMobileBasalOverlay(canvas, plot, start, end, points, showCurrent, showBase, density, SugarliciousColors.argb(SugarliciousColorRole.SECONDARY), linePaint, fillPaint, maximumOverride = settledBasalMaximum(points, start, end, showCurrent, showBase))

        private fun drawInsulinActivity(
            canvas: Canvas,
            band: RectF,
            start: Long,
            end: Long,
            now: Long,
            points: List<TherapyHistorySample>,
        ) {
            val allActual =
                finiteInsulinActivitySeries(points)
            val actual = cgmInsulinActivitySeries(allActual, now, start, end)
            if (actual.size < 2) return
            val activityScale = cgmInsulinActivityScale(allActual.map { it.second })

            fun activityY(value: Double): Float = mapCgmActivityY(value, activityScale, band)
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY)
            linePaint.strokeWidth = 1.35f.dp
            linePaint.pathEffect = null
            canvas.drawPath(smoothValuePath(actual, start, end, band, ::activityY), linePaint)
        }

        private fun predictionEnabled(kind: PredictionKind): Boolean =
            when (kind) {
                PredictionKind.IOB -> showPredictionIob
                PredictionKind.COB, PredictionKind.ACOB -> showPredictionCob
                PredictionKind.UAM -> showPredictionUam
                PredictionKind.ZERO_TEMP -> showPredictionZeroTemp
            }

        private fun drawPrediction(
            canvas: Canvas,
            series: GlucosePrediction,
            plot: RectF,
            start: Long,
            end: Long,
            yScale: CgmGraphYScale,
        ) {
            val color =
                when (series.kind) {
                    PredictionKind.IOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_IOB)
                    PredictionKind.COB, PredictionKind.ACOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_COB)
                    PredictionKind.UAM -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_UAM)
                    PredictionKind.ZERO_TEMP -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP)
                }
            val radius = predictionDotRadiusDp.dp
            val outlineWidth = predictionDotOutlineWidthDp.dp
            val maximumPredictionCenter = plot.right - radius - outlineWidth / 2f - 1f.dp
            series.samples.forEach { point ->
                val x = mapX(point.measuredAtEpochMs, start, end, plot)
                if (x < plot.left - radius) return@forEach
                if (x > maximumPredictionCenter) return@forEach
                // ratio() clamps by design; skip overflow instead of piling dots along the edge.
                if (!point.valueMgDl.isFinite() || point.valueMgDl < yScale.minimumMgDl || point.valueMgDl > yScale.maximumMgDl) return@forEach
                val y = mapGlucoseY(point.valueMgDl, plot, yScale)
                if (outlineWidth > 0f) {
                    dotOutlinePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
                    dotOutlinePaint.strokeWidth = outlineWidth
                    canvas.drawCircle(x, y, radius + outlineWidth / 2f, dotOutlinePaint)
                }
                fillPaint.color = color
                canvas.drawCircle(x, y, radius, fillPaint)
            }
        }

        companion object {
            private const val CLOCK_REFRESH_MS = 30_000L
        }

        private fun drawRoundedBorder(
            canvas: Canvas,
            rect: RectF,
            radius: Float,
        ) {
            linePaint.style = Paint.Style.STROKE
            linePaint.pathEffect = null
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
            linePaint.strokeWidth = 1f.dp
            canvas.drawRoundRect(rect, radius, radius, linePaint)
        }

        private fun dotColor(
            value: Double,
            thresholds: CgmThresholds,
        ): Int =
            when (thresholds.classify(value)) {
                CgmRangeClass.VERY_LOW -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_VERY_LOW)
                CgmRangeClass.LOW -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_LOW)
                CgmRangeClass.IN_RANGE -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE)
                CgmRangeClass.HIGH -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_HIGH)
                CgmRangeClass.VERY_HIGH -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_VERY_HIGH)
                null -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE)
            }

        private fun glucoseLabel(valueMgDl: Double): String = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0) else valueMgDl.toInt().toString()

        private val Float.dp get() = this * density
    }

@SuppressLint("DrawAllocation")
internal class MetabolicDashboardChart
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        sharedViewport: ChartViewport = ChartViewport(6),
        private val persistAxisScale: Boolean = true,
        private val drawOutline: Boolean = true,
    ) : InteractiveChartView(context, attrs, sharedViewport) {
        private val density = resources.displayMetrics.density
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var state: TherapyDisplayState? = null
        private var boundDurationHours: Int? = null
        private var stateSignature: List<Any?>? = null
        private var markerVisibility = TreatmentMarkerVisibility()
        private var renderNowEpochMs: Long = System.currentTimeMillis()
        private val smoothMetabolicScale =
            SettledGraphScale<MobileMetabolicScales> { a, b, f ->
                fun axis(x: GraphAxisScale, y: GraphAxisScale) = y.copy(bounds = GraphBounds(x.bounds.minimum + (y.bounds.minimum - x.bounds.minimum) * f, x.bounds.maximum + (y.bounds.maximum - x.bounds.maximum) * f))
                MobileMetabolicScales(axis(a.iob, b.iob), axis(a.cob, b.cob), axis(a.activity, b.activity))
            }
        private var scaleRightBlend = 0f
        private var showTimeAxis = true
        private var iobGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC
        private var cobGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC
        private var showPredictionDivider = false
        private var showBasalOverlay = false
        private var iobMaximumUnits = 10f
        private var cobMaximumGrams = 300f
        private val staticScaleStore = StaticGraphScaleStore(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE))
        private val axisScaleSession = GraphScaleSession().also { if (persistAxisScale) staticScaleStore.restore(it) }

        fun bind(
            state: TherapyDisplayState?,
            durationHours: Int,
            markerVisibility: TreatmentMarkerVisibility = TreatmentMarkerVisibility(),
            scaleRightBlend: Float = 0f,
            showTimeAxis: Boolean = true,
            iobGraphScaleMode: CgmGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC,
            cobGraphScaleMode: CgmGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC,
            iobMaximumUnits: Float = 10f,
            cobMaximumGrams: Float = 300f,
            showPredictionDivider: Boolean = false,
            clockEpochMs: Long = System.currentTimeMillis(),
            showBasalOverlay: Boolean = false,
        ) {
            val clockBucket = clockEpochMs / 30_000L
            val newStateSignature =
                listOf(
                    state?.glucose,
                    state?.therapyHistory,
                    state?.therapyEvents,
                    markerVisibility,
                    scaleRightBlend,
                    showTimeAxis,
                    iobGraphScaleMode,
                    cobGraphScaleMode,
                    iobMaximumUnits,
                    cobMaximumGrams,
                    showPredictionDivider,
                    showBasalOverlay,
                    clockBucket,
                )
            if (stateSignature == newStateSignature && boundDurationHours == durationHours) return
            this.state = state
            stateSignature = newStateSignature
            boundDurationHours = durationHours
            this.markerVisibility = markerVisibility
            this.scaleRightBlend = scaleRightBlend.coerceIn(0f, 1f)
            this.showTimeAxis = showTimeAxis
            this.iobGraphScaleMode = iobGraphScaleMode
            this.cobGraphScaleMode = cobGraphScaleMode
            this.showPredictionDivider = showPredictionDivider
            this.showBasalOverlay = showBasalOverlay
            if (this.iobMaximumUnits != iobMaximumUnits) axisScaleSession.clear(GraphAxis.IOB)
            if (this.cobMaximumGrams != cobMaximumGrams) axisScaleSession.clear(GraphAxis.COB)
            this.iobMaximumUnits = iobMaximumUnits
            this.cobMaximumGrams = cobMaximumGrams
            renderNowEpochMs = clockEpochMs
            if (!isAttachedToWindow) viewport.setHours(durationHours.toFloat())
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bounds =
                mobileMetabolicGraphBounds(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    outlineInset = 0.5f.dp,
                    timeAxisHeight = if (showTimeAxis) TIME_AXIS_HEIGHT_DP.dp else 0f,
                    valueAxisWidth = VALUE_AXIS_WIDTH_DP.dp,
                    scaleRightBlend = scaleRightBlend,
                    separatorHeight = 2f.dp,
                    markerHeadroomMaximum = 24f.dp,
                )
            val outer = bounds.outer
            if (outer.width() <= 24f || outer.height() <= 24f) return
            val radius = GRAPH_CORNER_RADIUS_DP.dp
            val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
            canvas.withClip(clip) {
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.SURFACE)
                canvas.drawRoundRect(outer, radius, radius, fillPaint)
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
                canvas.drawRoundRect(bounds.plotRegion, radius, radius, fillPaint)
                val chartNow = renderNowEpochMs
                val viewportSnapshot = viewport.snapshot(chartNow)
                val end = viewportSnapshot.endEpochMs
                val start = viewportSnapshot.startEpochMs
                val allPoints = state?.therapyHistory.orEmpty()
                val points = allPoints.filter { it.measuredAtEpochMs in start..end }
                val left = bounds.plotRegion.left
                val right = bounds.plotRegion.right
                val top = bounds.plotRegion.top
                val bottom = bounds.plotRegion.bottom
                val iobPlot = bounds.iobLane
                val cobLanePlot = bounds.cobLane
                val iobDataPlot = bounds.iobData
                val cobPlot = bounds.cobData
                // AndroidAPS calculates IOB/COB samples at five-minute timestamps and draws those
                // actual samples. Sugarlicious must not fabricate a future decay from two observations.
                val proposedScales =
                    resolveMetabolicScales(
                        session = axisScaleSession,
                        iobMode = iobGraphScaleMode,
                        cobMode = cobGraphScaleMode,
                        allPoints = allPoints,
                        visiblePoints = points,
                        iobMaximumUnits = iobMaximumUnits.toDouble(),
                        cobMaximumGrams = cobMaximumGrams.toDouble(),
                        therapyEvents = state?.therapyEvents.orEmpty(),
                        viewportStartEpochMs = start,
                        viewportEndEpochMs = end,
                    )
                val scales = smoothMetabolicScale.resolve(proposedScales, viewport.isNavigatingGesture, android.os.SystemClock.uptimeMillis())
                if (smoothMetabolicScale.animating) postInvalidateOnAnimation()
                if (persistAxisScale) {
                    staticScaleStore.persist(GraphAxis.IOB, proposedScales.iob)
                    staticScaleStore.persist(GraphAxis.COB, proposedScales.cob)
                }
                val iobRange = scales.iob
                val cobRange = scales.cob
                val dividerTimestamp = viewportSnapshot.liveEdgeEpochMs
                // Keep the semantic boundary in data coordinates. A historical viewport may move it
                // completely off-screen; clamping it to an edge would turn it into a sticky overlay.
                val dividerX = mapX(dividerTimestamp, start, end, iobDataPlot)
                canvas.withClip(Path().apply { addRoundRect(bounds.plotRegion, radius, radius, Path.Direction.CW) }) {
                    if (showBasalOverlay) {
                        drawMobileBasalOverlay(canvas, iobPlot, start, end, allPoints, true, true, this@MetabolicDashboardChart.density, SugarliciousColors.argb(SugarliciousColorRole.SECONDARY), maximumOverride = settledBasalMaximum(allPoints, start, end, true, true))
                    }
                    drawLane(canvas, iobDataPlot, points, start, end, dividerTimestamp, iob = true, range = iobRange, drawScale = false)
                    drawInsulinActivity(canvas, iobDataPlot, allPoints, start, end, dividerTimestamp, scales.activity, scales.iob)
                    drawLane(canvas, cobPlot, points, start, end, dividerTimestamp, iob = false, range = cobRange, drawScale = false)
                    if (showPredictionDivider && dividerTimestamp in start..end) {
                        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_DIVIDER)
                        linePaint.strokeWidth = 1f.dp
                        linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
                        canvas.drawLine(dividerX, iobPlot.top, dividerX, cobPlot.bottom, linePaint)
                        linePaint.pathEffect = null
                    }

                    drawTreatmentMarkers(
                        canvas,
                        iobDataPlot,
                        cobPlot,
                        iobPlot.top,
                        allPoints,
                        state?.therapyEvents.orEmpty(),
                        points,
                        start,
                        end,
                        iobRange,
                        cobRange,
                    )
                    if (points.none { it.totalIob != null || it.cobGrams != null }) {
                        drawText(
                            canvas,
                            "Noch kein IOB/COB-Verlauf",
                            (left + right) / 2f,
                            (top + bottom) / 2f,
                            10f,
                            SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED),
                            Paint.Align.CENTER,
                        )
                    }
                }
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.SURFACE)
                canvas.drawRect(bounds.separator, fillPaint)
                drawMetabolicScale(canvas, iobDataPlot, iobRange, onRight = false, alpha = 1f - scaleRightBlend)
                drawMetabolicScale(canvas, iobDataPlot, iobRange, onRight = true, alpha = scaleRightBlend)
                drawMetabolicScale(canvas, cobPlot, cobRange, onRight = false, alpha = 1f - scaleRightBlend)
                drawMetabolicScale(canvas, cobPlot, cobRange, onRight = true, alpha = scaleRightBlend)
                drawMetabolicZeroLine(canvas, iobDataPlot, iobRange, onRight = false, label = "0 U", alpha = 1f - scaleRightBlend, drawGridLine = true)
                drawMetabolicZeroLine(canvas, iobDataPlot, iobRange, onRight = true, label = "0 U", alpha = scaleRightBlend, drawGridLine = false)
                drawMetabolicZeroLine(canvas, cobPlot, cobRange, onRight = false, label = "0 g", alpha = 1f - scaleRightBlend, drawGridLine = true)
                drawMetabolicZeroLine(canvas, cobPlot, cobRange, onRight = true, label = "0 g", alpha = scaleRightBlend, drawGridLine = false)
                if (showTimeAxis) {
                    drawSharedGrid(canvas, iobDataPlot, cobPlot, outer.bottom, start, end, chartNow, dividerX)
                }
            }
            if (drawOutline) {
                linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
                linePaint.strokeWidth = 1f.dp
                linePaint.pathEffect = null
                canvas.drawRoundRect(outer, radius, radius, linePaint)
            }
        }

        private fun drawSharedGrid(
            canvas: Canvas,
            iob: RectF,
            cob: RectF,
            axisBottom: Float,
            start: Long,
            end: Long,
            now: Long,
            nowLineX: Float,
        ) {
            val ticks = RelativeGraphTimeAxis.ticks(start, end, now, viewport.axisIntervalHours)
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_AXIS_TICK)
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = null
            ticks.forEach { tick ->
                val isNow = tick.hoursBack == 0
                val x = if (isNow) nowLineX else mapX(tick.timestampEpochMs, start, end, cob)
                if (x < cob.left || x > cob.right) return@forEach
                val align =
                    when {
                        tick.timestampEpochMs <= start + 30_000L -> Paint.Align.LEFT
                        !persistAxisScale && tick.timestampEpochMs >= end - 30_000L -> Paint.Align.RIGHT
                        isNow -> Paint.Align.CENTER
                        else -> Paint.Align.CENTER
                    }
                val labelX =
                    when (align) {
                        Paint.Align.LEFT -> cob.left + 3f.dp
                        Paint.Align.RIGHT -> cob.right - 3f.dp
                        else -> x
                    }
                val labelSize = 11.5f
                val tickX = timeLabelCenterX(tick.label, labelX, align, labelSize)
                canvas.drawLine(tickX, cob.bottom + 2f.dp, tickX, cob.bottom + 8f.dp, linePaint)
                drawText(
                    canvas,
                    tick.label,
                    labelX,
                    axisBottom - 4f.dp,
                    labelSize,
                    SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL),
                    align,
                    bold = false,
                )
            }
        }

        private fun drawLane(
            canvas: Canvas,
            plot: RectF,
            points: List<TherapyHistorySample>,
            start: Long,
            end: Long,
            liveEdge: Long,
            iob: Boolean,
            range: GraphAxisScale,
            drawScale: Boolean = true,
        ) {
            val actual =
                extendSeriesToLiveEdge(
                    points
                        .mapNotNull { point ->
                            (if (iob) point.totalIob else point.cobGrams)?.takeIf { it.isFinite() }?.let { point.measuredAtEpochMs to it }
                        },
                    liveEdge,
                    start,
                    end,
                )
            if (actual.isEmpty()) return

            fun y(value: Double) = mapAxisY(value, range, plot)
            val zeroY = y(0.0)
            val color = SugarliciousColors.argb(if (iob) SugarliciousColorRole.GRAPH_IOB else SugarliciousColorRole.GRAPH_COB)
            val area = valuePathClosedAt(actual, start, end, plot, ::y, zeroY)
            fillPaint.shader =
                LinearGradient(0f, plot.top, 0f, plot.bottom, withAlpha(color, 112), withAlpha(color, 7), Shader.TileMode.CLAMP)
            canvas.drawPath(area, fillPaint)
            fillPaint.shader = null
            linePaint.color = color
            linePaint.strokeWidth = 2.35f.dp
            linePaint.pathEffect = null
            canvas.drawPath(
                valuePath(actual, start, end, plot, ::y),
                linePaint,
            )
            if (drawScale) {
                drawMetabolicScale(canvas, plot, range, onRight = false, alpha = 1f - scaleRightBlend)
                drawMetabolicScale(canvas, plot, range, onRight = true, alpha = scaleRightBlend)
            }
        }

        private fun drawMetabolicScale(
            canvas: Canvas,
            plot: RectF,
            range: GraphAxisScale,
            onRight: Boolean,
            alpha: Float = 1f,
        ) {
            if (alpha <= 0f) return
            val tickGap = 2f.dp
            val tickLength = 5f.dp
            val labelX = if (onRight) plot.right + tickGap + tickLength + 2f.dp else plot.left - tickGap - tickLength - 2f.dp
            val labelColor = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), (255f * alpha.coerceIn(0f, 1f)).toInt())
            val scaleTextSizeSp = 11.5f
            val scalePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, scaleTextSizeSp, resources.displayMetrics)
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, false)
                }
            val scaleMetrics = scalePaint.fontMetrics
            val tickLayout = metabolicAxisTickLayout(plot, range, scaleMetrics)
            val align = if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT
            drawText(
                canvas,
                formatMetabolicScale(range.bounds.maximum),
                labelX,
                tickLayout.maximumBaselineY,
                scaleTextSizeSp,
                labelColor,
                align,
                bold = false,
            )
            drawMetabolicScaleTick(canvas, plot, tickLayout.maximumCenterY, onRight, alpha)
            if (range.bounds.minimum < -0.01) {
                drawText(
                    canvas,
                    formatMetabolicScale(range.bounds.minimum),
                    labelX,
                    tickLayout.minimumBaselineY,
                    scaleTextSizeSp,
                    labelColor,
                    align,
                    bold = false,
                )
                drawMetabolicScaleTick(canvas, plot, tickLayout.minimumCenterY, onRight, alpha)
            }
        }

        private fun drawMetabolicScaleTick(
            canvas: Canvas,
            plot: RectF,
            centerY: Float,
            onRight: Boolean,
            alpha: Float = 1f,
        ) {
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_AXIS_TICK)
            linePaint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = null
            if (onRight) {
                canvas.drawLine(plot.right + 2f.dp, centerY, plot.right + 7f.dp, centerY, linePaint)
            } else {
                canvas.drawLine(plot.left - 2f.dp, centerY, plot.left - 7f.dp, centerY, linePaint)
            }
        }

        private fun drawMetabolicZeroLine(
            canvas: Canvas,
            plot: RectF,
            range: GraphAxisScale,
            onRight: Boolean,
            label: String,
            alpha: Float = 1f,
            drawGridLine: Boolean = true,
        ) {
            val zeroY = mapAxisY(0.0, range, plot).coerceIn(plot.top, plot.bottom)
            if (drawGridLine) {
                linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_GRID)
                linePaint.alpha = 255
                linePaint.strokeWidth = 0.8f.dp
                linePaint.pathEffect = null
                canvas.drawLine(plot.left, zeroY, plot.right, zeroY, linePaint)
            }
            if (alpha <= 0f) return
            val labelX = if (onRight) plot.right + 9f.dp else plot.left - 9f.dp
            val baseline = (zeroY - 2f.dp).coerceIn(plot.top + 10f.dp, plot.bottom - 2f.dp)
            drawText(
                canvas,
                label,
                labelX,
                baseline,
                11.5f,
                withAlpha(mobileScaleLabelColor(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), range.bounds.minimum >= -0.01), (255f * alpha.coerceIn(0f, 1f) * if (range.bounds.minimum >= -0.01) 1f else .55f).toInt()),
                if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT,
                bold = false,
            )
        }

        private fun drawInsulinActivity(
            canvas: Canvas,
            plot: RectF,
            visiblePoints: List<TherapyHistorySample>,
            start: Long,
            end: Long,
            liveEdge: Long,
            activityScale: GraphAxisScale,
            hostScale: GraphAxisScale,
        ) {
            val actual =
                smoothActivityToLiveEdge(
                    finiteInsulinActivitySeries(visiblePoints),
                    liveEdge,
                    start,
                    end,
                )
            if (actual.size < 2 || actual.all { it.second == 0.0 }) return

            fun y(value: Double) = mapActivityY(value, activityScale, plot, hostScale)
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY)
            linePaint.strokeWidth = 1.6f.dp
            linePaint.pathEffect = null
            canvas.drawPath(smoothValuePath(actual, start, end, plot, ::y), linePaint)
        }

        private fun drawTreatmentMarkers(
            canvas: Canvas,
            iobData: RectF,
            cobData: RectF,
            iobLaneTop: Float,
            history: List<TherapyHistorySample>,
            events: List<TherapyEvent>,
            legacyPoints: List<TherapyHistorySample>,
            start: Long,
            end: Long,
            iobRange: GraphAxisScale,
            cobRange: GraphAxisScale,
        ) {
            val visible =
                events
                    .flatMap(::expandECarbSimulation)
                    .filter {
                        it.timestampEpochMs in start..end &&
                            it.amount.isFinite() &&
                            it.amount > 0.0 &&
                            markerVisibility.shows(it.kind)
                    }.filterNot { it.kind == TherapyEventKind.ECARBS && it.amount < 1.0 }
            val explicitSmbTimes = visible.filter { it.kind == TherapyEventKind.SMB }.map { it.timestampEpochMs }.toSet()
            val legacySmb =
                legacyPoints.mapNotNull { point ->
                    point.smbUnits
                        ?.takeIf {
                            it > 0.0 &&
                                explicitSmbTimes.none { time ->
                                    kotlin.math.abs(time - point.measuredAtEpochMs) < 1_000L
                                }
                        }?.let {
                            TherapyEvent(
                                "legacy-smb:${point.measuredAtEpochMs}:$it",
                                TherapyEventKind.SMB,
                                point.measuredAtEpochMs,
                                it,
                            )
                        }
                }
            val zeroY = mapAxisY(0.0, iobRange, iobData)
            val cobZeroY = mapAxisY(0.0, cobRange, cobData)
            (visible + legacySmb).forEach { event ->
                val side = treatmentMarkerSide(event.kind, event.amount).dp
                val half = side / 2f
                val height = side * (sqrt(3.0).toFloat() / 2f)
                val eventPlot =
                    when (event.kind) {
                        // COB labels may use the dedicated separator above the COB
                        // lane. The marker and curve still remain inside cobData.
                        TherapyEventKind.MEAL_CARBS, TherapyEventKind.ECARBS ->
                            RectF(cobData.left, iobData.bottom, cobData.right, cobData.bottom)
                        else -> RectF(iobData.left, iobLaneTop, iobData.right, iobData.bottom)
                    }
                canvas.withClip(eventPlot) {
                    val upperRadius = if (event.kind == TherapyEventKind.MEAL_CARBS || event.kind == TherapyEventKind.ECARBS) 0f else GRAPH_CORNER_RADIUS_DP.dp
                    when (event.kind) {
                        TherapyEventKind.SMB -> {
                            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_IOB)
                            val x = mapX(event.timestampEpochMs, start, end, iobData)
                            val bottomY = (zeroY + height).coerceAtMost(iobData.bottom)
                            if (therapyInkClearsUpperContour(x - half, x + half, bottomY - height, eventPlot.left, eventPlot.right, eventPlot.top, upperRadius)) {
                                drawPath(roundedUpTriangle(x, bottomY, half, height, 2.4f.dp), fillPaint)
                            }
                        }
                        TherapyEventKind.MANUAL_CORRECTION ->
                            drawBolusMarkerAboveZero(
                                this,
                                event,
                                iobData,
                                start,
                                end,
                                zeroY,
                                side,
                                showAmount = false,
                                upperBoundary = eventPlot,
                                upperRadius = upperRadius,
                            )
                        TherapyEventKind.MEAL_BOLUS ->
                            drawCurveMarker(
                                this,
                                event,
                                iobData,
                                history,
                                start,
                                end,
                                iobRange,
                                true,
                                side,
                                "U",
                                labelGapDp = 2f,
                                upperBoundary = eventPlot,
                                upperRadius = upperRadius,
                            )
                        TherapyEventKind.MEAL_CARBS ->
                            drawCurveMarker(
                                this,
                                event,
                                cobData,
                                history,
                                start,
                                end,
                                cobRange,
                                false,
                                side,
                                "g",
                                labelGapDp = 2f,
                                upperBoundary = eventPlot,
                                upperRadius = upperRadius,
                            )
                        TherapyEventKind.ECARBS -> drawCarbMarkerAtZero(this, event, cobData, start, end, cobZeroY, side, eventPlot, upperRadius)
                    }
                }
            }
        }

        private fun drawBolusMarkerAboveZero(
            canvas: Canvas,
            event: TherapyEvent,
            plot: RectF,
            start: Long,
            end: Long,
            zeroY: Float,
            side: Float,
            showAmount: Boolean,
            upperBoundary: RectF,
            upperRadius: Float,
        ) {
            val half = side / 2f
            val height = side * (sqrt(3.0).toFloat() / 2f)
            val x = mapX(event.timestampEpochMs, start, end, plot)
            if (!therapyInkClearsUpperContour(x - half, x + half, zeroY - height, upperBoundary.left, upperBoundary.right, upperBoundary.top, upperRadius)) return
            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_IOB)
            canvas.drawPath(roundedDownTriangle(x, zeroY, half, height, 2.4f.dp), fillPaint)
            if (showAmount) {
                drawMarkerLabel(canvas, formatEventAmount(event.amount, "U"), x, zeroY - height, 3f, 8f, fillPaint.color, upperBoundary, upperRadius)
            }
        }

        private fun drawCarbMarkerAtZero(
            canvas: Canvas,
            event: TherapyEvent,
            plot: RectF,
            start: Long,
            end: Long,
            zeroY: Float,
            side: Float,
            upperBoundary: RectF,
            upperRadius: Float,
        ) {
            val half = side / 2f
            val height = side * (sqrt(3.0).toFloat() / 2f)
            val x = mapX(event.timestampEpochMs, start, end, plot)
            if (!therapyInkClearsUpperContour(x - half, x + half, zeroY - height, upperBoundary.left, upperBoundary.right, upperBoundary.top, upperRadius)) return
            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_COB)
            canvas.drawPath(roundedDownTriangle(x, zeroY, half, height, 2.4f.dp), fillPaint)
            drawMarkerLabel(
                canvas = canvas,
                value = formatEventAmount(event.amount, "g"),
                markerCenterX = x,
                markerTopY = zeroY - height,
                gapDp = 2f,
                sizeSp = 7f,
                color = fillPaint.color,
                upperBoundary = upperBoundary,
                upperRadius = upperRadius,
            )
        }

        private fun drawCurveMarker(
            canvas: Canvas,
            event: TherapyEvent,
            plot: RectF,
            history: List<TherapyHistorySample>,
            start: Long,
            end: Long,
            range: GraphAxisScale,
            iob: Boolean,
            side: Float,
            unit: String?,
            labelGapDp: Float = 3f,
            upperBoundary: RectF,
            upperRadius: Float,
        ) {
            val value = interpolateTherapyValue(history, event.timestampEpochMs, iob) ?: 0.0
            val half = side / 2f
            val height = side * (sqrt(3.0).toFloat() / 2f)
            val x = mapX(event.timestampEpochMs, start, end, plot)
            val y = mapAxisY(value, range, plot)
            if (!therapyInkClearsUpperContour(x - half, x + half, y - height, upperBoundary.left, upperBoundary.right, upperBoundary.top, upperRadius)) return
            fillPaint.color = SugarliciousColors.argb(if (iob) SugarliciousColorRole.GRAPH_IOB else SugarliciousColorRole.GRAPH_COB)
            canvas.drawPath(roundedDownTriangle(x, y, half, height, 2.4f.dp), fillPaint)
            unit?.let {
                drawMarkerLabel(
                    canvas = canvas,
                    value = formatEventAmount(event.amount, it),
                    markerCenterX = x,
                    markerTopY = y - height,
                    gapDp = labelGapDp,
                    sizeSp = 8f,
                    color = fillPaint.color,
                    upperBoundary = upperBoundary,
                    upperRadius = upperRadius,
                )
            }
        }

        private fun drawMarkerLabel(
            canvas: Canvas,
            value: String,
            markerCenterX: Float,
            markerTopY: Float,
            gapDp: Float,
            sizeSp: Float,
            color: Int,
            upperBoundary: RectF,
            upperRadius: Float,
        ) {
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp * 1.15f, resources.displayMetrics)
                    this.color = color
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, true)
                    textAlign = Paint.Align.LEFT
                }
            val visibleBounds = android.graphics.Rect()
            paint.getTextBounds(value, 0, value.length, visibleBounds)
            val left = markerCenterX - visibleBounds.exactCenterX()
            val baseline = markerTopY - gapDp.dp - visibleBounds.bottom
            if (!therapyInkClearsUpperContour(left + visibleBounds.left, left + visibleBounds.right, baseline + visibleBounds.top, upperBoundary.left, upperBoundary.right, upperBoundary.top, upperRadius)) return
            canvas.drawText(value, left, baseline, paint)
        }

        private val Float.dp get() = this * density
    }

internal data class MobileMetabolicScales(
    val iob: GraphAxisScale,
    val cob: GraphAxisScale,
    val activity: GraphAxisScale,
)

internal data class MetabolicAxisTickLayout(
    val maximumCenterY: Float,
    val maximumBaselineY: Float,
    val minimumCenterY: Float,
    val minimumBaselineY: Float,
    val zeroCenterY: Float,
)

internal fun metabolicAxisTickLayout(
    plot: RectF,
    range: GraphAxisScale,
    textMetrics: Paint.FontMetrics,
): MetabolicAxisTickLayout {
    fun centerY(value: Double): Float = plot.bottom - range.ratio(value).toFloat() * plot.height()

    fun baseline(centerY: Float): Float = centerY - (textMetrics.ascent + textMetrics.descent) / 2f

    val maximumCenterY = centerY(range.bounds.maximum)
    val minimumCenterY = centerY(range.bounds.minimum)
    return MetabolicAxisTickLayout(
        maximumCenterY = maximumCenterY,
        maximumBaselineY = baseline(maximumCenterY),
        minimumCenterY = minimumCenterY,
        minimumBaselineY = baseline(minimumCenterY),
        zeroCenterY = centerY(0.0),
    )
}

/** Resolves every secondary axis through the same active mode without mixing their units. */
internal fun resolveMetabolicScales(
    session: GraphScaleSession,
    mode: CgmGraphScaleMode = CgmGraphScaleMode.LOGARITHMIC,
    allPoints: List<TherapyHistorySample>,
    visiblePoints: List<TherapyHistorySample>,
    iobMode: CgmGraphScaleMode = mode,
    cobMode: CgmGraphScaleMode = mode,
    iobMaximumUnits: Double? = null,
    cobMaximumGrams: Double? = null,
    therapyEvents: List<app.sugarlicious.model.TherapyEvent> = emptyList(),
    viewportStartEpochMs: Long = Long.MIN_VALUE,
    viewportEndEpochMs: Long = Long.MAX_VALUE,
): MobileMetabolicScales {
    // Restored static bounds must still include the current configured maximum.
    if (iobMode == CgmGraphScaleMode.STATIC || iobMode == CgmGraphScaleMode.LOGARITHMIC) {
        val fixed = session.fixedBounds(GraphAxis.IOB)
        if (fixed != null && iobMaximumUnits != null && iobMaximumUnits.isFinite() && fixed.maximum < iobMaximumUnits) session.clear(GraphAxis.IOB)
    }
    return MobileMetabolicScales(
        iob =
            limitIobNegativeBounds(
                session.resolve(
                    axis = GraphAxis.IOB,
                    mode = iobMode,
                    seedValues = allPoints.mapNotNull { it.totalIob },
                    visibleValues = visiblePoints.mapNotNull { it.totalIob },
                    fallbackBounds = GraphBounds(0.0, 1.0),
                    minimumSpan = 0.1,
                    maxTickCount = 3,
                    requiredValues = listOfNotNull(iobMaximumUnits?.takeIf { iobMode != CgmGraphScaleMode.DYNAMIC && iobMode != CgmGraphScaleMode.LOGARITHMIC_DYNAMIC && it.isFinite() && it > 0.0 }),
                ),
            ),
        cob =
            session.resolve(
                axis = GraphAxis.COB,
                mode = cobMode,
                seedValues = allPoints.mapNotNull { it.cobGrams },
                visibleValues = visiblePoints.mapNotNull { it.cobGrams },
                fallbackBounds = GraphBounds(0.0, 10.0),
                minimumSpan = 1.0,
                maxTickCount = 5,
                requiredValues =
                    buildList {
                        cobMaximumGrams?.takeIf { cobMode != CgmGraphScaleMode.DYNAMIC && cobMode != CgmGraphScaleMode.LOGARITHMIC_DYNAMIC && it.isFinite() && it > 0.0 }?.let(::add)
                        if (cobMode == CgmGraphScaleMode.DYNAMIC || cobMode == CgmGraphScaleMode.LOGARITHMIC_DYNAMIC) {
                            relevantCarbScaleFloor(therapyEvents, viewportStartEpochMs, viewportEndEpochMs)?.let(::add)
                        }
                    },
            ),
        activity =
            insulinActivityScale(visiblePoints.mapNotNull { it.insulinActivityUnitsPerMinute }),
    )
}

private fun limitIobNegativeBounds(scale: GraphAxisScale): GraphAxisScale {
    val minimum = max(-2.0, scale.bounds.minimum)
    val maximum = maxOf(scale.bounds.maximum, 0.0, minimum + 0.1)
    return scale.copy(bounds = GraphBounds(minimum, maximum))
}

private fun insulinActivityScale(values: Iterable<Double>): GraphAxisScale {
    val finite = values.filter(Double::isFinite)
    val minimum = min(0.0, finite.minOrNull() ?: 0.0)
    val maximum = max(0.0, finite.maxOrNull() ?: 0.0)
    return GraphAxisScale(
        mode = CgmGraphScaleMode.DYNAMIC,
        bounds = GraphBounds(minimum, max(maximum, minimum + 0.000001)),
    )
}

/** Matches AndroidAPS GraphData.addActivity(0.8): zero at the bottom, peak at 80% of the supplied band. */
internal fun cgmInsulinActivityScale(values: Iterable<Double>): GraphAxisScale {
    val maximumAbsolute = values.filter(Double::isFinite).maxOfOrNull(::abs) ?: 0.0
    return GraphAxisScale(
        mode = CgmGraphScaleMode.DYNAMIC,
        bounds = GraphBounds(0.0, max(maximumAbsolute, 0.000001)),
    )
}

internal fun cgmBasalOverlayBounds(plot: RectF): RectF = RectF(plot)

internal fun relevantCarbScaleFloor(
    events: List<app.sugarlicious.model.TherapyEvent>,
    viewportStartEpochMs: Long,
    viewportEndEpochMs: Long,
): Double? =
    events
        .asSequence()
        .filter { it.kind == TherapyEventKind.MEAL_CARBS || it.kind == TherapyEventKind.ECARBS }
        .filter { it.validated && it.timestampEpochMs <= viewportEndEpochMs }
        .filter { event ->
            val activeMinutes = event.durationMinutes?.takeIf { it > 0 } ?: DEFAULT_CARB_RELEVANCE_MINUTES
            event.timestampEpochMs + activeMinutes * 60_000L >= viewportStartEpochMs
        }.mapNotNull { event -> (event.carbsGrams ?: event.amount).takeIf { it.isFinite() && it > 0.0 } }
        .maxOrNull()

private const val DEFAULT_CARB_RELEVANCE_MINUTES = 6 * 60

internal fun toolkitSmbMarkerSide(units: Double): Float = bolusMarkerSide(units)

internal fun bolusMarkerSide(units: Double): Float =
    when {
        abs(units) <= 0.1 -> 7f
        abs(units) <= 0.2 -> 9f
        abs(units) <= 0.5 -> 11f
        abs(units) <= 1.5 -> 13f
        else -> 16f
    }

internal fun treatmentMarkerSide(
    kind: TherapyEventKind,
    amount: Double,
): Float =
    when (kind) {
        TherapyEventKind.SMB, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.MEAL_BOLUS -> bolusMarkerSide(amount)
        TherapyEventKind.MEAL_CARBS -> 15f
        TherapyEventKind.ECARBS -> 11f
    }

internal fun expandECarbSimulation(event: TherapyEvent): List<TherapyEvent> {
    if (event.kind != TherapyEventKind.ECARBS) return listOf(event)
    val duration = event.durationMinutes?.takeIf { it > 0 } ?: return listOf(event)
    if (!event.amount.isFinite() || event.amount < 1.0) return emptyList()
    // A marker every five minutes makes labels overlap even on a 3 h view.
    // Fifteen-minute portions keep the simulation distributed over its full
    // duration while leaving each amount and triangle readable.
    val timelineStepCount = ((duration + 14) / 15).coerceAtLeast(1)
    val wholeGrams =
        kotlin.math
            .floor(event.amount)
            .toInt()
            .coerceAtLeast(1)
    val gramLimitedStepCount = wholeGrams
    val stepCount = min(timelineStepCount, gramLimitedStepCount)
    val intervalMs = duration * 60_000L / stepCount
    val baseGrams = wholeGrams / stepCount
    val extraWholeGrams = wholeGrams % stepCount
    val fractionalRemainder = event.amount - wholeGrams
    return List(stepCount) { index ->
        val grams =
            baseGrams.toDouble() +
                (if (index < extraWholeGrams) 1.0 else 0.0) +
                (if (index == stepCount - 1) fractionalRemainder else 0.0)
        event.copy(
            id = "${event.id}:simulation:$index",
            timestampEpochMs = event.timestampEpochMs + index * intervalMs,
            amount = grams,
            carbsGrams = grams,
        )
    }
}

internal fun interpolateTherapyValue(
    points: List<TherapyHistorySample>,
    timestamp: Long,
    iob: Boolean,
): Double? {
    val values =
        points
            .mapNotNull { point ->
                (if (iob) point.totalIob else point.cobGrams)?.takeIf(Double::isFinite)?.let { point.measuredAtEpochMs to it }
            }.sortedBy { it.first }
    if (values.isEmpty()) return null
    val before = values.lastOrNull { it.first <= timestamp }
    val after = values.firstOrNull { it.first >= timestamp }
    if (before == null) return after?.second
    if (after == null || before.first == after.first) return before.second
    val ratio = (timestamp - before.first).toDouble() / (after.first - before.first).toDouble()
    return before.second + (after.second - before.second) * ratio
}

internal fun formatEventAmount(
    amount: Double,
    unit: String,
): String {
    val rounded = if (kotlin.math.abs(amount - kotlin.math.round(amount)) < 0.0001) "%.0f" else "%.1f"
    return String.format(Locale.getDefault(), "$rounded %s", amount, unit)
}

internal fun glucoseLogRatio(valueMgDl: Double): Double = GlucoseGraphScale.ratio(valueMgDl)

private fun mapGlucoseY(
    valueMgDl: Double,
    plot: RectF,
    scale: CgmGraphYScale,
): Float = plot.bottom - scale.ratio(valueMgDl).toFloat() * plot.height()

internal fun glucoseLogRatio(
    valueMgDl: Double,
    maximumMgDl: Double,
): Double = GlucoseGraphScale.ratio(valueMgDl, maximumMgDl)

internal fun stableCgmScale(
    previous: CgmGraphYScale?,
    proposed: CgmGraphYScale,
): CgmGraphYScale {
    if (previous == null || previous.mode != proposed.mode) return proposed
    if (proposed.mode == CgmGraphScaleMode.STATIC || proposed.mode == CgmGraphScaleMode.LOGARITHMIC) return proposed
    if (proposed.minimumMgDl < previous.minimumMgDl || proposed.maximumMgDl > previous.maximumMgDl) {
        return proposed.copy(
            minimumMgDl = minOf(previous.minimumMgDl, proposed.minimumMgDl),
            maximumMgDl = maxOf(previous.maximumMgDl, proposed.maximumMgDl),
        )
    }
    val previousSpan = previous.maximumMgDl - previous.minimumMgDl
    val proposedSpan = proposed.maximumMgDl - proposed.minimumMgDl
    return if (proposedSpan <= previousSpan * 0.72) proposed else previous
}

private fun windowedStepSamples(
    points: List<TherapyHistorySample>,
    start: Long,
    end: Long,
): List<TherapyHistorySample> {
    if (points.isEmpty()) return emptyList()
    val seed = points.lastOrNull { it.measuredAtEpochMs <= start }
    val visible = points.filter { it.measuredAtEpochMs in (start + 1)..end }
    val combined =
        buildList {
            seed?.let { add(it.copy(measuredAtEpochMs = start)) }
            addAll(visible)
        }.toMutableList()
    if (combined.isEmpty()) return emptyList()
    if (combined.first().measuredAtEpochMs > start) combined.add(0, combined.first().copy(measuredAtEpochMs = start))
    if (combined.last().measuredAtEpochMs < end) combined += combined.last().copy(measuredAtEpochMs = end)
    return combined
}

private fun effectiveBasal(sample: TherapyHistorySample): Double = sample.tempBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: sample.baseBasalUnitsPerHour ?: 0.0

private fun stepPath(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
    closeAt: Float? = null,
): Path =
    Path().apply {
        if (values.isEmpty()) return@apply
        val firstX = mapX(values.first().first, start, end, plot)
        if (closeAt != null) moveTo(firstX, closeAt) else moveTo(firstX, mapValue(values.first().second))
        if (closeAt != null) lineTo(firstX, mapValue(values.first().second))
        var priorY = mapValue(values.first().second)
        values.drop(1).forEach { (time, value) ->
            val x = mapX(time, start, end, plot)
            lineTo(x, priorY)
            priorY = mapValue(value)
            lineTo(x, priorY)
        }
        if (closeAt != null) {
            lineTo(mapX(values.last().first, start, end, plot), closeAt)
            close()
        }
    }

/**
 * Builds AAPS-style step points: every target change contributes two points at the same time,
 * producing the vertical transition between the two horizontal target sections. Real gaps stay
 * gaps so missing target history is never silently invented.
 */
internal fun targetStepPaths(
    samples: List<TargetSample>,
    start: Long,
    end: Long,
    continuityToleranceMs: Long = 90_000L,
): List<List<Pair<Long, Double>>> = TargetStepTimeline.build(samples, start, end, continuityToleranceMs)

internal fun targetDisplayEndEpochMs(
    sourceEnd: Long,
    temporary: Boolean,
    isCurrent: Boolean,
    now: Long,
    viewportEnd: Long,
): Long {
    if (!isCurrent) return sourceEnd
    val displayBoundary = if (temporary) now else viewportEnd
    return maxOf(sourceEnd, displayBoundary)
}

internal fun contentAnchoredDashPhase(
    viewportStartEpochMs: Long,
    viewportDurationMs: Long,
    plotWidthPx: Float,
    periodPx: Float,
): Float {
    if (viewportDurationMs <= 0L || plotWidthPx <= 0f || periodPx <= 0f) return 0f
    val pixelsFromEpoch = viewportStartEpochMs.toDouble() * plotWidthPx.toDouble() / viewportDurationMs.toDouble()
    return ((pixelsFromEpoch % periodPx + periodPx) % periodPx).toFloat()
}

internal fun currentTimeLabelAnchor(
    dividerX: Float,
    plotRight: Float,
    edgePadding: Float,
): Float = minOf(dividerX, plotRight - edgePadding)

private fun valuePath(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
): Path =
    Path().apply {
        values.forEachIndexed { index, (time, value) ->
            val x = mapX(time, start, end, plot)
            val y = mapValue(value)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

internal data class MonotoneCurveSegment(
    val startX: Float,
    val startY: Float,
    val control1X: Float,
    val control1Y: Float,
    val control2X: Float,
    val control2Y: Float,
    val endX: Float,
    val endY: Float,
)

/** Monotone cubic Hermite interpolation: smooth at samples without inventing extrema. */
internal fun monotoneCurveSegments(points: List<Pair<Float, Float>>): List<MonotoneCurveSegment> {
    val values = points.distinctBy { it.first }.sortedBy { it.first }
    if (values.size < 2) return emptyList()
    val intervals = FloatArray(values.lastIndex) { index -> values[index + 1].first - values[index].first }
    val slopes = FloatArray(values.lastIndex) { index -> (values[index + 1].second - values[index].second) / intervals[index] }
    val tangents = FloatArray(values.size)
    tangents[0] = slopes.first()
    tangents[tangents.lastIndex] = slopes.last()
    for (index in 1 until tangents.lastIndex) {
        val previous = slopes[index - 1]
        val next = slopes[index]
        tangents[index] =
            if (previous == 0f || next == 0f || previous * next <= 0f) {
                0f
            } else {
                val previousInterval = intervals[index - 1]
                val nextInterval = intervals[index]
                val firstWeight = 2f * nextInterval + previousInterval
                val secondWeight = nextInterval + 2f * previousInterval
                (firstWeight + secondWeight) / (firstWeight / previous + secondWeight / next)
            }
    }
    return intervals.indices.map { index ->
        val (startX, startY) = values[index]
        val (endX, endY) = values[index + 1]
        val third = intervals[index] / 3f
        MonotoneCurveSegment(
            startX = startX,
            startY = startY,
            control1X = startX + third,
            control1Y = startY + tangents[index] * third,
            control2X = endX - third,
            control2Y = endY - tangents[index + 1] * third,
            endX = endX,
            endY = endY,
        )
    }
}

private fun smoothValuePath(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
): Path {
    val points = values.map { (time, value) -> mapX(time, start, end, plot) to mapValue(value) }
    val segments = monotoneCurveSegments(points)
    return Path().apply {
        if (segments.isEmpty()) return@apply
        moveTo(segments.first().startX, segments.first().startY)
        segments.forEach { segment ->
            cubicTo(
                segment.control1X,
                segment.control1Y,
                segment.control2X,
                segment.control2Y,
                segment.endX,
                segment.endY,
            )
        }
    }
}

/**
 * Carries the last measured metabolic value to the live edge. This closes the
 * five-minute sampling gap without inventing values inside the prediction lane.
 */
internal fun extendSeriesToLiveEdge(
    values: List<Pair<Long, Double>>,
    liveEdge: Long,
    viewportStart: Long,
    viewportEnd: Long,
): List<Pair<Long, Double>> {
    val actual =
        values
            .asSequence()
            .filter { (time, value) -> time in viewportStart..liveEdge && value.isFinite() }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .toMutableList()
    if (actual.isEmpty() || liveEdge !in viewportStart..viewportEnd) return actual
    if (actual.last().first < liveEdge) actual += liveEdge to actual.last().second
    return actual
}

private fun valuePathClosedAt(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
    baseline: Float,
): Path =
    Path().apply {
        if (values.isEmpty()) return@apply
        moveTo(mapX(values.first().first, start, end, plot), baseline)
        values.forEach { (time, value) -> lineTo(mapX(time, start, end, plot), mapValue(value)) }
        lineTo(mapX(values.last().first, start, end, plot), baseline)
        close()
    }

private fun mapAxisY(
    value: Double,
    scale: GraphAxisScale,
    plot: RectF,
): Float = plot.bottom - scale.ratio(value).toFloat() * plot.height()

internal fun mapActivityY(
    value: Double,
    scale: GraphAxisScale,
    plot: RectF,
    hostScale: GraphAxisScale = scale,
): Float {
    // Activity has a different unit from IOB. Map it into the host's existing space,
    // sharing its zero; retain a single linear factor for positive and negative activity.
    val positive = if (scale.bounds.maximum > 0.0) maxOf(0.0, hostScale.bounds.maximum) * ACTIVITY_HEIGHT_FRACTION / scale.bounds.maximum else Double.POSITIVE_INFINITY
    val negative = if (scale.bounds.minimum < 0.0 && hostScale.bounds.minimum < 0.0) hostScale.bounds.minimum / scale.bounds.minimum else Double.POSITIVE_INFINITY
    val factor = minOf(positive, negative).takeIf { it.isFinite() } ?: 0.0
    return plot.bottom - hostScale.ratio(value * factor).toFloat() * plot.height()
}

internal fun mapCgmActivityY(
    value: Double,
    scale: GraphAxisScale,
    plot: RectF,
): Float = plot.bottom - plot.height() * scale.ratio(value).toFloat() * ACTIVITY_HEIGHT_FRACTION

internal fun cgmInsulinActivityBand(
    dataPlot: RectF,
    targetTop: Float,
    targetBottom: Float,
): RectF =
    RectF(
        dataPlot.left,
        minOf(targetTop, targetBottom).coerceIn(dataPlot.top, dataPlot.bottom),
        dataPlot.right,
        maxOf(targetTop, targetBottom).coerceIn(dataPlot.top, dataPlot.bottom),
    )

internal fun continuousActivitySeries(
    actual: List<Pair<Long, Double>>,
    future: List<Pair<Long, Double>>,
    boundaryTime: Long,
): Pair<List<Pair<Long, Double>>, List<Pair<Long, Double>>> {
    val samples = (actual.filter { it.first < boundaryTime } + future).distinctBy { it.first }.sortedBy { it.first }
    return samples.filter { it.first <= boundaryTime } to samples.filter { it.first >= boundaryTime }
}

private fun formatMetabolicScale(value: Double): String =
    when {
        abs(value) >= 10.0 -> String.format(Locale.getDefault(), "%.0f", value)
        abs(value) >= 1.0 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> String.format(Locale.getDefault(), "%.2f", value)
    }

private fun roundedUpTriangle(
    cx: Float,
    baseY: Float,
    halfWidth: Float,
    height: Float,
    radius: Float,
): Path =
    Path().apply {
        val apexY = baseY - height
        moveTo(cx - halfWidth + radius, baseY)
        lineTo(cx + halfWidth - radius, baseY)
        quadTo(cx + halfWidth, baseY, cx + halfWidth - radius * 0.7f, baseY - radius)
        lineTo(cx + radius * 0.7f, apexY + radius)
        quadTo(cx, apexY, cx - radius * 0.7f, apexY + radius)
        lineTo(cx - halfWidth + radius * 0.7f, baseY - radius)
        quadTo(cx - halfWidth, baseY, cx - halfWidth + radius, baseY)
        close()
    }

internal fun timeToXFraction(
    time: Long,
    start: Long,
    end: Long,
): Float = ((time - start).toDouble() / (end - start).coerceAtLeast(1L)).toFloat()

internal fun targetScaleOnRight(
    hasVisiblePredictions: Boolean,
): Boolean = !hasVisiblePredictions

internal data class PredictionViewportPolicy(
    val effectiveFutureWindowMs: Long,
    val scaleRightBlendOverride: Float?,
)

internal fun resolvePredictionViewportPolicy(
    historyHours: Float,
    predictionsEnabled: Boolean,
    futureWindowMs: Long,
): PredictionViewportPolicy {
    val effectiveFutureWindowMs = futureWindowMs.coerceIn(0L, 2L * HOUR_MS)
    val isFullHistory = historyHours >= MAX_VISIBLE_GRAPH_HOURS
    return PredictionViewportPolicy(
        effectiveFutureWindowMs = effectiveFutureWindowMs,
        scaleRightBlendOverride =
            0f.takeIf {
                isFullHistory && predictionsEnabled && effectiveFutureWindowMs > 0L
            },
    )
}

internal fun predictionScaleRightBlend(
    predictions: List<GlucosePrediction>,
    now: Long,
    viewportStartEpochMs: Long,
    viewportEndEpochMs: Long,
    transitionFraction: Float = 0.08f,
): Float {
    val duration = (viewportEndEpochMs - viewportStartEpochMs).coerceAtLeast(1L)
    val transitionDuration = (duration * transitionFraction.coerceIn(0.01f, 0.5f)).coerceAtLeast(1f)
    val strongestVisiblePresence =
        PredictionDisplayTimeline
            .anchor(predictions, now)
            .asSequence()
            .flatMap { it.samples.asSequence() }
            .map { it.measuredAtEpochMs }
            .filter { it in viewportStartEpochMs..viewportEndEpochMs }
            .maxOfOrNull { timestamp ->
                val distanceToEdge =
                    minOf(
                        timestamp - viewportStartEpochMs,
                        viewportEndEpochMs - timestamp,
                    ).coerceAtLeast(0L)
                (distanceToEdge / transitionDuration).coerceIn(0f, 1f)
            } ?: 0f
    return 1f - strongestVisiblePresence
}

private fun roundedDownTriangle(
    cx: Float,
    apexY: Float,
    halfWidth: Float,
    height: Float,
    radius: Float,
): Path =
    Path().apply {
        val topY = apexY - height
        val safeRadius = radius.coerceAtMost(min(halfWidth, height) * 0.35f)
        moveTo(cx - halfWidth + safeRadius, topY)
        lineTo(cx + halfWidth - safeRadius, topY)
        quadTo(cx + halfWidth, topY, cx + halfWidth - safeRadius * 0.55f, topY + safeRadius)
        lineTo(cx + safeRadius * 0.55f, apexY - safeRadius)
        quadTo(cx, apexY, cx - safeRadius * 0.55f, apexY - safeRadius)
        lineTo(cx - halfWidth + safeRadius * 0.55f, topY + safeRadius)
        quadTo(cx - halfWidth, topY, cx - halfWidth + safeRadius, topY)
        close()
    }

internal data class TreatmentMarkerVisibility(
    val mealBolus: Boolean = true,
    val correction: Boolean = true,
    val smb: Boolean = true,
    val mealCarbs: Boolean = true,
    val eCarbs: Boolean = true,
) {
    fun shows(kind: TherapyEventKind) =
        when (kind) {
            TherapyEventKind.MEAL_BOLUS -> mealBolus
            TherapyEventKind.MANUAL_CORRECTION -> correction
            TherapyEventKind.SMB -> smb
            TherapyEventKind.MEAL_CARBS -> mealCarbs
            TherapyEventKind.ECARBS -> eCarbs
        }
}

private fun mapX(
    time: Long,
    start: Long,
    end: Long,
    plot: RectF,
): Float = plot.left + timeToXFraction(time, start, end) * plot.width()

private fun withAlpha(
    color: Int,
    alpha: Int,
): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

internal fun luminousTargetValueColor(targetBandColor: Int): Int = derivedTargetValueArgb(targetBandColor)

private fun View.drawText(
    canvas: Canvas,
    value: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Int,
    align: Paint.Align,
    bold: Boolean = false,
) {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp * 1.15f, resources.displayMetrics)
            this.color = color
            textAlign = align
            if (bold) {
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        }
    canvas.drawText(value, x, y, paint)
}

private fun View.timeLabelCenterX(
    value: String,
    anchorX: Float,
    align: Paint.Align,
    sizeSp: Float,
): Float {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp * 1.15f, resources.displayMetrics)
        }
    val halfWidth = paint.measureText(value) / 2f
    return when (align) {
        Paint.Align.LEFT -> anchorX + halfWidth
        Paint.Align.RIGHT -> anchorX - halfWidth
        Paint.Align.CENTER -> anchorX
    }
}

/** Secondary labels preserve the selected scale hue while reducing visual emphasis. */
internal fun mobileScaleLabelColor(color: Int, primary: Boolean): Int =
    if (primary) color else withAlpha(color, (Color.alpha(color) * .55f).toInt())
