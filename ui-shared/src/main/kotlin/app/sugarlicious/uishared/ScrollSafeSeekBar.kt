package app.sugarlicious.uishared

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.SeekBar
import kotlin.math.abs

/** Defer native tracking until horizontal intent is clear; a scroll never writes a value on DOWN. */
open class ScrollSafeSeekBar(
    context: Context,
) : SeekBar(context) {
    private var pendingDown: MotionEvent? = null
    private var horizontal = false
    private var scrolling = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTouch()
                pendingDown = MotionEvent.obtain(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scrolling) return false
                pendingDown?.let { down ->
                    val dx = abs(event.x - down.x)
                    val dy = abs(event.y - down.y)
                    if (maxOf(dx, dy) <= slop) return true
                    if (dy > slop && dx < dy * 2f) {
                        clearTouch()
                        scrolling = true
                        return false
                    }
                    if (dx < slop * 2 || dx < dy * 2f) return true
                    horizontal = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    super.onTouchEvent(down)
                    pendingDown?.recycle()
                    pendingDown = null
                }
                return horizontal && super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                if (scrolling) {
                    clearTouch()
                    return false
                }
                pendingDown?.let { down ->
                    val dx = abs(event.x - down.x)
                    val dy = abs(event.y - down.y)
                    if (maxOf(dx, dy) > slop) {
                        clearTouch()
                        return false
                    }
                    super.onTouchEvent(down)
                }
                val handled = super.onTouchEvent(event)
                if (!horizontal) performClick()
                clearTouch()
                return handled
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                if (horizontal) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                }
                clearTouch()
                scrolling = true
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        clearTouch()
        super.onDetachedFromWindow()
    }

    private fun clearTouch() {
        pendingDown?.recycle()
        pendingDown = null
        horizontal = false
        scrolling = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
