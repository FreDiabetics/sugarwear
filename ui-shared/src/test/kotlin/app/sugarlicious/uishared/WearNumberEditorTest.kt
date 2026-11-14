package app.sugarlicious.uishared

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w226dp-h226dp-round-340dpi")
class WearNumberEditorTest {
    @Test fun `watch numeric entry uses a round full screen instead of phone alert`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_WATCH, true)
        SharedNumberEditor.show(activity, "Größe", 12, 1, 30) {}
        val dialog = ShadowDialog.getLatestDialog()
        assertFalse(dialog is AlertDialog)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun `numeric editor keeps actions reachable above the keyboard and restores padding`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_WATCH, true)
        SharedNumberEditor.show(activity, "Größe", 12, 1, 30) {}
        val dialog = ShadowDialog.getLatestDialog()
        val scroll = dialog.window!!.decorView.findViewWithTag<android.view.View>("wear_number_scroll")
        val keyboard =
            androidx.core.view.WindowInsetsCompat
                .Builder()
                .setInsets(
                    androidx.core.view.WindowInsetsCompat.Type
                        .ime(),
                    androidx.core.graphics.Insets
                        .of(0, 0, 0, 120),
                ).build()
        androidx.core.view.ViewCompat
            .dispatchApplyWindowInsets(scroll, keyboard)
        assertEquals(120, scroll.paddingBottom)
        androidx.core.view.ViewCompat
            .dispatchApplyWindowInsets(
                scroll,
                androidx.core.view.WindowInsetsCompat
                    .Builder()
                    .build(),
            )
        assertEquals(0, scroll.paddingBottom)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun `invalid input stays open and cancel never writes while valid input commits once`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_WATCH, true)
        var saved = 12
        var writes = 0
        SharedNumberEditor.show(activity, "Größe", 12, 1, 30) {
            saved = it
            writes++
        }
        val dialog = ShadowDialog.getLatestDialog()
        val root = dialog.window!!.decorView
        val input = root.findViewWithTag<EditText>("wear_number_input")
        val save = root.findViewWithTag<android.view.View>("wear_number_save")
        input.setText("99")
        save.performClick()
        assertTrue(dialog.isShowing)
        assertEquals(0, writes)
        input.setText("23")
        save.performClick()
        assertFalse(dialog.isShowing)
        assertEquals(23, saved)
        assertEquals(1, writes)
        SharedNumberEditor.show(activity, "Größe", 23, 1, 30) { writes++ }
        ShadowDialog.getLatestDialog().dismiss()
        assertEquals(1, writes)
        controller.pause().stop().destroy()
    }

    @Test fun `text value preserves sensor leading zeros and uses caller validation`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_WATCH, true)
        var saved = ""
        var dismissed = 0
        SharedNumberEditor.showText(
            activity,
            "Sensorcode",
            "0012",
            android.text.InputType.TYPE_CLASS_NUMBER,
            { if (it.length == 4) null else "4 Ziffern" },
            { saved = it },
            { dismissed++ },
        )
        val dialog = ShadowDialog.getLatestDialog()
        val root = dialog.window!!.decorView
        assertEquals("0012", root.findViewWithTag<EditText>("wear_number_input").text.toString())
        root.findViewWithTag<android.view.View>("wear_number_save").performClick()
        assertEquals("0012", saved)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, dismissed)
        controller.pause().stop().destroy()
    }
}
