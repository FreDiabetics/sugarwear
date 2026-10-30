package app.aapswear.g7watch

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7NavigationTest {
    @Test fun `notification navigation reuses single task without activity flags`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = g7OpenAppIntent(context)

        assertEquals(ComponentName(context, G7WatchActivity::class.java), intent.component)
        assertEquals(0, intent.flags)
    }
}
