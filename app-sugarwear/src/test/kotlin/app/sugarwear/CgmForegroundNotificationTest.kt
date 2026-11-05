package app.sugarwear

import android.app.Notification
import app.sugarlicious.sensor.protocol.CollectorCycleClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmForegroundNotificationTest {
    @Suppress("DEPRECATION") // Notification sound has no replacement inspection API.
    @Test
    fun `collector foreground notification is ongoing silent and not auto cancel`() {
        val service = Robolectric.buildService(CgmCollectorService::class.java).get()
        val notification = service.notification()

        assertEquals("Foreground Channel", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNull(notification.sound)
    }

    @Test fun `foreground notification refreshes only for a newly committed fresh value`() {
        assertTrue(shouldUpdateCgmForegroundNotification(inserted = true, CollectorCycleClassification.SUCCESS_FRESH))
        assertFalse(shouldUpdateCgmForegroundNotification(inserted = false, CollectorCycleClassification.SUCCESS_FRESH))
        assertFalse(shouldUpdateCgmForegroundNotification(inserted = true, CollectorCycleClassification.SUCCESS_AGED))
        assertFalse(shouldUpdateCgmForegroundNotification(inserted = true, CollectorCycleClassification.INVALID_PACKET))
    }
}
