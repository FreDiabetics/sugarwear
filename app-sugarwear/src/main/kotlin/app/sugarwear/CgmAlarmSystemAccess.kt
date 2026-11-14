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
    val policySettingsAvailable: Boolean = false,
    val watchSettingsAvailable: Boolean = false,
    val fullScreenAllowed: Boolean = true,
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
    when {
        snapshot.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE -> "Vollständige Ruhe blockiert Alarmtöne"
        snapshot.dndAllowsAlarmAudio == true -> "Alarmton vom Ruhemodus zugelassen"
        snapshot.dndAllowsAlarmAudio == false -> "Alarmton durch Ruhemodus blockiert"
        else -> "Alarmton im Ruhemodus nicht prüfbar"
    }

internal fun cgmAlarmAccessLabel(snapshot: CgmAlarmSystemSnapshot): String =
    when {
        snapshot.policyAccessGranted -> "Nicht-Stören-Zugriff erlaubt"
        !snapshot.policySettingsAvailable -> "Auf dieser Uhr nicht verfügbar"
        else -> "Nicht-Stören-Zugriff fehlt"
    }

internal fun cgmAlarmAudioWarning(snapshot: CgmAlarmSystemSnapshot): String? =
    when {
        snapshot.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ->
            "Vollständige Ruhe blockiert den Alarmton. Eine App-Ausnahme oder eigene Lautstärke hebt diesen Systemmodus nicht auf. Erlaube Alarme in den Nicht-Stören-Einstellungen der Uhr."
        snapshot.dndAllowsAlarmAudio == false ->
            "Der Nicht-Stören-Modus blockiert Alarmtöne. Erlaube dort die Systemausnahme Alarme; die Benachrichtigungsausnahme allein reicht nicht."
        else -> null
    }

internal fun cgmAlarmAccessGuidance(snapshot: CgmAlarmSystemSnapshot): String =
    if (!snapshot.policySettingsAvailable) {
        "Diese Uhr bietet keine App-Ausnahme zum Freigeben an. Eigene SugarWear-Töne benötigen zugelassene System-Alarme. Prüfe Nicht stören in den Uhreinstellungen; vollständige Ruhe bleibt stumm."
    } else {
        "Die App-Ausnahme betrifft Benachrichtigungen. Eigene SugarWear-Töne benötigen zusätzlich zugelassene System-Alarme. Vollständige Ruhe bleibt auch mit App-Ausnahme oder eigener Lautstärke stumm."
    }

internal data class CgmAlarmSettingsAction(
    val label: String,
    val action: String,
)

internal fun cgmAlarmSettingsAction(snapshot: CgmAlarmSystemSnapshot): CgmAlarmSettingsAction? =
    when {
        snapshot.dndAllowsAlarmAudio == false && snapshot.watchSettingsAvailable -> CgmAlarmSettingsAction("Uhreinstellungen öffnen", Settings.ACTION_SETTINGS)
        snapshot.policySettingsAvailable && !snapshot.policyAccessGranted -> CgmAlarmSettingsAction("Systemfreigabe öffnen", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        snapshot.watchSettingsAvailable -> CgmAlarmSettingsAction("Uhreinstellungen öffnen", Settings.ACTION_SETTINGS)
        snapshot.policySettingsAvailable -> CgmAlarmSettingsAction("Systemfreigabe öffnen", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        else -> null
    }

internal object CgmAlarmSystemAccess {
    private fun settingsAvailable(context: Context, action: String): Boolean =
        runCatching {
            context.packageManager
                .resolveActivity(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.let { it.exported && !CgmSystemAccess.isKnownSettingsStub(it) } == true
        }.onFailure { android.util.Log.w("CgmAlarmSystemAccess", "Cannot inspect settings action $action", it) }.getOrDefault(false)

    fun openRecommendedSettings(activity: Activity, snapshot: CgmAlarmSystemSnapshot): CgmSettingsOpenResult {
        val target = cgmAlarmSettingsAction(snapshot) ?: return CgmSettingsOpenResult.UNAVAILABLE
        if (target.action == Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) return openPolicySettings(activity)
        return if (CgmSystemAccess.open(activity, listOf(Intent(target.action)))) CgmSettingsOpenResult.OPENED else CgmSettingsOpenResult.FAILED
    }

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
            policySettingsAvailable = settingsAvailable(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            watchSettingsAvailable = settingsAvailable(context, Settings.ACTION_SETTINGS),
            fullScreenAllowed = android.os.Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent(),
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
