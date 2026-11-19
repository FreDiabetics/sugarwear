package app.sugarwear.ui.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Common design tokens for UI elements.
 */
@Immutable
data class CommonVisualStyle(
    val brandPrimary: Color = Color(0xFF22EB60),
    val brandSecondary: Color = Color(0xFF242424),
    val surfaceColor: Color = Color(0xFF141414),
    val surfaceBorderColor: Color = Color(0x33FFFFFF),
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0x99FFFFFF),
    val textDisabled: Color = Color(0x4DFFFFFF),
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 18.dp,
    val cornerRadiusLarge: Dp = 50.dp,
    val defaultPadding: Dp = 16.dp,
)
