package app.aapswear.g7watch

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7AlarmSystemAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()

    @Test
    fun `own audio follows effective alarm policy not notification channel bypass`() {
        assertEquals(true, g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_ALL, false))
        assertEquals(true, g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_ALARMS, false))
        assertEquals(false, g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_NONE, true))
        assertEquals(false, g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_PRIORITY, false))
        assertEquals(true, g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_PRIORITY, true))
        assertNull(g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_PRIORITY, null))
        assertNull(g7DndAllowsAlarmAudio(NotificationManager.INTERRUPTION_FILTER_UNKNOWN, true))
    }

    @Test
    fun `only a policy access settings screen can grant policy access`() {
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), G7AlarmSystemAccess.settingsCandidates(activity).map(Intent::getAction))
    }

    @Test
    fun `unavailable policy screen does not misleadingly open unrelated settings`() {
        val attempted = mutableListOf<String?>()
        val result =
            G7AlarmSystemAccess.openPolicySettings(activity, launch = {
                attempted += it.action
                error("vendor rejected activity")
            })
        assertEquals(G7SettingsOpenResult.FAILED, result)
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), attempted)
    }

    @Test
    fun `settings launch does not require package visibility query match`() {
        val attempted = mutableListOf<String?>()
        val result = G7AlarmSystemAccess.openPolicySettings(activity, launch = { attempted += it.action })
        assertEquals(G7SettingsOpenResult.OPENED, result)
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), attempted)
    }

    @Test
    fun `unsupported device is reported when no public setting resolves`() {
        assertEquals(
            G7SettingsOpenResult.UNAVAILABLE,
            G7AlarmSystemAccess.openPolicySettings(activity, canResolve = { false }, launch = {}),
        )
    }

    @Test
    fun `snapshot reads notification permission and actual policy access`() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val denied = G7AlarmSystemAccess.snapshot(context)
        assertFalse(denied.policyAccessGranted)
        assertTrue(denied.notificationPermissionGranted)

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        assertTrue(G7AlarmSystemAccess.snapshot(context).policyAccessGranted)
    }
}
