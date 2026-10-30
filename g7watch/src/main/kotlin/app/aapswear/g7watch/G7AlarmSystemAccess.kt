package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings

internal enum class G7SettingsOpenResult { OPENED, UNAVAILABLE, FAILED }

internal data class G7AlarmSystemSnapshot(
    val policyAccessGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val alarmVolume: Int,
    val alarmVolumeMaximum: Int,
    val ringerMode: Int,
    val interruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
    val priorityAllowsAlarms: Boolean? = null,
) {
    val dndAllowsAlarmAudio: Boolean? get() = g7DndAllowsAlarmAudio(interruptionFilter, priorityAllowsAlarms)
}

internal fun g7DndAllowsAlarmAudio(filter: Int, priorityAllowsAlarms: Boolean?): Boolean? =
    when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
        NotificationManager.INTERRUPTION_FILTER_NONE -> false
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> priorityAllowsAlarms
        else -> null
    }

internal fun g7AlarmAudioPolicyLabel(snapshot: G7AlarmSystemSnapshot): String =
    when (snapshot.dndAllowsAlarmAudio) {
        true -> "Alarmton vom Ruhemodus zugelassen"
        false -> "Alarmton durch Ruhemodus blockiert"
        null -> "Alarmton im Ruhemodus nicht prüfbar"
    }

internal object G7AlarmSystemAccess {
    fun isAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun snapshot(context: Context): G7AlarmSystemSnapshot {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        return G7AlarmSystemSnapshot(
            policyAccessGranted = notifications.isNotificationPolicyAccessGranted,
            notificationPermissionGranted =
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = notifications.areNotificationsEnabled(),
            alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM),
            alarmVolumeMaximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            ringerMode = audio.ringerMode,
            interruptionFilter = runCatching { notifications.currentInterruptionFilter }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_UNKNOWN),
            priorityAllowsAlarms =
                runCatching {
                    notifications.consolidatedNotificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
                }.getOrNull(),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun settingsCandidates(activity: Activity): List<Intent> =
        // App-notification and general settings cannot grant policy access.
        listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openPolicySettings(
        activity: Activity,
        canResolve: (Intent) -> Boolean = { true },
        launch: (Intent) -> Unit = activity::startActivity,
    ): G7SettingsOpenResult {
        var attempted = false
        settingsCandidates(activity).forEach { intent ->
            if (!canResolve(intent) || G7SystemAccess.isKnownSettingsStub(activity, intent)) return@forEach
            attempted = true
            if (runCatching { launch(intent) }.isSuccess) return G7SettingsOpenResult.OPENED
        }
        return if (attempted) G7SettingsOpenResult.FAILED else G7SettingsOpenResult.UNAVAILABLE
    }
}
