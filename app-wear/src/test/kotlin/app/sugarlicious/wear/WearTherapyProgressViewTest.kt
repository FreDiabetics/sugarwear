package app.sugarlicious.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearTherapyProgressViewTest {
    @Test fun `remaining segment leaves the same visible separation as Mobile at every size`() {
        for (side in listOf(264, 528)) {
            val view = WearTherapyProgressView(ApplicationProvider.getApplicationContext())
            view.layout(0, 0, side, side)
            view.bind(.5f, Color.CYAN)
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val stroke = 7f * side / 66f
            val radius = (side - stroke) / 2f
            // Inside Mobile's visible gap, beyond the projecting fill cap.
            val angle = Math.toRadians(270.0) + .82 * stroke / radius
            val x = (side / 2f + radius * kotlin.math.cos(angle)).toInt()
            val y = (side / 2f + radius * kotlin.math.sin(angle)).toInt()
            assertEquals("clear segment gap at size $side", 0, Color.alpha(bitmap.getPixel(x, y)))
            bitmap.recycle()
        }
    }

    @Test
    fun `ring accepts only finite normalized progress and retains missing state`() {
        val view = WearTherapyProgressView(ApplicationProvider.getApplicationContext())
        view.bind(Float.NaN, Color.CYAN)
        assertEquals(null, view.progress)
        view.bind(2f, Color.CYAN)
        assertEquals(1f, view.progress)
        view.bind(-1f, Color.CYAN)
        assertEquals(0f, view.progress)
        view.bind(null, Color.CYAN)
        assertEquals(null, view.progress)
    }
}
