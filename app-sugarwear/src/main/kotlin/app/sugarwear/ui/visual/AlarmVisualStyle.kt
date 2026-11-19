package app.sugarwear.ui.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual styling configuration for alarm screens and full-screen alert overlays.
 */
@Immutable
data class AlarmVisualStyle(
    val backgroundColor: Color = Color(0xFF000000),
    val highAccentColor: Color = Color(0xFFFFD040),
    val lowAccentColor: Color = Color(0xFFFF5C69),
    val buttonBackgroundColor: Color = Color(0xFF242424),
    val snoozeButtonBackgroundColor: Color = Color(0xFF22EB60),
    val primaryTextColor: Color = Color(0xFFFFFFFF),
    val snoozeTextColor: Color = Color(0xFF000000),
    val glucoseValueFontSize: TextUnit = 48.sp,
    val headlineFontSize: TextUnit = 26.sp,
    val iconSizeDp: Dp = 80.dp,
    val buttonCornerRadiusDp: Dp = 50.dp,
    val contentPaddingDp: Dp = 24.dp,
    val buttonSpacingDp: Dp = 10.dp,
    val animationEnabled: Boolean = true,
)
