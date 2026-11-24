package app.sugarwear

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout

/** Pairing content wraps its current step, including long errors and large system text. */
internal class CgmPairingTileLayout(
    context: Context,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setPadding(HORIZONTAL_PADDING_DP.dp, VERTICAL_PADDING_DP.dp, HORIZONTAL_PADDING_DP.dp, VERTICAL_PADDING_DP.dp)
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
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
