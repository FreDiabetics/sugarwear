package app.sugarlicious.mobile

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.mobileMenuAccentArgb
import app.sugarlicious.mobile.ui.theme.mobileMenuOnAccentArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileMenuColorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `dark menu uses readable device palette tone`() {
        assertEquals(
            app.sugarlicious.uishared.SugarliciousAccentPalette
                .primary(true),
            mobileMenuAccentArgb(context, dark = true),
        )
    }

    @Test
    fun `light menu uses contrasting device palette tone`() {
        assertEquals(
            app.sugarlicious.uishared.SugarliciousAccentPalette
                .primary(false),
            mobileMenuAccentArgb(context, dark = false),
        )
    }

    @Test
    fun `dark menu foreground uses matching readable device tone`() {
        val foreground = mobileMenuOnAccentArgb(context, dark = true)
        assertEquals(android.graphics.Color.BLACK, foreground)
        assertTrue(ColorUtils.calculateContrast(foreground, mobileMenuAccentArgb(context, dark = true)) >= 4.5)
    }

    @Test
    fun `light menu foreground uses matching readable device tone`() {
        val foreground = mobileMenuOnAccentArgb(context, dark = false)
        assertEquals(android.graphics.Color.BLACK, foreground)
        assertTrue(ColorUtils.calculateContrast(foreground, mobileMenuAccentArgb(context, dark = false)) >= 4.5)
    }
}
