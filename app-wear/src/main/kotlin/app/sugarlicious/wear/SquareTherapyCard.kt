package app.sugarlicious.wear

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Equal weighted cards remain square on every round-display width. */
class SquareTherapyCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val side = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(widthMeasureSpec, if (side > 0) MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY) else heightMeasureSpec)
        }
    }
