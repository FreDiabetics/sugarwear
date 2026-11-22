package app.sugarwear

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun `shared pill contains only three icon destinations and shows labels on long press`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val pill = CgmBottomNavigation.pill(activity, CgmNavTab.OVERVIEW)
            assertEquals("sugarwear-nav-pill", pill.tag)
            val root = pill as ViewGroup
            assertEquals(3, root.childCount)
            assertFalse(descendants(pill).any { it is TextView })
            val labels = listOf("Übersicht", "Verbindungen", "Profil")
            val destinations =
                listOf(
                    CgmWatchActivity::class.java.name,
                    CgmConnectionsActivity::class.java.name,
                    CgmSettingsActivity::class.java.name,
                )
            labels.forEachIndexed { index, label ->
                val button = root.getChildAt(index)
                assertEquals("nav-" + CgmNavTab.entries[index].name.lowercase(), button.tag)
                assertTrue(button.contentDescription.toString().contains(label))
                assertEquals(index == 0, button.isSelected)
                assertTrue(button.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
                assertTrue(button.performLongClick())
                if (index != 0) {
                    button.performClick()
                    assertEquals(
                        destinations[index],
                        Shadows
                            .shadowOf(activity)
                            .nextStartedActivity
                            .component
                            ?.className,
                    )
                }
            }
            assertFalse(root.isClickable)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `pill is the final scrollable menu item and is not duplicated on rebuild`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val page =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(activity).apply { text = "Letzter Menüpunkt" })
                }
            val scroll = ScrollView(activity).apply { addView(page) }
            CgmBottomNavigation.appendAtEnd(activity, scroll, CgmNavTab.PROFILE)
            val pill = page.getChildAt(page.childCount - 1)
            assertEquals("sugarwear-nav-pill", pill.tag)
            assertEquals(2, page.childCount)
            assertSame(page, pill.parent)
            assertTrue(pill is LinearLayout)
            CgmBottomNavigation.appendAtEnd(activity, scroll, CgmNavTab.PROFILE)
            assertSame(pill, page.getChildAt(page.childCount - 1))
            assertEquals(2, page.childCount)
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
