package app.sugarwear

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmSettingsPresentationTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test
    fun `saving a slider keeps its current control until the latest write completes`() {
        val controller = Robolectric.buildActivity(CgmAppearanceActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            awaitReady(activity)
            activity.window.decorView
                .findViewWithTag<View>("settings-element-Glukosewert")
                .performClick()
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(50))
            val slider = descendants(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single()
            val model = androidx.lifecycle.ViewModelProvider(activity)[CgmAppearanceSettingsViewModel::class.java]
            val worker =
                CgmAppearanceSettingsViewModel::class.java
                    .getDeclaredField("worker")
                    .apply { isAccessible = true }
                    .get(model)

            @Suppress("UNCHECKED_CAST")
            val flow =
                CgmSettingsWorker::class.java
                    .getDeclaredField("mutableState")
                    .apply { isAccessible = true }
                    .get(worker) as kotlinx.coroutines.flow.MutableStateFlow<CgmSettingsIoState<CgmAppearanceSettingsSnapshot>>
            val baseline = checkNotNull(flow.value.snapshot)
            slider.progress += 1
            flow.value = flow.value.copy(loading = true)
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(50))
            org.junit.Assert.assertSame(slider, descendants(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single())
            flow.value = CgmSettingsIoState(snapshot = baseline.copy(glucoseScalePercent = baseline.glucoseScalePercent + 1), loading = false)
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(50))
            assertEquals(baseline.glucoseScalePercent + 1, descendants(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single().progress + app.sugarlicious.model.GlucoseTrendSizing.MIN_SCALE_PERCENT)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `each plus and minus tap persists once without leaving the settings menu`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = CgmAppearanceStore(context)
        preferences.setGlucoseScalePercent(100)
        val controller = Robolectric.buildActivity(CgmAppearanceActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            awaitReady(activity)
            activity.window.decorView
                .findViewWithTag<View>("settings-element-Glukosewert")
                .performClick()
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(50))
            for ((label, expected) in listOf("+" to 101, "+" to 102, "−" to 101, "−" to 100, "+" to 101)) {
                descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == label }.performClick()
                val deadline =
                    System.nanoTime() +
                        java.util.concurrent.TimeUnit.SECONDS
                            .toNanos(5)
                while (System.nanoTime() < deadline) {
                    org.robolectric.Shadows
                        .shadowOf(android.os.Looper.getMainLooper())
                        .idleFor(java.time.Duration.ofMillis(16))
                    val slider = descendants(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single()
                    if (preferences.glucoseScalePercent() == expected && slider.progress + app.sugarlicious.model.GlucoseTrendSizing.MIN_SCALE_PERCENT == expected) break
                    Thread.sleep(2)
                }
                assertEquals(expected, preferences.glucoseScalePercent())
                assertEquals(expected, descendants(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single().progress + app.sugarlicious.model.GlucoseTrendSizing.MIN_SCALE_PERCENT)
                org.junit.Assert.assertFalse(activity.isFinishing)
                org.junit.Assert.assertFalse(activity.isDestroyed)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `settings remain readable with a light or customized display palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CgmAppearanceStore(context)
        store.save(AppearanceMode.LIGHT, CgmAppearanceRole.MENU_TEXT_PRIMARY, Color.BLACK)
        store.save(AppearanceMode.LIGHT, CgmAppearanceRole.MENU_SURFACE, Color.WHITE)
        val palette = store.load(AppearanceMode.LIGHT)

        assertEquals(Color.WHITE, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        assertEquals(0xFF202020.toInt(), palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
        assertEquals(Color.BLACK, palette.argb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
        assertEquals(Color.WHITE, palette.argb(CgmAppearanceRole.MENU_SURFACE))
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round")
    fun `appearance keeps choice labels unconstrained and does not save on construction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CgmAppearanceStore(context).load(AppearanceMode.DARK)
        val preferences = context.getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val controller = Robolectric.buildActivity(CgmAppearanceActivity::class.java).setup().visible()
        try {
            awaitReady(controller.get())
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

    @Test
    @Config(qualifiers = "w192dp-h192dp-round")
    fun `appearance categories open individually and preserve all saved values`() {
        for (type in listOf(CgmAppearanceActivity::class.java, CgmDirectToWatchSettingsActivity::class.java)) {
            val controller = Robolectric.buildActivity(type).setup().visible()
            val activity = controller.get()
            try {
                awaitReady(activity)
                val prefs =
                    listOf("g7_appearance", app.sugarlicious.protocol.DirectToWatchSettingsContract.PREFERENCES)
                        .associateWith { activity.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
                val tags = descendants(activity.window.decorView).mapNotNull { (it.tag as? String)?.takeIf { tag -> tag.startsWith("settings-element-") } }.toList()
                assertTrue(tags.isNotEmpty())
                for (tag in tags) {
                    activity.window.decorView
                        .findViewWithTag<View>(tag)
                        .performClick()
                    assertTrue(descendants(activity.window.decorView).none { it.tag == tag })
                    activity.onBackPressedDispatcher.onBackPressed()
                    org.robolectric.Shadows
                        .shadowOf(android.os.Looper.getMainLooper())
                        .idle()
                    assertTrue(descendants(activity.window.decorView).any { it.tag == tag })
                }
                prefs.forEach { (name, before) -> assertEquals(before, activity.getSharedPreferences(name, Context.MODE_PRIVATE).all) }
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun `alarm list opens each type without triggering or changing an alarm`() {
        val controller = Robolectric.buildActivity(CgmAlarmSettingsActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            awaitReady(activity)
            val before = CgmAlarmSettingsStore.read(activity)
            for (type in SugarWearAlarmType.entries) {
                var target: View = descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == cgmAlarmTitle(type) }
                while (!target.isClickable) target = target.parent as View
                target.performClick()
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Test-Ton abspielen" })
                activity.onBackPressedDispatcher.onBackPressed()
                org.robolectric.Shadows
                    .shadowOf(android.os.Looper.getMainLooper())
                    .idle()
            }
            assertEquals(before, CgmAlarmSettingsStore.read(activity))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun awaitReady(activity: android.app.Activity) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (System.nanoTime() < deadline) {
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(16))
            val decor = activity.window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 454, 454)
            if (descendants(decor).any { it.tag?.toString()?.startsWith("settings-element-") == true } ||
                descendants(decor).filterIsInstance<TextView>().any { it.text.toString() == "Ruhemodi" }
            ) {
                return
            }
            Thread.sleep(2)
        }
        throw AssertionError("Settings snapshot must load in its Compose host")
    }

    private fun descendants(view: View): Sequence<View> =
        sequence {
            yield(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
            }
        }
}
