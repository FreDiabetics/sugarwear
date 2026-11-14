package app.sugarlicious.mobile

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.lifecycle.withCreationCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiltApplicationGraphTest {
    @Test fun `application supplies the generated injection graph`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(Class.forName("dagger.hilt.internal.GeneratedComponentManager").isInstance(app))
    }

    @Test fun `assisted editors keep their initial values and retained identity isolated`() {
        val controller = Robolectric.buildActivity(CloudEndpointTreatmentSettingsActivity::class.java).create()
        try {
            val owner = controller.get()

            fun editor(key: String, color: Int) =
                injectedViewModelProvider(owner) { extras ->
                    extras.withCreationCallback<AppearanceColorEditorViewModel.Factory> { it.create(color) }
                }[key, AppearanceColorEditorViewModel::class.java]
            val first = editor("first", 0xffff0000.toInt())
            val second = editor("second", 0xff0000ff.toInt())
            assertNotSame(first, second)
            assertSame(first, editor("first", 0xff0000ff.toInt()))
            assertEquals(0xffff0000.toInt(), first.uiState.value.openingArgb)
            assertEquals(0xff0000ff.toInt(), second.uiState.value.openingArgb)
            val provider = injectedViewModelProvider(owner)
            assertSame(provider[DashboardViewModel::class.java], injectedViewModelProvider(owner)[DashboardViewModel::class.java])
        } finally {
            controller.destroy()
        }
    }
}
