package app.sugarwear.debug.playground.testing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarwear.R

/**
 * Publishes test notifications to a dedicated debug channel for visual inspection.
 * Uses a separate channel without sound/vibration by default, preventing false alarms.
 */
object DebugNotificationPublisher {
    private const val DEBUG_CHANNEL_ID = "sugarwear_debug_playground_channel_v1"
    private const val DEBUG_CHANNEL_NAME = "SugarWear Playground Test Notification"
    private const val DEBUG_NOTIFICATION_ID = 9991

    fun ensureDebugChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DEBUG_CHANNEL_ID) == null) {
            val channel =
                NotificationChannel(
                    DEBUG_CHANNEL_ID,
                    DEBUG_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Kanal für SugarWear Playground Test-Notifications"
                    enableVibration(false)
                    setSound(null, null)
                }
            manager.createNotificationChannel(channel)
        }
    }

    fun postTestErrorNotification(context: Context, error: CgmCollectorError) {
        ensureDebugChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val notification =
            NotificationCompat
                .Builder(context, DEBUG_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cgm_notification)
                .setContentTitle("[TEST] SugarWear-Fehler ${error.code}")
                .setContentText("[TEST] ${error.safeMessage}")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

        manager.notify(DEBUG_NOTIFICATION_ID, notification)
    }

    fun cancelAllTestNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(DEBUG_NOTIFICATION_ID)
    }
}
