package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsCategoryIconsTest {
    @Test
    fun `about entry renders supplied black information glyph on circular background`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = checkNotNull(settingsCategoryIcon(context, "about", "Über Sugarlicious")).drawable
        val image = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 66, 66)
        drawable.draw(Canvas(image))
        assertEquals(Color.BLACK, image.getPixel(34, 19))
        assertEquals(Color.BLACK, image.getPixel(35, 40))
        assertEquals(0, Color.alpha(image.getPixel(0, 0)))
        image.recycle()
    }

    @Test
    fun `appearance entry renders its supplied icon instead of falling back to the settings gear`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icon = settingsCategoryIcon(context, "appearance", "Farben & Darstellung")
        assertNotNull(icon)
        val image = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(icon).drawable
        drawable.setBounds(0, 0, 66, 66)
        drawable.draw(Canvas(image))
        assertEquals(Color.BLACK, image.getPixel(33, 44))
        image.recycle()
    }

    @Test
    fun `background recoloring preserves the black user icon and transparency outside the circle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = SugarliciousColors.palette
        try {
            listOf(Color.RED, Color.BLUE).forEach { background ->
                SugarliciousColors.apply(SugarliciousPalette(mapOf(SugarliciousColorRole.SETTINGS_GENERAL to background)))
                val drawable = checkNotNull(settingsCategoryIcon(context, "general", "Allgemein")).drawable
                val image = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 66, 66)
                drawable.draw(Canvas(image))
                assertEquals(background, image.getPixel(33, 6))
                assertEquals(Color.BLACK, image.getPixel(38, 33))
                assertEquals(0, Color.alpha(image.getPixel(0, 0)))
                image.recycle()
            }
        } finally {
            SugarliciousColors.apply(previous)
        }
    }
}
