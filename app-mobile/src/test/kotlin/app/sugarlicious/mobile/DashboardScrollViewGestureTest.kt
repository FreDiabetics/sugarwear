package app.sugarlicious.mobile

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardScrollViewGestureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `closing header hands movement and release momentum to content`() {
        val activity =
            org.robolectric.Robolectric
                .buildActivity(android.app.Activity::class.java)
                .setup()
                .visible()
                .get()
        val parent =
            DashboardScrollView(activity).apply {
                topPullEnabled = true
                topPullExpanded = true
                topPullExpansionPx = 40f
                onTopPull = { distance ->
                    topPullExpansionPx = (40f + distance).coerceIn(0f, 220f)
                    topPullExpanded = topPullExpansionPx > 0f
                }
                addView(
                    android.widget.LinearLayout(activity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        addView(android.view.View(activity), android.widget.LinearLayout.LayoutParams(300, 3000))
                    },
                )
            }
        activity.setContentView(parent)
        parent.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 300, 300)
        listOf(MotionEvent.ACTION_DOWN to 220f, MotionEvent.ACTION_MOVE to 120f, MotionEvent.ACTION_MOVE to 80f, MotionEvent.ACTION_UP to 40f).forEachIndexed { index, (action, y) ->
            MotionEvent.obtain(1L, 1L + index * 32L, action, 150f, y, 0).also {
                parent.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        val releasedAt = parent.scrollY
        assertTrue("scroll=$releasedAt, header=${parent.topPullExpansionPx}, child=${parent.getChildAt(0).height}, viewport=${parent.height}", releasedAt > 0)
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        assertTrue("Content must keep moving after the finger lifts", parent.scrollY > releasedAt)
        activity.finish()
    }

    @Test fun `upward gesture closes expanded header before content can scroll`() {
        var distance = 0f
        var finished = false
        val parent =
            DashboardScrollView(context).apply {
                topPullEnabled = true
                topPullExpanded = true
                onTopPull = { distance = it }
                onTopPullFinished = { finished = true }
                addView(android.view.View(context), android.widget.FrameLayout.LayoutParams(300, 1000))
                measure(
                    android.view.View.MeasureSpec
                        .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec
                        .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, 300, 300)
            }
        listOf(MotionEvent.ACTION_DOWN to 220f, MotionEvent.ACTION_MOVE to 120f, MotionEvent.ACTION_UP to 100f).forEachIndexed { index, (action, y) ->
            MotionEvent.obtain(1, 1L + index * 32, action, 150f, y, 0).also {
                parent.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        assertTrue(distance < 0f)
        assertTrue(finished)
        org.junit.Assert.assertEquals(0, parent.scrollY)
    }

    @Test fun `pull on nonclickable page content expands header and completes gesture`() {
        var pulled = 0f
        var finished = false
        val parent =
            DashboardScrollView(context).apply {
                topPullEnabled = true
                onTopPull = { pulled = it }
                onTopPullFinished = { finished = true }
                addView(android.view.View(context), android.widget.FrameLayout.LayoutParams(300, 1000))
                measure(
                    android.view.View.MeasureSpec
                        .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec
                        .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, 300, 300)
            }
        listOf(MotionEvent.ACTION_DOWN to 60f, MotionEvent.ACTION_MOVE to 160f, MotionEvent.ACTION_UP to 180f).forEachIndexed { index, (action, y) ->
            MotionEvent.obtain(1, 1L + index * 32, action, 150f, y, 0).also {
                parent.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        assertTrue(pulled > 0f)
        assertTrue(finished)
        org.junit.Assert.assertEquals(0, parent.scrollY)
    }

    @Test fun `vertical scrolling cancels switches and clickable menu rows`() {
        listOf(android.widget.Switch(context), android.widget.Button(context)).forEach { control ->
            var actions = 0
            if (control is android.widget.Switch) {
                control.setOnCheckedChangeListener { _, _ -> actions++ }
            } else {
                control.setOnClickListener { actions++ }
            }
            val body =
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(control, android.widget.LinearLayout.LayoutParams(300, 80))
                    addView(android.view.View(context), android.widget.LinearLayout.LayoutParams(300, 1000))
                }
            val parent =
                DashboardScrollView(context).apply {
                    addView(body)
                    measure(
                        android.view.View.MeasureSpec
                            .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec
                            .makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, 300, 300)
                }
            listOf(MotionEvent.ACTION_DOWN to 40f, MotionEvent.ACTION_MOVE to -60f, MotionEvent.ACTION_MOVE to -120f, MotionEvent.ACTION_UP to -160f).forEachIndexed { index, (action, y) ->
                MotionEvent.obtain(1, 1L + index * 32, action, 200f, y, 0).also {
                    parent.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
            org.junit.Assert.assertEquals(0, actions)
            assertTrue(parent.scrollY > 0)
        }
    }

    @Test fun `horizontal chart gesture is not intercepted by vertical dashboard scroll`() {
        val view = DashboardScrollView(context)
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val horizontal = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 95f, 53f, 0)

        view.onInterceptTouchEvent(down)
        assertFalse(view.onInterceptTouchEvent(horizontal))

        down.recycle()
        horizontal.recycle()
    }

    @Test fun `multi pointer chart gesture is never intercepted`() {
        val properties =
            arrayOf(
                MotionEvent.PointerProperties().apply { id = 0 },
                MotionEvent.PointerProperties().apply { id = 1 },
            )
        val coordinates =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    x = 40f
                    y = 50f
                },
                MotionEvent.PointerCoords().apply {
                    x = 100f
                    y = 50f
                },
            )
        val event =
            MotionEvent.obtain(
                0L,
                16L,
                MotionEvent.ACTION_MOVE,
                2,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                0,
                0,
            )

        assertFalse(DashboardScrollView(context).onInterceptTouchEvent(event))
        event.recycle()
    }

    @Test fun `completed touch delegates accessibility click`() {
        val view = DashboardScrollView(context)
        var clicked = false
        view.setOnClickListener { clicked = true }
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val up = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_UP, 50f, 50f, 0)

        view.onTouchEvent(down)
        view.onTouchEvent(up)

        assertTrue(clicked)
        down.recycle()
        up.recycle()
    }
}
