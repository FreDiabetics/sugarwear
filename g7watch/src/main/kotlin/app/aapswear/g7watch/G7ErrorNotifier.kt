package app.aapswear.g7watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import androidx.core.content.edit
import app.aapswear.g7.CgmAlarmType
import app.aapswear.g7.G7CollectorError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

internal data class G7AcknowledgedError(
    val signature: String,
    val acknowledgedAtEpochMs: Long,
)

internal fun g7ErrorSignature(error: G7CollectorError): String = "${error.code}|${error.safeMessage}"

/** High-priority surface reserved for an actually actionable direct-Watch problem. */
internal object G7ErrorNotifier {
    private const val CHANNEL_PREFIX = "direct_watch_collector_errors_v5_d"
    private const val CHANNEL_FAMILY_PREFIX = "direct_watch_collector_errors_"
    private const val CHANNEL_NAME = "SugarWear-Fehler"
    private const val NOTIFICATION_ID = 7002
    private const val PREFS = "g7_error_notifications"
    private const val KEY_ACTIVE_SIGNATURE = "active_signature"
    private const val KEY_ACTIVE_CODE = "active_code"
    private const val KEY_ACTIVE_MESSAGE = "active_message"
    private const val KEY_FIRST_OCCURRED_AT = "first_occurred_at"
    private const val KEY_LAST_POSTED_AT = "last_posted_at"
    private const val KEY_LAST_ACK_SIGNATURE = "last_ack_signature"
    private const val KEY_LAST_ACK_AT = "last_ack_at"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = channelId(context)
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_FAMILY_PREFIX) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }
        manager.createNotificationChannel(
            NotificationChannel(channelId, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Dringende Fehler von SugarWear"
                enableVibration(true)
                setSound(
                    g7AlarmSoundUri(context, CgmAlarmType.SENSOR_ERROR),
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setBypassDnd(G7AlarmSystemAccess.isAccessGranted(context))
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun show(
        context: Context,
        error: G7CollectorError,
    ) {
        ensureChannel(context)
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = g7ErrorSignature(error)
        val previousSignature = prefs.getString(KEY_ACTIVE_SIGNATURE, null)
        val sameActiveError = signature == previousSignature
        val firstOccurredAt =
            if (sameActiveError) {
                prefs.getLong(KEY_FIRST_OCCURRED_AT, error.occurredAtEpochMs)
            } else {
                error.occurredAtEpochMs
            }
        prefs.edit {
            putString(KEY_ACTIVE_SIGNATURE, signature)
            putString(KEY_ACTIVE_CODE, error.code)
            putString(KEY_ACTIVE_MESSAGE, error.safeMessage)
            putLong(KEY_FIRST_OCCURRED_AT, firstOccurredAt)
            putLong(KEY_LAST_POSTED_AT, System.currentTimeMillis())
        }

        app.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                context = app,
                title = if (error.code == "G7-SIGNAL-LOSS") "Signalverlust" else "SugarWear-Fehler ${error.code}",
                body = error.safeMessage,
                occurredAtEpochMs = firstOccurredAt,
                onlyAlertOnce = sameActiveError,
            ),
        )
    }

    /** A valid new reading means the active connection problem is over; do not require cleanup acknowledgement. */
    fun markRecovered(context: Context) {
        clearActive(context)
    }

    fun clearActive(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_ACTIVE_SIGNATURE)
            remove(KEY_ACTIVE_CODE)
            remove(KEY_ACTIVE_MESSAGE)
            remove(KEY_FIRST_OCCURRED_AT)
            remove(KEY_LAST_POSTED_AT)
        }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun acknowledge(context: Context): G7AcknowledgedError? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = prefs.getString(KEY_ACTIVE_SIGNATURE, null) ?: return null
        val acknowledgedAt = System.currentTimeMillis()
        prefs.edit {
            putString(KEY_LAST_ACK_SIGNATURE, signature)
            putLong(KEY_LAST_ACK_AT, acknowledgedAt)
            remove(KEY_ACTIVE_SIGNATURE)
            remove(KEY_ACTIVE_CODE)
            remove(KEY_ACTIVE_MESSAGE)
            remove(KEY_FIRST_OCCURRED_AT)
            remove(KEY_LAST_POSTED_AT)
        }
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        return G7AcknowledgedError(signature, acknowledgedAt)
    }

    private fun buildNotification(
        context: Context,
        title: String,
        body: String,
        occurredAtEpochMs: Long,
        onlyAlertOnce: Boolean,
    ): Notification {
        val openIntent = g7OpenAppIntent(context)
        val openApp =
            PendingIntent.getActivity(
                context,
                7002,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val acknowledge =
            PendingIntent.getBroadcast(
                context,
                7002,
                Intent(context, G7ErrorAcknowledgeReceiver::class.java).setAction(G7ErrorAcknowledgeReceiver.ACTION_ACKNOWLEDGE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val errorTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(occurredAtEpochMs))
        val detail = "$body\nAufgetreten: $errorTime"
        val actionIcon = Icon.createWithResource(context, R.drawable.ic_g7_notification)
        return Notification
            .Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_g7_notification)
            .setColor(0xFFFF5D6C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(occurredAtEpochMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(onlyAlertOnce)
            .addAction(Notification.Action.Builder(actionIcon, "Quittieren", acknowledge).build())
            .build()
    }

    internal fun channelId(context: Context): String =
        CHANNEL_PREFIX + if (G7AlarmSystemAccess.isAccessGranted(context)) "1" else "0"
}

class G7ErrorAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val acknowledged = G7ErrorNotifier.acknowledge(context) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.applicationContext.recordG7Diagnostic(
                    code = "G7-ALERT-ACK",
                    message = "Collector error notification acknowledged",
                    metadata =
                        mapOf(
                            "signature" to acknowledged.signature,
                            "acknowledgedAtEpochMs" to acknowledged.acknowledgedAtEpochMs,
                        ),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "app.aapswear.g7watch.ACKNOWLEDGE_ERROR"
    }
}
