package app.sugarlicious.uishared

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Switch
import kotlin.math.abs

/** A settings switch commits a tap, never a swipe through the scrolling page. */
@Suppress("DEPRECATION")
class ScrollSafeSwitch(
    context: Context,
) : Switch(context) {
    private var downX = 0f
    private var downY = 0f
    private var tap = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tap = true
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    tap = false
                    isPressed = false
                }
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                if (tap && abs(event.x - downX) <= slop && abs(event.y - downY) <= slop) performClick()
                tap = false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                tap = false
                isPressed = false
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
