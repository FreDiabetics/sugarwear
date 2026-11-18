package app.sugarwear

import android.Manifest
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
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
class CgmSetupActivityTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test fun `first run explains notifications without automatically granting access`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildActivity(CgmSetupActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            awaitCgmSettingsUi(activity) {
                descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Benachrichtigungen" }
            }
            val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertTrue(texts.contains("Benachrichtigungen"))
            assertTrue(texts.contains("Noch nicht erlaubt"))
            assertFalse(app.getSharedPreferences("g7_setup", Context.MODE_PRIVATE).getBoolean("complete", false))
            assertTrue(CgmSystemAccess.missingRuntimePermissions(app).contains(Manifest.permission.POST_NOTIFICATIONS))
            descendants(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Später einrichten" }.performClick()
            assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "Genaue Alarme" })
            assertTrue(CgmSystemAccess.missingRuntimePermissions(app).contains(Manifest.permission.POST_NOTIFICATIONS))
        } finally {
            controller.pause().stop().destroy()
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
}
