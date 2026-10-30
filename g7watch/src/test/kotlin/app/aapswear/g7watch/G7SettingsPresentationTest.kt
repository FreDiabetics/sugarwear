package app.aapswear.g7watch

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.aapswear.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7SettingsPresentationTest {
    @Test
    fun `settings remain readable with a light or customized display palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = G7AppearanceStore(context)
        store.save(AppearanceMode.LIGHT, G7AppearanceRole.MENU_TEXT_PRIMARY, Color.BLACK)
        store.save(AppearanceMode.LIGHT, G7AppearanceRole.MENU_SURFACE, Color.WHITE)
        val palette = store.load(AppearanceMode.LIGHT)

        assertEquals(Color.WHITE, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        assertEquals(0xFF202020.toInt(), palette.settingsArgb(G7AppearanceRole.MENU_SURFACE))
        assertEquals(Color.BLACK, palette.argb(G7AppearanceRole.MENU_TEXT_PRIMARY))
        assertEquals(Color.WHITE, palette.argb(G7AppearanceRole.MENU_SURFACE))
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round")
    fun `appearance keeps choice labels unconstrained and does not save on construction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        G7AppearanceStore(context).load(AppearanceMode.DARK)
        val preferences = context.getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val controller = Robolectric.buildActivity(G7AppearanceActivity::class.java).setup()
        try {
            val labels = descendants(controller.get().window.decorView).filterIsInstance<TextView>().toList()
            assertTrue(labels.any { it.text.toString() == "Glukosewert" })
            assertTrue(labels.any { it.text.toString() == "Trendpfeil" })
            assertTrue(labels.any { it.text.toString() == "WearOS-Tile · Graph" })
            labels.filter { it.text.toString() in listOf("Hell", "Dunkel") }.forEach {
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, it.layoutParams.height)
            }
            assertEquals(before, preferences.all)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
            }
        }
}
