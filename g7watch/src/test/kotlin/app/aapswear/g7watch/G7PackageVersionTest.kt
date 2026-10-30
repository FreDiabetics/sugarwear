package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7PackageVersionTest {
    @Test
    fun `suite release exposes the unified version through installed metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        assertEquals(71L, packageInfo.longVersionCode)
        assertEquals(
            "0.7.1",
            app.aapswear.uishared.AppReleaseInfo
                .version(context),
        )
    }
}
