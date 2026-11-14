package app.sugarlicious.wear

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.DecelerateInterpolator
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseBarScale
import app.sugarlicious.model.GlucoseBarState
import app.sugarlicious.protocol.WatchUiColors
import app.sugarlicious.uishared.GlucoseRangeBarPalette
import app.sugarlicious.uishared.GlucoseRangeBarRenderer

/**
 * Native Wear counterpart of the Mobile glucose progress bar.
 * Animates only after a new validated reading; no polling or permanent animator.
 */
internal fun WatchUiColors.glucoseRangeBarPalette(): GlucoseRangeBarPalette =
    GlucoseRangeBarPalette(
        low = glucoseBarLow,
        target = glucoseBarTarget,
        high = glucoseBarHigh,
        underlay = glucoseBarUnderlay,
    )

internal class WearGlucoseRangeBarView(
    context: Context,
) : View(context) {
    private val renderer = GlucoseRangeBarRenderer()
    private var palette = GlucoseRangeBarPalette()
    private val gapPx = 7f * resources.displayMetrics.density
    private var target: GlucoseBarState? = null
    private var displayedPosition: Float? = null
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(valueMgDl: Double?, thresholds: CgmThresholds, palette: GlucoseRangeBarPalette) {
        val next = GlucoseBarScale.state(valueMgDl, thresholds)
        val valueChanged = next != target
        if (!valueChanged && this.palette == palette) return
        this.palette = palette
        if (!valueChanged) {
            invalidate()
            return
        }
        val startPosition = displayedPosition
        animator?.cancel()
        animator = null
        target = next

        if (next != null && startPosition != null && isShown && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()) {
            animator =
                ValueAnimator.ofFloat(startPosition, next.position).apply {
                    duration = 300L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        displayedPosition = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        } else {
            displayedPosition = next?.position
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(
            canvas = canvas,
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            state = target?.copy(position = displayedPosition ?: target?.position ?: 0.5f),
            palette = palette,
            gapPx = gapPx,
        )
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        displayedPosition = target?.position
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            animator?.cancel()
            animator = null
            displayedPosition = target?.position
        }
    }
}
