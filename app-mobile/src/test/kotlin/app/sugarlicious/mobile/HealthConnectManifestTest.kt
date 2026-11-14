package app.sugarlicious.mobile

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthConnectManifestTest {
    @Test
    fun `android 14 permission usage rationale is discoverable`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent =
            Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
                .addCategory("android.intent.category.HEALTH_PERMISSIONS")
                .setPackage(context.packageName)

        val matches = context.packageManager.queryIntentActivities(intent, 0)

        assertTrue(matches.isNotEmpty())
        val activityInfo = matches.first().activityInfo
        assertEquals("android.permission.START_VIEW_PERMISSION_USAGE", activityInfo.permission)
    }

    @Test
    fun `blood glucose write permission is declared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )

        assertTrue(
            packageInfo.requestedPermissions.orEmpty().contains(
                "android.permission.health.WRITE_BLOOD_GLUCOSE",
            ),
        )
    }
}
