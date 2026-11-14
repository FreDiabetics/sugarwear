package app.sugarlicious.complications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchfaceHandsOpacityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        context
            .getSharedPreferences("native_watchface_hands", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `opacity toggles independently and persists per native face`() {
        assertEquals("full", WatchfaceHandsOpacity.text(context, WatchfaceGraphProfile.APEX))
        WatchfaceHandsOpacity.toggle(context, WatchfaceGraphProfile.APEX)
        assertEquals("dim", WatchfaceHandsOpacity.text(context, WatchfaceGraphProfile.APEX))
        assertEquals("full", WatchfaceHandsOpacity.text(context, WatchfaceGraphProfile.ORBIT))
        WatchfaceHandsOpacity.toggle(context, WatchfaceGraphProfile.APEX)
        assertEquals("full", WatchfaceHandsOpacity.text(context, WatchfaceGraphProfile.APEX))
    }

    @Test
    fun `graph carries reference state without making the full graph tappable`() {
        val service = Robolectric.buildService(NativeApexGraphComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.LONG_TEXT) as LongTextComplicationData
        assertEquals("full", data.text.getTextAt(service.resources, Instant.now()).toString())
        assertNull(data.smallImage)
        assertNotNull(data.monochromaticImage)
        assertNotNull(data.monochromaticImage?.ambientImage)
        assertEquals(android.graphics.drawable.Icon.TYPE_DATA, data.monochromaticImage!!.image.type)
        assertNull(data.tapAction)
        WatchfaceHandsOpacity.toggle(service, WatchfaceGraphProfile.APEX)
        val dim =
            kotlinx.coroutines.runBlocking {
                service.onComplicationRequest(
                    androidx.wear.watchface.complications.datasource
                        .ComplicationRequest(11, ComplicationType.LONG_TEXT, false),
                )
            } as LongTextComplicationData
        assertEquals("dim", dim.text.getTextAt(service.resources, Instant.now()).toString())
        val preview = service.getPreviewData(ComplicationType.LONG_TEXT) as LongTextComplicationData
        assertEquals("full", preview.text.getTextAt(service.resources, Instant.now()).toString())
        val legacy = Robolectric.buildService(GlucoseGraphComplication::class.java).create().get()
        assertEquals(SmallImageComplicationData::class.java, legacy.getPreviewData(ComplicationType.SMALL_IMAGE).javaClass)
    }
}
