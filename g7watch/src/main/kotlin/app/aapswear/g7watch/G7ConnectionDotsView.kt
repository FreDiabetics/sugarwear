package app.aapswear.g7watch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import kotlin.math.PI
import kotlin.math.cos

/** Seven calm travelling dots. This is intentionally not a pairing progress indicator. */
internal class G7ConnectionDotsView(
    context: Context,
) : View(context) {
    internal val dotCountForTest: Int get() = DOT_COUNT
    var color: Int = 0xffffffff.toInt()
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }
    var isAnimating: Boolean = true
        set(value) {
            field = value
            if (value && isAttachedToWindow && isVisible) animator.start() else animator.cancel()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var phase = 0f
    private val animator =
        ValueAnimator.ofFloat(0f, DOT_COUNT.toFloat()).apply {
            duration = 2_100L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAnimating && isVisible && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAnimating && visibility == VISIBLE && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = color
        val radius = 2.6f * resources.displayMetrics.density
        val usable = (width - radius * 2f).coerceAtLeast(0f)
        repeat(DOT_COUNT) { index ->
            val x = radius + usable * index / (DOT_COUNT - 1)
            val wave = ((cos((index - phase) * PI / 3.5) + 1.0) / 2.0).toFloat()
            paint.alpha = (55 + 200 * wave).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, height / 2f, radius, paint)
        }
    }

    private companion object {
        const val DOT_COUNT = 7
    }
}
