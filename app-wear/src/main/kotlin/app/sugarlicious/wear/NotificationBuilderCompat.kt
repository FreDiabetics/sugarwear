package app.sugarlicious.wear

import android.app.Notification

/**
 * Platform Notification.Builder does not expose NotificationCompat.Builder#setSilent.
 * Keep the call-site semantics explicit without introducing an AndroidX notification builder.
 * The LOW-importance channel remains the primary Android 8+ sound/vibration policy.
 */
@Suppress("DEPRECATION") // Required compatibility path for the platform builder below API 26 channels.
internal fun Notification.Builder.setSilent(silent: Boolean): Notification.Builder =
    apply {
        if (silent) {
            setSound(null)
            setVibrate(null)
        }
    }
