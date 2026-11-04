package app.aapswear.g7watch

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7PauseExitDialogTest {
    @get:org.junit.Rule val mainDispatcher = G7MainDispatcherRule()

    @Test fun `explicit pause offers closing once and reopening stays paused without another dialog`() {
        val controller = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(activity)[G7SystemStatusViewModel::class.java]
            awaitG7SettingsUi(activity) { vm.state.value.snapshot != null && !vm.state.value.loading }
            val before = G7SensorStateStore(activity).read()
            activity.pauseCollectorWithExitOffer()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertEquals("SugarWear beenden?", Shadows.shadowOf(dialog).title)
            awaitG7SettingsUi(activity) { !vm.state.value.loading && !G7SensorStateStore(activity).read().collectorEnabled }
            assertFalse(G7SensorStateStore(activity).read().collectorEnabled)
            assertEquals(before.sensor, G7SensorStateStore(activity).read().sensor)
            assertEquals(before.lastReading, G7SensorStateStore(activity).read().lastReading)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        } finally {
            controller.pause().stop().destroy()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val reopened = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup().visible()
        try {
            val reopenedVm = androidx.lifecycle.ViewModelProvider(reopened.get())[G7SystemStatusViewModel::class.java]
            awaitG7SettingsUi(reopened.get()) { reopenedVm.state.value.snapshot != null && !reopenedVm.state.value.loading }
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
            assertFalse(G7SensorStateStore(reopened.get()).read().collectorEnabled)
        } finally {
            reopened.pause().stop().destroy()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }
}
