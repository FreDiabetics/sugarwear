package app.sugarwear

import org.junit.Assert.assertEquals
import org.junit.Test

class CgmMetadataAgeTextTest {
    @Test fun `app and Tile use the same compact minute suffix without duplicate dots`() {
        for (input in listOf("vor 2 Minuten", "vor 2 Min", "vor 2 Min.")) assertEquals("vor 2 Min.", cgmCompactAgeText(input))
        assertEquals("vor 1 Min.", cgmCompactAgeText("vor 1 Minute"))
        assertEquals("vor 2 h", cgmCompactAgeText("vor 2 h"))
    }
}
