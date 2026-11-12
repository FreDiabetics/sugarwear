package app.sugarwear

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmPauseExitDialogTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test fun `explicit pause offers closing once and reopening stays paused without another dialog`() {
        val controller = Robolectric.buildActivity(CgmSystemStatusActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            val vm = androidx.lifecycle.ViewModelProvider(activity)[CgmSystemStatusViewModel::class.java]
            awaitCgmSettingsUi(activity) { vm.state.value.snapshot != null && !vm.state.value.loading }
            val before = CgmSensorStateStore(activity).read()
            activity.pauseCollectorWithExitOffer()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            assertNotNull(dialog.window!!.decorView.findViewWithTag<android.view.View>("pause-exit-confirm"))
            awaitCgmSettingsUi(activity) { !vm.state.value.loading && !CgmSensorStateStore(activity).read().collectorEnabled }
            assertFalse(CgmSensorStateStore(activity).read().collectorEnabled)
            assertEquals(before.sensor, CgmSensorStateStore(activity).read().sensor)
            assertEquals(before.lastReading, CgmSensorStateStore(activity).read().lastReading)
            dialog.window!!
                .decorView
                .findViewWithTag<android.view.View>("pause-exit-cancel")
                .performClick()
        } finally {
            controller.pause().stop().destroy()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val reopened = Robolectric.buildActivity(CgmSystemStatusActivity::class.java).setup().visible()
        try {
            val reopenedVm = androidx.lifecycle.ViewModelProvider(reopened.get())[CgmSystemStatusViewModel::class.java]
            awaitCgmSettingsUi(reopened.get()) { reopenedVm.state.value.snapshot != null && !reopenedVm.state.value.loading }
            assertFalse(ShadowDialog.getLatestDialog().isShowing)
            assertFalse(CgmSensorStateStore(reopened.get()).read().collectorEnabled)
        } finally {
            reopened.pause().stop().destroy()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }
}
