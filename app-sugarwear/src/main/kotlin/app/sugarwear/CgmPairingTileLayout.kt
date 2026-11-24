package app.sugarwear

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val actions = children.filterIsInstance<CgmTileActionButton>().filter { it.pairingStyle }.toList()
        if (actions.isNotEmpty()) {
            val preferredTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
            actions.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, preferredTextSize) }
            val preferredWidth = actions.maxOf { it.pairingInkBounds().width() }
            val contentWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val textLimit = (contentWidth - ACTION_MIN_HEIGHT_DP.dp).coerceAtLeast(1)
            if (preferredWidth > textLimit) {
                val fittedTextSize = preferredTextSize * textLimit / preferredWidth
                actions.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedTextSize) }
            }
            val textWidth = actions.maxOf { it.pairingInkBounds().width() }
            val textHeight = actions.maxOf { it.pairingInkBounds().height() }
            val inset = maxOf(ACTION_INSET_DP.dp, (ACTION_MIN_HEIGHT_DP.dp - textHeight + 1) / 2)
            actions.forEach { it.setPairingSize(textWidth + 2 * inset, textHeight + 2 * inset) }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val TILE_HEIGHT_DP = 220
        private const val HORIZONTAL_PADDING_DP = 12
        private const val VERTICAL_PADDING_DP = 8
        private const val ACTION_INSET_DP = 8
        private const val ACTION_MIN_HEIGHT_DP = 44
        private const val ACTION_GAP_DP = 4

        fun actionLayoutParams(context: Context) =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (ACTION_GAP_DP * context.resources.displayMetrics.density).toInt()
            }
    }
}
