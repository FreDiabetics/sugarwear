package app.sugarlicious.complications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.uishared.SystemTypography

internal fun isVigilPairingRequired(presentation: DirectToWatchHeaderPresentation): Boolean =
    presentation.sensorDisconnected || presentation.glucose == "-" && presentation.secondary.startsWith("Bitte Sensor")

internal fun hasVigilSignalLoss(state: TherapyDisplayState?, now: Long): Boolean =
    DirectSensorReadingResolver.directSessionState(state) != "PAUSED" && !isVigilSensorDisconnected(state) && TherapyDisplayFormatter.freshness(state, now) == Freshness.SIGNAL_LOSS

internal fun vigilNeedsPairing(state: TherapyDisplayState?, now: Long): Boolean =
    isVigilPairingRequired(DirectToWatchPresentationFormatter.header(state, now))

private fun warningPaint(size: Float, foreground: Int) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = size
        textAlign = Paint.Align.CENTER
        SystemTypography.apply(this, true)
    }

private fun drawAlert(context: Context, canvas: Canvas, left: Float, top: Float, size: Float) {
    context.getDrawable(app.sugarlicious.uishared.R.drawable.ic_alert)?.apply {
        setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        draw(canvas)
    }
}

internal fun renderVigilInactiveHeader(context: Context, ambient: Boolean, paused: Boolean = false, message: String? = null): Bitmap {
    val image = Bitmap.createBitmap(340, 108, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    drawAlert(context, canvas, 115f, 18f, 40f)
    val paint = warningPaint(22f * context.resources.configuration.fontScale, if (ambient) 0xFFB5B5B5.toInt() else Color.WHITE)
    val text = message ?: if (paused) "Datenempfang pausiert" else "Kein Sensor verbunden"
    if (paint.measureText(text) > 268f) paint.textSize *= 268f / paint.measureText(text)
    canvas.drawText(text, 135f, 93f, paint)
    return image
}

/** A simple icon and white label below the untouched graph, without a mask or pill. */
internal fun renderVigilSignalLossFooter(context: Context): Bitmap {
    val image = Bitmap.createBitmap(270, 78, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    drawAlert(context, canvas, 117f, 0f, 36f)
    val paint = warningPaint(30f * context.resources.configuration.fontScale, Color.WHITE)
    val text = "Signalverlust"
    val fit = minOf(1f, 250f / paint.measureText(text), 34f / (paint.descent() - paint.ascent()))
    paint.textSize *= fit
    canvas.drawText(text, 135f, 76f - paint.descent(), paint)
    return image
}
