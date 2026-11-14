package app.sugarlicious.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTherapyGeometryTest {
    @Test
    fun `therapy icons fit completely inside the pill contour at native DeviceVendor sizes`() {
        listOf(19f, 28f, 34f).forEach { iconSize ->
            val left = 35f - iconSize / 2f
            val top = therapyWidgetIconTopPx(34.5f, 69f, iconSize, left, 154f, 69f, 34.5f)
            val allowedBottom = 69f - roundedWidgetContourInset(34.5f, left)
            assertTrue("icon $iconSize extends beyond the pill", top + iconSize <= allowedBottom)
        }
    }
}
