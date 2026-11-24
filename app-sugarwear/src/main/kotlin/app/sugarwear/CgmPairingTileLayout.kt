package app.sugarwear

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView

/** Centers each pairing step inside a fixed-height, scrollable card. */
internal class CgmPairingTileLayout(
    context: Context,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(HORIZONTAL_PADDING_DP.dp, VERTICAL_PADDING_DP.dp, HORIZONTAL_PADDING_DP.dp, VERTICAL_PADDING_DP.dp)
    }

    fun viewport(): NestedScrollView =
        NestedScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToOutline = true
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TILE_HEIGHT_DP.dp)
            addView(
                this@CgmPairingTileLayout,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val TILE_HEIGHT_DP = 220
        private const val HORIZONTAL_PADDING_DP = 12
        private const val VERTICAL_PADDING_DP = 8
        private const val ACTION_GAP_DP = 4

        fun actionLayoutParams(context: Context) =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (ACTION_GAP_DP * context.resources.displayMetrics.density).toInt()
            }
    }
}
