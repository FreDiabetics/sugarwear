package app.sugarlicious.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.GlucoseVisualSpec
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisuals

@Composable
internal fun OverviewCompactGlucoseHeader(
    glucose: String,
    color: Color,
    trend: Trend,
    delta: String,
    unit: String,
    age: String,
    modifier: Modifier = Modifier,
    onWatch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val background = SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_GLUCOSE_PILL)
    val readableGlucose =
        if (androidx.core.graphics.ColorUtils
                .calculateContrast(color.toArgb(), background.toArgb()) >= 3.0
        ) {
            color
        } else if (SugarliciousColors.palette.isLight) {
            Color.Black
        } else {
            Color.White
        }
    Row(
        modifier
            .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 16.dp)
            .fillMaxWidth()
            .drawWithCache {
                val offsetY = 10.dp.toPx()
                val radius = minOf(40.dp.toPx(), size.height / 2f, size.width / 2f)
                val strength = if (SugarliciousColors.palette.isLight) .32f else .78f
                val shadow = Color.Black.copy(alpha = strength)
                onDrawBehind {
                    // Reuse the pill silhouette; only its downward offset remains visible.
                    drawRoundRect(shadow, Offset(0f, offsetY), size, CornerRadius(radius))
                }
            }.background(background, RoundedCornerShape(40.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactReading(glucose, readableGlucose, trend, delta, unit, age, Modifier.weight(1f).offset(x = 8.dp))
        if (onWatch != null && onSettings != null) {
            OverviewActionMenu(onWatch, onSettings)
        }
    }
}

@Composable
private fun CompactReading(glucose: String, color: Color, trend: Trend, delta: String, unit: String, age: String, modifier: Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val reference = GlucoseVisualSpec.twoByTwoWidgetReference()
    BoxWithConstraints(modifier) {
        // Font measurement already includes system font scaling. Arrow geometry
        // applies that scale once before converting dp to pixels.
        val textStyle = LocalTextStyle.current
        val glucoseWidth = measurer.measure(glucose, textStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp), softWrap = false).size.width
        val ageNumber = age.removeSuffix("m").toLongOrNull()?.toString()
        val number = SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
        val label = SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val metadata =
            buildAnnotatedString {
                withStyle(number) { append(delta) }
                withStyle(label) { append(" $unit · ") }
                if (ageNumber == null) {
                    withStyle(label) { append("—") }
                } else {
                    withStyle(label) { append("vor ") }
                    withStyle(number) { append(ageNumber) }
                    withStyle(label) { append(" Min.") }
                }
            }
        val metadataWidth = measurer.measure(metadata, textStyle, softWrap = false).size.width
        val arrowHeight = reference.trendHeight * (30f / reference.glucoseTextSize) * 1.2f * density.fontScale
        val arrowDp = LocalSugarliciousTrendArrowMaxSize.current?.let { minOf(arrowHeight.dp, it) } ?: arrowHeight.dp
        val trendWidth = with(density) { arrowDp.toPx() } * (TrendVisuals.spec(trend)?.aspectRatio ?: 0f)
        val internalGap = with(density) { 6.dp.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        val available = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val jointScale = minOf(1f, (available - internalGap).coerceAtLeast(1f) / (glucoseWidth + trendWidth + gap + metadataWidth).coerceAtLeast(1f))
        val stacked = jointScale < .75f
        val glucoseScale = if (stacked) minOf(1f, (available - internalGap).coerceAtLeast(1f) / (glucoseWidth + trendWidth).coerceAtLeast(1f)) else jointScale
        val metadataScale = if (stacked) minOf(1f, available / metadataWidth.toFloat().coerceAtLeast(1f)) else jointScale
        val reading: @Composable () -> Unit = {
            GlucoseValueAndTrend(
                glucose,
                color,
                trend,
                reference.copy(
                    glucoseTextSize = 30f * glucoseScale,
                    trendHeight = arrowDp.value * glucoseScale,
                ),
                trendColor = SugarliciousColors.TextPrimary,
            )
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                reading()
                OverviewReadingMetadata(delta, unit, age, compact = true, scale = metadataScale)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                reading()
                androidx.compose.foundation.layout
                    .Spacer(Modifier.size((8f * jointScale).dp))
                OverviewReadingMetadata(delta, unit, age, compact = true, scale = metadataScale)
            }
        }
    }
}
