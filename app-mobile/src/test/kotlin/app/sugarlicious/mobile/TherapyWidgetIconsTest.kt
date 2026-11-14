package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TherapyWidgetIconsTest {
    @Test
    fun `new IOB symbol uses the supplied filled body instead of the old hollow syringe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = context.getDrawable(app.sugarlicious.uishared.R.drawable.ic_iob_bckgrnd)!!.mutate() as LayerDrawable
        drawable.findDrawableByLayerId(app.sugarlicious.uishared.R.id.therapy_icon_background).setTint(Color.RED)
        drawable.findDrawableByLayerId(app.sugarlicious.uishared.R.id.therapy_icon_symbol).setTint(Color.BLACK)
        val bitmap = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 66, 66)
        drawable.draw(Canvas(bitmap))
        assertEquals(Color.BLACK, bitmap.getPixel(26, 40))
    }

    @Test
    fun `supplied IOB and COB widget icons keep colored circles and black symbols independently`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("iob", "cob", "basal", "basalmore", "basalless").forEach { metric ->
            val id = context.resources.getIdentifier("ic_${metric}_bckgrnd", "drawable", context.packageName)
            assertTrue("missing supplied $metric background icon", id != 0)
            val drawable = context.getDrawable(id)!!.mutate() as LayerDrawable
            val circleId = context.resources.getIdentifier("therapy_icon_background", "id", context.packageName)
            val symbolId = context.resources.getIdentifier("therapy_icon_symbol", "id", context.packageName)
            drawable.findDrawableByLayerId(circleId).setTint(Color.RED)
            drawable.findDrawableByLayerId(symbolId).setTint(Color.BLACK)
            val bitmap = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 66, 66)
            drawable.draw(Canvas(bitmap))
            assertEquals(Color.RED, bitmap.getPixel(33, 2))
            assertTrue((10..55).any { y -> (10..55).any { x -> bitmap.getPixel(x, y) == Color.BLACK } })
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        }
    }
}
