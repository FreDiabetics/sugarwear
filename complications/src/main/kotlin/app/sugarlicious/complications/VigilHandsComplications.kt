package app.sugarlicious.complications

import android.app.PendingIntent
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
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest

object VigilHands {
    private const val PREFERENCES = "vigil_interaction"
    private const val DIMMED = "hands_dimmed"
    private var canonicalBitmaps: Pair<Bitmap, Bitmap>? = null

    private fun bitmaps(context: Context): Pair<Bitmap, Bitmap> =
        synchronized(this) {
            canonicalBitmaps ?: (
                BitmapFactory.decodeResource(context.resources, R.drawable.hour_hand_transparent) to
                    BitmapFactory.decodeResource(context.resources, R.drawable.minute_hand_transparent)
            ).also { canonicalBitmaps = it }
        }

    fun opacity(context: Context): Float = if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DIMMED, false)) .3f else 1f

    @android.annotation.SuppressLint("ApplySharedPref") // The tap state must be durable before publishing a new image.
    internal fun toggle(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        check(preferences.edit().putBoolean(DIMMED, !preferences.getBoolean(DIMMED, false)).commit())
        requestUpdate(context)
    }

    fun requestUpdate(context: Context) {
        ActiveProviderUpdates.request(context, DirectToWatchHandsComplication::class.java)
    }

    /** Reference composite for 22:10 capture, using the same assets/geometry as native WFF hands. */
    fun render(context: Context, hour: Int, minute: Int, ambient: Boolean = false): Bitmap {
        val output = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val (hourBitmap, minuteBitmap) = bitmaps(context)
        val hourAngle = (hour % 12) * 30f + minute * .5f
        val minuteAngle = minute * 6f

        fun hand(bitmap: Bitmap, angle: Float, shadow: Boolean) {
            val x = if (shadow) 2f else 0f
            val y = if (shadow) 3f else 0f
            val saved = canvas.save()
            canvas.rotate(angle, 225f + x, 225f + y)
            paint.colorFilter = if (shadow) PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN) else null
            paint.alpha = if (shadow) 160 else 255
            canvas.drawBitmap(bitmap, null, RectF(x, y, 450f + x, 450f + y), paint)
            canvas.restoreToCount(saved)
        }
        if (!ambient) {
            hand(hourBitmap, hourAngle, true)
            hand(minuteBitmap, minuteAngle, true)
        }
        hand(hourBitmap, hourAngle, false)
        hand(minuteBitmap, minuteAngle, false)
        // The canonical ApeX second-hand pivot has an 8.5 px white outer disc and
        // a 5 px black inner disc on its 512 px canvas. Preserve that hub in the preview composite.
        paint.colorFilter = null
        paint.alpha = 255
        paint.color = Color.WHITE
        canvas.drawCircle(225f, 225f, 8.5f * 450f / 512f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(225f, 225f, 5f * 450f / 512f, paint)
        val alpha = ((if (ambient) 160 else 255) * opacity(context)).toInt()
        if (alpha == 255) return output
        return Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(output, 0f, 0f, Paint().apply { this.alpha = alpha })
        }
    }
}

class DirectToWatchHandsComplication : TrackedComplicationService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData = build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        ActiveProviderUpdates.activate(this, javaClass, request.complicationInstanceId)
        return build()
    }

    internal fun build(): ComplicationData {
        val mask = Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb((255 * VigilHands.opacity(this@DirectToWatchHandsComplication)).toInt(), 255, 255, 255)) })
        return SmallImageComplicationData
            .Builder(
                SmallImage
                    .Builder(mask, SmallImageType.PHOTO)
                    .setAmbientImage(mask)
                    .build(),
                PlainComplicationText.Builder("Vigil Zeiger").build(),
            ).build()
    }
}

class DirectToWatchHandsToggleComplication : TrackedComplicationService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData = build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        ActiveProviderUpdates.activate(this, javaClass, request.complicationInstanceId)
        return build()
    }

    private fun build(): ComplicationData {
        val transparent = Icon.createWithBitmap(Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        val action = PendingIntent.getBroadcast(this, 710, Intent(this, VigilHandsToggleReceiver::class.java).setAction(VigilHandsToggleReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return SmallImageComplicationData
            .Builder(
                SmallImage.Builder(transparent, SmallImageType.PHOTO).setAmbientImage(transparent).build(),
                PlainComplicationText.Builder("Zeigerdeckkraft umschalten").build(),
            ).setTapAction(action)
            .build()
    }
}

class VigilHandsToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION) VigilHands.toggle(context)
    }

    companion object {
        const val ACTION = "app.aapswear.complications.TOGGLE_VIGIL_HANDS"
    }
}
