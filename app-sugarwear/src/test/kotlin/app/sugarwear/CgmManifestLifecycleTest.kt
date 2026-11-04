package app.sugarwear

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35, 36, 37])
class CgmManifestLifecycleTest {
    @Test fun `collector targets android 17 for wear os 7 compatibility`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(37, context.applicationInfo.targetSdkVersion)
    }

    @Test fun `package replacement can restore collector`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(context.packageName)

        val matches = context.packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue(
            matches.any {
                it.activityInfo.name == "app.sugarwear.CgmBootReceiver"
            },
        )
    }

    @Test fun `connected device foreground service permissions and type are declared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in permissions)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in permissions)
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permissions)

        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, CgmCollectorService::class.java),
                0,
            )
        assertTrue(
            service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0,
        )
    }

    @Test fun `collector UI uses a single task and signal loss receiver is registered`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activity =
            context.packageManager.getActivityInfo(
                ComponentName(context, CgmWatchActivity::class.java),
                0,
            )
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activity.launchMode)

        val receiver =
            context.packageManager.getReceiverInfo(
                ComponentName(context, CgmSignalLossReceiver::class.java),
                0,
            )
        assertTrue(receiver.enabled)
        assertTrue(!receiver.exported)
    }

    @Test fun `all collector activities share the wear recents task`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activities =
            listOf(
                CgmWatchActivity::class.java,
                CgmAppearanceActivity::class.java,
                CgmDirectToWatchSettingsActivity::class.java,
                CgmSettingsActivity::class.java,
                CgmAlarmSettingsActivity::class.java,
                CgmSystemStatusActivity::class.java,
            )

        activities.forEach { activityClass ->
            val activity =
                context.packageManager.getActivityInfo(
                    ComponentName(context, activityClass),
                    0,
                )
            assertNull(activity.taskAffinity)
        }
    }
}
