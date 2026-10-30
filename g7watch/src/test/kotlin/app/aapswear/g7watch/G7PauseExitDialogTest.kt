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
    @Test fun `explicit pause offers closing once and reopening stays paused without another dialog`() {
        val controller = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup()
        val activity = controller.get()
        val before = G7SensorStateStore(activity).read()
        activity.pauseCollectorWithExitOffer()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertEquals("SugarWear beenden?", Shadows.shadowOf(dialog).title)
        assertFalse(G7SensorStateStore(activity).read().collectorEnabled)
        assertEquals(before.sensor, G7SensorStateStore(activity).read().sensor)
        assertEquals(before.lastReading, G7SensorStateStore(activity).read().lastReading)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(G7SystemStatusActivity::class.java).setup()
        try {
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
            assertFalse(G7SensorStateStore(reopened.get()).read().collectorEnabled)
        } finally {
            reopened.pause().stop().destroy()
        }
    }
}
