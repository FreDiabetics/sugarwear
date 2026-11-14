package app.sugarlicious.mobile

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.TherapyBridgeDisplayField
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyIndicatorGeometry
import app.sugarlicious.model.TherapyIndicatorIcon
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.model.TherapyProgressSemantics
import app.sugarlicious.model.TherapyRingGeometry
import app.sugarlicious.model.basalIndicatorIcon
import app.sugarlicious.model.effectiveBasalPresentation
import app.sugarlicious.model.latestDeliveredBolus
import app.sugarlicious.uishared.R as SharedUiR

internal data class TherapyIndicatorPresentation(
    val label: String,
    val value: String,
    val secondary: String? = null,
    val progress: Float? = null,
    @DrawableRes val iconRes: Int,
    val colorRole: SugarliciousColorRole,
    val numericValue: Double? = null,
)

internal fun therapyIndicatorPresentations(
    state: TherapyDisplayState?,
    iobMaximumUnits: Float,
    nowEpochMs: Long,
    cobMaximumGrams: Float = 300f,
): List<TherapyIndicatorPresentation> {
    val iob = state?.insulin?.totalIob?.takeIf(Double::isFinite)
    val cob = state?.carbs?.cobGrams?.takeIf(Double::isFinite)
    val basal = effectiveBasalPresentation(state, nowEpochMs)
    val safeIobMaximum = iobMaximumUnits.takeIf { it > 0f }?.toDouble()
    val safeCobMaximum = cobMaximumGrams.takeIf { it > 0f }?.toDouble()
    return listOf(
        TherapyIndicatorPresentation(
            label = "IOB",
            value = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, iob, state),
            secondary = latestBolusPresentation(state, nowEpochMs),
            progress = iob?.let { TherapyProgressSemantics.scaled(it, safeIobMaximum) },
            iconRes = SharedUiR.drawable.ic_iob,
            colorRole = SugarliciousColorRole.THERAPY_IOB_PROGRESS,
            numericValue = iob,
        ),
        TherapyIndicatorPresentation(
            label = "COB",
            value = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, cob, state),
            progress = cob?.let { TherapyProgressSemantics.scaled(it, safeCobMaximum) },
            iconRes = SharedUiR.drawable.ic_cob,
            colorRole = SugarliciousColorRole.THERAPY_COB_PROGRESS,
            numericValue = cob,
        ),
        TherapyIndicatorPresentation(
            label = "Basal",
            value = TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, basal?.unitsPerHour, state).removeSuffix("/h"),
            secondary = basal?.percent?.takeIf { it != 100 }?.let { "@$it%" },
            progress = basal?.percent?.let(::basalProgress),
            iconRes = basalIconResource(basal?.percent),
            colorRole = SugarliciousColorRole.THERAPY_BASAL_PROGRESS,
            numericValue = basal?.unitsPerHour,
        ),
    )
}

internal fun latestBolusPresentation(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): String? {
    val bolus = latestDeliveredBolus(state, nowEpochMs) ?: return null
    return formatCompactBolusAge(nowEpochMs - bolus.deliveredAtEpochMs)
}

internal fun formatCompactBolusAge(ageMs: Long): String {
    val totalMinutes = ageMs.coerceAtLeast(0L) / 60_000L
    return "vor ${totalMinutes}m"
}

internal fun basalIconResource(percent: Int?): Int =
    when (basalIndicatorIcon(percent)) {
        TherapyIndicatorIcon.BASAL -> SharedUiR.drawable.ic_basal
        TherapyIndicatorIcon.BASAL_LESS -> SharedUiR.drawable.ic_basalless
        TherapyIndicatorIcon.BASAL_MORE -> SharedUiR.drawable.ic_basalmore
        TherapyIndicatorIcon.IOB, TherapyIndicatorIcon.COB -> error("Basal icon expected")
    }

internal fun basalProgress(percent: Int): Float =
    requireNotNull(TherapyProgressSemantics.basal(percent))

internal fun therapyIndicatorSecondaryFontSizeSp(value: String): Int =
    when {
        value.length >= 14 -> 7
        value.length >= 10 -> 8
        else -> 10
    }

@Composable
internal fun TherapyIndicatorRow(
    indicators: List<TherapyIndicatorPresentation>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(horizontal = 1.dp), contentAlignment = Alignment.Center) {
        val fontScale = LocalDensity.current.fontScale
        val spaceForThree = (maxWidth.value - 12f) / 3f
        val diameter = if (spaceForThree >= 66f * fontScale) minOf(76f * fontScale, spaceForThree) else 76f * fontScale
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            indicators.forEach { indicator -> TherapyCircularIndicator(indicator, Modifier, diameter) }
        }
    }
}

@Composable
internal fun TherapyCircularIndicator(
    indicator: TherapyIndicatorPresentation,
    modifier: Modifier,
    diameterDp: Float,
) {
    val animatedProgress =
        animateFloatAsState(
            targetValue = indicator.progress ?: 0f,
            animationSpec = spring(dampingRatio = app.sugarlicious.model.TherapyRingStyle.MOTION_DAMPING, stiffness = app.sugarlicious.model.TherapyRingStyle.MOTION_STIFFNESS),
            label = "${indicator.label} progress",
        )
    val accent = SugarliciousColors.color(indicator.colorRole)
    val density = LocalDensity.current
    val proportionalScale = TherapyIndicatorGeometry.scale(diameterDp)
    val measurer = rememberTextMeasurer()

    fun fittedSize(value: String, baseSp: Float): Float {
        // The measured ring already includes the surface's system scaling.
        val nominal = TherapyIndicatorGeometry.fontSizeSp(baseSp, diameterDp, density.fontScale)
        val measured = measurer.measure(value, TextStyle(fontSize = nominal.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), softWrap = false).size.width
        val available = with(density) { (diameterDp * TherapyIndicatorGeometry.VALUE_WIDTH_FRACTION).dp.toPx() }
        return nominal * minOf(1f, available / measured.coerceAtLeast(1))
    }
    Box(
        modifier =
            modifier.semantics {
                contentDescription =
                    buildString {
                        append(indicator.label)
                        append(' ')
                        append(indicator.value)
                        indicator.secondary?.let { append(", ").append(it) }
                    }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(diameterDp.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val displayedProgress = indicator.progress?.let { animatedProgress.value.coerceIn(0f, 1f) }
                val geometry = TherapyRingGeometry()
                val stroke = (geometry.strokeWidthDp * proportionalScale).dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val sweep = geometry.sweepDegrees
                val arcs = app.sugarlicious.model.progressArcSegments(displayedProgress, arcSize.width + stroke, stroke, sweep, capClearanceStrokeRatio = app.sugarlicious.model.TherapyRingStyle.CAP_CLEARANCE_STROKE_RATIO)
                if (arcs.trackSweep > 0f) drawArc(accent.copy(alpha = app.sugarlicious.model.TherapyRingStyle.TRACK_ALPHA), geometry.composeStartDegrees + arcs.trackStartOffset, arcs.trackSweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (arcs.fillSweep > 0f) drawArc(accent, geometry.composeStartDegrees, arcs.fillSweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val valueTextSize = fittedSize(indicator.value, TherapyIndicatorGeometry.VALUE_TEXT_SP)
            SplitFlapTherapyValue(indicator.value, indicator.numericValue, valueTextSize, Modifier.align(Alignment.Center))
            indicator.secondary?.let {
                val secondaryTextSize = fittedSize(it, therapyIndicatorSecondaryFontSizeSp(it).toFloat())
                Text(
                    it,
                    modifier = Modifier.align(Alignment.Center).offset(y = (diameterDp * .17f).dp),
                    color = SugarliciousColors.TextSecondary,
                    fontSize = secondaryTextSize.sp,
                    lineHeight = secondaryTextSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val metric =
                when (indicator.label) {
                    "IOB" -> TherapyProgressMetric.IOB
                    "COB" -> TherapyProgressMetric.COB
                    else -> TherapyProgressMetric.BASAL
                }
            SugarliciousIcon(
                indicator.iconRes,
                null,
                Modifier.align(Alignment.Center).offset(y = (diameterDp * (TherapyIndicatorGeometry.ICON_CENTER_Y_FRACTION - .5f)).dp).size(TherapyIndicatorGeometry.iconSizeDp(metric, diameterDp).dp),
                accent,
            )
        }
    }
}

/** Increasing magnitudes advance the reel; decreasing magnitudes reverse it. */
internal fun therapyValueRollDirection(previous: Double, next: Double): Int = if (next >= previous) 1 else -1
