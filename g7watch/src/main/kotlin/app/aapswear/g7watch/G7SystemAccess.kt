package app.aapswear.g7watch

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
import app.aapswear.g7.CgmAlarmType

internal data class G7SystemAccessSnapshot(
    val bluetoothGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val exactAlarmsGranted: Boolean,
    val dndAccessGranted: Boolean,
    val alarmChannels: List<G7AlarmChannelAccess>,
)

internal data class G7AlarmChannelAccess(
    val id: String,
    val title: String,
    val importance: Int?,
    val bypassDnd: Boolean,
)

/** Reads system state; opening a settings screen never changes the reported grant. */
internal object G7SystemAccess {
    val runtimePermissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    fun snapshot(context: Context): G7SystemAccessSnapshot {
        val missing = missingRuntimePermissions(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = G7AlarmSettingsStore.read(context)
        return G7SystemAccessSnapshot(
            bluetoothGranted = Manifest.permission.BLUETOOTH_SCAN !in missing && Manifest.permission.BLUETOOTH_CONNECT !in missing,
            notificationsGranted = Manifest.permission.POST_NOTIFICATIONS !in missing && manager.areNotificationsEnabled(),
            batteryUnrestricted = G7BackgroundAccess.isBatteryUnrestricted(context),
            exactAlarmsGranted = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            dndAccessGranted = manager.isNotificationPolicyAccessGranted,
            alarmChannels =
                CgmAlarmType.entries.map { type ->
                    val id = G7CgmAlarmNotifier.channelId(context, type, settings)
                    val channel = manager.getNotificationChannel(id)
                    G7AlarmChannelAccess(id, g7AlarmTitle(type), channel?.importance, channel?.canBypassDnd() == true)
                },
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
