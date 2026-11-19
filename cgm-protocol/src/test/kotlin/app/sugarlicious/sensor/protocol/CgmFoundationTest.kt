package app.sugarlicious.sensor.protocol

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Trend
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CgmFoundationTest {
    private val now = 1_800_000_000_000L

    private fun reading(
        value: Double,
        at: Long = now,
        id: String = value.toString(),
    ) = CgmReading(
        id,
        DataSourceId.DEXCOM_G7_WATCH,
        "sensor",
        "session",
        value,
        at,
        at,
    )

    @Test fun `delta accepts only adjacent sensor readings`() {
        assertEquals(4.0, CgmDeltaCalculator.calculate(reading(112.0), reading(108.0, now - 5 * 60_000L)))
        assertNull(CgmDeltaCalculator.calculate(reading(112.0), reading(108.0, now - 20 * 60_000L)))
    }

    @Test fun `reading identity is independent of transport sequence`() {
        fun converted(sequence: Long) =
            SensorProtocolReading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = sequence,
                glucoseMgDl = 123.0,
                sensorTimestampEpochMs = now,
                receivedAtEpochMs = now,
                sensorState = CgmSensorState.ACTIVE,
            ).toCgm()
        assertEquals(
            converted(2_423L).id,
            converted(2_420L).id,
        )
        assertTrue(CgmReadingIdentity.create("sensor", "session", now) != CgmReadingIdentity.create("sensor", "session", now + 300_000L))
    }

    @Test fun `delta rejects sensor errors and implausible glucose values`() {
        val sensorError = reading(0.0, now - 5 * 60_000L).copy(status = CgmReadingStatus.SENSOR_ERROR)
        assertNull(CgmDeltaCalculator.calculate(reading(112.0), sensorError))
        assertNull(CgmDeltaCalculator.calculate(reading(112.0), reading(Double.NaN, now - 5 * 60_000L)))
        assertNull(CgmDeltaCalculator.calculate(reading(112.0), reading(1_001.0, now - 5 * 60_000L)))
    }

    @Test fun `CGM conversion marks implausible values invalid without inventing a replacement`() {
        val converted =
            SensorProtocolReading(
                sensorId = "sensor",
                sessionId = "session",
                sequenceNumber = 1L,
                glucoseMgDl = 14.0,
                sensorTimestampEpochMs = now,
                receivedAtEpochMs = now,
                sensorState = CgmSensorState.ACTIVE,
            ).toCgm()

        assertEquals(CgmReadingStatus.INVALID, converted.status)
        assertEquals(14.0, converted.glucoseMgDl)
    }

    @Test fun `phone loss never changes watch ownership`() {
        val manager = CollectorOwnershipManager(CollectorOwner.WATCH)
        assertEquals(CollectorOwner.WATCH, manager.phoneDisconnected())
    }

    @Test fun `fresh setup clears stale errors retries and readings`() {
        val stale =
            CgmPersistedState(
                sensor = CgmSensor("old"),
                collectorEnabled = true,
                connectionState = CgmConnectionState.DISCONNECTED,
                protocolState = CgmProtocolState.ERROR,
                sessionState = CgmSessionState.RECOVERING,
                authenticationState = CgmAuthenticationState.FAILED,
                lastReading = reading(99.0),
                lastSuccessfulConnectionEpochMs = now - 60_000L,
                nextReconnectEpochMs = now + 60_000L,
                retryCount = 5,
                lastError = CgmCollectorError("G7-BLE-107", true, now, "not found"),
            )
        val state = CgmSessionManager(stale).beginInitialSetup(CgmSensor("new"))
        assertEquals(CgmConnectionState.SCANNING, state.connectionState)
        assertEquals(CgmProtocolState.SCANNING, state.protocolState)
        assertEquals(CgmSessionState.INITIAL_SETUP, state.sessionState)
        assertEquals(CgmAuthenticationState.REQUIRED, state.authenticationState)
        assertEquals(0, state.retryCount)
        assertNull(state.lastError)
        assertNull(state.lastReading)
        assertNull(state.lastSuccessfulConnectionEpochMs)
        assertNull(state.nextReconnectEpochMs)
    }

    @Test fun `entering a sensor code can prepare setup without enabling scan`() {
        val stale =
            CgmPersistedState(
                sensor = CgmSensor("old"),
                collectorEnabled = true,
                protocolState = CgmProtocolState.ERROR,
                sessionState = CgmSessionState.RECOVERING,
                nextReconnectEpochMs = now + 60_000L,
                retryCount = 4,
            )
        val prepared = CgmSessionManager(stale).prepareInitialSetup(CgmSensor("new"))
        assertFalse(prepared.collectorEnabled)
        assertEquals(CgmConnectionState.DISCONNECTED, prepared.connectionState)
        assertEquals(CgmProtocolState.IDLE, prepared.protocolState)
        assertEquals(CgmSessionState.UNINITIALIZED, prepared.sessionState)
        assertNull(prepared.nextReconnectEpochMs)
        assertEquals(0, prepared.retryCount)
    }

    @Test fun `explicit collector start enables prepared sensor`() {
        val prepared = CgmSessionManager().prepareInitialSetup(CgmSensor("sensor"))
        val started = CgmSessionManager(prepared).startCollector()
        assertTrue(started.collectorEnabled)
        assertEquals(CollectorOwner.WATCH, started.collectorOwner)
        assertEquals(CgmSessionState.INITIAL_SETUP, started.sessionState)
        assertEquals(CgmProtocolState.IDLE, started.protocolState)
    }

    @Test fun `collector stop prevents scheduled reconnect but keeps sensor configuration`() {
        val configured =
            CgmPersistedState(
                sensor = CgmSensor("sensor"),
                collectorEnabled = true,
                protocolState = CgmProtocolState.WAITING_FOR_NEXT_READING,
                sessionState = CgmSessionState.WAITING_FOR_NEXT_READING,
                nextReconnectEpochMs = now + 60_000L,
                retryCount = 3,
            )
        val stopped = CgmSessionManager(configured).stop()
        assertFalse(stopped.collectorEnabled)
        assertEquals("sensor", stopped.sensor?.sensorId)
        assertEquals(CgmProtocolState.IDLE, stopped.protocolState)
        assertEquals(CgmSessionState.UNINITIALIZED, stopped.sessionState)
        assertNull(stopped.nextReconnectEpochMs)
        assertEquals(0, stopped.retryCount)
    }

    @Test fun `reading schedules autonomous reconnect and clears retries`() {
        val manager = CgmSessionManager(CgmPersistedState(collectorEnabled = true, retryCount = 4))
        val state = manager.readingReceived(reading(112.0))
        assertEquals(CgmSessionState.WAITING_FOR_NEXT_READING, state.sessionState)
        assertEquals(0, state.retryCount)
        assertEquals(now + 270_000L, state.nextReconnectEpochMs)
    }

    @Test fun `sensor error packet preserves last valid reading and schedules its next window`() {
        val lastValid = reading(112.0, now - 5 * 60_000L, "valid")
        val sensorError =
            reading(0.0, now, "sensor-error").copy(status = CgmReadingStatus.SENSOR_ERROR)

        val state =
            CgmSessionManager(CgmPersistedState(collectorEnabled = true, lastReading = lastValid))
                .readingReceived(sensorError)

        assertEquals(lastValid, state.lastReading)
        assertEquals(now + 270_000L, state.nextReconnectEpochMs)
    }

    @Test fun `recovery is bounded and eventually requires user`() {
        val manager = CgmSessionManager(CgmPersistedState(collectorEnabled = true))
        repeat(12) { index -> manager.failure(CgmCollectorError("AUTH_$index", true, now + index, "Authentication unavailable")) }
        assertEquals(CgmSessionState.USER_INTERVENTION_REQUIRED, manager.state.sessionState)
        assertTrue(manager.state.retryCount <= 10)
    }

    @Test fun `direct and fallback window misses retain cadence from last real reading`() {
        val lastReading = reading(112.0, now - 60_000L, "last")
        val expectedReconnect = now + 210_000L
        val windowErrors =
            listOf(
                "G7-GATT-133",
                "G7-GATT-215",
                "G7-BLE-107",
                "G7-BLE-111",
                "G7-BLE-FALLBACK-107",
            )

        windowErrors.forEach { code ->
            val state =
                CgmSessionManager(
                    CgmPersistedState(
                        collectorEnabled = true,
                        lastReading = lastReading,
                        retryCount = 4,
                    ),
                ).failure(CgmCollectorError(code, true, now, "window miss"))

            assertEquals(expectedReconnect, state.nextReconnectEpochMs, code)
            assertEquals(0, state.retryCount, code)
            assertEquals(CgmSessionState.RECOVERING, state.sessionState, code)
        }
    }

    @Test fun `non recoverable collector errors never create an endless retry`() {
        val manager = CgmSessionManager(CgmPersistedState(collectorEnabled = true, retryCount = 2))
        val state = manager.failure(CgmCollectorError("G7-AUTH-211", false, now, "Rebond required"))
        assertEquals(CgmSessionState.USER_INTERVENTION_REQUIRED, state.sessionState)
        assertNull(state.nextReconnectEpochMs)
    }

    @Test fun `watch collector selects alternate KEKS auth slot`() {
        assertContentEquals(byteArrayOf(0x00), CgmReceiverSlot.PRIMARY.keksPersistence6())
        assertContentEquals(byteArrayOf(0x02), CgmReceiverSlot.WATCH_ALTERNATE.keksPersistence6())
    }

    @Test fun `high alarm is not duplicated and resolves with hysteresis`() {
        val settings = alarmSettings()
        val first = CgmAlarmEngine.evaluate(reading(181.0), emptyMap(), settings, now)
        val sameAlarm = CgmAlarmEngine.evaluate(reading(185.0, now + 5 * 60_000L), first, settings, now + 5 * 60_000L)
        assertEquals(first[CgmAlarmType.HIGH]?.triggeredAtEpochMs, sameAlarm[CgmAlarmType.HIGH]?.triggeredAtEpochMs)
        val held = CgmAlarmEngine.evaluate(reading(176.0, now + 10 * 60_000L), sameAlarm, settings, now + 10 * 60_000L)
        assertEquals(CgmAlarmState.ACTIVE, held[CgmAlarmType.HIGH]?.state)
        val resolved = CgmAlarmEngine.evaluate(reading(174.0, now + 15 * 60_000L), held, settings, now + 15 * 60_000L)
        assertEquals(CgmAlarmState.RESOLVED, resolved[CgmAlarmType.HIGH]?.state)
    }

    @Test fun `G7 packet parser accepts validated glucose framing and rejects malformed data`() {
        val packet =
            ByteBuffer
                .allocate(19)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put(0x4e)
                    put(7)
                    putInt(1234)
                    putShort(17)
                    putShort(42)
                    putShort(20)
                    putShort(112)
                    put(0x06)
                    put(12)
                    putShort(118)
                    put(0)
                }.array()
        val reading = CgmGlucosePacketParser().parse(packet, CgmSensor("sensor", "session"), now)
        assertEquals(112.0, reading.glucoseMgDl)
        assertEquals(1.2, reading.trendRateMgDlPerMinute)
        assertEquals(118.0, reading.predictedMgDl)
        assertEquals(now - 20_000L, reading.sensorTimestampEpochMs)
        assertEquals(now - 1_234_000L, reading.sensorStartEpochMs)
        assertEquals(reading.sensorStartEpochMs!! + 10L * 24L * 60L * 60_000L, reading.sensorEndEpochMs)
        assertEquals(reading.sensorEndEpochMs!! + 12L * 60L * 60_000L, reading.graceEndEpochMs)
        assertEquals(7, reading.protocolStatusCode)
        assertEquals(6, reading.calibrationStateCode)
        assertEquals(42, reading.reservedField)
        val fallingPacket = packet.copyOf().also { it[15] = (-16).toByte() }
        assertEquals(-1.6, CgmGlucosePacketParser().parse(fallingPacket, CgmSensor("sensor", "session"), now).trendRateMgDlPerMinute)
        assertFailsWith<IllegalArgumentException> { CgmGlucosePacketParser().parse(byteArrayOf(1), CgmSensor("sensor"), now) }
    }

    @Test fun `setup accepts manual pin and GS1 applicator barcode`() {
        assertEquals("1234", CgmSetupParser.parse("1234")?.pairingCode)
        val barcode = "0100386270000000\u001d2409876\u001d21SERIAL"
        val parsed = CgmSetupParser.parse(barcode)
        assertEquals("9876", parsed?.pairingCode)
        assertEquals("SERIAL", parsed?.sensorSerial)
        assertNull(CgmSetupParser.parse("12"))
    }

    @Test fun `trend mapping uses shared model`() {
        assertEquals(Trend.DOUBLE_UP, CgmTrendMapper.fromRate(3.1))
        assertEquals(Trend.FLAT, CgmTrendMapper.fromRate(0.2))
    }

    private fun alarmSettings() =
        CgmAlarmSettings(
            veryHighThreshold = 250.0,
            highThreshold = 180.0,
            lowThreshold = 70.0,
            veryLowThreshold = 55.0,
            rapidRiseThreshold = 3.0,
            rapidFallThreshold = 3.0,
            signalLossMinutes = 15,
        )
}
