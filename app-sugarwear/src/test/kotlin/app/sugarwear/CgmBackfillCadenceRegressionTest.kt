package app.sugarwear

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmBackfillCadenceRegressionTest {
    @Test fun `observed 595 second gap requests history on first returning live packet`() {
        val start = CgmCollectorBackfillProtocol.requestedStart(390_474, 391_069)
        assertNotNull(start)
    }

    @Test fun `range includes an earlier drifting slot and excludes both known endpoints`() {
        val anchor = 390_474L
        val live = 391_069L
        val missing = 390_771L
        val start = requireNotNull(CgmCollectorBackfillProtocol.requestedStart(anchor, live))
        val end = requireNotNull(CgmCollectorBackfillProtocol.requestedEnd(live))
        assertTrue(start > anchor)
        assertTrue(missing in start..end)
        assertTrue(end < live)
    }

    @Test fun `history end includes preceding sample with 297 second cadence`() {
        val live = 391_069L
        val previous = live - 297L
        assertTrue(requireNotNull(CgmCollectorBackfillProtocol.requestedEnd(live)) >= previous)
    }

    @Test fun `normal cadence jitter never requests a history transfer`() {
        for (cadence in 295L..305L) {
            assertNull(CgmCollectorBackfillProtocol.requestedStart(390_474, 390_474 + cadence))
        }
    }

    @Test fun `drift tolerant range remains inside existing 24 hour bound`() {
        val live = 200_000L
        val start = requireNotNull(CgmCollectorBackfillProtocol.requestedStart(10_000, live))
        val end = requireNotNull(CgmCollectorBackfillProtocol.requestedEnd(live))
        assertTrue(start >= live - CgmCollectorBackfillProtocol.MAX_WINDOW_SECONDS)
        assertTrue(end - start <= CgmCollectorBackfillProtocol.MAX_WINDOW_SECONDS)
    }
}
