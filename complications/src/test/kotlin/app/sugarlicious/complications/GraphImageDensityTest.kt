package app.sugarlicious.complications

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphImageDensityTest {
    @Test
    fun `Orbit and existing Apex provider render canonical semicircle dimensions`() {
        val orbit = Robolectric.buildService(OrbitGlucoseGraphComplication::class.java).create().get()
        val data = orbit.getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        val bitmap = (data.smallImage.image.loadDrawable(orbit) as BitmapDrawable).bitmap
        org.junit.Assert.assertEquals(660, bitmap.width)
        org.junit.Assert.assertEquals(330, bitmap.height)
        org.junit.Assert.assertEquals(660 to 194, complicationImageSize(ProviderKind.GRAPH))
        val apex = preview("mdpi")
        org.junit.Assert.assertEquals(660, apex.width)
        org.junit.Assert.assertEquals(194, apex.height)
        assertTrue("existing Apex provider must retain real chart ink", whitePixels(apex) > 0)
        assertTrue(whitePixels(bitmap) > 0)
        assertTrue(ComplicationUpdatePlanner.allManagedProviders.contains(OrbitGlucoseGraphComplication::class.java))
    }

    @Test
    fun `fixed size graph bitmap does not magnify dots when device density increases`() {
        try {
            val normal = whitePixels(preview("mdpi"))
            val dense = whitePixels(preview("xhdpi"))
            assertTrue("preview must contain visible glucose dots", normal > 0)
            assertTrue("fixed pixel image changed with physical DPI: $normal to $dense", abs(normal - dense) <= normal * .05)
        } finally {
            RuntimeEnvironment.setQualifiers("mdpi")
        }
    }

    private fun preview(qualifiers: String): Bitmap {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val service = Robolectric.buildService(GlucoseGraphComplication::class.java).create().get()
        service
            .getSharedPreferences("watch_display", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("graph_color_cgm_in", Color.WHITE)
            .putInt("graph_color_cgm_low", Color.WHITE)
            .putInt("graph_color_cgm_high", Color.WHITE)
            .commit()
        val data = service.getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        return (data.smallImage.image.loadDrawable(service) as BitmapDrawable).bitmap
    }

    private fun whitePixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == Color.WHITE) count++
            }
        }
        return count
    }
}
