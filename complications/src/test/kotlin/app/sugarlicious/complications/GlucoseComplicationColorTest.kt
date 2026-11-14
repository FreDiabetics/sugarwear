package app.sugarlicious.complications

import android.app.Application
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ColorSourceApplication :
    Application(),
    ComplicationGlucoseColorSource {
    override fun outOfRangeGlucoseColor(valueMgDl: Double): Int = 0xFF123456.toInt()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ColorSourceApplication::class)
class GlucoseComplicationColorTest {
    @Test
    fun `glucose ranged data carries exactly the host color without recoloring icon or title`() {
        val service = Robolectric.buildService(GlucoseRangedComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.RANGED_VALUE) as RangedValueComplicationData
        assertArrayEquals(intArrayOf(0xFF123456.toInt()), data.colorRamp?.colors)
        assertEquals(false, data.colorRamp?.interpolated)
        assertNotNull(data.text)
    }

    @Test
    fun `therapy providers do not inherit a glucose color hint`() {
        val service = Robolectric.buildService(IobRangedValueComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.RANGED_VALUE) as RangedValueComplicationData
        assertNull(data.colorRamp)
    }
}
