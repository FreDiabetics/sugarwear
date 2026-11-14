package app.sugarlicious.wear

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35, 36, 37])
class WearRuntimeManifestTest {
    @Test fun `wear app targets android 17 for wear os 7 compatibility`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(37, context.applicationInfo.targetSdkVersion)
    }

    @Test fun `persistent runtime permissions and connected device type are declared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
            )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in permissions)
        assertTrue("android.permission.CHANGE_NETWORK_STATE" in permissions)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in permissions)
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permissions)

        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, StateDataLayerService::class.java),
                0,
            )
        assertTrue(
            service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0,
        )
    }

    @Test fun `boot and package replacement can restore Wear runtime`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach { action ->
            val matches =
                context.packageManager.queryBroadcastReceivers(
                    Intent(action).setPackage(context.packageName),
                    0,
                )
            assertTrue(
                matches.any { it.activityInfo.name == "app.sugarlicious.wear.WearRuntimeBootReceiver" },
            )
        }
    }
}
