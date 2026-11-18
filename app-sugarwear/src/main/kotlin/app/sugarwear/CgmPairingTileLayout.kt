package app.sugarwear

import android.content.Context
import android.widget.LinearLayout

internal class CgmPairingTileSize(
    var height: Int = 0,
)

/** Reserve the longest normal pairing layout, then retain any larger error layout. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required pairing size state.
internal class CgmPairingTileLayout(
    context: Context,
    private val size: CgmPairingTileSize,
) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val reserved = (RESERVED_HEIGHT_DP * resources.displayMetrics.density * resources.configuration.fontScale).toInt()
        size.height = maxOf(size.height, reserved, measuredHeight)
        setMeasuredDimension(measuredWidth, size.height)
    }

    private companion object {
        const val RESERVED_HEIGHT_DP = 380
    }
}
