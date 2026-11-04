package app.sugarwear

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmNavigationTest {
    @Test fun `notification navigation reuses single task without activity flags`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = cgmOpenAppIntent(context)

        assertEquals(ComponentName(context, CgmWatchActivity::class.java), intent.component)
        assertEquals(0, intent.flags)
    }
}
