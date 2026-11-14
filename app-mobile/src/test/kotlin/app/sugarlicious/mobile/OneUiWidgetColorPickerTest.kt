package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OneUiWidgetColorPickerTest {
    @Test
    fun `spectrum hue and opacity expose accessibility adjustments`() {
        val source = java.io.File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousColorSettingsPanel.kt").readText()
        val spectrum = source.substringAfter("internal fun SaturationBrightnessPicker(").substringBefore("internal fun HuePicker(")
        listOf("Sättigung erhöhen", "Sättigung verringern", "Helligkeit erhöhen", "Helligkeit verringern").forEach { assertTrue(spectrum.contains(it)) }
        val hue = source.substringAfter("internal fun HuePicker(").substringBefore("internal fun AlphaPicker(")
        assertTrue(hue.contains("progressBarRangeInfo = ProgressBarRangeInfo"))
        assertTrue(hue.contains("setProgress"))
        val alpha = source.substringAfter("internal fun AlphaPicker(").substringBefore("internal fun NotificationGraphSettingsPanel(")
        assertTrue(alpha.contains("progressBarRangeInfo = ProgressBarRangeInfo"))
        assertTrue(alpha.contains("setProgress"))
    }

    @Test
    fun `opening color remains bit exact including transparent RGB`() {
        listOf(0x00123456, 0x12ABCDEF, 0xFF010203.toInt(), -1).forEach {
            assertEquals(it, WidgetPickerColor.fromArgb(it).argb)
        }
    }

    @Test
    fun `swatches and spectrum preserve alpha and opacity preserves RGB`() {
        val initial = WidgetPickerColor.fromArgb(0x12123456)
        assertEquals(0x12ABCDEF, initial.swatch(0xFFABCDEF.toInt()).argb)
        assertEquals(0x12, android.graphics.Color.alpha(initial.spectrum(h = 180f).argb))
        assertEquals(0x00123456, initial.opacity(-1f).argb)
        assertEquals(0xFF123456.toInt(), initial.opacity(2f).argb)
    }

    @Test
    fun `hue remains editable for black and grayscale`() {
        val black = WidgetPickerColor.fromArgb(android.graphics.Color.BLACK).spectrum(h = 120f, s = 1f)
        assertEquals(120f, black.hue, .001f)
        assertEquals(android.graphics.Color.GREEN, black.spectrum(v = 1f).argb)
    }

    @Test
    fun `swatches have stable grid and include pure white and black`() {
        val swatches = widgetPickerSwatches()
        assertEquals(42, swatches.size)
        assertTrue(swatches.contains(android.graphics.Color.WHITE))
        assertTrue(swatches.contains(android.graphics.Color.BLACK))
    }

    @Test
    fun `mobile and widget color editors share the reconstructed picker`() {
        val source = java.io.File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousColorSettingsPanel.kt").readText()
        val editor = source.substringAfter("internal fun ColorEditorDialog(").substringBefore("internal fun SaturationBrightnessPicker(")
        assertTrue(editor.contains("OneUiWidgetColorPicker("))
        assertTrue(editor.contains("widgetStyle ?: LocalOneUiWidgetSettingsColors.current"))
    }
}
