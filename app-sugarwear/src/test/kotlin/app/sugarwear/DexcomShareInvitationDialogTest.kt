package app.sugarwear

import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.descendants
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DexcomShareInvitationDialogTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test fun `configured account can open and cancel an invite with uploads off and no receiver`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("sugarwear_dexcom_share", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("encrypted_account", "test")
            .putString("encrypted_password", "test")
            .commit()
        val controller = Robolectric.buildActivity(CgmShareSettingsActivity::class.java).setup().visible()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val root = controller.get().window.decorView as ViewGroup
            val toggle = root.descendants.filterIsInstance<app.sugarlicious.uishared.ScrollSafeSwitch>().single { it.text == "Dexcom Share aktivieren" }
            assertFalse(toggle.isChecked)
            val testButton = root.descendants.filterIsInstance<android.widget.Button>().single { it.text == "Verbindung testen" }
            assertTrue(testButton.isEnabled)
            assertNotNull(testButton.background)
            root.descendants
                .filterIsInstance<TextView>()
                .single { it.text == "Follower einladen" }
                .performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = checkNotNull(ShadowDialog.getLatestDialog())
            val dialogRoot = dialog.window!!.decorView as ViewGroup
            val fields = dialogRoot.descendants.filterIsInstance<EditText>().toList()
            fields.single { it.hint == "Name des Followers" }.setText("Test")
            val email = fields.single { it.hint == "E-Mail des Followers" }
            email.setText("invalid")
            dialogRoot.descendants.filterIsInstance<android.widget.Button>().single { it.text == "Einladung senden" }.performClick()
            assertTrue(dialog.isShowing)
            assertNotNull(email.error)
            dialogRoot.descendants.filterIsInstance<android.widget.Button>().single { it.text == controller.get().getString(android.R.string.cancel) }.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(dialog.isShowing)
            assertFalse(prefs.getBoolean("enabled", false))
        } finally {
            controller.pause().stop().destroy()
            prefs.edit().clear().commit()
        }
    }
}
