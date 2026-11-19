package app.sugarwear.ui.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual styling configuration for the main SugarWear home/overview screen.
 */
@Immutable
data class HomeVisualStyle(
    val backgroundColor: Color = Color(0xFF000000),
    val cardBackgroundColor: Color = Color(0xFF1C1C1C),
    val cardBorderColor: Color = Color(0x33FFFFFF),
    val cardCornerRadiusDp: Dp = 18.dp,
    val primaryTextColor: Color = Color(0xFFFFFFFF),
    val secondaryTextColor: Color = Color(0xB3FFFFFF),
    val glucoseScalePercent: Int = 100,
    val graphTileHeightDp: Dp = 140.dp,
    val contentPaddingDp: Dp = 18.dp,
    val cardSpacingDp: Dp = 8.dp,
    val showSignalLossBanner: Boolean = true,
    val animateStatusIndicators: Boolean = true,
)
