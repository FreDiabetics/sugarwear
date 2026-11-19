package app.sugarwear

import android.app.NotificationManager
import android.content.Context

internal object CgmAlertPresentation {
    const val ACTIVE_NOTIFICATION_ID = 7199

    fun show(
        context: Context,
        alarm: SugarWearAlarmInstance,
        settings: SugarWearAlarmSettings,
        playSound: Boolean,
    ) {
        clearLegacyNotifications(context)
        CgmAlarmNotifier.show(context, alarm, settings, onlyAlertOnce = !playSound)
    }

    fun cancelCurrent(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(ACTIVE_NOTIFICATION_ID)
        clearLegacyNotifications(context)
        CgmAlarmAudioService.stop(context)
    }

    fun clearLegacyNotifications(context: Context) {
        SugarWearAlarmType.entries.forEach { type ->
            CgmAlarmNotifier.cancel(context, type)
        }
    }
}
