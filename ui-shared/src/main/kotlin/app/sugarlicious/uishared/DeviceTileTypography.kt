package app.sugarlicious.uishared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.graphics.createBitmap
import kotlin.math.ceil

/** Device-owned regular/bold fonts; no bundled copy and no repeated package lookup per reading. */
class DeviceTileTypefaceCache(
    private val load: (Boolean) -> Typeface?,
) {
    private val faces = mutableMapOf<Boolean, Typeface?>()

    @Synchronized fun get(bold: Boolean): Typeface? {
        if (!faces.containsKey(bold)) faces[bold] = load(bold)
        return faces[bold]
    }

    @Synchronized fun clear() = faces.clear()
}

fun loadDeviceTileTypeface(context: Context, bold: Boolean): Typeface? {
    if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
    return runCatching {
        val fontContext = context.createPackageContext("com.monotype.android.font.samsungone", 0)
        Typeface.createFromAsset(fontContext.assets, "fonts/SamsungOneUI-${if (bold) "Bold" else "Regular"}.ttf")
    }.getOrNull()
}

/** DeviceVendor's ProtoLayout host ignores FlipFont families, so draw only the text with the device font. */
fun renderDeviceTileTextBitmap(context: Context, face: Typeface, content: CharSequence, sizeSp: Float, color: Int, maxWidthPx: Int = Int.MAX_VALUE, maxHeightPx: Int = Int.MAX_VALUE): Bitmap {
    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
            this.color = color
        }
    val desiredWidth = Layout.getDesiredWidth(content, paint).coerceAtLeast(1f)
    val desiredHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent + paint.fontMetrics.leading).coerceAtLeast(1f)
    val fit = minOf(1f, maxWidthPx.coerceAtLeast(1) / desiredWidth, maxHeightPx.coerceAtLeast(1) / desiredHeight)
    paint.textSize *= fit

    fun buildLayout(): StaticLayout {
        val width = ceil(Layout.getDesiredWidth(content, paint).toDouble()).toInt().coerceIn(1, maxWidthPx.coerceAtLeast(1))
        return StaticLayout.Builder
            .obtain(content, 0, content.length, paint, width)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
    }
    var layout = buildLayout()
    if (layout.height > maxHeightPx.coerceAtLeast(1)) {
        paint.textSize *= maxHeightPx.coerceAtLeast(1).toFloat() / layout.height
        layout = buildLayout()
    }
    return createBitmap(layout.width, layout.height.coerceAtLeast(1)).also { layout.draw(Canvas(it)) }
}
