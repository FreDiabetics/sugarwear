package app.sugarlicious.wear

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileSelectorBrandTest {
    @Test fun `both selectors use the centered standalone eighty unit symbol`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(GlucoseTileService::class.java, TherapyTileService::class.java).forEach { service ->
            val info = context.packageManager.getServiceInfo(ComponentName(context, service), 0)
            assertEquals(R.drawable.ic_tile_brand, info.icon)
        }
        val drawable = requireNotNull(context.getDrawable(R.drawable.ic_tile_brand))
        val image = Bitmap.createBitmap(324, 324, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 324, 324)
        drawable.draw(Canvas(image))
        val rows = (0 until 324).filter { y -> (0 until 324).any { x -> image.getPixel(x, y) ushr 24 > 127 } }
        assertTrue(kotlin.math.abs(240 - (rows.last() - rows.first() + 1)) <= 2)
        assertTrue(kotlin.math.abs(323 - (rows.last() + rows.first())) <= 2)
    }
}
