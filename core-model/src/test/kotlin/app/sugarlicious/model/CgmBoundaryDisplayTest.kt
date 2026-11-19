package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CgmBoundaryDisplayTest {
    @Test fun `direct display boundaries are exclusive and never alter raw values`() {
        assertEquals(CgmBoundaryDisplay.LOW, cgmBoundaryDisplay(39.0))
        assertNull(cgmBoundaryDisplay(40.0))
        assertNull(cgmBoundaryDisplay(400.0))
        assertEquals(CgmBoundaryDisplay.HIGH, cgmBoundaryDisplay(401.0))
    }
}
