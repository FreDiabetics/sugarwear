package app.sugarlicious.mobile

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSnapTest {
    @Test fun `a retained control gesture cannot snap a page that did not scroll`() {
        val activity =
            org.robolectric.Robolectric
                .buildActivity(android.app.Activity::class.java)
                .setup()
                .visible()
                .get()
        val context = activity
        val scroll = DashboardScrollView(context).apply { sectionSnapEnabled = true }
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(View(context), LinearLayout.LayoutParams(400, 200))
                addView(TextView(context).apply { tag = "settings_snap_heading" }, LinearLayout.LayoutParams(400, 40))
                addView(
                    View(context).apply {
                        setOnTouchListener { view, _ ->
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                    },
                    LinearLayout.LayoutParams(400, 800),
                )
            }
        scroll.addView(body)
        activity.setContentView(scroll)
        scroll.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 400, 400)
        scroll.scrollTo(0, 184)
        listOf(
            Triple(0L, MotionEvent.ACTION_DOWN, 50f to 100f),
            Triple(100L, MotionEvent.ACTION_MOVE, 90f to 101f),
            Triple(1000L, MotionEvent.ACTION_MOVE, 90f to 180f),
            Triple(2000L, MotionEvent.ACTION_UP, 90f to 180f),
        ).forEach { (time, action, point) ->
            MotionEvent.obtain(0, time, action, point.first, point.second, 0).also {
                scroll.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        scroll.computeScroll()
        assertEquals(184, scroll.scrollY)
    }

    @Test fun `slow scroll settles nearby headings but fast flings remain free`() {
        assertEquals(300, settingsSnapTarget(294, listOf(0, 300, 650), 120f, 1f, 800))
        assertNull(settingsSnapTarget(284, listOf(0, 300, 650), 120f, 1f, 800))
        assertNull(settingsSnapTarget(284, listOf(0, 300, 650), 1200f, 1f, 800))
        assertNull(settingsSnapTarget(450, listOf(0, 300, 650), 100f, 1f, 800))
        assertNull(settingsSnapTarget(284, listOf(0, 300, 650), 100f, 1f, 290))
    }

    @Test fun `gentle scroll does not pull distant headings across the page`() {
        assertNull(settingsSnapTarget(200, listOf(0, 300, 650), 120f, 1f, 800))
        assertNull(settingsSnapTarget(200, listOf(0, 300, 650), 1200f, 1f, 800))
    }

    @Test fun `menu text and neutral surfaces follow the selected appearance`() {
        listOf(false, true).forEach { light ->
            assertEquals(if (light) Color.rgb(37, 37, 37) else Color.WHITE, MobileSettingsStyle.text(light))
            assertEquals(if (light) Color.rgb(102, 102, 102) else Color.WHITE, MobileSettingsStyle.secondary(light))
            assertEquals(if (light) Color.rgb(242, 242, 242) else Color.BLACK, MobileSettingsStyle.page(light))
            assertEquals(if (light) Color.WHITE else Color.rgb(28, 28, 28), MobileSettingsStyle.card(light))
        }
    }
}
