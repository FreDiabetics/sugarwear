package app.aapswear.g7watch

import app.aapswear.g7.CollectorCycleClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class G7ReliabilityPolicyTest {
    @Test
    fun `expected window identity includes sensor and session`() {
        val slot = 1_000_000L
        assertNotEquals(
            expectedWindowId("sensor-a", "session-a", slot),
            expectedWindowId("sensor-b", "session-b", slot),
        )
        assertEquals(
            expectedWindowId("sensor-a", "session-a", slot),
            expectedWindowId("sensor-a", "session-a", slot),
        )
    }

    @Test
    fun `rehydration reconstructs only active sensor interval`() {
        assertEquals(
            listOf(600_000L, 900_000L, 1_200_000L),
            missingExpectedSlots(
                fromExpectedAt = 300_000L,
                untilExclusive = 1_500_000L,
                sensorStartAt = 600_000L,
                sensorEndAt = 1_300_000L,
            ),
        )
    }

    @Test
    fun `retention covers at least seven full days`() {
        assertTrue(g7SlotRetentionDurationMs() >= 7L * 24L * 60L * 60_000L)
    }

    @Test
    fun `callback timeout phases remain diagnosable`() {
        val codes =
            listOf(
                G7_DIRECT_CONNECT_TIMEOUT_ERROR_CODE,
                G7_DISCOVERY_CALLBACK_TIMEOUT_ERROR_CODE,
                G7_DESCRIPTOR_CALLBACK_TIMEOUT_ERROR_CODE,
                G7_WRITE_CALLBACK_TIMEOUT_ERROR_CODE,
            )
        assertEquals(codes.size, codes.distinct().size)
        codes.forEach { code ->
            assertEquals(
                CollectorCycleClassification.GATT_NO_CALLBACK,
                classifyG7CycleFailure(code, null),
            )
        }
    }
}
