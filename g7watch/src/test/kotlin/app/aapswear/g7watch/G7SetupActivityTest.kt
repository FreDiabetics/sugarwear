package app.aapswear.g7watch

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
class G7SetupActivityTest {
    @Test fun `first run explains Bluetooth without automatically pairing or granting access`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val activity = Robolectric.buildActivity(G7SetupActivity::class.java).setup().get()
        val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        assertTrue(texts.contains("Bluetooth"))
        assertTrue(texts.contains("Noch nicht erlaubt"))
        assertFalse(app.getSharedPreferences("g7_setup", Context.MODE_PRIVATE).getBoolean("complete", false))
        assertTrue(G7SystemAccess.missingRuntimePermissions(app).contains(Manifest.permission.BLUETOOTH_SCAN))
        descendants(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Später einrichten" }.performClick()
        assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "Benachrichtigungen" })
        assertTrue(G7SystemAccess.missingRuntimePermissions(app).contains(Manifest.permission.BLUETOOTH_SCAN))
        activity.finish()
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
}
