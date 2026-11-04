package app.aapswear.g7watch

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7TileSelectorBrandTest {
    @Test fun `both selectors reference the standalone brand vector`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(G7CollectorTileService::class.java, G7GraphTileService::class.java).forEach { service ->
            val info = context.packageManager.getServiceInfo(ComponentName(context, service), 0)
            assertEquals(R.drawable.ic_tile_brand, info.icon)
        }
    }
}
