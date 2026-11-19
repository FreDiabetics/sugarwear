package app.sugarwear

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmAlarmCapabilityUiTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `permissions activity displays four canonical permission cards`() {
        val controller = Robolectric.buildActivity(CgmPermissionsActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            awaitCgmSettingsUi(activity) { texts(activity.window.decorView).any { it.text == "Benachrichtigungen" } }
            val titles = texts(activity.window.decorView).map { it.text.toString() }.toList()
            assertTrue(titles.contains("Benachrichtigungen"))
            assertTrue(titles.contains("Genaue Alarme"))
            assertTrue(titles.contains("Bluetooth"))
            assertTrue(titles.contains("Hintergrundbetrieb/Akkuoptimierungen"))
            assertFalse(titles.contains("Nicht stören"))
            assertFalse(titles.contains("Alarmkanäle"))
        } finally {
            controller.pause().stop().destroy()
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test fun `total silence is named explicitly rather than implying missing policy access`() {
        val snapshot = CgmAlarmSystemSnapshot(true, true, true, 15, 15, 2, NotificationManager.INTERRUPTION_FILTER_NONE, true)
        assertEquals("Vollständige Ruhe blockiert Alarmtöne", cgmAlarmAudioPolicyLabel(snapshot))
    }

    @Test fun `a real exported policy screen remains available without granting access`() {
        val handler =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "example.settings"
                        name = "example.settings.PolicyActivity"
                        exported = true
                        enabled = true
                        applicationInfo = ApplicationInfo().apply { packageName = "example.settings" }
                    }
            }
        registerPolicyActivity(handler.activityInfo)
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        val snapshot = CgmAlarmSystemAccess.snapshot(context)
        assertTrue(snapshot.policySettingsAvailable)
        assertFalse(snapshot.policyAccessGranted)
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, cgmAlarmSettingsAction(snapshot)?.action)
    }

    @Test fun `blocked audio directs to real watch settings rather than an irrelevant exemption`() {
        val snapshot = CgmAlarmSystemSnapshot(false, true, true, 15, 15, 2, NotificationManager.INTERRUPTION_FILTER_NONE, false, policySettingsAvailable = true, watchSettingsAvailable = true)
        assertEquals(Settings.ACTION_SETTINGS, cgmAlarmSettingsAction(snapshot)?.action)
    }

    @Test fun `four step setup evaluates permissions cleanly`() {
        val access = CgmSystemAccessSnapshot(true, true, true, true, false, listOf(CgmAlarmChannelAccess("alarm", "Alarm", 2, false)))
        val alarm = CgmAlarmSystemSnapshot(false, true, true, 15, 15, 2, NotificationManager.INTERRUPTION_FILTER_ALL, false)
        val model = CgmSetupViewModel { }
        model.restore(0, false)
        assertTrue(model.granted(access, alarm))
        model.restore(1, false)
        assertTrue(model.granted(access, alarm))
        model.restore(2, false)
        assertTrue(model.granted(access, alarm))
        model.restore(3, false)
        assertTrue(model.granted(access, alarm))
    }

    @Test fun `test notification warns that total silence blocks its bundled sound`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        CgmAlarmNotifier.showTest(context, SugarWearAlarmType.HIGH, CgmAlarmSettingsStore.read(context))
        val notification = shadowOf(manager).getNotification(CgmAlarmNotifier.testNotificationId(SugarWearAlarmType.HIGH))
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .contains("Vollständige Ruhe"),
        )
    }

    private fun registerPolicyActivity(info: ActivityInfo) {
        val manager = shadowOf(context.packageManager)
        manager.addOrUpdateActivity(info)
        manager.addIntentFilterForActivity(ComponentName(info.packageName, info.name), IntentFilter(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    private fun texts(view: View): Sequence<TextView> = descendants(view).filterIsInstance<TextView>()

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
}
