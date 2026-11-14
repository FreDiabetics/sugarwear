package app.sugarlicious.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetDetailIconsTest {
    @Test
    fun `every detail icon retains its circle and independently colored original symbol`() {
        listOf(WidgetColorRole.DELTA_ICON, WidgetColorRole.AGE_ICON, WidgetColorRole.TIR_ICON).forEach { role ->
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            WidgetDetailIcons.draw(Canvas(bitmap), role, 7f, 7f, 66f, Color.RED, Color.BLUE)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
            assertEquals(Color.RED, bitmap.getPixel(40, 10))
            assertTrue((7..72).any { y -> (7..72).any { x -> bitmap.getPixel(x, y) == Color.BLUE } })
        }
    }
}
