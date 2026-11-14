package app.sugarlicious.wear

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.sugarlicious.model.TherapyIndicatorGeometry
import app.sugarlicious.model.TherapyRingGeometry
import app.sugarlicious.model.TherapyRingStyle
import app.sugarlicious.uishared.TherapyRingRenderer

/** Mobile-identical ring with finite animation only while this View is visible. */
class WearTherapyProgressView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        internal var progress: Float? = null
            private set
        private var displayedProgress: Float? = null
        private var accent = Color.WHITE
        private var animator: ValueAnimator? = null
        private val geometry = TherapyRingGeometry()
        private val renderer = TherapyRingRenderer()
        private val bounds = RectF()

        internal fun bind(value: Float?, color: Int) {
            val next = value?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
            val changed = progress != next
            accent = color
            progress = next
            if (!changed) {
                invalidate()
                return
            }
            animator?.cancel()
            if (next == null || displayedProgress == null || !isAttachedToWindow || !isShown || !ValueAnimator.areAnimatorsEnabled()) {
                displayedProgress = next
                invalidate()
                return
            }
            animator =
                ValueAnimator.ofFloat(displayedProgress!!, next).apply {
                    duration = TherapyRingStyle.MOTION_DURATION_MS
                    setInterpolator { TherapyRingStyle.motionFraction(it) }
                    addUpdateListener {
                        displayedProgress = (it.animatedValue as Float).coerceIn(0f, 1f)
                        invalidate()
                    }
                    start()
                }
        }

        private fun finishMotion() {
            animator?.cancel()
            animator = null
            displayedProgress = progress
        }

        override fun onDetachedFromWindow() {
            finishMotion()
            super.onDetachedFromWindow()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility != VISIBLE) finishMotion()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val diameter = minOf(width, height).toFloat()
            val stroke = geometry.strokeWidthDp * TherapyIndicatorGeometry.scale(diameter)
            if (diameter <= stroke) return
            val left = (width - diameter) / 2f + stroke / 2f
            val top = (height - diameter) / 2f + stroke / 2f
            bounds.set(left, top, left + diameter - stroke, top + diameter - stroke)
            renderer.draw(canvas, bounds, stroke, displayedProgress, accent, geometry)
        }
    }
