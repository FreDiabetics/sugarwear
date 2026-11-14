package app.sugarlicious.uishared

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class WearSettingsControlsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun stepButton(root: android.view.View, text: String): android.widget.Button {
        if (root is android.widget.Button && root.text.toString() == text) return root
        if (root is android.view.ViewGroup) {
            repeat(root.childCount) { index ->
                val found = runCatching { stepButton(root.getChildAt(index), text) }.getOrNull()
                if (found != null) return found
            }
        }
        error("Missing step button $text")
    }

    @Test fun `rotary scroll focus never highlights the entire settings surface`() {
        val scroll = WearSettingsScrollView(context)
        assertTrue(scroll.isFocusableInTouchMode)
        assertFalse(scroll.defaultFocusHighlightEnabled)
    }

    @Test fun `nested slider retains a usable full width track on a 192 dp watch`() {
        val slider = SeekBar(context)
        WearSettingsUi.slider(slider, Color.GREEN, Color.DKGRAY, "Konturdicke")
        val control = WearSettingsUi.inlineSlider(slider) {}
        // 192dp viewport minus page (32), element (16) and nested card (26) insets.
        val available = WearSettingsUi.dp(context, 118)
        control.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(available, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        control.layout(0, 0, control.measuredWidth, control.measuredHeight)
        assertEquals(available, slider.measuredWidth)
        assertTrue(slider.measuredWidth - slider.paddingLeft - slider.paddingRight >= WearSettingsUi.dp(context, 96))
        val decrease = stepButton(control, "−")
        val increase = stepButton(control, "+")
        assertTrue(decrease.measuredWidth >= WearSettingsUi.dp(context, 48))
        assertTrue(increase.measuredHeight >= WearSettingsUi.dp(context, 48))
        assertEquals(decrease.measuredWidth, increase.measuredWidth)
    }

    @Test fun `round page padding adapts to watch width without changing child layout`() {
        fun root(width: Int): android.widget.LinearLayout {
            val config =
                android.content.res.Configuration(context.resources.configuration).apply {
                    screenLayout = (
                        screenLayout and
                            android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK
                                .inv()
                    ) or android.content.res.Configuration.SCREENLAYOUT_ROUND_YES
                    screenWidthDp = width
                }
            return android.widget.LinearLayout(context.createConfigurationContext(config)).also { WearSettingsUi.pagePadding(it) }
        }
        val small = root(192)
        val large = root(240)
        assertTrue(large.paddingLeft > small.paddingLeft)
        assertTrue(small.paddingBottom > small.paddingLeft)
        assertEquals(small.paddingLeft, small.paddingRight)
    }

    @Test fun `step buttons preserve native callbacks and persist only effective changes`() {
        val slider =
            SeekBar(context).apply {
                max = 3
                progress = 2
            }
        var gestureWrites = 0
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) gestureWrites++
                }

                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            },
        )
        val writes = mutableListOf<Int>()
        val row = WearSettingsUi.inlineSlider(slider) { writes += it }
        assertTrue(writes.isEmpty())
        assertTrue(slider.parent === row)
        stepButton(row, "+").performClick()
        stepButton(row, "+").performClick()
        stepButton(row, "−").performClick()
        assertEquals(listOf(3, 2), writes)
        assertEquals(0, gestureWrites)
        assertEquals(2, slider.progress)
    }

    @Suppress("DEPRECATION") // This API-35 test verifies the Boolean accessibility compatibility path.
    @Test
    fun `choice exposes checked radio semantics and a full width touch target`() {
        val row = WearSettingsUi.choice(context, "Systemfarbe", true, Color.GREEN, Color.DKGRAY) {}
        val info = row.createAccessibilityNodeInfo()
        assertTrue(info.isCheckable)
        assertTrue(info.isChecked)
        assertEquals(android.widget.RadioButton::class.java.name, info.className)
        assertTrue(row.minimumHeight >= WearSettingsUi.dp(context, 48))
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, row.layoutParams.width)
    }

    @Test fun `navigation rows grow for large fonts and groups own inset separators`() {
        val scaled =
            context.createConfigurationContext(
                android.content.res
                    .Configuration(context.resources.configuration)
                    .apply { fontScale = 2f },
            )
        val first = WearSettingsUi.navigationRow(scaled, "Farben und Darstellung", "Individuell konfigurieren", Color.WHITE, Color.GRAY) {}
        val second = WearSettingsUi.navigationRow(scaled, "Alarme", null, Color.WHITE, Color.GRAY) {}
        val group = WearSettingsUi.navigationGroup(scaled, listOf(first, second), Color.DKGRAY)
        group.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(WearSettingsUi.dp(scaled, 160), android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(3, group.childCount)
        assertTrue(first.measuredHeight > WearSettingsUi.dp(scaled, 48))
        assertEquals(2, first.childCount)
        assertEquals(1, second.childCount)
        val separator = group.getChildAt(1).layoutParams as android.widget.LinearLayout.LayoutParams
        assertTrue(separator.marginStart > 0)
    }

    @Test fun `slider exposes its purpose and remains keyboard accessible`() {
        val slider = SeekBar(context)
        WearSettingsUi.slider(slider, Color.GREEN, Color.DKGRAY, "Konturdicke")
        assertEquals("Konturdicke", slider.contentDescription)
        assertTrue(slider.isFocusable)
        assertFalse(slider.isFocusableInTouchMode)
    }

    @Test fun `header keeps readable wrapping and a named back button at double font size`() {
        val scaled =
            context.createConfigurationContext(
                android.content.res
                    .Configuration(context.resources.configuration)
                    .apply { fontScale = 2f },
            )
        val header = WearSettingsUi.header(scaled, "Farben und Darstellung", Color.WHITE) {}
        header.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(WearSettingsUi.dp(scaled, 192), android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
        val back = header.getChildAt(0)
        assertTrue(back is android.widget.ImageButton)
        assertEquals("Zurück", back.contentDescription)
        assertEquals(WearSettingsUi.dp(scaled, 48), back.measuredWidth)
        val title = header.getChildAt(1) as android.widget.TextView
        assertTrue(title.lineCount > 1)
        assertTrue(title.isAccessibilityHeading)
    }

    @Test fun `wear list typography matches DeviceVendor Health fifteen and twelve sp roles`() {
        assertEquals(15f, WearSettingsUi.textSize(11f), 0f)
        assertEquals(12f, WearSettingsUi.textSize(8.5f), 0f)
    }

    @Test fun `slider track does not stretch to its touch target and stays visible on black`() {
        val slider = SeekBar(context)
        // The user's saved border is transparent gray, not opaque black.
        WearSettingsUi.slider(slider, Color.GREEN, 0x00404040)
        val track = slider.progressDrawable
        assertEquals(WearSettingsUi.dp(context, 10), track.intrinsicHeight)
        track.setBounds(0, 0, 300, 48)
        track.level = 2500
        val bitmap = android.graphics.Bitmap.createBitmap(300, 48, android.graphics.Bitmap.Config.ARGB_8888)
        track.draw(android.graphics.Canvas(bitmap))
        assertEquals(Color.GREEN, bitmap.getPixel(30, 24))
        assertEquals(Color.rgb(70, 70, 73), bitmap.getPixel(200, 24))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(200, 0))
    }

    @Test fun `switch thumb stays a circle inside the taller track bounds`() {
        val toggle = ScrollSafeSwitch(context)
        WearSettingsUi.toggle(toggle, Color.BLUE, Color.WHITE)
        val thumb = toggle.thumbDrawable
        val width = WearSettingsUi.dp(context, 20)
        val height = WearSettingsUi.dp(context, 24)
        thumb.setBounds(0, 0, width, height)
        val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        thumb.draw(android.graphics.Canvas(image))
        assertEquals(Color.TRANSPARENT, image.getPixel(width / 2, 0))
        assertEquals(Color.WHITE, image.getPixel(width / 2, height / 2))
    }

    @Test fun `scrolling across a switch never commits but an ordinary tap does`() {
        val toggle = ScrollSafeSwitch(context)

        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 20, action, x, y, 0)
            toggle.onTouchEvent(event)
            event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(MotionEvent.ACTION_MOVE, 30f, 120f)
        touch(MotionEvent.ACTION_UP, 30f, 120f)
        assertFalse(toggle.isChecked)
        touch(MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(MotionEvent.ACTION_UP, 20f, 20f)
        assertTrue(toggle.isChecked)
    }

    @Test fun `vertical drag across slider never starts tracking or writes a value`() {
        val slider =
            ScrollSafeSeekBar(context).apply {
                max = 100
                progress = 30
                layout(0, 0, 240, 60)
            }
        var writes = 0
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) writes++
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    writes++
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    writes++
                }
            },
        )
        listOf(
            Triple(MotionEvent.ACTION_DOWN, 150f, 20f),
            Triple(MotionEvent.ACTION_MOVE, 155f, 120f),
            Triple(MotionEvent.ACTION_CANCEL, 155f, 120f),
        ).forEach { (action, x, y) ->
            val event = MotionEvent.obtain(0, 20, action, x, y, 0)
            slider.onTouchEvent(event)
            event.recycle()
        }
        assertEquals(30, slider.progress)
        assertEquals(0, writes)
    }
}
