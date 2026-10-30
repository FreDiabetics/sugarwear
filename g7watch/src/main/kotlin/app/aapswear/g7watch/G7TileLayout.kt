package app.aapswear.g7watch

import kotlin.math.min

internal data class G7SquareTileSpec(
    val sideDp: Float,
    val cornerRadiusDp: Float,
    val innerPaddingDp: Float,
)

/** A rounded square this size stays inside a circular display without device-specific constants. */
internal fun g7SquareTileSpec(
    screenWidthDp: Int,
    screenHeightDp: Int,
): G7SquareTileSpec {
    val shortest = min(screenWidthDp.takeIf { it > 0 } ?: 192, screenHeightDp.takeIf { it > 0 } ?: 192).toFloat()
    val side = shortest * 0.76f
    return G7SquareTileSpec(
        sideDp = side,
        cornerRadiusDp = side * 0.13f,
        innerPaddingDp = side * 0.065f,
    )
}
