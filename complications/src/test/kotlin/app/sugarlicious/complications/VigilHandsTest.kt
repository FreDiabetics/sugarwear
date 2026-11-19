package app.sugarlicious.complications

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VigilHandsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun reset() {
        context
            .getSharedPreferences("vigil_interaction", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun ink(bitmap: Bitmap): Long = (0 until 450).sumOf { y -> (0 until 450).sumOf { x -> Color.alpha(bitmap.getPixel(x, y)).toLong() } }

    @Test fun `center action persists thirty percent opacity and second tap restores full opacity`() {
        reset()
        val full = VigilHands.render(context, 22, 10)
        assertEquals(1f, VigilHands.opacity(context))
        val receiver = VigilHandsToggleReceiver()
        receiver.onReceive(context, Intent(VigilHandsToggleReceiver.ACTION))
        assertEquals(.3f, VigilHands.opacity(context))
        val dimmed = VigilHands.render(context, 22, 10)
        assertTrue(ink(dimmed).toDouble() / ink(full) in 0.29..0.31)
        receiver.onReceive(context, Intent("unrelated"))
        assertEquals(.3f, VigilHands.opacity(context))
        receiver.onReceive(context, Intent(VigilHandsToggleReceiver.ACTION))
        assertEquals(1f, VigilHands.opacity(context))
    }

    @Test fun `hands follow hour and minute with transparent corners and no seconds`() {
        reset()
        val first = VigilHands.render(context, 22, 10)
        val sameTwelveHourPosition = VigilHands.render(context, 10, 10)
        val nextMinute = VigilHands.render(context, 22, 11)
        assertTrue(first.sameAs(sameTwelveHourPosition))
        assertTrue(!first.sameAs(nextMinute))
        assertEquals(Color.TRANSPARENT, first.getPixel(0, 0))
        assertTrue("Ambient omits the offset shadow", ink(VigilHands.render(context, 22, 10, ambient = true)) < ink(first))
    }

    @Test fun `both concentric pivot dots match Apex and dim together with the hands`() {
        reset()
        val full = VigilHands.render(context, 22, 10)
        assertEquals(Color.BLACK, full.getPixel(225, 225))
        assertEquals(Color.WHITE, full.getPixel(225, 219))
        VigilHandsToggleReceiver().onReceive(context, Intent(VigilHandsToggleReceiver.ACTION))
        val dimmed = VigilHands.render(context, 22, 10)
        assertEquals(76, Color.alpha(dimmed.getPixel(225, 225)))
        assertEquals(76, Color.alpha(dimmed.getPixel(225, 219)))
        assertEquals(48, Color.alpha(VigilHands.render(context, 22, 10, ambient = true).getPixel(225, 225)))
    }

    @Test fun `merged glucose and graph providers retain tap actions and ambient images`() {
        val header =
            Robolectric
                .buildService(DirectToWatchHeaderComplication::class.java)
                .create()
                .get()
                .getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        val graph =
            Robolectric
                .buildService(DirectToWatchGraphComplication::class.java)
                .create()
                .get()
                .getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        org.junit.Assert.assertNull("Vigil opens via its local native Launch region", header.tapAction)
        assertNotNull(graph.tapAction)
        assertNotNull(header.smallImage.ambientImage)
        assertNotNull(graph.smallImage.ambientImage)
    }

    @Test fun `tap-only providers reuse canonical actions and cache a one pixel image`() {
        val open = Robolectric.buildService(DirectToWatchCollectorActionComplication::class.java).create().get()
        val scale = Robolectric.buildService(DirectToWatchGraphActionComplication::class.java).create().get()
        val openData = open.getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        val scaleData = scale.getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        assertEquals(DirectToWatchActions.collector(open), openData.tapAction)
        assertEquals(DirectToWatchActions.graphScale(scale), scaleData.tapAction)
        assertTrue(openData.smallImage === scaleData.smallImage)
        assertEquals(
            1,
            openData.smallImage.image
                .loadDrawable(open)!!
                .intrinsicWidth,
        )
        val before = DirectToWatchPreferences.graphHours(scale)
        scaleData.tapAction!!.send()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertTrue(before != DirectToWatchPreferences.graphHours(scale))
    }

    @Test fun `native seconds opacity mask follows the same center tap as hour and minute hands`() {
        reset()
        val provider = Robolectric.buildService(DirectToWatchHandsComplication::class.java).create().get()

        fun alpha(): Int {
            val data = provider.getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
            val drawable = data.smallImage.image.loadDrawable(provider) as android.graphics.drawable.BitmapDrawable
            return Color.alpha(drawable.bitmap.getPixel(0, 0))
        }
        assertEquals(255, alpha())
        VigilHands.toggle(context)
        assertEquals(76, alpha())
    }

    @Test fun `only center provider publishes a tap action and clock supplies ambient imagery`() {
        val hands =
            Robolectric
                .buildService(DirectToWatchHandsComplication::class.java)
                .create()
                .get()
                .getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        val center =
            Robolectric
                .buildService(DirectToWatchHandsToggleComplication::class.java)
                .create()
                .get()
                .getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        val clock =
            Robolectric
                .buildService(DirectToWatchClockComplication::class.java)
                .create()
                .get()
                .getPreviewData(ComplicationType.SMALL_IMAGE) as SmallImageComplicationData
        assertNull(hands.tapAction)
        assertNotNull(center.tapAction)
        assertNotNull(hands.smallImage.ambientImage)
        assertNotNull(clock.smallImage.ambientImage)
        reset()
        center.tapAction!!.send()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertEquals(.3f, VigilHands.opacity(context))
    }
}
