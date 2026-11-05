package app.sugarwear

import org.junit.Assert.assertEquals
import org.junit.Test

class CgmSystemChromePolicyTest {
    @Test fun `pre Android 15 retains explicit bar colors`() {
        assertEquals(CgmSystemChromeStrategy.EXPLICIT_BAR_COLORS, cgmSystemChromeStrategy(34))
    }

    @Test fun `Android 15 draws the palette behind transparent system bars`() {
        assertEquals(CgmSystemChromeStrategy.EDGE_TO_EDGE_BACKGROUND, cgmSystemChromeStrategy(35))
    }
}
