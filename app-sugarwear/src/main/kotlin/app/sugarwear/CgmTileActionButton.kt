package app.sugarwear

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button

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
        minHeight = PAIRING_MIN_HEIGHT_DP.dp
        minimumHeight = PAIRING_MIN_HEIGHT_DP.dp
        setPadding(PAIRING_HORIZONTAL_PADDING_DP.dp, PAIRING_VERTICAL_PADDING_DP.dp, PAIRING_HORIZONTAL_PADDING_DP.dp, PAIRING_VERTICAL_PADDING_DP.dp)
        setSingleLine(false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, PAIRING_TEXT_SP)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pairingSize) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) available else (available * 280f / 450f).toInt()
        val height = (width * 85f / 280f).toInt()
        val desiredText = width * 34f / 280f * resources.configuration.fontScale
        setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredText)
        val textWidth = paint.measureText(text.toString())
        if (textWidth > width * 0.94f) setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredText * width * 0.94f / textWidth)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    private val Int.dp get() = kotlin.math.ceil(this * resources.displayMetrics.density).toInt()

    private companion object {
        const val PAIRING_MIN_HEIGHT_DP = 48
        const val PAIRING_HORIZONTAL_PADDING_DP = 8
        const val PAIRING_VERTICAL_PADDING_DP = 4
        const val PAIRING_TEXT_SP = 15f
    }
}
