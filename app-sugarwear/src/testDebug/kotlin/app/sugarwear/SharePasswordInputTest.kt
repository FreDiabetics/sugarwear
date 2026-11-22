package app.sugarwear

import android.os.Looper
import android.text.method.PasswordTransformationMethod
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.descendants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharePasswordInputTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test fun `account dialog accepts a typed password without a credential provider and clears it on dismissal`() {
        val controller = Robolectric.buildActivity(CgmShareSettingsActivity::class.java).setup().visible()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val root = controller.get().window.decorView as ViewGroup
            root.descendants
                .filterIsInstance<TextView>()
                .first { it.text == "Konto einrichten" }
                .performClick()
            val dialog = checkNotNull(ShadowAlertDialog.getLatestAlertDialog())
            val content = dialog.window!!.decorView as ViewGroup
            val field = content.descendants.filterIsInstance<EditText>().single { it.hint == "Dexcom-Passwort" }
            field.append(" aB!5 ")
            assertEquals(" aB!5 ", field.text.toString())
            assertTrue(field.transformationMethod is PasswordTransformationMethod)
            assertFalse(field.isSaveEnabled)
            assertTrue(field.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            assertTrue(dialog.window!!.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            dialog.dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("", field.text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
