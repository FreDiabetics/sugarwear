package app.sugarlicious.mobile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors

@Composable
internal fun OverviewReadingMetadata(delta: String, unit: String, age: String, compact: Boolean = false, scale: Float = 1f) {
    val numberSize = (if (compact) 15f else 24f).times(scale).sp
    val labelSize = (if (compact) 13f else 18f).times(scale).sp
    val number = SpanStyle(color = if (compact && !SugarliciousColors.palette.isLight) Color.White else SugarliciousColors.TextPrimary, fontSize = numberSize, fontWeight = FontWeight.Bold)
    val label = SpanStyle(color = if (compact && !SugarliciousColors.palette.isLight) Color(0xFFB5B5B5) else SugarliciousColors.TextSecondary, fontSize = labelSize, fontWeight = FontWeight.SemiBold)
    val deltaText =
        buildAnnotatedString {
            withStyle(number) { append(delta) }
            withStyle(label) { append(" $unit") }
        }
    val ageNumber = age.removeSuffix("m").toLongOrNull()?.toString()
    val ageText =
        buildAnnotatedString {
            if (ageNumber == null) {
                withStyle(label) { append("—") }
            } else {
                withStyle(label) { append("vor ") }
                withStyle(number) { append(ageNumber) }
                withStyle(label) { append(" Min.") }
            }
        }
    Text(
        buildAnnotatedString {
            append(deltaText)
            withStyle(label) { append(" · ") }
            append(ageText)
        },
        textAlign = TextAlign.Center,
        lineHeight = (if (compact) 22f else 32f).times(scale).sp,
        maxLines = if (compact) 1 else Int.MAX_VALUE,
        softWrap = !compact,
    )
}
