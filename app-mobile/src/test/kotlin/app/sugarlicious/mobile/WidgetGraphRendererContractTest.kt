package app.sugarlicious.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetGraphRendererContractTest {
    @Test
    fun `graph widget renders from LocalSize instead of fixed bitmap`() {
        val source = File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()

        assertTrue(source.contains("override val sizeMode: SizeMode = SizeMode.Exact"))
        assertTrue(source.contains("val size = LocalSize.current"))
        assertTrue(source.contains("widgetRenderSizeDp(size.width.value, size.height.value, contentInsets)"))
        assertTrue(source.contains("(renderSize.widthDp * pixelDensity).roundToInt()"))
        assertTrue(source.contains("(renderSize.heightDp * pixelDensity).roundToInt()"))
        assertFalse(source.contains("width: Int = 800"))
        assertFalse(source.contains("height: Int = 360"))
        assertFalse(source.contains("val density = safeWidth / 400f"))
    }

    @Test
    fun `graph widget contains cgm only and no therapy overlays`() {
        val source = File("src/main/kotlin/app/sugarlicious/mobile/SugarliciousWidgets.kt").readText()
        val start = source.indexOf("internal fun renderWidgetGraph")
        val end = source.indexOf("internal fun canonicalWidgetSamples", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val body = source.substring(start, end)

        assertTrue(body.contains("canonicalWidgetSamples"))
        assertFalse(body.contains("glucosePredictions"))
        assertFalse(body.contains("therapyHistory"))
        assertFalse(body.contains("totalIob"))
        assertFalse(body.contains("cobGrams"))
        assertFalse(body.contains("basal"))
        assertFalse(body.contains("SMB"))
    }
}
