package app.sugarwear

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w192dp-h192dp-round")
class CgmBottomNavigationTest {
    @Test
    fun `footer exposes all three actual navigation destinations`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val footer = CgmBottomNavigation.footer(activity, CgmNavTab.OVERVIEW)
            assertEquals("sugarwear-footer", footer.tag)
            val root = footer as ViewGroup
            assertEquals(3, root.childCount)
            val labels = listOf("Übersicht", "Verbindungen", "Profil")
            val destinations =
                listOf(
                    CgmWatchActivity::class.java.name,
                    CgmConnectionsActivity::class.java.name,
                    CgmSettingsActivity::class.java.name,
                )
            labels.forEachIndexed { index, label ->
                val item = root.getChildAt(index)
                assertEquals("nav-" + CgmNavTab.entries[index].name.lowercase(), item.tag)
                val text = descendants(item).filterIsInstance<TextView>().single()
                assertEquals(label, text.text.toString())
                assertEquals(index == 0, item.isSelected)
                assertTrue(item.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
                if (index != 0) {
                    item.performClick()
                    assertEquals(destinations[index], Shadows.shadowOf(activity).nextStartedActivity.component?.className)
                }
            }
            assertFalse(root.isClickable)
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
