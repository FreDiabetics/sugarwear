package app.aapswear.g7watch

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7WearScrollChromeTest {
    @Test
    fun `SugarWear scroll surfaces expose the transient native Wear scrollbar`() {
        val scroll = G7EdgeFadeScrollView(ApplicationProvider.getApplicationContext()).applyG7EdgeFade()

        assertTrue(scroll.isVerticalScrollBarEnabled)
        assertTrue(scroll.isScrollbarFadingEnabled)
        assertEquals(View.SCROLLBARS_INSIDE_OVERLAY, scroll.scrollBarStyle)
        assertFalse(scroll.isVerticalFadingEdgeEnabled)
        assertTrue(scroll.isFocusable)
        assertTrue(scroll.isFocusableInTouchMode)
        assertFalse(scroll.defaultFocusHighlightEnabled)
        assertTrue(scroll.descendantFocusability != ViewGroup.FOCUS_BLOCK_DESCENDANTS)
        assertEquals(View.OVER_SCROLL_NEVER, scroll.overScrollMode)
    }
}
