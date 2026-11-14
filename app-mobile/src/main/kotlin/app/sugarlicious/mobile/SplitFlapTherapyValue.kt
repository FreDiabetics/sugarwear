package app.sugarlicious.mobile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

/** Decimal places remain keyed from the right when a leading digit appears or disappears. */
@Composable
internal fun SplitFlapTherapyValue(text: String, numericValue: Double?, fontSize: Float, modifier: Modifier = Modifier) {
    val previous = remember { arrayOf(numericValue) }
    val previousText = remember { arrayOf(text) }
    val oldText = previousText[0]
    val directionMemory = remember { intArrayOf(1) }
    val before = previous[0]
    val direction =
        if (before != null && numericValue != null && before != numericValue) {
            val sign = if (before < 0 && numericValue <= 0) -1 else 1
            therapyValueRollDirection(before, numericValue) * sign
        } else {
            directionMemory[0]
        }
    SideEffect {
        previous[0] = numericValue
        directionMemory[0] = direction
        previousText[0] = text
    }
    val style =
        LocalTextStyle.current.copy(
            fontSize = fontSize.sp,
            lineHeight = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = SugarliciousColors.TextPrimary,
            fontFeatureSettings = "tnum",
        )
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, character ->
            key(text.lastIndex - index, numericValue != null) {
                if (character in '0'..'9' && numericValue != null) {
                    val place = text.drop(index + 1).count { it in '0'..'9' }
                    SplitFlapDigit(character, direction, place, style, if (before != null) oldText.getOrNull(oldText.lastIndex - (text.lastIndex - index))?.takeIf { it in '0'..'9' } ?: '0' else character)
                } else {
                    Text(character.toString(), style = style, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun SplitFlapDigit(target: Char, direction: Int, place: Int, style: TextStyle, initial: Char) {
    var shown by remember { mutableStateOf(initial) }
    var from by remember { mutableStateOf(initial) }
    var to by remember { mutableStateOf(initial) }
    val progress = remember { Animatable(1f) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val measured = measurer.measure("0", style, softWrap = false).size
    val width = with(density) { measured.width.toDp() }
    val height = with(density) { measured.height.toDp() }
    LaunchedEffect(target, direction) {
        // Interruption starts from the face currently visible, not an obsolete source value.
        shown = if (progress.value < .5f) from else to
        progress.snapTo(1f)
        val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale == 0f) {
            shown = target
            from = target
            to = target
            return@LaunchedEffect
        }
        val steps = splitFlapSteps(shown, target, direction)
        if (steps == 0) return@LaunchedEffect
        delay((place.coerceAtMost(4) * 35L * scale).toLong())
        repeat(steps) {
            from = shown
            to = ('0'.code + (shown.digitToInt() + if (direction > 0) 1 else 9) % 10).toChar()
            progress.snapTo(0f)
            progress.animateTo(1f, tween(if (steps <= 3) 110 else 65, easing = LinearEasing))
            shown = to
        }
    }
    Box(Modifier.width(width).height(height), contentAlignment = Alignment.Center) {
        if (progress.value >= 1f) {
            Text(shown.toString(), style = style, maxLines = 1, softWrap = false)
        } else {
            fun half(top: Boolean): Modifier =
                Modifier.drawWithContent {
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(0f, if (top) 0f else size.height / 2f, size.width, if (top) size.height / 2f else size.height)
                    drawContent()
                    drawContext.canvas.restore()
                }
            val forward = direction > 0
            Text(to.toString(), Modifier.then(half(forward)), style = style, maxLines = 1, softWrap = false)
            Text(from.toString(), Modifier.then(half(!forward)), style = style, maxLines = 1, softWrap = false)
            if (progress.value < .5f) {
                Text(
                    from.toString(),
                    half(forward).graphicsLayer {
                        rotationX = (if (forward) -1 else 1) * progress.value * 180f
                        transformOrigin = TransformOrigin(.5f, .5f)
                        cameraDistance = measured.height * 8f
                    },
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Text(
                    to.toString(),
                    half(!forward).graphicsLayer {
                        rotationX = (if (forward) 1 else -1) * (1f - progress.value) * 180f
                        transformOrigin = TransformOrigin(.5f, .5f)
                        cameraDistance = measured.height * 8f
                    },
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Canvas(Modifier.size(width, height)) {
                val shade = kotlin.math.sin(progress.value * kotlin.math.PI).toFloat() * .13f
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = shade), Color.Transparent)))
            }
        }
    }
}

internal fun splitFlapSteps(from: Char, to: Char, direction: Int): Int {
    if (from !in '0'..'9' || to !in '0'..'9') return 0
    return if (direction > 0) (to - from + 10) % 10 else (from - to + 10) % 10
}
