package app.sugarlicious.mobile

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OneUiButtonContrastTest {
    @Test
    fun `light accents use black and dark accents use white`() {
        assertEquals(Color.Black, oneUiButtonContentColor(Color(0xFFE2C3C0)))
        assertEquals(Color.Black, oneUiButtonContentColor(Color.White))
        assertEquals(Color.White, oneUiButtonContentColor(Color.Black))
        assertEquals(Color.White, oneUiButtonContentColor(Color(0xFF16345F)))
    }
}
