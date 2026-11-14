package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewGraphSizingTest {
    @Test fun `three therapy columns fit exact landscape pixels without rounding overflow`() {
        val width = overviewProgressColumnWidthPx(1508, 42, 364)
        assertEquals(474, width)
        org.junit.Assert.assertTrue(width * 3 + 84 <= 1508)
        assertEquals(364, overviewProgressColumnWidthPx(900, 42, 364))
    }

    @Test fun `metabolic graph stays readable on compact and large profiles`() {
        assertEquals(120, overviewMetabolicGraphHeight(98))
        assertEquals(145, overviewMetabolicGraphHeight(170))
    }

    @Test fun `independently scrolling landscape does not shrink chart to fit cards`() {
        assertEquals(400f, overviewFittedCgmHeight(400f, 360f, 0f, listOf(180f, null, 202f)))
    }

    @Test fun `first three cards fit while the fourth stays below the fold`() {
        val height = overviewFittedCgmHeight(400f, 1000f, 280f, listOf(180f, null, 300f, 240f))
        assertEquals(210f, height)
        assertEquals(1000f, 280f + 30f + 180f + height + 300f)
        assertEquals(height, overviewFittedCgmHeight(400f, 1000f, 280f, listOf(180f, null, 300f)))
    }

    @Test fun `disabling therapy gives the CGM more room while preserving metabolic card`() {
        assertEquals(202f, overviewFittedCgmHeight(400f, 800f, 280f, listOf(null, 300f)))
        assertEquals(514f, overviewFittedCgmHeight(400f, 800f, 280f, listOf(null)))
    }

    @Test fun `large text preserves a readable floor rather than distorting graphs`() {
        assertEquals(96f, overviewFittedCgmHeight(400f, 500f, 280f, listOf(180f, null, 300f)))
    }
}
