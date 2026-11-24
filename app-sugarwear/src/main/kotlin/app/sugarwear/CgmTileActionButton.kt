package app.sugarwear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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

internal class CgmTileActionButton(
    context: Context,
) : Button(context) {
    var pairingStyle = false
        private set
    private val ink = Rect()
    private var pairingWidth = 0
    private var pairingHeight = 0

    init {
        applyCgmTileActionStyle()
    }

    fun usePairingStyle() {
        pairingStyle = true
        setSingleLine()
    }

    fun pairingInkBounds(): Rect {
        val label = text.toString()
        paint.getTextBounds(label, 0, label.length, ink)
        return ink
    }

    fun setPairingSize(width: Int, height: Int) {
        if (pairingWidth == width && pairingHeight == height) return
        pairingWidth = width
        pairingHeight = height
        forceLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        if (pairingStyle) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(minOf(pairingWidth, available), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(pairingHeight, MeasureSpec.EXACTLY),
            )
            return
        }

        val width =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                available
            } else {
                (available * CgmAlarmDesignTokens.ACTION_WIDTH / CgmAlarmDesignTokens.SIZE).toInt()
            }
        val height = (width * CgmAlarmDesignTokens.ACTION_HEIGHT / CgmAlarmDesignTokens.ACTION_WIDTH).toInt()
        val desiredText = width * CgmAlarmDesignTokens.ACTION_TEXT_SIZE / CgmAlarmDesignTokens.ACTION_WIDTH * resources.configuration.fontScale
        paint.textSize = desiredText
        val textWidth = paint.measureText(text.toString())
        val finalTextSize = if (textWidth > width * 0.94f) desiredText * width * 0.94f / textWidth else desiredText
        setTextSize(TypedValue.COMPLEX_UNIT_PX, finalTextSize)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!pairingStyle) {
            super.onDraw(canvas)
            return
        }
        // Center the visible glyphs instead of the font's advance and line box.
        val bounds = pairingInkBounds()
        val lineLeft = layout?.getLineLeft(0) ?: 0f
        val checkpoint = canvas.save()
        canvas.translate(width / 2f - lineLeft - bounds.exactCenterX(), height / 2f - baseline - bounds.exactCenterY())
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
