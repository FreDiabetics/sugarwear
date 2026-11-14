package app.sugarlicious.uishared

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppReleaseInfoTest {
    @Test fun `about uses installed package version and includes current release changes`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unbekannt"
        AppReleaseInfo.Product.entries.forEach { product ->
            val summary = AppReleaseInfo.summary(context, product)
            assertTrue(summary.startsWith("Version $installed"))
            assertTrue(summary.contains("\n\nNeu\n• "))
        }
    }
}
