package app.sugarwear

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings

internal enum class CgmSettingsOpenResult { OPENED, UNAVAILABLE, FAILED }

internal data class CgmAlarmSystemSnapshot(
    val policyAccessGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val alarmVolume: Int,
    val alarmVolumeMaximum: Int,
    val ringerMode: Int,
    val interruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
    val priorityAllowsAlarms: Boolean? = null,
) {
    val dndAllowsAlarmAudio: Boolean? get() = cgmDndAllowsAlarmAudio(interruptionFilter, priorityAllowsAlarms)
}

internal fun cgmDndAllowsAlarmAudio(filter: Int, priorityAllowsAlarms: Boolean?): Boolean? =
    when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
        NotificationManager.INTERRUPTION_FILTER_NONE -> false
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> priorityAllowsAlarms
        else -> null
    }

internal fun cgmAlarmAudioPolicyLabel(snapshot: CgmAlarmSystemSnapshot): String =
    when (snapshot.dndAllowsAlarmAudio) {
        true -> "Alarmton vom Ruhemodus zugelassen"
        false -> "Alarmton durch Ruhemodus blockiert"
        null -> "Alarmton im Ruhemodus nicht prüfbar"
    }

internal object CgmAlarmSystemAccess {
    fun isAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun snapshot(context: Context): CgmAlarmSystemSnapshot {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        return CgmAlarmSystemSnapshot(
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
    ): CgmSettingsOpenResult {
        var attempted = false
        settingsCandidates(activity).forEach { intent ->
            if (!canResolve(intent) || CgmSystemAccess.isKnownSettingsStub(activity, intent)) return@forEach
            attempted = true
            if (runCatching { launch(intent) }.isSuccess) return CgmSettingsOpenResult.OPENED
        }
        return if (attempted) CgmSettingsOpenResult.FAILED else CgmSettingsOpenResult.UNAVAILABLE
    }
}
