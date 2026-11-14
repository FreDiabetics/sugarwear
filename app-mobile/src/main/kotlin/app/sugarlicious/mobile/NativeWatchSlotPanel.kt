package app.sugarlicious.mobile

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal fun nativeWatchSlotPath(bounds: NativeSlotBounds): Path =
    Path().apply {
        val rect = Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        when (bounds.kind) {
            "BoundingOval" -> addOval(rect)
            "BoundingArc" -> {
                val half = bounds.thickness / 2f
                val outer = Rect(rect.left - half, rect.top - half, rect.right + half, rect.bottom + half)
                val inner = Rect(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
                val sweep = bounds.endAngle - bounds.startAngle
                arcTo(outer, bounds.startAngle - 90f, sweep, true)
                arcTo(inner, bounds.endAngle - 90f, -sweep, false)
                close()
            }
            else -> addRect(rect)
        }
    }

@Composable
internal fun NativeWatchSlotHighlight(slot: NativeWatchSlot, modifier: Modifier = Modifier) {
    val blink by rememberInfiniteTransition(label = "slot-highlight").animateFloat(
        initialValue = .25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "slot-outline",
    )
    Canvas(modifier) {
        scale(size.minDimension / 512f, pivot = Offset.Zero) {
            drawPath(nativeWatchSlotPath(slot.bounds), Color(0xFF6CA9FF).copy(alpha = blink), style = Stroke(5f))
        }
    }
}

@Composable
internal fun NativeWatchSlotPanel(faceIndex: Int, slots: List<NativeWatchSlot>, selectedId: Int?, assignments: Map<Int, Int> = emptyMap(), compact: Boolean = false, onSelected: (Int) -> Unit) {
    val nativeBounds = slots.map { it.bounds }
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (compact) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                nativeSlotEditorOrder(faceIndex, slots).forEach { slot ->
                    NativeSlotChoice(faceIndex, slot, selectedId == slot.id, assignments[slot.id], nativeBounds, Modifier.width((104f * fontScale).dp), onSelected)
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = (maxWidth.value / (85f * fontScale)).toInt().coerceIn(1, 4)
                Column {
                    nativeSlotEditorOrder(faceIndex, slots).chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { slot ->
                                NativeSlotChoice(faceIndex, slot, selectedId == slot.id, assignments[slot.id], nativeBounds, Modifier.weight(1f), onSelected)
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeSlotChoice(faceIndex: Int, slot: NativeWatchSlot, selected: Boolean, variantId: Int?, bounds: List<NativeSlotBounds>, modifier: Modifier, onSelected: (Int) -> Unit) {
    val enabled = isNativeSlotEditable(slot)
    Surface(modifier = modifier.alpha(if (enabled) 1f else .35f).semantics { this.selected = selected && enabled }.clickable(enabled = enabled) { onSelected(slot.id) }, shape = RoundedCornerShape(12.dp), color = OneUiWidgetStyle.action) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IsolatedNativeSlotPreview(faceIndex, slot, selected && enabled, Modifier.size((72f * LocalDensity.current.fontScale).dp), bounds, variantId)
            Text(slot.displayName, color = OneUiWidgetStyle.primary, style = oneUiMenuTypography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
