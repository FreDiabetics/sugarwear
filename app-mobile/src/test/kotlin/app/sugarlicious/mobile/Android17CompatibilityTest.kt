package app.sugarlicious.mobile

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35, 36, 37])
class Android17CompatibilityTest {
    @Test fun `mobile targets android 17 and declares local network access`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        assertEquals(37, context.applicationInfo.targetSdkVersion)
        assertTrue(LocalNetworkAccessPolicy.PERMISSION in info.requestedPermissions.orEmpty())
    }
}
