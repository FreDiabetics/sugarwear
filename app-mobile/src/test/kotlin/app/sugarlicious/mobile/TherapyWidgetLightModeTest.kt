package app.sugarlicious.mobile

import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TherapyWidgetLightModeTest {
    private val palette =
        WidgetPalette(
            WidgetColorRole.entries.associateWith { 0xFF252525.toInt() },
        )

    @Test
    fun `therapy text is black in light mode`() {
        assertEquals(0xFF000000.toInt(), therapyWidgetTextArgb(AppearanceMode.LIGHT, palette))
    }

    @Test
    fun `therapy text keeps configured palette color in dark mode`() {
        assertEquals(0xFF252525.toInt(), therapyWidgetTextArgb(AppearanceMode.DARK, palette))
    }
}
