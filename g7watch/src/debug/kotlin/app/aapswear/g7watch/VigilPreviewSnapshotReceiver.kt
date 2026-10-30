package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.aapswear.complications.VigilHands
import app.aapswear.complications.renderVigilClock
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Debug-only device-font capture using the production WFF geometry and canonical hand assets. */
class VigilPreviewSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val source = BitmapFactory.decodeFile(File(context.filesDir, "vigil-preview-source.png").path) ?: return
        require(source.width == source.height && source.width in 450..2_000)
        val output = renderVigilPreview(context, source)
        File(context.filesDir, "vigil-preview-2210.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        const val ACTION = "app.aapswear.g7watch.DEBUG_VIGIL_PREVIEW_2210"
    }
}

internal fun renderVigilPreview(context: Context, source: Bitmap, includeDecorations: Boolean = false, replaceClock: Boolean = true): Bitmap {
    val document =
        context.assets.open("vigil_preview_definition.xml").use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }

    fun Element.number(name: String, fallback: Float = 0f): Float = getAttribute(name).toFloatOrNull() ?: fallback

    fun Element.bounds() = RectF(number("x"), number("y"), number("x") + number("width"), number("y") + number("height"))

    val clock =
        (0 until document.getElementsByTagName("ComplicationSlot").length)
            .map { document.getElementsByTagName("ComplicationSlot").item(it) as Element }
            .single { it.getAttribute("slotId") == "5" }
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    val scale = source.width / document.documentElement.number("width")
    canvas.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val clockBounds = clock.bounds()
    if (replaceClock) {
        canvas.drawRect(clockBounds, Paint().apply { color = Color.BLACK })
        canvas.drawBitmap(renderVigilClock(context, "22:10"), null, clockBounds, paint)
    }
    if (!includeDecorations) return output

    val groups = document.getElementsByTagName("Group")
    val markers = (0 until groups.length).map { groups.item(it) as Element }.single { it.getAttribute("name") == "vigil-perimeter-markers" }
    canvas.withTranslation(markers.number("x"), markers.number("y")) {
        val parts = markers.getElementsByTagName("PartImage")
        for (index in 0 until parts.length) {
            val part = parts.item(index) as Element
            val image = part.getElementsByTagName("Image").item(0) as Element
            val resource = image.getAttribute("resource")
            if (!resource.endsWith("_close")) continue
            val bitmap = context.assets.open(resource + ".png").use(BitmapFactory::decodeStream)
            paint.alpha = 255
            paint.colorFilter = PorterDuffColorFilter(if (resource.contains("minutemarkers")) 0xFF2B2B2B.toInt() else 0xFF282828.toInt(), PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, null, part.bounds(), paint)
            paint.colorFilter = null
        }
    }

    paint.alpha = 255
    canvas.drawBitmap(VigilHands.render(context, 10, 8), 0f, 0f, paint)
    val seconds = context.assets.open("second_hand_transparent.png").use(BitmapFactory::decodeStream)
    canvas.withTranslation(2f, 3f) {
        canvas.withRotation(192f, 225f, 225f) {
            paint.colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            paint.alpha = (160 * VigilHands.opacity(context)).toInt()
            canvas.drawBitmap(seconds, null, RectF(0f, 0f, 450f, 450f), paint)
        }
    }
    paint.colorFilter = PorterDuffColorFilter(0xFFFF3B30.toInt(), PorterDuff.Mode.MULTIPLY)
    paint.alpha = (255 * VigilHands.opacity(context)).toInt()
    canvas.withRotation(192f, 225f, 225f) {
        canvas.drawBitmap(seconds, null, RectF(0f, 0f, 450f, 450f), paint)
    }
    return output
}
