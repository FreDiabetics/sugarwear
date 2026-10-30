package app.aapswear.g7watch

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarWearProviderVisibilityTest {
    @Test fun `Vigil internals stay bindable but hidden while ordinary providers stay selectable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        @Suppress("DEPRECATION")
        val services =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)
                .services
                .orEmpty()
                .filter { it.permission == "com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER" }
        val internal = services.filter { it.name.substringAfterLast('.').startsWith("DirectToWatch") }
        val ordinary = services - internal.toSet()
        assertEquals(12, internal.size)
        assertEquals(8, ordinary.size)
        assertTrue(internal.all { it.exported && it.enabled && it.metaData.getBoolean("android.support.wearable.complications.HIDDEN") })
        assertTrue(ordinary.all { it.exported && it.enabled && it.metaData?.getBoolean("android.support.wearable.complications.HIDDEN") != true })
    }
}
