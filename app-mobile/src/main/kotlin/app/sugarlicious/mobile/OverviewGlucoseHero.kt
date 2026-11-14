package app.sugarlicious.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseAgeText
import app.sugarlicious.model.GlucoseBarScale
import app.sugarlicious.model.GlucoseVisualSpec
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.uishared.GlucoseRangeBarPalette
import app.sugarlicious.uishared.GlucoseRangeBarRenderer
import app.sugarlicious.model.GlucoseBarRange as CanonicalGlucoseBarRange
import app.sugarlicious.uishared.R as SharedUiR

/** Glucose header with a canonical-value range bar. */
@Composable
internal fun OverviewGlucoseHero(
    glucoseText: String,
    glucoseColor: Color,
    glucoseMgDl: Double?,
    thresholds: CgmThresholds,
    trend: Trend,
    delta: String,
    age: String,
    unitLabel: String,
    glucoseScalePercent: Int,
    trendScalePercent: Int,
    active: Boolean = true,
    centeredGlucose: Boolean = false,
    status: String? = null,
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val reference = GlucoseVisualSpec.twoByTwoWidgetReference()
    val selected = reference.scaled(glucoseScalePercent, trendScalePercent)
    BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClickLabel = "Glukosedetails", onClick = onClick)) {
        val textSize = 84f * selected.glucoseTextSize / reference.glucoseTextSize
        val arrowHeight = 50f * selected.trendHeight / reference.trendHeight * density.fontScale
        val arrowWidth = (TrendVisuals.spec(trend)?.aspectRatio ?: 0f) * arrowHeight
        val measured = measurer.measure(glucoseText, LocalTextStyle.current.copy(fontSize = textSize.sp, fontWeight = FontWeight.Bold), softWrap = false)
        val groupWidth =
            with(density) {
                measured.size.width
                    .toDp()
                    .value
            } + arrowWidth + 6f
        val fit = minOf(1f, (maxWidth.value - 96f).coerceAtLeast(1f) / groupWidth.coerceAtLeast(1f))
        val waveHeight = (maxWidth.value * .18f).coerceIn(30f, 86f).dp
        Column(Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().offset(y = 44.dp).padding(start = if (centeredGlucose) 0.dp else 24.dp), horizontalArrangement = if (centeredGlucose) Arrangement.Center else Arrangement.Start) {
                GlucoseValueAndTrend(
                    glucoseText,
                    glucoseColor,
                    trend,
                    selected.copy(glucoseTextSize = textSize * fit, trendHeight = arrowHeight * fit),
                    trendColor = SugarliciousColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            GlucoseRangeBar(glucoseMgDl.takeIf { active }, thresholds, waveHeight.value.coerceAtLeast(66f))
            Spacer(Modifier.height(22.dp))
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftMotionInfoPill(
                    SharedUiR.drawable.ic_metadata_delta,
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(delta) }
                        withStyle(SpanStyle(color = SugarliciousColors.TextSecondary)) { append(" $unitLabel") }
                    },
                )
                SoftMotionInfoPill(SharedUiR.drawable.ic_metadata_age, softMotionAgeText(age, SugarliciousColors.TextPrimary, SugarliciousColors.TextSecondary))
            }
            status?.let { Text(it, color = SugarliciousColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun SoftMotionInfoPill(icon: Int, text: androidx.compose.ui.text.AnnotatedString) {
    val fontScale = LocalDensity.current.fontScale
    val iconSize = ((if (icon == SharedUiR.drawable.ic_metadata_delta) 19f else 17f) * fontScale).dp
    Row(
        Modifier.background(SugarliciousColors.Surface, RoundedCornerShape(40.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size((20f * fontScale).dp), contentAlignment = Alignment.Center) {
            SugarliciousIcon(icon, null, Modifier.size(iconSize), SugarliciousColors.TextSecondary)
        }
        Text(text, color = SugarliciousColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun softMotionAge(age: String): String = GlucoseAgeText.fromAgeMinutes(age)

internal fun softMotionAgeText(age: String, numberColor: Color, labelColor: Color): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        val text = softMotionAge(age)
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.SemiBold)) { append(text) }
        Regex("[0-9]+").findAll(text).forEach { match ->
            addStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }
    }

@Composable
private fun GlucoseRangeBar(value: Double?, thresholds: CgmThresholds, heightDp: Float) {
    val renderer = remember { GlucoseRangeBarRenderer() }
    val state = GlucoseBarScale.state(value, thresholds)
    val position by animateFloatAsState(state?.position ?: .5f, spring(dampingRatio = 1f, stiffness = 90f), label = "glucose position")
    val selectedColor =
        when (state?.range) {
            CanonicalGlucoseBarRange.LOW -> SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_WAVE_LOW)
            CanonicalGlucoseBarRange.TARGET -> SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_WAVE_IN_RANGE)
            CanonicalGlucoseBarRange.HIGH -> SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_WAVE_HIGH)
            null -> Color.Transparent
        }
    val barColor by animateColorAsState(selectedColor, spring(dampingRatio = 1f, stiffness = 90f), label = "glucose interval color")
    val underlay = SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_WAVE_UNDERLAY)
    Box(Modifier.fillMaxWidth(.88f).height(heightDp.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.BottomCenter)
                .clearAndSetSemantics {},
        ) {
            renderer.draw(
                canvas = drawContext.canvas.nativeCanvas,
                widthPx = size.width,
                heightPx = size.height,
                state = state?.copy(position = position),
                palette =
                    GlucoseRangeBarPalette(
                        low = barColor.toArgb(),
                        target = barColor.toArgb(),
                        high = barColor.toArgb(),
                        underlay = underlay.toArgb(),
                    ),
                gapPx = 7.dp.toPx(),
            )
        }
    }
}
