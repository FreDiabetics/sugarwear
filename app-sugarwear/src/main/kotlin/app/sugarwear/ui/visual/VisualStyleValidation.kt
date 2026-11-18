package app.sugarwear.ui.visual

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Validates visual style parameters to prevent rendering artifacts or invalid dimensions.
 */
object VisualStyleValidation {
    fun validateGraphStyle(style: GraphVisualStyle): GraphVisualStyle =
        style.copy(
            lineWidthDp = style.lineWidthDp.coerceIn(0.5.dp, 10.dp),
            pointRadiusDp = style.pointRadiusDp.coerceIn(1.dp, 20.dp),
            historicalPointRadiusDp = style.historicalPointRadiusDp.coerceIn(1.dp, 15.dp),
            currentPointRadiusDp = style.currentPointRadiusDp.coerceIn(1.dp, 20.dp),
            strokeWidthDp = style.strokeWidthDp.coerceIn(0.1.dp, 5.dp),
            dotOutlineWidthDp = style.dotOutlineWidthDp.coerceIn(0.1.dp, 5.dp),
            fillAlpha = style.fillAlpha.coerceIn(0.0f, 1.0f),
            animationDurationMillis = style.animationDurationMillis.coerceIn(0, 10_000),
        )

    fun validateHomeStyle(style: HomeVisualStyle): HomeVisualStyle =
        style.copy(
            cardCornerRadiusDp = style.cardCornerRadiusDp.coerceIn(0.dp, 50.dp),
            glucoseScalePercent = style.glucoseScalePercent.coerceIn(50, 200),
            graphTileHeightDp = style.graphTileHeightDp.coerceIn(80.dp, 300.dp),
            contentPaddingDp = style.contentPaddingDp.coerceIn(4.dp, 40.dp),
            cardSpacingDp = style.cardSpacingDp.coerceIn(0.dp, 30.dp),
        )

    fun validateAlarmStyle(style: AlarmVisualStyle): AlarmVisualStyle =
        style.copy(
            iconSizeDp = style.iconSizeDp.coerceIn(30.dp, 150.dp),
            buttonCornerRadiusDp = style.buttonCornerRadiusDp.coerceIn(0.dp, 50.dp),
            contentPaddingDp = style.contentPaddingDp.coerceIn(8.dp, 40.dp),
            buttonSpacingDp = style.buttonSpacingDp.coerceIn(2.dp, 30.dp),
        )

    private fun Dp.coerceIn(min: Dp, max: Dp): Dp =
        if (this < min) {
            min
        } else if (this > max) {
            max
        } else {
            this
        }
}
