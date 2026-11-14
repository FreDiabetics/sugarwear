package app.sugarlicious.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearIconsTest {
    @Test
    fun `menu and branding have breathing space while clinical vectors retain native geometry`() {
        assertEquals(.08f, wearUiIconInsetFraction(R.drawable.ic_settings), 0f)
        assertEquals(.08f, wearUiIconInsetFraction(R.drawable.ic_foreground), 0f)
        assertEquals(0f, wearUiIconInsetFraction(app.sugarlicious.uishared.R.drawable.ic_iob), 0f)
        assertEquals(0f, wearUiIconInsetFraction(app.sugarlicious.uishared.R.drawable.ic_trend_flat), 0f)
    }

    @Test
    fun `only colored icons on light Wear surfaces receive a black silhouette`() {
        assertTrue(shouldOutlineWearIcon(0xFFF4F6F8.toInt(), colored = true))
        assertFalse(shouldOutlineWearIcon(0xFF101216.toInt(), colored = true))
        assertFalse(shouldOutlineWearIcon(0xFFF4F6F8.toInt(), colored = false))
    }
}
