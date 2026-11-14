package app.sugarlicious.mobile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt

/** Only settle nearby headings after a gentle gesture. Never turn a fling into a page jump. */
internal fun settingsSnapTarget(position: Int, headings: List<Int>, velocityDpPerSecond: Float, density: Float, maxScroll: Int): Int? {
    val offsets = headings.filter { it in 0..maxScroll }.map { it - position }
    return settingsSnapOffset(offsets, velocityDpPerSecond, density)?.let { position + it }
}

private fun settingsSnapOffset(offsets: List<Int>, velocityDpPerSecond: Float, density: Float): Int? {
    if (!velocityDpPerSecond.isFinite() || abs(velocityDpPerSecond) > 600f) return null
    if (!density.isFinite() || density <= 0f) return null
    return offsets
        .minByOrNull { abs(it.toLong()) }
        ?.takeIf { abs(it.toLong()) in 1..(8f * density).toLong() }
}

internal class SettingsHeadingRegistry(
    val scroll: ScrollState,
    val insetPx: Float,
) {
    val positions = mutableMapOf<String, Int>()
    var viewportTop = 0f
}

internal val LocalSettingsHeadingRegistry = staticCompositionLocalOf<SettingsHeadingRegistry?> { null }

internal fun Modifier.settingsSnapHeading(key: String, offsetDp: Float = 0f): Modifier =
    composed {
        val registry = LocalSettingsHeadingRegistry.current
        val density = LocalDensity.current.density
        DisposableEffect(registry, key) { onDispose { registry?.positions?.remove(key) } }
        onGloballyPositioned { coordinates ->
            registry?.let {
                it.positions[key] = (coordinates.positionInWindow().y - it.viewportTop + it.scroll.value - it.insetPx + offsetDp * density).roundToInt()
            }
        }
    }

@Composable
internal fun rememberSettingsSnapConnection(registry: SettingsHeadingRegistry?): NestedScrollConnection {
    val density = LocalDensity.current.density
    return remember(registry, density) {
        object : NestedScrollConnection {
            private var releaseSpeed = 0f

            override suspend fun onPreFling(available: Velocity): Velocity {
                releaseSpeed = available.y / density
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                registry?.let {
                    settingsSnapTarget(it.scroll.value, it.positions.values.toList(), releaseSpeed, density, it.scroll.maxValue)?.let { target ->
                        it.scroll.animateScrollTo(
                            target,
                            androidx.compose.animation.core
                                .tween(320),
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }
}

@Composable
internal fun rememberWatchSectionSnap(state: LazyListState, headerIndices: Set<Int>): NestedScrollConnection {
    val density = LocalDensity.current.density
    return remember(state, headerIndices, density) {
        object : NestedScrollConnection {
            private var releaseSpeed = 0f

            override suspend fun onPreFling(available: Velocity): Velocity {
                releaseSpeed = available.y / density
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val offsets =
                    state.layoutInfo.visibleItemsInfo
                        .filter { it.index in headerIndices }
                        .map { it.offset }
                settingsSnapOffset(offsets, releaseSpeed, density)?.let {
                    state.animateScrollBy(
                        it.toFloat(),
                        androidx.compose.animation.core
                            .tween(320),
                    )
                }
                return Velocity.Zero
            }
        }
    }
}
