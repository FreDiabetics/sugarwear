package app.aapswear.g7watch

import app.aapswear.g7.G7Sensor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7DiscoveryCandidatesTest {
    @Test fun `unreachable unverified peer cannot occupy the next candidates advertisement window`() {
        var now = 1_000L
        val candidates = G7DiscoveryCandidates { now }
        val wrong = "AA:BB:CC:DD:EE:01"
        val current = "AA:BB:CC:DD:EE:02"
        candidates.defer(wrong)
        now += 7_000L // Captured phone/sensor slot followed the other G7 by seven seconds.
        assertFalse(candidates.accepts(wrong.lowercase()))
        assertTrue(candidates.accepts(current))
        now = 1_000L + G7DiscoveryCandidates.CANDIDATE_COOLDOWN_MS
        assertTrue(candidates.accepts(wrong)) // A radio failure must never permanently blacklist it.
    }

    @Test fun `authentication rejection remains separate from temporary radio cooldown`() {
        var now = 1_000L
        val candidates = G7DiscoveryCandidates { now }
        candidates.reject("AA:BB:CC:DD:EE:01")
        candidates.defer("AA:BB:CC:DD:EE:02")
        now += 60_000L
        assertFalse(candidates.accepts("AA:BB:CC:DD:EE:01"))
        assertTrue(candidates.accepts("AA:BB:CC:DD:EE:02"))
        candidates.clear()
        assertTrue(candidates.accepts("AA:BB:CC:DD:EE:01"))
    }

    @Test fun `only initial unauthenticated connection failures switch discovery candidate`() {
        val initial = G7Sensor("setup")
        for (error in listOf(G7_GATT_133_ERROR_CODE, G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, "G7-GATT-201")) {
            assertTrue(shouldDeferUnverifiedG7Candidate(initial, false, error, true))
            assertFalse(shouldDeferUnverifiedG7Candidate(initial.copy(deviceAddress = "AA:BB:CC:DD:EE:01"), false, error, true))
            assertFalse(shouldDeferUnverifiedG7Candidate(initial, true, error, true))
            assertFalse(shouldDeferUnverifiedG7Candidate(initial, false, error, false))
        }
        assertFalse(shouldDeferUnverifiedG7Candidate(initial, false, "G7-AUTH-MISMATCH", true))
        assertFalse(shouldDeferUnverifiedG7Candidate(initial, false, "G7-PERM-401", false))
        assertFalse(shouldDeferUnverifiedG7Candidate(initial, false, G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE, true))
    }
}
