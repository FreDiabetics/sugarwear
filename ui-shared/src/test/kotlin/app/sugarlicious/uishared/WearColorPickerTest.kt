package app.sugarlicious.uishared

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w226dp-h226dp-round-340dpi")
class WearColorPickerTest {
    @Test fun `reset callback remains authoritative instead of recreating an override`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var override: Int? = Color.RED
        val dialog = WearColorPicker.show(controller.get(), "Farbe", Color.RED, Color.GREEN, { override = it }, { override = null }, {})
        dialog.window!!
            .decorView
            .findViewWithTag<View>("wear_color_reset")
            .performClick()
        org.junit.Assert.assertNull(override)
        assertEquals(
            "#FF00FF00",
            dialog.window!!
                .decorView
                .findViewWithTag<EditText>("wear_color_hex")
                .text
                .toString(),
        )
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun `palette is visible before scrolling on the Ultra round viewport`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val dialog = WearColorPicker.show(controller.get(), "Farbe", Color.RED, Color.GREEN, {}, {}, {})
        val root = dialog.window!!.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 480)
        val firstColor = descendants(root).first { it.contentDescription?.toString() == "#FFFFFFFF" }
        val visible = android.graphics.Rect(0, 0, firstColor.width, firstColor.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(firstColor, visible)
        org.junit.Assert.assertTrue("Palette bounds=$visible", visible.intersect(0, 0, root.width, root.height))
        org.junit.Assert.assertTrue("Palette visible=$visible focus=${root.findFocus()} swatchHeight=${firstColor.height}", visible.height() >= 70)
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun `Wear done applies a typed valid hex without requiring keyboard submit`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_WATCH, true)
        var saved = Color.RED
        SharedColorEditor.show(activity, "Farbe", Color.RED, Color.BLACK, Color.WHITE, Color.GRAY, Color.GREEN, { saved = it }, {})
        val dialog = ShadowDialog.getLatestDialog()
        val root = dialog.window!!.decorView
        root.findViewWithTag<EditText>("wear_color_hex").setText("#FF112233")
        descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Fertig" }.performClick()
        assertEquals(0xFF112233.toInt(), saved)
        controller.pause().stop().destroy()
    }

    @Test fun `Wear reset applies supplied default even when legacy reset callback is empty`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var saved = Color.RED
        val dialog = WearColorPicker.show(controller.get(), "Farbe", Color.RED, Color.GREEN, { saved = it }, {}, {})
        dialog.window!!
            .decorView
            .findViewWithTag<View>("wear_color_reset")
            .performClick()
        assertEquals(Color.GREEN, saved)
        assertEquals(
            "#FF00FF00",
            dialog.window!!
                .decorView
                .findViewWithTag<EditText>("wear_color_hex")
                .text
                .toString(),
        )
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
}
