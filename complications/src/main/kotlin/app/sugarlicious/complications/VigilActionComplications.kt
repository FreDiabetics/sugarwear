package app.sugarlicious.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

internal object DirectToWatchActions {
    fun collector(context: Context): PendingIntent {
        val component = ComponentName("app.sugarwear", "app.sugarwear.CgmWatchActivity")
        val intent =
            context.packageManager.getLaunchIntentForPackage(component.packageName)?.apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } ?: Intent().setComponent(component)
        return PendingIntent.getActivity(context, 701, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun graphScale(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            702,
            Intent(context, DirectToWatchGraphScaleReceiver::class.java).setAction(DirectToWatchGraphScaleReceiver.ACTION_CYCLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Tap-only surfaces never resolve therapy data or render a clinical bitmap. */
abstract class VigilActionComplication(
    private val collector: Boolean,
) : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData = build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData = build()

    private fun build(): ComplicationData =
        SmallImageComplicationData
            .Builder(
                transparentImage,
                PlainComplicationText.Builder(if (collector) "SugarWear öffnen" else "Graphzeitraum wechseln").build(),
            ).setTapAction(if (collector) DirectToWatchActions.collector(this) else DirectToWatchActions.graphScale(this))
            .build()

    private companion object {
        val transparentImage by lazy {
            val icon = Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            SmallImage.Builder(icon, SmallImageType.PHOTO).setAmbientImage(icon).build()
        }
    }
}

class DirectToWatchCollectorActionComplication : VigilActionComplication(true)

class DirectToWatchGraphActionComplication : VigilActionComplication(false)
