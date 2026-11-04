package app.sugarwear

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiltApplicationGraphTest {
    @Test fun `application supplies the generated injection graph`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(Class.forName("dagger.hilt.internal.GeneratedComponentManager").isInstance(app))
    }
}
