package app.sugarlicious.mobile

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Observe without consuming: normal buttons, graph gestures and scrolling keep working. */
@Composable
internal fun Modifier.outsideDoubleTap(enabled: Boolean, bounds: Rect?, onDoubleTap: () -> Unit): Modifier {
    val currentBounds by rememberUpdatedState(bounds)
    val callback by rememberUpdatedState(onDoubleTap)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var previousTime = Long.MIN_VALUE
        var previousPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var valid = true
            var position = down.position
            var releasedAt = down.uptimeMillis
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.size > 1) valid = false
                if (change != null) {
                    position = change.position
                    releasedAt = change.uptimeMillis
                    if ((position - down.position).getDistance() > viewConfiguration.touchSlop) valid = false
                }
            } while (event.changes.any { it.pressed })
            val layout = coordinates[0]
            val pill = currentBounds
            val inWindow = if (layout?.isAttached == true) layout.localToWindow(position) else null
            if (!valid || releasedAt - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis || pill == null || inWindow == null || pill.contains(inWindow)) {
                previousTime = Long.MIN_VALUE
                return@awaitEachGesture
            }
            val elapsed = if (previousTime == Long.MIN_VALUE) Long.MAX_VALUE else releasedAt - previousTime
            if (elapsed in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis && (position - previousPosition).getDistance() <= viewConfiguration.touchSlop * 3f) {
                previousTime = Long.MIN_VALUE
                callback()
            } else {
                previousTime = releasedAt
                previousPosition = position
            }
        }
    }
}
