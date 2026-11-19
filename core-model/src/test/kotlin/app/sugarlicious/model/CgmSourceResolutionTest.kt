package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CgmSourceResolutionTest {
    private val now = 1_000_000_000L

    @Test
    fun `fresh Mobile is primary in automatic mode`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 2),
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.MOBILE_PRIMARY, result.state)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, result.canonicalSource)
    }

    @Test
    fun `fifteen minute Mobile timeout switches to already available Watch stream`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 16),
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
        assertEquals("mobile_missing_or_timed_out", result.reason)
    }

    @Test
    fun `timeout without a fresh Watch stream becomes no source`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 16),
                watch = watch(minutesAgo = 13),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.NO_SOURCE, result.state)
        assertEquals(CgmCanonicalSource.NONE, result.canonicalSource)
        assertEquals(null, result.reading)
    }

    @Test
    fun `newer Watch value does not override Mobile before the fifteen minute timeout`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 5, value = 116.0),
                watch = watch(minutesAgo = 0, value = 110.0),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.MOBILE_PRIMARY, result.state)
        assertEquals(116.0, result.reading?.glucoseMgDl)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, result.canonicalSource)
    }

    @Test
    fun `Mobile recovery requires two distinct fresh readings`() {
        val firstMobile = mobile(minutesAgo = 1, value = 111.0)
        val watchReading = watch(minutesAgo = 1, value = 111.0)
        val first =
            CanonicalCgmSourceResolver.resolve(
                mobile = firstMobile,
                watch = watchReading,
                nowEpochMs = now,
                previous = CgmResolverMemory(state = CgmSourceState.WATCH_DIRECT),
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, first.state)
        assertEquals(1, first.memory.recoveryReadingCount)

        val duplicate =
            CanonicalCgmSourceResolver.resolve(
                mobile = firstMobile,
                watch = watchReading,
                nowEpochMs = now,
                previous = first.memory,
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, duplicate.state)
        assertEquals(1, duplicate.memory.recoveryReadingCount)

        val second =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(secondsAgo = 20, value = 112.0),
                watch = watch(secondsAgo = 25, value = 112.0),
                nowEpochMs = now,
                previous = duplicate.memory,
            )

        assertEquals(CgmSourceState.MOBILE_PRIMARY, second.state)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, second.canonicalSource)
    }

    @Test
    fun `Watch remains canonical when it produces a newer value during Mobile recovery`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 4, value = 108.0),
                watch = watch(minutesAgo = 0, value = 109.0),
                nowEpochMs = now,
                previous = CgmResolverMemory(state = CgmSourceState.WATCH_DIRECT),
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
    }

    @Test
    fun `degraded Mobile readings do not count toward recovery`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 8, value = 108.0),
                watch = watch(minutesAgo = 1, value = 109.0),
                nowEpochMs = now,
                previous =
                    CgmResolverMemory(
                        state = CgmSourceState.MOBILE_RECOVERY,
                        recoveryReadingCount = 1,
                        lastRecoveryMobileTimestampEpochMs = now - 9 * 60_000L,
                    ),
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
        assertEquals(0, result.memory.recoveryReadingCount)
        assertEquals(null, result.memory.lastRecoveryMobileTimestampEpochMs)
    }

    @Test
    fun `same sensor timestamp is deduplicated`() {
        val measured = now - 60_000L
        val mobile =
            CgmSourceCandidate(
                source = CgmCanonicalSource.MOBILE_AAPS,
                glucoseMgDl = 104.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now - 20_000L,
                sensorId = "sensor",
                sessionId = "session",
            )
        val watch =
            CgmSourceCandidate(
                source = CgmCanonicalSource.WATCH_G7_DIRECT,
                glucoseMgDl = 104.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now - 30_000L,
                sensorId = "sensor",
                sessionId = "session",
            )

        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile,
                watch = watch,
                nowEpochMs = now,
            )

        assertTrue(result.deduplicatedSameMeasurement)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, result.canonicalSource)
    }

    @Test
    fun `different sensor sessions are never identified as the same measurement only by ids`() {
        val measured = now - 60_000L
        val mobile =
            CgmSourceCandidate(
                source = CgmCanonicalSource.MOBILE_AAPS,
                glucoseMgDl = 100.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now,
                sensorId = "sensor-a",
                sessionId = "session-a",
            )
        val watch =
            CgmSourceCandidate(
                source = CgmCanonicalSource.WATCH_G7_DIRECT,
                glucoseMgDl = 120.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now,
                sensorId = "sensor-b",
                sessionId = "session-b",
            )

        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile,
                watch = watch,
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `same timestamp and glucose with different known sensor and session ids is not deduplicated`() {
        val measured = now - 60_000L
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = identified(CgmCanonicalSource.MOBILE_AAPS, measured, 104.0, "sensor-a", "session-a"),
                watch = identified(CgmCanonicalSource.WATCH_G7_DIRECT, measured, 104.0, "sensor-b", "session-b"),
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `same timestamp and glucose with different known sensor id is not deduplicated`() {
        val measured = now - 60_000L
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = identified(CgmCanonicalSource.MOBILE_AAPS, measured, 104.0, "sensor-a", "session"),
                watch = identified(CgmCanonicalSource.WATCH_G7_DIRECT, measured, 104.0, "sensor-b", "session"),
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `same timestamp and glucose with different known session id is not deduplicated`() {
        val measured = now - 60_000L
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = identified(CgmCanonicalSource.MOBILE_AAPS, measured, 104.0, "sensor", "session-a"),
                watch = identified(CgmCanonicalSource.WATCH_G7_DIRECT, measured, 104.0, "sensor", "session-b"),
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `partial unknown identity is not enough for cross source deduplication`() {
        val measured = now - 60_000L
        val mobile = identified(CgmCanonicalSource.MOBILE_AAPS, measured, 104.0, "sensor", null)
        val watch = identified(CgmCanonicalSource.WATCH_G7_DIRECT, measured, 104.8, "sensor", "session")

        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile,
                watch = watch,
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `unknown cross source identity is never treated as one measurement`() {
        val measured = now - 60_000L
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = identified(CgmCanonicalSource.MOBILE_AAPS, measured, 104.0, null, null),
                watch = identified(CgmCanonicalSource.WATCH_G7_DIRECT, measured, 104.0, null, null),
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `phone can disappear during recovery without source flapping`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = null,
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
                previous =
                    CgmResolverMemory(
                        state = CgmSourceState.MOBILE_RECOVERY,
                        recoveryReadingCount = 1,
                        lastRecoveryMobileTimestampEpochMs = now - 60_000L,
                    ),
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
    }

    @Test
    fun `phone reachability is not an input to source selection`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 18),
                watch = watch(minutesAgo = 2),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
    }

    @Test
    fun `non finite values and impossible receipt timestamps are rejected`() {
        val invalidCandidates =
            listOf(
                mobile(value = Double.NaN),
                mobile().copy(receivedAtEpochMs = now + 6 * 60_000L),
                mobile().copy(receivedAtEpochMs = now - 6 * 60_000L),
            )

        invalidCandidates.forEach { candidate ->
            val result =
                CanonicalCgmSourceResolver.resolve(
                    mobile = candidate,
                    watch = null,
                    nowEpochMs = now,
                )

            assertEquals(CgmSourceState.NO_SOURCE, result.state)
            assertEquals(null, result.reading)
        }
    }

    private fun mobile(
        minutesAgo: Long = 0,
        secondsAgo: Long? = null,
        value: Double = 112.0,
    ): CgmSourceCandidate =
        candidate(
            CgmCanonicalSource.MOBILE_AAPS,
            secondsAgo?.times(1_000L) ?: minutesAgo * 60_000L,
            value,
        )

    private fun watch(
        minutesAgo: Long = 0,
        secondsAgo: Long? = null,
        value: Double = 112.0,
    ): CgmSourceCandidate =
        candidate(
            CgmCanonicalSource.WATCH_G7_DIRECT,
            secondsAgo?.times(1_000L) ?: minutesAgo * 60_000L,
            value,
        )

    private fun candidate(
        source: CgmCanonicalSource,
        ageMs: Long,
        value: Double,
    ): CgmSourceCandidate =
        CgmSourceCandidate(
            source = source,
            glucoseMgDl = value,
            measuredAtEpochMs = now - ageMs,
            receivedAtEpochMs = now - ageMs + 5_000L,
        )

    private fun identified(
        source: CgmCanonicalSource,
        measuredAtEpochMs: Long,
        value: Double,
        sensorId: String?,
        sessionId: String?,
    ): CgmSourceCandidate =
        CgmSourceCandidate(
            source = source,
            glucoseMgDl = value,
            measuredAtEpochMs = measuredAtEpochMs,
            receivedAtEpochMs = now,
            sensorId = sensorId,
            sessionId = sessionId,
        )
}
