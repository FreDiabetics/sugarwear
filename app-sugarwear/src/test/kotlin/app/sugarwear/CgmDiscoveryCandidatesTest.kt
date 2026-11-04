package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmDiscoveryCandidatesTest {
    @Test fun `unreachable unverified peer cannot occupy the next candidates advertisement window`() {
        var now = 1_000L
        val candidates = CgmDiscoveryCandidates { now }
        val wrong = "AA:BB:CC:DD:EE:01"
        val current = "AA:BB:CC:DD:EE:02"
        candidates.defer(wrong)
        now += 7_000L // Captured phone/sensor slot followed the other G7 by seven seconds.
        assertFalse(candidates.accepts(wrong.lowercase()))
        assertTrue(candidates.accepts(current))
        now = 1_000L + CgmDiscoveryCandidates.CANDIDATE_COOLDOWN_MS
        assertTrue(candidates.accepts(wrong)) // A radio failure must never permanently blacklist it.
    }

    @Test fun `authentication rejection remains separate from temporary radio cooldown`() {
        var now = 1_000L
        val candidates = CgmDiscoveryCandidates { now }
        candidates.reject("AA:BB:CC:DD:EE:01")
        candidates.defer("AA:BB:CC:DD:EE:02")
        now += 60_000L
        assertFalse(candidates.accepts("AA:BB:CC:DD:EE:01"))
        assertTrue(candidates.accepts("AA:BB:CC:DD:EE:02"))
        candidates.clear()
        assertTrue(candidates.accepts("AA:BB:CC:DD:EE:01"))
    }

    @Test fun `only initial unauthenticated connection failures switch discovery candidate`() {
        val initial = CgmSensor("setup")
        for (error in listOf(CGM_GATT_133_ERROR_CODE, CGM_DIRECT_CONNECT_TIMEOUT_ERROR_CODE, "G7-GATT-201")) {
            assertTrue(shouldDeferUnverifiedCgmCandidate(initial, false, error, true))
            assertFalse(shouldDeferUnverifiedCgmCandidate(initial.copy(deviceAddress = "AA:BB:CC:DD:EE:01"), false, error, true))
            assertFalse(shouldDeferUnverifiedCgmCandidate(initial, true, error, true))
            assertFalse(shouldDeferUnverifiedCgmCandidate(initial, false, error, false))
        }
        assertFalse(shouldDeferUnverifiedCgmCandidate(initial, false, "G7-AUTH-MISMATCH", true))
        assertFalse(shouldDeferUnverifiedCgmCandidate(initial, false, "G7-PERM-401", false))
        assertFalse(shouldDeferUnverifiedCgmCandidate(initial, false, CGM_WRITE_CALLBACK_TIMEOUT_ERROR_CODE, true))
    }
}
