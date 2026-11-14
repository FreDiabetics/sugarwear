package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class CircularComplicationPreviewTest {
    @Test
    fun `ranged accessibility zoom preserves default geometry and grows with enlarged system text`() {
        assertEquals(.9f, rangedCatalogSystemZoom(.9f), 0f)
        assertEquals(1f, rangedCatalogSystemZoom(1f), 0f)
        assertEquals(144f, 72f * rangedCatalogSystemZoom(2f), 0f)
        assertEquals(10f, 5f * rangedCatalogSystemZoom(2f), 0f)
    }

    @Test
    fun `display names replace combination separators without changing signed numeric content`() {
        assertEquals("Glukose · Trend · Delta", complicationDisplayName("Glukose + Trend + Delta"))
        assertEquals("Basal · IOB · COB", complicationDisplayName("Basal + IOB + COB"))
        assertEquals("+5 mg/dL ↗", complicationDisplayName("+5 mg/dL ↗"))
        assertEquals("123 +5", complicationDisplayName("123 +5"))
    }

    @Test
    fun `short text uses settings text roles while ranged sizing stays unchanged`() {
        val single = catalogPreviewSizing(72f, ranged = false, twoValues = false)
        val pair = catalogPreviewSizing(72f, ranged = false, twoValues = true)
        assertEquals(18f, single.primary, .001f)
        assertEquals(13f, pair.primary, .001f)
        assertEquals(18f, pair.secondary, .001f)
        assertEquals(16f, pair.icon, .001f)
        assertEquals(single.primary / 2, catalogPreviewSizing(36f, ranged = false, twoValues = false).primary, .001f)
        assertEquals(CatalogPreviewSizing(17f, 11f, 13f), catalogPreviewSizing(72f, ranged = true, twoValues = true))
    }

    @Test
    fun `narrow and short preview slots retain a centered circular ring`() {
        listOf(60f to 72f, 72f to 48f, 72f to 72f).forEach { (width, height) ->
            val bounds = circularComplicationPreviewBounds(width, height)
            assertEquals(minOf(width, height), bounds.width, 0f)
            assertEquals(bounds.width, bounds.height, 0f)
            assertEquals(width / 2f, bounds.center.x, 0f)
            assertEquals(height / 2f, bounds.center.y, 0f)
        }
    }
}
