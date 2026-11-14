package app.sugarlicious.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

internal object AppRuntimeAccess {
    fun notificationLabel(context: Context): String =
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            "Erlaubt"
        } else {
            "Freigeben"
        }

    fun batteryLabel(context: Context): String = if (isIgnoringBatteryOptimizations(context)) "Uneingeschränkt" else "Optimiert"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context
            .getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
}
