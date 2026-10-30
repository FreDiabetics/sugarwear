package app.aapswear.g7watch

import android.app.Notification

/** Platform-builder equivalent of the NotificationCompat silent flag. */
@Suppress("DEPRECATION") // Required compatibility path for the platform builder below API 26 channels.
internal fun Notification.Builder.setSilent(silent: Boolean): Notification.Builder =
    apply {
        if (silent) {
            setSound(null)
            setVibrate(null)
        }
    }
