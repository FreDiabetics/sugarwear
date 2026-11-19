package app.sugarwear.ui.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual styling configuration for the glucose graph renderer.
 * Strictly presentation parameters; no medical or sensor processing logic.
 */
@Immutable
data class GraphVisualStyle(
    val lineWidthDp: Dp = 2.dp,
    val pointRadiusDp: Dp = 4.dp,
    val historicalPointRadiusDp: Dp = 3.dp,
    val currentPointRadiusDp: Dp = 4.2.dp,
    val strokeWidthDp: Dp = 1.1.dp,
    val dotOutlineWidthDp: Dp = 0.95.dp,
    val backgroundColor: Color = Color(0xFF000000),
    val targetAreaColor: Color = Color(0x264CAF50),
    val lowAreaColor: Color = Color(0x33F44336),
    val highAreaColor: Color = Color(0x33FF9800),
    val lowLineColor: Color = Color(0xFFFF5C69),
    val highLineColor: Color = Color(0xFFFFD040),
    val dotInRangeColor: Color = Color(0xFF4CAF50),
    val dotLowColor: Color = Color(0xFFFF5C69),
    val dotVeryLowColor: Color = Color(0xFFD32F2F),
    val dotHighColor: Color = Color(0xFFFFD040),
    val dotVeryHighColor: Color = Color(0xFFF57C00),
    val dotOutlineColor: Color = Color(0xFFFFFFFF),
    val axisTextColor: Color = Color(0xB3FFFFFF),
    val axisTickColor: Color = Color(0x33FFFFFF),
    val nowLineColor: Color = Color(0x80FFFFFF),
    val borderColor: Color = Color(0x33FFFFFF),
    val fillEnabled: Boolean = true,
    val fillAlpha: Float = 0.25f,
    val showGrid: Boolean = true,
    val showTimeLabels: Boolean = true,
    val showGlucoseLabels: Boolean = true,
    val showTargetRange: Boolean = true,
    val animationEnabled: Boolean = false,
    val animationDurationMillis: Int = 300,
)
