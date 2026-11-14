package app.sugarlicious.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize

/** Original watchface assets above edited complication demos, at the fixed selector time. */
@Composable
internal fun NativePreviewHands(faceIndex: Int, modifier: Modifier = Modifier) {
    val apex = faceIndex == 0
    val hour = ImageBitmap.imageResource(if (apex) R.drawable.apex_preview_hour_hand else R.drawable.orbit_preview_hour_hand)
    val minute = ImageBitmap.imageResource(if (apex) R.drawable.apex_preview_minute_hand else R.drawable.orbit_preview_minute_hand)
    val second = ImageBitmap.imageResource(if (apex) R.drawable.apex_preview_second_hand else R.drawable.orbit_preview_second_hand)
    Canvas(modifier) {
        val target = IntSize(size.width.toInt(), size.height.toInt())
        val dial = if (apex) Color(0xFFFFF6D8) else Color.White
        listOf(Triple(hour, 304.2667f, dial), Triple(minute, 51.2f, dial), Triple(second, 192f, Color(0xFFFFA16B))).forEach { (image, angle, tint) ->
            rotate(angle, Offset(size.width / 2, size.height / 2)) {
                drawImage(image, dstSize = target, colorFilter = if (apex || image == second) ColorFilter.tint(tint) else null)
            }
        }
    }
}
