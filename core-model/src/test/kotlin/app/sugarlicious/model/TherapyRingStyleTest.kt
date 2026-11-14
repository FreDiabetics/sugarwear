package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TherapyRingStyleTest {
    @Test fun `finite foreground spring starts and finishes at the supplied values`() {
        assertEquals(0f, TherapyRingStyle.motionFraction(0f))
        assertEquals(1f, TherapyRingStyle.motionFraction(1f))
        assertTrue(TherapyRingStyle.motionFraction(.1f) < TherapyRingStyle.motionFraction(.5f))
        for (step in 0..1000) assertTrue(TherapyRingStyle.motionFraction(step / 1000f) in 0f..1.03f)
    }
}
