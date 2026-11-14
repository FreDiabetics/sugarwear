package app.sugarlicious.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearBasalBackgroundIconTest {
    @Test
    fun `BR detail icon keeps colored circle and black symbol instead of tinting both`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = ImageView(context)
        view.renderSugarliciousWearIcon(app.sugarlicious.uishared.R.drawable.ic_basal_bckgrnd, Color.RED, Color.BLACK)
        val bitmap = Bitmap.createBitmap(66, 66, Bitmap.Config.ARGB_8888)
        view.drawable.setBounds(0, 0, 66, 66)
        view.drawable.draw(Canvas(bitmap))
        assertEquals(Color.RED, bitmap.getPixel(33, 2))
        assertEquals(Color.BLACK, bitmap.getPixel(33, 33))
    }
}
