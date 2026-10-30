package app.aapswear.g7watch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** A quiet, continuous Wear-safe indeterminate arc; animation stops with the UI surface. */
internal class G7IndeterminateLoader(
    context: Context,
) : View(context) {
    var color: Int = 0xffffffff.toInt()
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 5f * resources.displayMetrics.density
        }
    private val bounds = RectF()
    private var rotation = 0f
    private val animator =
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1_150L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotation = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val inset = paint.strokeWidth / 2f + resources.displayMetrics.density
        bounds.set(inset, inset, width - inset, height - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(bounds, rotation, 252f, false, paint)
    }
}
