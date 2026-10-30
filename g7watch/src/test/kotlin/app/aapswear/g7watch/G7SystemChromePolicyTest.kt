package app.aapswear.g7watch

import org.junit.Assert.assertEquals
import org.junit.Test

class G7SystemChromePolicyTest {
    @Test fun `pre Android 15 retains explicit bar colors`() {
        assertEquals(G7SystemChromeStrategy.EXPLICIT_BAR_COLORS, g7SystemChromeStrategy(34))
    }

    @Test fun `Android 15 draws the palette behind transparent system bars`() {
        assertEquals(G7SystemChromeStrategy.EDGE_TO_EDGE_BACKGROUND, g7SystemChromeStrategy(35))
    }
}
