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
internal object CgmPairingActionTokens {
    const val WIDTH_FRACTION = 0.74f
    const val TEXT_SIZE = 36f
    const val TEXT_WIDTH_FRACTION = 0.90f
    const val REFERENCE_TEXT = "Erneut versuchen"
}

internal class CgmTileActionButton(
    context: Context,
) : Button(context) {
    private var pairingStyle = false

    init {
        applyCgmTileActionStyle()
    }

    fun usePairingStyle() {
        pairingStyle = true
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val baseWidth =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                available
            } else {
                (available * CgmAlarmDesignTokens.ACTION_WIDTH / CgmAlarmDesignTokens.SIZE).toInt()
            }
        val width =
            if (pairingStyle && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
                (available * CgmPairingActionTokens.WIDTH_FRACTION).toInt()
            } else {
                baseWidth
            }
        val heightReferenceWidth = if (pairingStyle) baseWidth else width
        val height =
            (
                heightReferenceWidth *
                    CgmAlarmDesignTokens.ACTION_HEIGHT /
                    CgmAlarmDesignTokens.ACTION_WIDTH
            ).toInt()
        val textReferenceSize =
            if (pairingStyle) {
                CgmPairingActionTokens.TEXT_SIZE
            } else {
                CgmAlarmDesignTokens.ACTION_TEXT_SIZE
            }
        val desiredText =
            heightReferenceWidth * textReferenceSize /
                CgmAlarmDesignTokens.ACTION_WIDTH *
                resources.configuration.fontScale
        val finalTextSize =
            if (pairingStyle) {
                paint.textSize = desiredText
                val referenceWidth = paint.measureText(CgmPairingActionTokens.REFERENCE_TEXT)
                val availableTextWidth = width * CgmPairingActionTokens.TEXT_WIDTH_FRACTION
                if (referenceWidth > availableTextWidth) {
                    desiredText * availableTextWidth / referenceWidth
                } else {
                    desiredText
                }
            } else {
                paint.textSize = desiredText
                val textWidth = paint.measureText(text.toString())
                if (textWidth > width * 0.94f) {
                    desiredText * width * 0.94f / textWidth
                } else {
                    desiredText
                }
            }
        setTextSize(TypedValue.COMPLEX_UNIT_PX, finalTextSize)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
