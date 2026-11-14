package app.sugarwear.ui.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual presentation parameters for ProtoLayout tiles.
 */
@Immutable
data class TileVisualStyle(
    val backgroundColor: Color = Color(0xFF000000),
    val primaryTextColor: Color = Color(0xFFFFFFFF),
    val secondaryTextColor: Color = Color(0xB3FFFFFF),
    val accentColor: Color = Color(0xFF22EB60),
    val warningColor: Color = Color(0xFFFF5C69),
    val cornerRadiusDp: Dp = 16.dp,
    val graphTileWidthDp: Dp = 180.dp,
    val graphTileHeightDp: Dp = 100.dp,
    val showBorder: Boolean = true,
)
