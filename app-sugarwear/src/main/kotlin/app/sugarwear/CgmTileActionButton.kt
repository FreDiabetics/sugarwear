package app.sugarwear

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun Button.applyCgmTileActionStyle() {
    isAllCaps = false
    setPadding(0, 0, 0, 0)
    minWidth = 0
    minimumWidth = 0
    minHeight = 0
    minimumHeight = 0
    includeFontPadding = false
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    stateListAnimator = null
    elevation = 0f
    backgroundTintList = null
    background =
        GradientDrawable().apply {
            setColor(CgmAlarmDesignTokens.BUTTON)
            cornerRadius = 1000f
        }
}

/** The same 280 by 85 artboard button used by the native alarm's OK action. */
internal class CgmTileActionButton(
    context: Context,
) : Button(context) {
    private var pairingSize = false

    init {
        applyCgmTileActionStyle()
    }

    fun usePairingSize() {
        pairingSize = true
        setSingleLine(true)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        if (pairingSize) {
            measurePairingAction(available)
            return
        }
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) available else (available * 280f / 450f).toInt()
        val height = (width * 85f / 280f).toInt()
        val desiredText = width * 34f / 280f * resources.configuration.fontScale
        setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredText)
        val textWidth = paint.measureText(text.toString())
        if (textWidth > width * 0.94f) setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredText * width * 0.94f / textWidth)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    private fun measurePairingAction(width: Int) {
        val scale = width / CgmAlarmDesignTokens.ACTION_WIDTH
        val referenceHeight = (CgmAlarmDesignTokens.ACTION_HEIGHT * scale).roundToInt()
        val touchHeight = ceil(PAIRING_MIN_HEIGHT_DP * resources.displayMetrics.density).toInt()
        val height = maxOf(referenceHeight, touchHeight)
        val textSize = CgmAlarmDesignTokens.ACTION_TEXT_SIZE * scale * resources.configuration.fontScale
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        val measured = paint.measureText(text.toString())
        val available = (width - 2).coerceAtLeast(1)
        if (measured > available) setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * available / measured)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    private companion object {
        const val PAIRING_MIN_HEIGHT_DP = 48
    }
}
