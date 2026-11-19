package app.sugarlicious.uishared

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class DeviceTileTypographyTest {
    @Test fun `text bitmap honors system font scale and keeps transparent background`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metrics = context.resources.displayMetrics

        @Suppress("DEPRECATION")
        val oldScale = metrics.scaledDensity
        try {
            @Suppress("DEPRECATION")
            metrics.scaledDensity = metrics.density
            val normal = renderDeviceTileTextBitmap(context, Typeface.DEFAULT, "123", 20f, Color.WHITE)
            @Suppress("DEPRECATION")
            metrics.scaledDensity = metrics.density * 2f
            val large = renderDeviceTileTextBitmap(context, Typeface.DEFAULT, "123", 20f, Color.WHITE)
            assertTrue("normal=${normal.width}x${normal.height}, large=${large.width}x${large.height}", large.width > normal.width * 1.8f)
            assertTrue(large.height > normal.height * 1.5f)
            assertTrue(normal.hasAlpha())
            normal.recycle()
            large.recycle()
        } finally {
            @Suppress("DEPRECATION")
            metrics.scaledDensity = oldScale
        }
    }

    @Test fun `large system font and long value stay inside assigned ring box`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = renderDeviceTileTextBitmap(context, Typeface.DEFAULT_BOLD, "123.45 U/h", 60f, Color.WHITE, 96, 42)
        assertTrue(bitmap.width <= 96)
        assertTrue(bitmap.height <= 42)
        assertTrue(bitmap.width > 20)
        assertTrue(bitmap.height > 5)
        bitmap.recycle()
    }
}
