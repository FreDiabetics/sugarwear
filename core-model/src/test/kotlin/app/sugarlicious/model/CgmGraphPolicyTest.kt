package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CgmGraphPolicyTest {
    private val minute = 60_000L

    @Test
    fun `first valid low reading activates low`() {
        assertEquals(RangeExcursion.LOW, CgmGraphPolicy.rangeExcursion(listOf(sample(1, 65.0)), 70.0, 180.0))
        assertEquals(
            RangeExcursion.LOW,
            CgmGraphPolicy.rangeExcursion(listOf(sample(1, 65.0), sample(6, 60.0)), 70.0, 180.0),
        )
    }

    @Test
    fun `first valid high reading activates high`() {
        assertEquals(RangeExcursion.HIGH, CgmGraphPolicy.rangeExcursion(listOf(sample(1, 190.0)), 70.0, 180.0))
        assertEquals(
            RangeExcursion.HIGH,
            CgmGraphPolicy.rangeExcursion(listOf(sample(1, 190.0), sample(6, 200.0)), 70.0, 180.0),
        )
    }

    @Test
    fun `return in range clears an excursion`() {
        assertNull(
            CgmGraphPolicy.rangeExcursion(
                listOf(sample(1, 60.0), sample(6, 62.0), sample(11, 110.0)),
                70.0,
                180.0,
            ),
        )
    }

    @Test
    fun `duplicate and invalid events do not count but recovered history does`() {
        val firstHigh = sample(1, 190.0)
        val duplicate = firstHigh.copy(sequenceNumber = 99, receivedAtEpochMs = 2 * minute)
        val invalid = sample(3, 110.0, quality = CgmQuality.INVALID)
        val backfill = sample(0, 200.0).copy(receivedAtEpochMs = 4 * minute)
        val secondHigh = sample(6, 195.0).copy(receivedAtEpochMs = 6 * minute)

        assertEquals(
            RangeExcursion.HIGH,
            CgmGraphPolicy.rangeExcursion(listOf(firstHigh, duplicate, invalid, backfill), 70.0, 180.0),
        )
        assertEquals(
            RangeExcursion.HIGH,
            CgmGraphPolicy.rangeExcursion(listOf(firstHigh, duplicate, invalid, backfill, secondHigh), 70.0, 180.0),
        )
    }

    @Test
    fun `persisted consecutive values tint immediately after reconnect regardless of arrival order`() {
        val olderHigh = sample(1, 190.0).copy(receivedAtEpochMs = 20 * minute)
        val newerHigh = sample(6, 195.0).copy(receivedAtEpochMs = 10 * minute)

        assertEquals(
            RangeExcursion.HIGH,
            CgmGraphPolicy.rangeExcursion(listOf(newerHigh, olderHigh), 70.0, 180.0),
        )
    }

    @Test
    fun `invalid points are ignored while gaps and session changes do not delay current tint`() {
        assertEquals(
            RangeExcursion.LOW,
            CgmGraphPolicy.rangeExcursion(
                listOf(sample(1, 60.0), sample(6, 61.0, quality = CgmQuality.INVALID)),
                70.0,
                180.0,
            ),
        )
        assertEquals(
            RangeExcursion.LOW,
            CgmGraphPolicy.rangeExcursion(listOf(sample(1, 60.0), sample(10, 61.0)), 70.0, 180.0),
        )
        assertEquals(
            RangeExcursion.LOW,
            CgmGraphPolicy.rangeExcursion(
                listOf(sample(1, 60.0, session = "one"), sample(6, 61.0, session = "two")),
                70.0,
                180.0,
            ),
        )
    }

    @Test
    fun `latest valid source value determines tint without confirmation`() {
        val phone =
            sample(1, 60.0, source = DataSourceId.ANDROID_APS).copy(
                sensorId = null,
                sessionId = null,
            )
        val watch = sample(6, 61.0, source = DataSourceId.DEXCOM_G7_WATCH)

        assertEquals(RangeExcursion.LOW, CgmGraphPolicy.rangeExcursion(listOf(phone, watch), 70.0, 180.0))
    }

    @Test
    fun `latest event determines tint even if backfill arrives last and opposite range precedes it`() {
        assertEquals(
            RangeExcursion.HIGH,
            CgmGraphPolicy.rangeExcursion(listOf(sample(6, 190.0), sample(1, 60.0)), 70.0, 180.0),
        )
        assertNull(CgmGraphPolicy.rangeExcursion(listOf(sample(1, Double.NaN), sample(6, 9999.0)), 70.0, 180.0))
    }

    private fun sample(
        minutes: Long,
        value: Double,
        session: String = "session",
        quality: CgmQuality = CgmQuality.VALID,
        source: DataSourceId = DataSourceId.DEXCOM_G7_WATCH,
    ) = GlucoseSample(
        valueMgDl = value,
        measuredAtEpochMs = minutes * minute,
        source = source,
        sensorId = "sensor",
        sessionId = session,
        sequenceNumber = minutes,
        receivedAtEpochMs = minutes * minute,
        quality = quality,
    )
}
