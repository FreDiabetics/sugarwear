package app.sugarlicious.complications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.DailyTimeInRange
import app.sugarlicious.model.DailyTir
import app.sugarlicious.storage.TherapyStateStore
import app.sugarlicious.uishared.SystemTypography
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Internal Orbit Arc source, not an extra user-configurable complication slot. */
class OrbitDailyTirComplication : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val now = System.currentTimeMillis()
        val store = TherapyStateStore(this)
        val resolved = DirectSensorReadingResolver.resolve(this, store.state.first())
        val history = store.recordHistory(resolved, now)
        val days = DailyTimeInRange.calculate(history, now, ZoneId.systemDefault(), complicationCgmThresholds())
        return payload(days)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        payload(
            (4 downTo 0).map { DailyTir(LocalDate.now().minusDays(it.toLong()), listOf(82, 76, 91, 87, 89)[4 - it], 60_000) },
        )

    private fun payload(days: List<DailyTir>): ComplicationData {
        val description =
            days.joinToString("; ") {
                "${it.date}: ${it.percent?.let { p -> "$p%" } ?: "– (Tagesdaten unvollständig)"}, ${it.coveredMillis / 60_000} Minuten erfasst"
            }
        return MonochromaticImageComplicationData
            .Builder(
                MonochromaticImage.Builder(Icon.createWithBitmap(renderDailyTir(days))).build(),
                PlainComplicationText.Builder("TIR · $description · vorhandene Messdaten; heute unvollständig").build(),
            ).build()
    }
}

internal fun renderDailyTir(days: List<DailyTir>): Bitmap {
    val bitmap = Bitmap.createBitmap(660, 330, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            SystemTypography.apply(this, emphasized = true)
        }
    val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    days.take(5).forEachIndexed { index, day ->
        val angle = -160f + index * 35f

        fun caption(value: String, radius: Float, size: Float, alpha: Int) {
            paint.textSize = size
            paint.alpha = alpha
            // Weather/moon positions describe the visual centre, not a Canvas baseline.
            // Centre glyphs radially too, otherwise bold letters drift into the outer band.
            val baselineRadius = radius + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            val path =
                Path().apply {
                    addArc(RectF(330 - baselineRadius, 330 - baselineRadius, 330 + baselineRadius, 330 + baselineRadius), angle - 17, 34f)
                }
            canvas.drawTextOnPath(value, path, 0f, 0f, paint)
        }
        caption(day.date.format(weekday), 285.58f, 48f, 255)
        caption(day.percent?.let { "$it%" } ?: "–", 193.42f, 46f, 255)
    }
    return bitmap
}

internal fun android.content.Context.complicationCgmThresholds(): CgmThresholds {
    val preferences = getSharedPreferences("watch_display", android.content.Context.MODE_PRIVATE)
    return CgmThresholds(
        veryHighMgDl = preferences.getFloat("threshold_very_high", 250f).toDouble(),
        highMgDl = preferences.getFloat("threshold_high", 180f).toDouble(),
        lowMgDl = preferences.getFloat("threshold_low", 70f).toDouble(),
        veryLowMgDl = preferences.getFloat("threshold_very_low", 50f).toDouble(),
    ).takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT
}
