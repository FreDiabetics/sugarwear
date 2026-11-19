package app.sugarlicious.uishared

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScrollFeedbackProvider
import android.view.View
import androidx.annotation.RequiresApi

/** Delegate crown feedback to the device implementation and its system haptic settings. */
class WearRotaryFeedback(
    private val view: View,
) {
    private val feedback = if (Build.VERSION.SDK_INT >= 35) platformFeedback(view) else null

    fun onScroll(event: MotionEvent, consumedPixels: Int) {
        if (consumedPixels == 0 || !view.isHapticFeedbackEnabled) return
        val platform = feedback
        if (platform != null) {
            platform(event, consumedPixels)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    @RequiresApi(35)
    private fun platformFeedback(view: View): (MotionEvent, Int) -> Unit {
        val provider = ScrollFeedbackProvider.createProvider(view)
        return { event, pixels -> provider.onScrollProgress(event.deviceId, event.source, MotionEvent.AXIS_SCROLL, pixels) }
    }
}
