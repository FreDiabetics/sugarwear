package app.sugarlicious.mobile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.GlucosePrediction
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GlucoseVisualSpec
import app.sugarlicious.model.LoopVisualState
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.loopPresentation
import app.sugarlicious.storage.PredictionDisplayTimeline
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SugarliciousOverviewScreen(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    preferences: DashboardUiPreferences,
    now: Long,
    callbacks: DashboardCallbacks,
    observationError: DashboardObservationError? = null,
) {
    val unit = preferences.unitFor(state)
    val glucose = state?.glucose
    val headerStatus = softMotionHeaderStatus(state, now)
    val freshness = headerStatus.freshness
    val knownGlucose = headerStatus.hasValue
    val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, now)
    val density = LocalDensity.current
    val screenHeightDp =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
                .value
                .roundToInt()
        }
    val metrics = DashboardLayoutMetrics.forScreenHeight(screenHeightDp)
    val gap = 12.dp
    // Extra spacing scrolls with the cards rather than reducing the existing graph heights.
    val referenceGapDp = if (preferences.compact || preferences.showMetabolicGraph) 5 else 8
    val visibility =
        DashboardVisibilityState(
            showTherapyDetails = false,
            showMetabolicGraph = preferences.showMetabolicGraph,
        )
    val preferredCgmGraphHeightDp = maxOf(metrics.glucoseChartHeight, metrics.cgmGraphHeight(visibility.copy(showMetabolicGraph = false), referenceGapDp))
    val metabolicGraphHeightDp = overviewMetabolicGraphHeight(metrics.metabolicChartHeight)

    val cgmChartViewport =
        rememberSaveable(
            saver =
                listSaver(
                    save = { viewport ->
                        val state = viewport.savedState()
                        listOf(
                            state.historyHours.toRawBits().toLong(),
                            state.navigationEndEpochMs ?: Long.MIN_VALUE,
                        )
                    },
                    restore = { values ->
                        ChartViewport(preferences.graphHours).apply {
                            restore(
                                GraphViewportSavedState(
                                    historyHours = Float.fromBits(values[0].toInt()),
                                    navigationEndEpochMs = values[1].takeUnless { it == Long.MIN_VALUE },
                                ),
                            )
                        }
                    },
                ),
        ) { ChartViewport(preferences.graphHours) }
    val metabolicChartViewport = cgmChartViewport
    var appliedGraphHours by remember { mutableIntStateOf(preferences.graphHours) }
    var sharedViewportSnapshot by remember(cgmChartViewport, now) {
        mutableStateOf(cgmChartViewport.snapshot(now))
    }
    DisposableEffect(cgmChartViewport, now) {
        val listener = { sharedViewportSnapshot = cgmChartViewport.snapshot(now) }
        cgmChartViewport.addListener(listener)
        listener()
        onDispose { cgmChartViewport.removeListener(listener) }
    }

    val enabledPredictions =
        if (preferences.showCgmGraph && preferences.predictionsEnabled) {
            state
                ?.glucosePredictions
                .orEmpty()
                .filter { it.kind in preferences.enabledPredictionKinds }
        } else {
            emptyList()
        }
    val predictionFutureWindowMs =
        predictionFutureWindowMs(
            enabled = preferences.showCgmGraph && preferences.predictionsEnabled,
            predictions = enabledPredictions,
            now = now,
        )
    val predictionViewportPolicy =
        resolvePredictionViewportPolicy(
            historyHours = cgmChartViewport.hours,
            predictionsEnabled = preferences.showCgmGraph && preferences.predictionsEnabled,
            futureWindowMs = predictionFutureWindowMs,
        )
    val sharedScaleRightBlend =
        predictionViewportPolicy.scaleRightBlendOverride
            ?: predictionScaleRightBlend(
                enabledPredictions,
                now,
                sharedViewportSnapshot.startEpochMs,
                sharedViewportSnapshot.endEpochMs,
            )

    LaunchedEffect(
        preferences.graphHours,
    ) {
        if (preferences.graphHours != appliedGraphHours) {
            cgmChartViewport.setHours(
                preferences.graphHours.toFloat(),
                resetPan = true,
            )
            appliedGraphHours = preferences.graphHours
        }
    }

    LaunchedEffect(
        predictionViewportPolicy.effectiveFutureWindowMs,
    ) {
        cgmChartViewport.setFutureWindow(predictionViewportPolicy.effectiveFutureWindowMs, now)
    }

    val glucoseText = if (knownGlucose && glucose != null) formatGlucose(glucose.valueMgDl, unit) else "—"
    val rangePresentation =
        widgetRangePresentation(
            state = state,
            samples = canonicalWidgetSamples(state, now, 24L * 60L * 60_000L),
            thresholds = preferences.cgmThresholds,
            now = now,
        )
    val glucoseColor =
        (if (TherapyDisplayFormatter.freshness(state, now) !in setOf(app.sugarlicious.model.Freshness.CURRENT, app.sugarlicious.model.Freshness.DELAYED)) Color(0xFF9E9E9E) else SugarliciousColors.color(overviewGlucoseColorRole(rangePresentation.visibleRole))).let { color -> if (displayable || TherapyDisplayFormatter.freshness(state, now) == app.sugarlicious.model.Freshness.SIGNAL_LOSS) color else color.copy(alpha = 0.62f) }
    val delta = if (displayable) formatDelta(glucose?.deltaMgDl, unit) else "—"
    val age = if (displayable) TherapyDisplayFormatter.ageMinutes(glucose?.measuredAtEpochMs, now) else "\u2014"
    var glucoseDetailsOpen by remember { mutableStateOf(false) }
    var rangePickerOpen by remember { mutableStateOf(false) }
    var rangePickerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val overviewScroll = rememberScrollState()
    var heroHeightPx by remember { mutableIntStateOf(with(density) { 240.dp.roundToPx() }) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var therapyHeightPx by remember { mutableIntStateOf(0) }
    var cgmHeadingHeightPx by remember { mutableIntStateOf(with(density) { 28.dp.roundToPx() }) }
    var metabolicHeightPx by remember { mutableIntStateOf(with(density) { (metabolicGraphHeightDp + 28).dp.roundToPx() }) }
    val compactVisible by remember { derivedStateOf { overviewScroll.value > heroHeightPx - with(density) { 88.dp.toPx() } } }

    fun collapseFraction(): Float = ((overviewScroll.value - heroHeightPx + with(density) { 88.dp.toPx() }) / with(density) { 48.dp.toPx() }).coerceIn(0f, 1f)

    if (glucoseDetailsOpen) SoftMotionGlucoseDetails(state, now, glucoseText, delta, unitLabel(unit)) { glucoseDetailsOpen = false }
    BoxWithConstraints(Modifier.fillMaxSize().outsideDoubleTap(rangePickerOpen, rangePickerBounds) { rangePickerOpen = false }) {
        val visibleHeights =
            buildList<Float?> {
                if (preferences.showTherapyProgress) add(with(density) { therapyHeightPx.toDp().value })
                if (preferences.showCgmGraph) add(null)
                if (preferences.showMetabolicGraph) add(with(density) { metabolicHeightPx.toDp().value })
            }
        val cgmGraphHeightDp =
            overviewFittedCgmHeight(
                preferredCgmGraphHeightDp.toFloat(),
                maxHeight.value,
                if (maxWidth > maxHeight) 0f else with(density) { (headerHeightPx + if (preferences.showCgmGraph) cgmHeadingHeightPx else 0).toDp().value },
                visibleHeights,
            ).toInt()
        val landscape = maxWidth > maxHeight

        @Composable fun Header() {
            OverviewInlineHeader(
                onSettings = { callbacks.navigate(DashboardScreen.SETTINGS) },
                onWatch = { callbacks.navigate(DashboardScreen.WATCH) },
                modifier =
                    Modifier
                        .onSizeChanged { headerHeightPx = it.height }
                        .then(if (landscape) Modifier else Modifier.overviewHeaderScrollFade(overviewScroll)),
                pinActionsToBottom = landscape,
                glucoseContent = {
                    Box(Modifier.onSizeChanged { heroHeightPx = it.height }) {
                        OverviewGlucoseHero(
                            glucoseText = glucoseText,
                            glucoseColor = glucoseColor,
                            glucoseMgDl = glucose?.valueMgDl,
                            thresholds = preferences.cgmThresholds,
                            trend = if (displayable) glucose?.trend ?: Trend.UNKNOWN else Trend.UNKNOWN,
                            delta = delta,
                            age = age,
                            unitLabel = unitLabel(unit),
                            glucoseScalePercent = preferences.glucoseScalePercent,
                            trendScalePercent = preferences.trendScalePercent,
                            active = headerStatus.waveActive,
                            centeredGlucose = landscape,
                            status = if (observationError != null) "Anzeigedaten nicht verfügbar" else headerStatus.label,
                            onClick = { glucoseDetailsOpen = true },
                        )
                    }
                },
            )
        }

        @Composable fun Cards() {
            // Keep the first card close to the action row; preserve spacing between cards.
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                if (preferences.showTherapyProgress) {
                    Box(Modifier.onSizeChanged { therapyHeightPx = it.height }) {
                        OverviewProgressCard(state.takeIf { displayable }, preferences, now)
                    }
                }

                if (preferences.showCgmGraph) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.onSizeChanged { cgmHeadingHeightPx = it.height + with(density) { 4.dp.roundToPx() } }) {
                            OverviewSectionHeading("Glukoseverlauf und Basalrate")
                        }
                        GlucoseGraphSurface(
                            state = state,
                            preferences = preferences,
                            viewport = cgmChartViewport,
                            chartHeightDp = cgmGraphHeightDp,
                            now = now,
                            onGraphHours = callbacks.setGraphHours,
                        )
                    }
                }

                if (preferences.showMetabolicGraph) {
                    Column(Modifier.onSizeChanged { metabolicHeightPx = it.height }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OverviewSectionHeading("Insulin und Kohlenhydrate")
                        MetabolicGraphSurface(
                            state = state,
                            preferences = preferences,
                            viewport = metabolicChartViewport,
                            chartHeightDp = metabolicGraphHeightDp,
                            now = now,
                            scaleRightBlend = sharedScaleRightBlend,
                        )
                    }
                }
                if (preferences.showTir || preferences.showTitr) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OverviewSectionHeading("Zeit im Zielbereich")
                        OverviewRangeCard(state, now, preferences.showTir, preferences.showTitr, rangePickerOpen, { rangePickerOpen = it }, { rangePickerBounds = it })
                    }
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxSize()
                        .padding(2.dp),
                ) {
                    Header()
                }
                Column(
                    Modifier
                        .weight(0.58f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(2.dp),
                ) {
                    Cards()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(overviewScroll).padding(2.dp)) {
                Header()
                Cards()
            }
        }
        if (!landscape && compactVisible) {
            OverviewCompactGlucoseHeader(
                glucoseText,
                glucoseColor,
                if (displayable) glucose?.trend ?: Trend.UNKNOWN else Trend.UNKNOWN,
                delta,
                unitLabel(unit),
                age,
                Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = collapseFraction() },
                onWatch = { callbacks.navigate(DashboardScreen.WATCH) },
                onSettings = { callbacks.navigate(DashboardScreen.SETTINGS) },
            )
        }
    }
}

@Composable
private fun OverviewSectionHeading(title: String) {
    Text(title, modifier = Modifier.fillMaxWidth(), color = SugarliciousColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

/** Fade content only near the viewport edge, moving through the header with the gesture. */
private fun Modifier.overviewHeaderScrollFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val offset = scroll.value.toFloat()
        if (offset > 0f) {
            val band = minOf(48.dp.toPx(), offset)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White), startY = offset, endY = offset + band),
                blendMode = BlendMode.DstIn,
            )
        }
    }

internal fun hasVisiblePredictions(
    predictions: List<GlucosePrediction>,
    now: Long,
    viewport: GraphViewportSnapshot,
): Boolean =
    PredictionDisplayTimeline
        .anchor(predictions, now)
        .any { series ->
            series.samples.any { it.measuredAtEpochMs in viewport.startEpochMs..viewport.endEpochMs }
        }

internal fun predictionFutureWindowMs(
    enabled: Boolean,
    predictions: List<GlucosePrediction>,
    now: Long,
): Long = if (enabled) PredictionDisplayTimeline.futureWindowMs(predictions, now) else 0L

@Composable
internal fun GlucoseValueAndTrend(
    glucoseText: String,
    glucoseColor: Color,
    trend: Trend,
    visualSpec: GlucoseVisualSpec,
    trendStyle: app.sugarlicious.model.TrendArrowStyle = MobileTrendArrowAppearance.style,
    trendColor: Color = glucoseColor,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = glucoseText,
            color = glucoseColor,
            fontSize = visualSpec.glucoseTextSize.sp,
            lineHeight = (visualSpec.glucoseTextSize * GlucoseTrendSizing.REFERENCE_GLUCOSE_LINE_HEIGHT_SP / GlucoseTrendSizing.REFERENCE_GLUCOSE_TEXT_SP).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.width(6.dp))
        SugarliciousTrendIndicator(trend, arrowSize = visualSpec.trendHeight.dp, color = trendColor, style = trendStyle)
    }
}

internal data class OverviewLoopTileState(
    val iconRes: Int,
    val label: String,
    val accent: Color,
)

internal fun overviewLoopTileState(state: TherapyDisplayState?): OverviewLoopTileState {
    val presentation = loopPresentation(state)
    val (icon, color) =
        when (presentation.visualState) {
            LoopVisualState.PUMP_SUSPENDED -> R.drawable.ic_pump_suspended to SugarliciousColors.Red
            LoopVisualState.SUSPENDED -> R.drawable.ic_loop_suspended to SugarliciousColors.Orange
            LoopVisualState.DEACTIVATED,
            LoopVisualState.UNKNOWN,
            -> R.drawable.ic_loop_deactivated to SugarliciousColors.TextSecondary
            LoopVisualState.CLOSED -> R.drawable.ic_loop_closed to SugarliciousColors.Green
        }
    return OverviewLoopTileState(icon, presentation.label, color)
}

@Composable
private fun GlucoseGraphSurface(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    viewport: ChartViewport,
    chartHeightDp: Int,
    now: Long,
    onGraphHours: (Int) -> Unit,
) {
    var visibleHours by remember(viewport) { mutableFloatStateOf(viewport.visibleHours) }
    val scaleTapInteraction = remember { MutableInteractionSource() }
    DisposableEffect(viewport) {
        val listener = { visibleHours = viewport.visibleHours }
        viewport.addListener(listener)
        onDispose { viewport.removeListener(listener) }
    }
    Box(modifier = Modifier.fillMaxWidth().height(chartHeightDp.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                GlucoseDashboardChart(
                    context = it,
                    sharedViewport = viewport,
                )
            },
            update = {
                it.bindOverview(state, preferences, now)
            },
        )
        Text(
            text = formatVisibleGraphHours(visibleHours),
            color = Color.Transparent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .clickable(indication = null, interactionSource = scaleTapInteraction) {
                        val currentIndex = OVERVIEW_GRAPH_HOUR_OPTIONS.indexOf(preferences.graphHours).coerceAtLeast(0)
                        val next = OVERVIEW_GRAPH_HOUR_OPTIONS[(currentIndex + 1) % OVERVIEW_GRAPH_HOUR_OPTIONS.size]
                        onGraphHours(next)
                    }.padding(start = 9.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
        )
    }
}

internal fun formatVisibleGraphHours(hours: Float): String {
    val bounded = hours.coerceIn(1f, 24f)
    val rounded = bounded.roundToInt()
    return if (kotlin.math.abs(bounded - rounded) < 0.05f) {
        "${rounded}h"
    } else {
        String.format(Locale.getDefault(), "%.1fh", bounded)
    }
}

internal fun GlucoseDashboardChart.bindOverview(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    nowEpochMs: Long,
) {
    bind(
        state = state,
        unit = preferences.unitFor(state),
        showPredictions = preferences.predictionsEnabled,
        durationHours = preferences.graphHours,
        showTargetRange = true,
        showTargetValue = preferences.showCgmTargetValue,
        showCurrentBasal = preferences.showCgmCurrentBasal,
        showBaseBasal = preferences.showCgmBaseBasal,
        showActivity = preferences.showCgmActivity,
        showPredictionIob = preferences.showCgmPredictionIob,
        showPredictionCob = preferences.showCgmPredictionCob,
        showPredictionUam = preferences.showCgmPredictionUam,
        showPredictionZeroTemp = preferences.showCgmPredictionZeroTemp,
        cgmDotRadiusDp = preferences.cgmDotRadiusDp,
        cgmDotOutlineEnabled = preferences.cgmDotOutlineEnabled,
        cgmDotOutlineWidthDp = preferences.cgmDotOutlineWidthDp,
        cgmHistoricalDotOutlineEnabled = preferences.cgmHistoricalDotOutlineEnabled,
        cgmHistoricalDotOutlineWidthDp = preferences.cgmHistoricalDotOutlineWidthDp,
        cgmCurrentDotOutlineWidthDp = preferences.cgmCurrentDotOutlineWidthDp,
        graphMaximumMgDl = preferences.graphMaximumMgDl,
        graphMinimumMgDl = preferences.graphMinimumMgDl,
        graphScaleMode = preferences.graphScaleMode,
        clockEpochMs = nowEpochMs,
    )
}

@Composable
private fun MetabolicGraphSurface(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    viewport: ChartViewport,
    chartHeightDp: Int,
    now: Long,
    scaleRightBlend: Float,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(chartHeightDp.dp),
        factory = {
            MetabolicDashboardChart(
                context = it,
                sharedViewport = viewport,
            )
        },
        update = {
            it.bind(
                state,
                preferences.graphHours,
                TreatmentMarkerVisibility(
                    mealBolus = preferences.showMealBolusMarkers,
                    correction = preferences.showCorrectionMarkers,
                    smb = preferences.showSmbMarkers,
                    mealCarbs = preferences.showMealCarbMarkers,
                    eCarbs = preferences.showECarbMarkers,
                ),
                scaleRightBlend = scaleRightBlend,
                showTimeAxis = !preferences.showCgmGraph,
                iobGraphScaleMode = preferences.iobGraphScaleMode,
                cobGraphScaleMode = preferences.cobGraphScaleMode,
                iobMaximumUnits = preferences.iobGraphMaximumUnits,
                cobMaximumGrams = preferences.cobGraphMaximumGrams,
                showPredictionDivider = preferences.showPredictionDivider,
                clockEpochMs = now,
            )
        },
    )
}

private fun formatGlucose(
    valueMgDl: Double,
    unit: GlucoseUnit,
): String =
    if (unit == GlucoseUnit.MMOL_L) {
        String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0)
    } else {
        valueMgDl.roundToInt().toString()
    }

private fun formatDelta(
    valueMgDl: Double?,
    unit: GlucoseUnit,
): String {
    if (valueMgDl == null) return "—"
    val converted = if (unit == GlucoseUnit.MMOL_L) valueMgDl / 18.0 else valueMgDl
    val prefix = if (converted >= 0.0) "+" else ""
    val body =
        if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.getDefault(), "%.1f", converted)
        } else {
            converted.roundToInt().toString()
        }
    return prefix + body
}

private fun formatNumber(
    value: Double?,
    digits: Int,
): String = value?.let { String.format(Locale.getDefault(), "%.${digits}f", it) } ?: "—"

private fun unitLabel(unit: GlucoseUnit): String = if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"

internal fun overviewGlucoseColorRole(role: WidgetColorRole): SugarliciousColorRole =
    when (role) {
        WidgetColorRole.VERY_HIGH -> SugarliciousColorRole.GLUCOSE_VERY_HIGH
        WidgetColorRole.URGENT_LOW -> SugarliciousColorRole.GLUCOSE_VERY_LOW
        WidgetColorRole.HIGH -> SugarliciousColorRole.GLUCOSE_HIGH
        WidgetColorRole.LOW -> SugarliciousColorRole.GLUCOSE_LOW
        WidgetColorRole.IN_RANGE -> SugarliciousColorRole.GLUCOSE_IN_RANGE
        else -> SugarliciousColorRole.TEXT_PRIMARY
    }
