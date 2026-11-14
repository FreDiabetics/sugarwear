package app.sugarlicious.uishared

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Rgb565Image(
    val data: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
)

fun Bitmap.toRgb565Image(): Rgb565Image {
    val colors = IntArray(width * height)
    getPixels(colors, 0, width, 0, 0, width, height)
    val bytes = ByteBuffer.allocate(colors.size * 2).order(ByteOrder.nativeOrder())
    colors.forEach { color ->
        val rgb565 =
            ((Color.red(color) shr 3) shl 11) or
                ((Color.green(color) shr 2) shl 5) or
                (Color.blue(color) shr 3)
        bytes.putShort(rgb565.toShort())
    }
    return Rgb565Image(bytes.array(), width, height)
}
