package app.sugarlicious.mobile

import android.graphics.RectF
import kotlin.math.min

internal enum class WidgetGraphSizeClass {
    VERY_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_WIDE,
    TALL,
}

internal object CgmGraphVisualPolicy {
    const val DOT_RADIUS_DP = 2.4f
    const val CURRENT_DOT_EXTRA_DP = 0.1f
    const val DOT_OUTLINE_WIDTH_DP = 0.95f
    const val BOUNDARY_STROKE_DP = 1.0f
    const val GRID_STROKE_DP = 0.7f
    const val CURRENT_TIME_STROKE_DP = 1.0f
    const val AXIS_TICK_HEIGHT_DP = 4.0f
    const val AXIS_TEXT_SP = 8.5f
    const val TARGET_LABEL_SP = 10.0f
}

internal data class WidgetGraphLayoutMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val sizeClass: WidgetGraphSizeClass,
    val plotLeftPx: Float,
    val plotTopPx: Float,
    val plotRightPx: Float,
    val plotBottomPx: Float,
    val axisTextPx: Float,
    val yAxisTextPx: Float,
    val dotRadiusPx: Float,
    val dotOutlineWidthPx: Float,
    val boundaryStrokePx: Float,
    val gridStrokePx: Float,
    val currentTimeStrokePx: Float,
    val tickHeightPx: Float,
    val outerInsetPx: Float,
    val yAxisGapPx: Float,
    val bottomAxisGapPx: Float,
) {
    val plotRect: RectF get() = RectF(plotLeftPx, plotTopPx, plotRightPx, plotBottomPx)
    val yAxisLeftPx: Float get() = plotRightPx + yAxisGapPx

    companion object {
        fun resolve(
            widthPx: Int,
            heightPx: Int,
            density: Float,
            scaledDensity: Float,
        ): WidgetGraphLayoutMetrics {
            val safeDensity = density.coerceAtLeast(0.5f)
            val safeScaledDensity = scaledDensity.coerceAtLeast(0.5f)
            val width = widthPx.coerceAtLeast(1)
            val height = heightPx.coerceAtLeast(1)
            val widthDp = width / safeDensity
            val heightDp = height / safeDensity
            val aspect = widthDp / heightDp.coerceAtLeast(1f)

            val sizeClass =
                when {
                    widthDp < 125f || heightDp < 82f -> WidgetGraphSizeClass.VERY_SMALL
                    aspect >= 2.35f -> WidgetGraphSizeClass.EXTRA_WIDE
                    heightDp / widthDp.coerceAtLeast(1f) >= 1.35f -> WidgetGraphSizeClass.TALL
                    widthDp < 185f || heightDp < 115f -> WidgetGraphSizeClass.SMALL
                    widthDp >= 300f || heightDp >= 190f -> WidgetGraphSizeClass.LARGE
                    else -> WidgetGraphSizeClass.MEDIUM
                }

            fun dp(value: Float): Float = value * safeDensity

            fun sp(value: Float): Float = value * safeScaledDensity

            val outerInsetDp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 5f
                    WidgetGraphSizeClass.SMALL -> 6f
                    else -> 8f
                }
            val requestedRightAxisDp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 27f
                    WidgetGraphSizeClass.SMALL -> 30f
                    WidgetGraphSizeClass.MEDIUM, WidgetGraphSizeClass.TALL -> 34f
                    WidgetGraphSizeClass.LARGE, WidgetGraphSizeClass.EXTRA_WIDE -> 38f
                }
            val requestedBottomAxisDp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 20f
                    WidgetGraphSizeClass.SMALL -> 22f
                    WidgetGraphSizeClass.MEDIUM, WidgetGraphSizeClass.EXTRA_WIDE -> 24f
                    WidgetGraphSizeClass.LARGE, WidgetGraphSizeClass.TALL -> 26f
                }
            val axisSp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 7.5f
                    WidgetGraphSizeClass.SMALL -> 8f
                    WidgetGraphSizeClass.MEDIUM, WidgetGraphSizeClass.EXTRA_WIDE -> CgmGraphVisualPolicy.AXIS_TEXT_SP
                    WidgetGraphSizeClass.LARGE, WidgetGraphSizeClass.TALL -> 9.5f
                }
            val yAxisSp = min(10f, axisSp + 0.5f)
            val dotRadiusDp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 2.0f
                    WidgetGraphSizeClass.SMALL -> 2.2f
                    else -> CgmGraphVisualPolicy.DOT_RADIUS_DP
                }.coerceIn(1.8f, 2.6f)
            val outlineDp =
                when (sizeClass) {
                    WidgetGraphSizeClass.VERY_SMALL -> 0.70f
                    WidgetGraphSizeClass.SMALL -> 0.82f
                    else -> CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP
                }.coerceIn(0.55f, CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP)

            val outerInset = min(dp(outerInsetDp), min(width, height) * 0.08f)
            val leftInset =
                (outerInset + dp(if (sizeClass == WidgetGraphSizeClass.VERY_SMALL) 2f else 4f))
                    .coerceAtMost(width * 0.22f)
            val rightAxisPx =
                dp(requestedRightAxisDp)
                    .coerceAtMost((width - leftInset - outerInset - dp(18f)).coerceAtLeast(dp(18f)))
            val bottomAxisPx =
                dp(requestedBottomAxisDp)
                    .coerceAtMost((height - outerInset - dp(24f)).coerceAtLeast(dp(16f)))
            val plotTop = outerInset
            val plotRight =
                (width - outerInset - rightAxisPx)
                    .coerceIn(leftInset + dp(12f), (width - outerInset).coerceAtLeast(leftInset + dp(12f)))
            val plotBottom =
                (height - outerInset - bottomAxisPx)
                    .coerceIn(plotTop + dp(18f), (height - outerInset).coerceAtLeast(plotTop + dp(18f)))

            return WidgetGraphLayoutMetrics(
                widthPx = width,
                heightPx = height,
                sizeClass = sizeClass,
                plotLeftPx = leftInset,
                plotTopPx = plotTop,
                plotRightPx = plotRight,
                plotBottomPx = plotBottom,
                axisTextPx = sp(axisSp),
                yAxisTextPx = sp(yAxisSp),
                dotRadiusPx = dp(dotRadiusDp),
                dotOutlineWidthPx = dp(outlineDp),
                boundaryStrokePx = dp(CgmGraphVisualPolicy.BOUNDARY_STROKE_DP),
                gridStrokePx = dp(CgmGraphVisualPolicy.GRID_STROKE_DP),
                currentTimeStrokePx = dp(CgmGraphVisualPolicy.CURRENT_TIME_STROKE_DP),
                tickHeightPx = dp(CgmGraphVisualPolicy.AXIS_TICK_HEIGHT_DP),
                outerInsetPx = outerInset,
                yAxisGapPx = dp(5f).coerceAtMost(rightAxisPx * 0.22f),
                bottomAxisGapPx = dp(3f).coerceAtMost(bottomAxisPx * 0.18f),
            )
        }
    }
}
