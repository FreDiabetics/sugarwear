package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ApexTherapyTextTest {
    @Test
    fun `preview rounds signed insulin only and preserves carb numbers`() {
        assertEquals("1.3" to "U", apexTherapyText("1.26U", Locale.US))
        assertEquals("-0.2" to "U", apexTherapyText("-0.16U", Locale.US))
        assertEquals("0,7" to "U", apexTherapyText("0.73U", Locale.GERMANY))
        assertEquals("15" to "g", apexTherapyText("15g", Locale.US))
    }

    @Test
    fun `preview leaves unknown compound and missing fields unchanged`() {
        listOf("", "—", "IOB", "+5", "0.5U/h", "1.2U · 15g", "1..2U", "NaNU").forEach {
            assertNull(apexTherapyText(it, Locale.US))
        }
    }
}
