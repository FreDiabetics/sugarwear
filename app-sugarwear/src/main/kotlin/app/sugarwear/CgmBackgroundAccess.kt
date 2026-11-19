package app.sugarwear

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

internal enum class CgmBackgroundRestrictionStatus {
    ALLOWED,
    RESTRICTED,
    UNKNOWN,
}

internal data class CgmBackgroundAccessSnapshot(
    val autoRevokeWhitelisted: CgmBackgroundRestrictionStatus,
    val batteryOptimizationIgnored: Boolean,
) {
    val overallOk: Boolean get() = autoRevokeWhitelisted != CgmBackgroundRestrictionStatus.RESTRICTED && batteryOptimizationIgnored
}

internal object CgmBackgroundAccess {
    fun isBatteryUnrestricted(context: Context): Boolean =
        context
            .getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun isAutoRevokeWhitelisted(context: Context): CgmBackgroundRestrictionStatus =
        runCatching {
            val whitelisted = context.packageManager.isAutoRevokeWhitelisted()
            if (whitelisted) CgmBackgroundRestrictionStatus.ALLOWED else CgmBackgroundRestrictionStatus.RESTRICTED
        }.getOrElse {
            CgmBackgroundRestrictionStatus.UNKNOWN
        }

    fun snapshot(context: Context): CgmBackgroundAccessSnapshot =
        CgmBackgroundAccessSnapshot(
            autoRevokeWhitelisted = isAutoRevokeWhitelisted(context),
            batteryOptimizationIgnored = isBatteryUnrestricted(context),
        )

    // User-initiated exemption is required for the direct BLE glucose collector,
    // whose core data and alert path cannot be deferred by Doze.
    @SuppressLint("BatteryLife")
    internal fun batterySettingsIntents(packageName: String): List<Intent> {
        val packageUri = "package:$packageName".toUri()
        return listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /**
     * Opens the first battery/background settings surface that exists on the current Wear build.
     * Grant state is never faked locally; the Activity re-reads PowerManager on resume.
     */
    fun openBatterySettings(activity: Activity): Boolean =
        CgmSystemAccess.open(activity, batterySettingsIntents(activity.packageName))
}
