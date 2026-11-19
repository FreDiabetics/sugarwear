package app.sugarlicious.uishared

import android.content.Context
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrollSafeSeekBarTest {
    @Test fun `slanted vertical scrolling never starts slider tracking`() {
        val slider =
            ScrollSafeSeekBar(ApplicationProvider.getApplicationContext<Context>()).apply {
                max = 100
                progress = 25
                layout(0, 0, 300, 48)
            }
        touch(slider, MotionEvent.ACTION_DOWN, 150f, 24f)
        touch(slider, MotionEvent.ACTION_MOVE, 180f, 48f)
        assertEquals(25, slider.progress)
        touch(slider, MotionEvent.ACTION_UP, 185f, 100f)
        assertEquals(25, slider.progress)
    }

    @Test fun `vertical swipe never changes progress even on initial down`() {
        val slider =
            ScrollSafeSeekBar(ApplicationProvider.getApplicationContext<Context>()).apply {
                max = 100
                progress = 25
                layout(0, 0, 300, 48)
            }
        var changes = 0
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) changes++
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            },
        )
        touch(slider, MotionEvent.ACTION_DOWN, 250f, 24f)
        assertEquals(25, slider.progress)
        touch(slider, MotionEvent.ACTION_MOVE, 251f, 90f)
        touch(slider, MotionEvent.ACTION_UP, 251f, 110f)
        assertEquals(25, slider.progress)
        assertEquals(0, changes)
    }

    @Test fun `deliberate horizontal drag still adjusts progress`() {
        val slider =
            ScrollSafeSeekBar(ApplicationProvider.getApplicationContext<Context>()).apply {
                max = 100
                progress = 25
                layout(0, 0, 300, 48)
            }
        touch(slider, MotionEvent.ACTION_DOWN, 150f, 24f)
        touch(slider, MotionEvent.ACTION_MOVE, 270f, 25f)
        touch(slider, MotionEvent.ACTION_UP, 270f, 25f)
        assertTrue(slider.progress > 75)
    }

    @Test fun `intentional tap works and canceled scroll leaves progress unchanged`() {
        val slider =
            ScrollSafeSeekBar(ApplicationProvider.getApplicationContext<Context>()).apply {
                max = 100
                progress = 25
                layout(0, 0, 300, 48)
            }
        touch(slider, MotionEvent.ACTION_DOWN, 250f, 24f)
        touch(slider, MotionEvent.ACTION_CANCEL, 250f, 24f)
        assertEquals(25, slider.progress)
        touch(slider, MotionEvent.ACTION_DOWN, 250f, 24f)
        touch(slider, MotionEvent.ACTION_UP, 250f, 24f)
        assertTrue(slider.progress > 75)
    }

    private fun touch(slider: SeekBar, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(1L, 10L, action, x, y, 0).also {
            slider.dispatchTouchEvent(it)
            it.recycle()
        }
    }
}
