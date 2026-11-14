package app.sugarlicious.mobile

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun menuSwipeTarget(
    screen: DashboardScreen,
    deltaX: Float,
    deltaY: Float,
    minimumDistancePx: Float,
): DashboardScreen? {
    if (abs(deltaX) < minimumDistancePx) return null
    if (abs(deltaX) <= abs(deltaY) * 1.25f) return null

    return if (deltaX < 0f) {
        when (screen) {
            DashboardScreen.OVERVIEW -> DashboardScreen.WATCH
            DashboardScreen.WATCH -> DashboardScreen.SETTINGS
            DashboardScreen.SETTINGS -> null
        }
    } else {
        when (screen) {
            DashboardScreen.OVERVIEW -> null
            DashboardScreen.WATCH -> DashboardScreen.OVERVIEW
            DashboardScreen.SETTINGS -> DashboardScreen.WATCH
        }
    }
}

internal fun Modifier.menuSwipeNavigation(
    screen: DashboardScreen,
    onNavigate: (DashboardScreen) -> Unit,
): Modifier =
    pointerInput(screen, onNavigate) {
        awaitEachGesture {
            val down =
                awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Final,
                )
            var deltaX = 0f
            var deltaY = 0f
            var childConsumed = false
            var pointerPressed = true

            while (pointerPressed) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                deltaX += delta.x
                deltaY += delta.y
                childConsumed = childConsumed || change.isConsumed
                pointerPressed = change.pressed
            }

            if (!childConsumed) {
                menuSwipeTarget(
                    screen = screen,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    minimumDistancePx = 72.dp.toPx(),
                )?.let(onNavigate)
            }
        }
    }
