package app.sugarlicious.uishared

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GraphTextContrastTest {
    @Test fun `graph labels use black on bright and white on dark composite surfaces`() {
        assertEquals(Color.BLACK, graphTextContrast(Color.WHITE))
        assertEquals(Color.WHITE, graphTextContrast(Color.BLACK))
        assertEquals(Color.BLACK, graphTextContrast(Color.BLACK, Color.YELLOW))
        assertEquals(Color.WHITE, graphTextContrast(Color.BLACK, 0x10FFFF00))
    }
}
