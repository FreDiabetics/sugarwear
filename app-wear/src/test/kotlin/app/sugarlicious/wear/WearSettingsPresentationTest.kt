package app.sugarlicious.wear

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.protocol.WatchUiColors
import app.sugarlicious.uishared.ScrollSafeSeekBar
import app.sugarlicious.uishared.ScrollSafeSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WearSettingsPresentationTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<WearSettingsActivity>>()

    @Before fun prepareMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After fun disposeActivitiesAndDispatcher() {
        controllers.asReversed().forEach { it.pause().stop().destroy() }
        mainDispatcher.scheduler.runCurrent()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(32))
        Dispatchers.resetMain()
    }

    @Test
    fun `custom display colors cannot hide settings or change their chrome`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WearDisplayPreferences.setActiveAppearanceMode(context, AppearanceMode.DARK)
        val stored =
            WearDisplayPreferences().copy(
                uiColors = WatchUiColors().copy(background = Color.RED, tileBackground = Color.GREEN, textPrimary = Color.BLACK),
            )
        WearDisplayPreferences.saveLocal(context, AppearanceMode.DARK, stored)
        val before = WearDisplayPreferences.read(context, AppearanceMode.DARK)
        val controller =
            Robolectric
                .buildActivity(WearSettingsActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .also(controllers::add)
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        awaitView(content) { it.tag == "settings-category-glucose" }
        val scroll = descendants(content).filterIsInstance<ScrollView>().single()
        val category = descendants(scroll).single { it.tag == "settings-category-glucose" }
        val label = descendants(category).filterIsInstance<TextView>().single { it.text.toString() == "Glukose" }
        assertEquals(Color.WHITE, label.currentTextColor)
        assertEquals(app.sugarlicious.uishared.WearSettingsUi.MENU_SURFACE, ((category.parent as View).background as GradientDrawable).color!!.defaultColor)
        assertEquals(before, WearDisplayPreferences.read(context, AppearanceMode.DARK))
    }

    @Test
    fun `every category builds without changing preferences and graph choices retain all periods`() {
        val controller =
            Robolectric
                .buildActivity(WearSettingsActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
                .also(controllers::add)
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        awaitView(content) { it.tag == "settings-category-glucose" }
        val before = WearDisplayPreferences.read(activity, WearDisplayPreferences.activeAppearanceMode(activity))
        val keys = descendants(content).mapNotNull { (it.tag as? String)?.takeIf { tag -> tag.startsWith("settings-category-") } }.toList()
        assertEquals(11, keys.size)
        keys.forEach { key ->
            descendants(content).single { it.tag == key }.performClick()
            awaitView(content) { it.contentDescription == "Zur\u00fcck" && descendants(content).none { view -> view.tag == key } }
            if (key == "settings-category-graph") {
                val buttons = descendants(content).filterIsInstance<TextView>().toList()
                WearDisplayPreferences.allowedGraphHours.forEach { hours ->
                    val label = activity.getString(R.string.graph_hours, hours)
                    val button = buttons.single { it.text.toString().removePrefix("✓  ") == label }
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, button.layoutParams.width)
                }
                val scroll = descendants(content).filterIsInstance<ScrollView>().single()
                val page = scroll.getChildAt(0) as LinearLayout
                val sliders = descendants(page).filterIsInstance<ScrollSafeSeekBar>().toList()
                assertTrue(sliders.isNotEmpty())
                assertTrue(sliders.all { (it.parent as LinearLayout).orientation == LinearLayout.VERTICAL })
                assertTrue(descendants(page).any { it is ScrollSafeSwitch })
            }
            assertEquals(before, WearDisplayPreferences.read(activity, WearDisplayPreferences.activeAppearanceMode(activity)))
            descendants(content).single { it.contentDescription == "Zurück" }.performClick()
            awaitView(content) { it.tag == key }
        }
    }

    private fun awaitView(root: View, predicate: (View) -> Boolean) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (descendants(root).none(predicate) && System.nanoTime() < deadline) {
            mainDispatcher.scheduler.runCurrent()
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(16))
            Thread.sleep(2)
        }
        assertTrue(
            "Expected settings view to render; tree=" +
                descendants(root).joinToString { view ->
                    "${view.javaClass.simpleName}(tag=${view.tag}, text=${(view as? TextView)?.text})"
                },
            descendants(root).any(predicate),
        )
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
}
