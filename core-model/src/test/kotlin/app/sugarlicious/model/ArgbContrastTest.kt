package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArgbContrastTest {
    @Test
    fun `light and dark surfaces are classified without Android runtime APIs`() {
        assertTrue(ArgbContrast.isLight(0xFFF4F6F8.toInt()))
        assertFalse(ArgbContrast.isLight(0xFF111318.toInt()))
    }
}
