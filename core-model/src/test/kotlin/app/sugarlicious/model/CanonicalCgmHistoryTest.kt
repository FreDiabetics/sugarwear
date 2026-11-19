package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalCgmHistoryTest {
    private val now = 2_000_000L

    @Test
    fun `phone wins for one real measurement while different sessions and sensors remain distinct`() {
        val watch = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor-a", "session-a", 7L, 120.0, now - 60_000L)
        val phone = watch.copy(source = DataSourceId.ANDROID_APS, receivedAtEpochMs = now)
        val newSession = watch.copy(sensorId = "sensor-b", sessionId = "session-b", receivedAtEpochMs = now)

        val merged =
            CanonicalCgmHistory.merge(
                listOf(watch, newSession, phone),
                now,
                preferredSource = DataSourceId.ANDROID_APS,
            )

        assertEquals(2, merged.size)
        assertEquals(DataSourceId.ANDROID_APS, merged.single { it.sessionId == "session-a" }.source)
        assertEquals(DataSourceId.DEXCOM_G7_WATCH, merged.single { it.sessionId == "session-b" }.source)
    }

    @Test
    fun `out of order input is sorted and invalid future and stale samples are excluded`() {
        val validOld = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 1L, 110.0, now - 10 * 60_000L)
        val validNew = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 2L, 115.0, now - 5 * 60_000L)
        val invalid = validNew.copy(sequenceNumber = 3L, quality = CgmQuality.INVALID)
        val future = validNew.copy(sequenceNumber = 4L, measuredAtEpochMs = now + 6 * 60_000L)
        val stale = validOld.copy(sequenceNumber = 5L, measuredAtEpochMs = now - CanonicalCgmHistory.DEFAULT_WINDOW_MS - 1L)
        val impossibleReceipt = validNew.copy(sequenceNumber = 6L, receivedAtEpochMs = now + 6 * 60_000L)

        assertEquals(
            listOf(validOld, validNew),
            CanonicalCgmHistory.merge(listOf(validNew, invalid, future, stale, impossibleReceipt, validOld), now),
        )
    }

    @Test
    fun `same source correction at one timestamp replaces the older received value`() {
        val first = sample(DataSourceId.ANDROID_APS, "sensor", "session", null, 120.0, now - 60_000L)
        val corrected = first.copy(valueMgDl = 125.0, receivedAtEpochMs = now)

        assertEquals(listOf(corrected), CanonicalCgmHistory.merge(listOf(first, corrected), now))
    }

    @Test
    fun `phone remains the historical winner while live resolver prefers watch`() {
        val watch = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 8L, 121.0, now - 60_000L)
        val phone = watch.copy(source = DataSourceId.ANDROID_APS, receivedAtEpochMs = now - 10_000L)

        val merged =
            CanonicalCgmHistory.merge(
                listOf(phone, watch.copy(receivedAtEpochMs = now)),
                now,
                preferredSource = DataSourceId.DEXCOM_G7_WATCH,
            )

        assertEquals(listOf(phone), merged)
    }

    @Test
    fun `timestamp tolerant phone duplicate wins and distinct watch gap is retained`() {
        val watchDuplicate = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", null, 120.0, now - 61_000L)
        val phone =
            watchDuplicate.copy(
                source = DataSourceId.ANDROID_APS,
                valueMgDl = 122.0,
                measuredAtEpochMs = now - 60_000L,
                receivedAtEpochMs = now - 20_000L,
            )
        val watchGap = watchDuplicate.copy(valueMgDl = 114.0, measuredAtEpochMs = now - 6 * 60_000L)

        val merged = CanonicalCgmHistory.merge(listOf(watchDuplicate, watchGap, phone), now)

        assertEquals(2, merged.size)
        assertEquals(DataSourceId.DEXCOM_G7_WATCH, merged.first().source)
        assertEquals(DataSourceId.ANDROID_APS, merged.last().source)
    }

    @Test
    fun `same session readings seconds apart collapse despite differing sequence metadata`() {
        val historyCopy = sample(DataSourceId.ANDROID_APS, "sensor", "session", 40L, 121.0, now - 61_000L)
        val liveCopy =
            historyCopy.copy(
                sequenceNumber = 41L,
                valueMgDl = 122.0,
                measuredAtEpochMs = now - 45_000L,
                receivedAtEpochMs = now,
            )

        assertEquals(listOf(liveCopy), CanonicalCgmHistory.merge(listOf(historyCopy, liveCopy), now))
    }

    @Test
    fun `regular five minute readings remain distinct`() {
        val previous = sample(DataSourceId.ANDROID_APS, "sensor", "session", 40L, 121.0, now - 5 * 60_000L)
        val current = sample(DataSourceId.ANDROID_APS, "sensor", "session", 41L, 122.0, now)

        assertEquals(listOf(previous, current), CanonicalCgmHistory.merge(listOf(previous, current), now))
    }

    @Test
    fun `same sequence at different measurement times remains distinct`() {
        val previous = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 42L, 121.0, now - 5 * 60_000L)
        val current = previous.copy(valueMgDl = 122.0, measuredAtEpochMs = now, receivedAtEpochMs = now)

        assertEquals(listOf(previous, current), CanonicalCgmHistory.merge(listOf(previous, current), now))
    }

    @Test
    fun `canonical identity is shared and never uses sequence as reading identity`() {
        val first = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 42L, 121.0, now - 5 * 60_000L)
        val sameMeasurement = first.copy(sequenceNumber = 99L, receivedAtEpochMs = now)
        val reusedSequence = first.copy(valueMgDl = 122.0, measuredAtEpochMs = now, receivedAtEpochMs = now)

        assertEquals(true, CanonicalCgmIdentity.sameMeasurement(first, sameMeasurement))
        assertEquals(false, CanonicalCgmIdentity.sameMeasurement(first, reusedSequence))
    }

    @Test
    fun `unknown cross source identities remain distinct`() {
        val phone = sample(DataSourceId.ANDROID_APS, "sensor", "session", null, 121.0, now - 60_000L).copy(sensorId = null, sessionId = null)
        val watch = phone.copy(source = DataSourceId.DEXCOM_G7_WATCH)

        assertEquals(2, CanonicalCgmHistory.merge(listOf(phone, watch), now).size)
    }

    @Test
    fun `delivery order cannot change canonical live backfill result`() {
        val older = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 10L, 110.0, now - 5 * 60_000L)
        val backfillCopy = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 11L, 120.0, now - 60_000L)
        val liveCopy = backfillCopy.copy(sequenceNumber = 12L, receivedAtEpochMs = now)
        val replacementSession =
            liveCopy.copy(
                sensorId = "sensor-2",
                sessionId = "session-2",
                sequenceNumber = 1L,
                valueMgDl = 130.0,
            )

        listOf(older, backfillCopy, liveCopy, replacementSession).permutations().forEach { deliveryOrder ->
            val merged = CanonicalCgmHistory.merge(deliveryOrder, now)

            assertEquals(listOf(now - 5 * 60_000L, now - 60_000L, now - 60_000L).sorted(), merged.map { it.measuredAtEpochMs })
            assertEquals(listOf("session", "session", "session-2"), merged.mapNotNull { it.sessionId })
            assertEquals(12L, merged.single { it.sessionId == "session" && it.measuredAtEpochMs == now - 60_000L }.sequenceNumber)
        }
    }

    @Test
    fun `time index follows a preferred phone timestamp for later duplicates`() {
        val first = sample(DataSourceId.DEXCOM_G7_WATCH, "sensor", "session", 1L, 120.0, now - 180_000L)
        val phone = first.copy(source = DataSourceId.ANDROID_APS, measuredAtEpochMs = now - 120_000L, receivedAtEpochMs = now)
        val later = first.copy(measuredAtEpochMs = now - 50_000L, receivedAtEpochMs = now)
        val otherSensor = later.copy(sensorId = "other", sessionId = "other")
        assertEquals(listOf(phone, otherSensor), CanonicalCgmHistory.merge(listOf(later, otherSensor, first, phone), now))
    }

    private fun sample(
        source: DataSourceId,
        sensor: String,
        session: String,
        sequence: Long?,
        value: Double,
        timestamp: Long,
    ) = GlucoseSample(
        valueMgDl = value,
        measuredAtEpochMs = timestamp,
        source = source,
        sensorId = sensor,
        sessionId = session,
        sequenceNumber = sequence,
        receivedAtEpochMs = timestamp + 1_000L,
    )

    private fun <T> List<T>.permutations(): List<List<T>> =
        if (size <= 1) {
            listOf(this)
        } else {
            indices.flatMap { index ->
                val selected = this[index]
                (take(index) + drop(index + 1)).permutations().map { listOf(selected) + it }
            }
        }
}
