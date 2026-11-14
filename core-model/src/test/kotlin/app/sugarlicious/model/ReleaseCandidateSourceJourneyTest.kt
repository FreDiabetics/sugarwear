package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReleaseCandidateSourceJourneyTest {
    private val minute = 60_000L
    private val base = 2_000_000_000L

    @Test
    fun `mobile watch failover and two-reading recovery form one duplicate-free journey`() {
        var memory = CgmResolverMemory()

        fun resolve(
            now: Long,
            mobile: CgmSourceCandidate?,
            watch: CgmSourceCandidate?,
        ): CgmSourceResolution = CanonicalCgmSourceResolver.resolve(mobile, watch, now, memory).also { memory = it.memory }

        val mobile110 = candidate(CgmCanonicalSource.MOBILE_AAPS, 110.0, base, 1)
        val mobile112 = candidate(CgmCanonicalSource.MOBILE_AAPS, 112.0, base + 5 * minute, 2)
        val watch111 = candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 111.0, base, 1)
        val watch113 = candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 113.0, base + 5 * minute, 2)

        val first = resolve(base + minute, mobile110, watch111)
        assertResolution(first, CgmSourceState.MOBILE_PRIMARY, CgmCanonicalSource.MOBILE_AAPS, 110.0)
        val second = resolve(base + 6 * minute, mobile112, watch113)
        assertResolution(second, CgmSourceState.MOBILE_PRIMARY, CgmCanonicalSource.MOBILE_AAPS, 112.0)

        val watch119 = candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 119.0, base + 20 * minute, 5)
        val failover = resolve(base + 21 * minute, mobile112, watch119)
        assertResolution(failover, CgmSourceState.WATCH_DIRECT, CgmCanonicalSource.WATCH_G7_DIRECT, 119.0)

        val watch121 = candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 121.0, base + 25 * minute, 6)
        val mobile120 = candidate(CgmCanonicalSource.MOBILE_AAPS, 120.0, base + 25 * minute, 6)
        val recoveryOne = resolve(base + 26 * minute, mobile120, watch121)
        assertResolution(recoveryOne, CgmSourceState.MOBILE_RECOVERY, CgmCanonicalSource.WATCH_G7_DIRECT, 121.0)
        assertEquals(1, recoveryOne.memory.recoveryReadingCount)

        val duplicateRecovery = resolve(base + 27 * minute, mobile120, watch121)
        assertEquals(CgmSourceState.MOBILE_RECOVERY, duplicateRecovery.state)
        assertEquals(1, duplicateRecovery.memory.recoveryReadingCount)

        val mobile122 = candidate(CgmCanonicalSource.MOBILE_AAPS, 122.0, base + 30 * minute, 7)
        val watch123 = candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 123.0, base + 30 * minute, 7)
        val recovered = resolve(base + 31 * minute, mobile122, watch123)
        assertResolution(recovered, CgmSourceState.MOBILE_PRIMARY, CgmCanonicalSource.MOBILE_AAPS, 122.0)

        val history =
            CanonicalCgmHistory.merge(
                listOf(
                    sample(mobile110),
                    sample(watch111),
                    sample(mobile112),
                    sample(watch113),
                    sample(watch119),
                    sample(mobile120),
                    sample(watch121),
                    sample(mobile122),
                    sample(watch123),
                ),
                nowEpochMs = base + 31 * minute,
                preferredSource = DataSourceId.ANDROID_APS,
            )
        assertEquals(history.map { it.measuredAtEpochMs }.distinct(), history.map { it.measuredAtEpochMs })
        assertEquals(DataSourceId.ANDROID_APS, history.single { it.measuredAtEpochMs == base }.source)
        assertEquals(DataSourceId.ANDROID_APS, history.single { it.measuredAtEpochMs == base + 30 * minute }.source)
        assertFalse(history.zipWithNext().any { (left, right) -> left.measuredAtEpochMs > right.measuredAtEpochMs })
    }

    @Test
    fun `stale sources session changes duplicates and out of order input remain safe`() {
        val bothStale =
            CanonicalCgmSourceResolver.resolve(
                candidate(CgmCanonicalSource.MOBILE_AAPS, 110.0, base, 1),
                candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 111.0, base, 1),
                base + 20 * minute,
            )
        assertEquals(CgmSourceState.NO_SOURCE, bothStale.state)

        val oldSession = sample(candidate(CgmCanonicalSource.WATCH_G7_DIRECT, 111.0, base + 5 * minute, 2))
        val newSession = oldSession.copy(valueMgDl = 112.0, sessionId = "session-2", sequenceNumber = 1)
        val duplicate = oldSession.copy(receivedAtEpochMs = base + 7 * minute)
        val merged =
            CanonicalCgmHistory.merge(
                listOf(newSession, duplicate, oldSession),
                nowEpochMs = base + 10 * minute,
            )
        assertEquals(2, merged.size)
        assertEquals(listOf("session-1", "session-2"), merged.mapNotNull { it.sessionId }.sorted())
    }

    private fun assertResolution(
        result: CgmSourceResolution,
        state: CgmSourceState,
        source: CgmCanonicalSource,
        value: Double,
    ) {
        assertEquals(state, result.state)
        assertEquals(source, result.canonicalSource)
        assertEquals(value, result.reading?.glucoseMgDl)
        assertEquals(result.reading?.measuredAtEpochMs, result.reading?.receivedAtEpochMs?.minus(1_000L))
    }

    private fun candidate(
        source: CgmCanonicalSource,
        value: Double,
        measuredAt: Long,
        sequence: Long,
    ) = CgmSourceCandidate(
        source = source,
        glucoseMgDl = value,
        measuredAtEpochMs = measuredAt,
        receivedAtEpochMs = measuredAt + 1_000L,
        sensorId = "sensor-1",
        sessionId = "session-1",
        sequenceNumber = sequence,
    )

    private fun sample(candidate: CgmSourceCandidate) =
        GlucoseSample(
            valueMgDl = candidate.glucoseMgDl,
            measuredAtEpochMs = candidate.measuredAtEpochMs,
            receivedAtEpochMs = candidate.receivedAtEpochMs,
            source = if (candidate.source == CgmCanonicalSource.WATCH_G7_DIRECT) DataSourceId.DEXCOM_G7_WATCH else DataSourceId.ANDROID_APS,
            sensorId = candidate.sensorId,
            sessionId = candidate.sessionId,
            sequenceNumber = candidate.sequenceNumber,
        )
}
