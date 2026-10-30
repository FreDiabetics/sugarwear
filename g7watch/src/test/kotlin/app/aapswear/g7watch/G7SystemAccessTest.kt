package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.provider.Settings
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
class G7SystemAccessTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun fakeSettingsInfo(system: Boolean = true) =
        ActivityInfo().apply {
            packageName = "com.google.android.apps.wearable.settings"
            name = "com.google.android.clockwork.settings.FakeSettingsActivity"
            applicationInfo = ApplicationInfo().apply { flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0 }
        }

    @Test fun `only exact system settings stub is rejected`() {
        assertTrue(G7SystemAccess.isKnownSettingsStub(fakeSettingsInfo()))
        assertFalse(G7SystemAccess.isKnownSettingsStub(null))
        assertFalse(G7SystemAccess.isKnownSettingsStub(fakeSettingsInfo(false)))
        assertFalse(G7SystemAccess.isKnownSettingsStub(fakeSettingsInfo().apply { packageName = "example.other" }))
        assertFalse(G7SystemAccess.isKnownSettingsStub(fakeSettingsInfo().apply { name = "example.RealSettings" }))
    }

    @Test fun `known battery no op falls through to app details but unresolved intent still launches`() {
        val intents = G7BackgroundAccess.batterySettingsIntents(activity.packageName)
        val info =
            fakeSettingsInfo().apply {
                enabled = true
                exported = true
                applicationInfo.packageName = packageName
            }
        val manager = shadowOf(activity.packageManager)
        manager.addOrUpdateActivity(info)
        intents.take(2).forEach { intent ->
            val filter =
                IntentFilter(intent.action).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    intent.data?.scheme?.let(::addDataScheme)
                }
            manager.addIntentFilterForActivity(ComponentName(info.packageName, info.name), filter)
        }
        val launched = mutableListOf<String?>()
        assertTrue(G7SystemAccess.open(activity, intents) { launched += it.action })
        assertEquals(listOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS), launched)

        launched.clear()
        assertTrue(G7SystemAccess.open(activity, listOf(Intent("example.unresolved.SETTINGS"))) { launched += it.action })
        assertEquals(listOf("example.unresolved.SETTINGS"), launched)
    }

    @Test fun `channel settings target own package and selected channel`() {
        val intent = G7SystemAccess.notificationSettingsIntents("app.sugarwear", "urgent").first()
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("app.sugarwear", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals("urgent", intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun `exact alarm and app details carry package URI`() {
        val intents = G7SystemAccess.exactAlarmSettingsIntents("example.collector")
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intents.first().action)
        assertEquals("package:example.collector", intents.first().data.toString())
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents[1].action)
        assertEquals("package:example.collector", intents[1].data.toString())
    }

    @Test fun `unsupported per app settings fall through and total failure is visible`() {
        val attempted = mutableListOf<String?>()
        assertTrue(
            G7SystemAccess.open(activity, G7SystemAccess.notificationSettingsIntents(activity.packageName)) {
                attempted += it.action
                if (it.action != Settings.ACTION_SETTINGS) throw android.content.ActivityNotFoundException()
            },
        )
        assertEquals(Settings.ACTION_SETTINGS, attempted.last())
        assertFalse(G7SystemAccess.open(activity, listOf(Intent(Settings.ACTION_SETTINGS))) { throw SecurityException() })
    }

    @Test fun `runtime permission set excludes location and privileged Bluetooth`() {
        assertEquals(setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS), G7SystemAccess.runtimePermissions.toSet())
    }
}
