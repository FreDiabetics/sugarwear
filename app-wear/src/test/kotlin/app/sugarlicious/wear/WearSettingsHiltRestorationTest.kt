package app.sugarlicious.wear

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearSettingsHiltRestorationTest {
    @Test fun `latest category and list position survive repeated process recreation`() {
        var saved: Bundle? = null
        listOf("graph" to 111, "tiles" to 222, "colors" to 333).forEachIndexed { index, next ->
            val controller = Robolectric.buildActivity(WearSettingsActivity::class.java).create(saved)
            try {
                val activity = controller.get()
                val model = ViewModelProvider.create(activity)[WearSettingsViewModel::class.java]
                if (index > 0) {
                    val expected = listOf("graph" to 111, "tiles" to 222)[index - 1]
                    assertEquals(expected.first, model.uiState.value.category)
                    assertEquals(expected.second, model.uiState.value.categoryListScrollY)
                }
                model.openCategory(next.first, next.second)
                saved = Bundle().also { controller.saveInstanceState(it) }
            } finally {
                controller.destroy()
            }
        }
    }
}
