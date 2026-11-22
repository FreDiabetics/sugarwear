package app.sugarwear

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri

internal data class CgmSystemAccessSnapshot(
    val bluetoothGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val exactAlarmsGranted: Boolean,
    val dndAccessGranted: Boolean,
    val alarmChannels: List<CgmAlarmChannelAccess>,
    val autoRevokeStatus: CgmBackgroundRestrictionStatus = CgmBackgroundRestrictionStatus.UNKNOWN,
)

internal data class CgmAlarmChannelAccess(
    val id: String,
    val title: String,
    val importance: Int?,
    val bypassDnd: Boolean,
)

/** A submitted notification is not proof that Wear OS displayed or announced it. */
internal enum class CgmAlarmNotificationReadiness {
    READY,
    PERMISSION_MISSING,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    CHANNEL_NOT_PROMINENT,
    CHANNEL_NOT_REGISTERED,
    ;

    val canSubmit: Boolean
        get() = this != PERMISSION_MISSING && this != APP_NOTIFICATIONS_DISABLED

    val label: String
        get() =
            when (this) {
                READY -> "Alarmkanäle priorisiert"
                PERMISSION_MISSING -> "Benachrichtigungen nicht erlaubt"
                APP_NOTIFICATIONS_DISABLED -> "App-Benachrichtigungen deaktiviert"
                CHANNEL_DISABLED -> "Mindestens ein Alarmkanal deaktiviert"
                CHANNEL_NOT_PROMINENT -> "Mindestens ein Alarmkanal nicht priorisiert"
                CHANNEL_NOT_REGISTERED -> "Alarmkanäle noch nicht eingerichtet"
            }
}

internal fun cgmAlarmNotificationReadiness(
    permissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channelImportance: Int?,
): CgmAlarmNotificationReadiness =
    when {
        !permissionGranted -> CgmAlarmNotificationReadiness.PERMISSION_MISSING
        !appNotificationsEnabled -> CgmAlarmNotificationReadiness.APP_NOTIFICATIONS_DISABLED
        channelImportance == null -> CgmAlarmNotificationReadiness.CHANNEL_NOT_REGISTERED
        channelImportance == NotificationManager.IMPORTANCE_NONE -> CgmAlarmNotificationReadiness.CHANNEL_DISABLED
        channelImportance < NotificationManager.IMPORTANCE_HIGH -> CgmAlarmNotificationReadiness.CHANNEL_NOT_PROMINENT
        else -> CgmAlarmNotificationReadiness.READY
    }

internal fun cgmAlarmNotificationOverview(access: CgmSystemAccessSnapshot): CgmAlarmNotificationReadiness {
    if (!access.notificationsGranted) return CgmAlarmNotificationReadiness.PERMISSION_MISSING
    val states =
        access.alarmChannels.map { channel ->
            cgmAlarmNotificationReadiness(true, true, channel.importance)
        }
    return when {
        CgmAlarmNotificationReadiness.CHANNEL_DISABLED in states -> CgmAlarmNotificationReadiness.CHANNEL_DISABLED
        CgmAlarmNotificationReadiness.CHANNEL_NOT_REGISTERED in states -> CgmAlarmNotificationReadiness.CHANNEL_NOT_REGISTERED
        CgmAlarmNotificationReadiness.CHANNEL_NOT_PROMINENT in states -> CgmAlarmNotificationReadiness.CHANNEL_NOT_PROMINENT
        else -> CgmAlarmNotificationReadiness.READY
    }
}

/** Reads system state; opening a settings screen never changes the reported grant. */
internal object CgmSystemAccess {
    val bluetoothPermissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    val runtimePermissions = bluetoothPermissions + Manifest.permission.POST_NOTIFICATIONS

    fun missingBluetoothPermissions(context: Context): Array<String> =
        bluetoothPermissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    fun snapshot(context: Context): CgmSystemAccessSnapshot {
        val missing = missingRuntimePermissions(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = CgmAlarmSettingsStore.read(context)
        return CgmSystemAccessSnapshot(
            bluetoothGranted = Manifest.permission.BLUETOOTH_SCAN !in missing && Manifest.permission.BLUETOOTH_CONNECT !in missing,
            notificationsGranted = Manifest.permission.POST_NOTIFICATIONS !in missing && manager.areNotificationsEnabled(),
            batteryUnrestricted = CgmBackgroundAccess.isBatteryUnrestricted(context),
            exactAlarmsGranted = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            dndAccessGranted = manager.isNotificationPolicyAccessGranted,
            alarmChannels =
                SugarWearAlarmType.entries.map { type ->
                    val id = CgmAlarmNotifier.channelId(context, type, settings)
                    val channel = manager.getNotificationChannel(id)
                    CgmAlarmChannelAccess(id, cgmAlarmTitle(type), channel?.importance, channel?.canBypassDnd() == true)
                },
            autoRevokeStatus = CgmBackgroundAccess.isAutoRevokeWhitelisted(context),
        )
    }

    fun appSettingsIntents(packageName: String): List<Intent> =
        listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData("package:$packageName".toUri()),
            Intent(Settings.ACTION_SETTINGS),
        )

    fun notificationSettingsIntents(packageName: String, channelId: String? = null): List<Intent> =
        buildList {
            if (channelId != null) {
                add(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
                )
            }
            add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            addAll(appSettingsIntents(packageName))
        }

    fun exactAlarmSettingsIntents(packageName: String): List<Intent> =
        listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData("package:$packageName".toUri())) + appSettingsIntents(packageName)

    internal fun isKnownSettingsStub(info: ActivityInfo?): Boolean =
        info != null &&
            info.packageName == "com.google.android.apps.wearable.settings" &&
            (
                info.name == "com.google.android.clockwork.settings.FakeSettingsActivity" ||
                    info.targetActivity == "com.google.android.clockwork.settings.FakeSettingsActivity"
            ) &&
            (info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    fun isKnownSettingsStub(activity: Activity, intent: Intent): Boolean =
        isKnownSettingsStub(runCatching { activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }.getOrNull())

    // A missing query result must still be launched: visibility can hide a usable settings app.
    // Only the positively identified platform no-op is skipped.
    fun open(activity: Activity, intents: List<Intent>, launch: (Intent) -> Unit = activity::startActivity): Boolean {
        for (intent in intents) {
            if (isKnownSettingsStub(activity, intent)) continue
            if (runCatching { launch(intent) }.isSuccess) return true
        }
        return false
    }
}
