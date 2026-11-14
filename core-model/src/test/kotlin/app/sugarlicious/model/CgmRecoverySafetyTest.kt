package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CgmRecoverySafetyTest {
    private val now = 1_800_000_000_000L
    private val watchMemory = CgmResolverMemory(state = CgmSourceState.WATCH_DIRECT)

    private fun mobile(minutesAgo: Long) =
        CgmSourceCandidate(
            CgmCanonicalSource.MOBILE_AAPS,
            123.0,
            now - minutesAgo * 60_000L,
            now,
        )

    private fun resolve(mobile: CgmSourceCandidate?, previous: CgmResolverMemory) =
        CanonicalCgmSourceResolver.resolve(mobile, null, now, previous)

    @Test fun `first fresh mobile reading cannot replace a missing Watch reading`() {
        val result = resolve(mobile(1), watchMemory)
        assertEquals(CgmSourceState.NO_SOURCE, result.state)
        assertEquals(CgmCanonicalSource.NONE, result.canonicalSource)
        assertNull(result.reading)
        assertEquals(1, result.memory.recoveryReadingCount)
        val recovered = resolve(mobile(0), result.memory)
        assertEquals(CgmSourceState.MOBILE_PRIMARY, recovered.state)
    }

    @Test fun `degraded mobile does not bypass hysteresis when Watch is absent`() {
        val result = resolve(mobile(14), watchMemory)
        assertEquals(CgmCanonicalSource.NONE, result.canonicalSource)
        assertNull(result.reading)
        assertEquals(0, result.memory.recoveryReadingCount)
    }

    @Test fun `out of order mobile then repeated first reading is not recovery`() {
        val first = resolve(mobile(2), watchMemory)
        val older = resolve(mobile(3), first.memory)
        val repeated = resolve(mobile(2), older.memory)
        assertEquals(1, repeated.memory.recoveryReadingCount)
        assertNull(repeated.reading)
        assertEquals(now - 2 * 60_000L, repeated.memory.lastRecoveryMobileTimestampEpochMs)
        assertEquals(CgmSourceState.MOBILE_PRIMARY, resolve(mobile(1), repeated.memory).state)
    }

    @Test fun `no source interval cannot erase the Watch recovery requirement`() {
        val gap = resolve(null, watchMemory)
        assertEquals(CgmSourceState.NO_SOURCE, gap.state)
        val first = resolve(mobile(1), gap.memory)
        assertNull(first.reading)
        assertEquals(1, first.memory.recoveryReadingCount)
        assertEquals(CgmSourceState.MOBILE_PRIMARY, resolve(mobile(0), first.memory).state)
    }
}
