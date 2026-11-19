package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CgmPresentationPolicyTest {
    private val minute = 60_000L
    private val now = 2_000_000L

    @Test
    fun `transport state cannot turn fresh or aging glucose into signal loss`() {
        assertEquals(CgmPresentationStatus.CURRENT, status(now - 2 * minute))
        assertEquals(CgmPresentationStatus.AGING, status(now - 8 * minute))
    }

    @Test
    fun `stale and actual signal loss remain separate states`() {
        assertEquals(CgmPresentationStatus.STALE, status(now - 13 * minute))
        assertEquals(CgmPresentationStatus.SIGNAL_LOSS, status(now - 16 * minute))
    }

    @Test
    fun `sensor error and no source have deterministic precedence`() {
        assertEquals(CgmPresentationStatus.SENSOR_ERROR, status(now, quality = CgmQuality.SENSOR_ERROR))
        assertEquals(
            CgmPresentationStatus.NO_SOURCE,
            CgmPresentationPolicy.classify(null, CgmQuality.VALID, nowEpochMs = now),
        )
    }

    private fun status(
        measuredAtEpochMs: Long?,
        quality: CgmQuality = CgmQuality.VALID,
    ) = CgmPresentationPolicy.classify(measuredAtEpochMs, quality, now)
}
