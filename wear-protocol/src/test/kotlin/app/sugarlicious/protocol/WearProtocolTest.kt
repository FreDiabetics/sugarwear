package app.sugarlicious.protocol

import app.sugarlicious.model.*
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.LoopState
import app.sugarlicious.model.TherapyHistorySample
import kotlin.test.*

class WearProtocolTest {
    @Test fun diagnosticBatchRoundTripsStableErrorCodes() {
        val event = DiagnosticEvent("id", 123L, "WATCH", "PREDICTION", "PRED-201", DiagnosticSeverity.WARNING, "Cache retained")
        val decoded = WearProtocol.decodeDiagnostics(WearProtocol.encodeDiagnostics(DiagnosticBatch(listOf(event), 124L)))
        assertEquals(listOf(event), decoded.events)
    }

    @Test fun roundTrip() {
        val s = TherapyDisplayState(receivedAtEpochMs = 2, glucose = GlucoseState(100.0, GlucoseUnit.MG_DL, measuredAtEpochMs = 1))
        assertEquals(s, WearProtocol.decode(WearProtocol.encode(s)))
    }

    @Test fun `trend provenance survives phone to watch transport`() {
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = 2,
                glucose =
                    GlucoseState(
                        100.0,
                        GlucoseUnit.MG_DL,
                        trend = Trend.SINGLE_UP,
                        measuredAtEpochMs = 1,
                        trendOrigin = ValueProvenance.DERIVED,
                    ),
            )

        assertEquals(ValueProvenance.DERIVED, WearProtocol.decode(WearProtocol.encode(state)).glucose?.trendOrigin)
    }

    @Test fun `AndroidAPS display semantics and signed values round trip unchanged`() {
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = 2,
                therapyBridgeDisplaySemantics = TherapyBridgeDisplaySemantics(glucoseUnit = GlucoseUnit.MMOL_L),
                insulin = InsulinState(totalIob = -0.1, bolusIob = 0.0, basalIob = -0.0),
                carbs = CarbState(cobGrams = 0.0, futureCarbsGrams = 5.0),
                basal = BasalState(currentUnitsPerHour = 0.9, tempAbsoluteUnitsPerHour = 1.1),
                pump = PumpState(status = "OK", reservoirUnits = 120.4, batteryPercent = 75),
                profile = ProfileState(name = "Default", diaHours = 5.0),
            )

        assertEquals(state, WearProtocol.decode(WearProtocol.encode(state)))
    }

    @Test fun `state envelope identifies the measurement and transport generation`() {
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = 2,
                glucose = GlucoseState(100.0, GlucoseUnit.MG_DL, measuredAtEpochMs = 1, sensorId = "s", sessionId = "x"),
            )
        val envelope = WearProtocol.decodeEnvelope(WearProtocol.encode(state, generatedAtEpochMs = 3))
        assertEquals(3, envelope.generatedAtEpochMs)
        assertEquals(DataSourceId.ANDROID_APS, envelope.source)
        assertEquals("s", envelope.sensorId)
        assertEquals("x", envelope.sessionId)
        assertTrue(envelope.eventId.contains(":1:2"))
    }

    @Test fun `event identity changes for therapy only canonical revisions`() {
        val first = TherapyDisplayState(receivedAtEpochMs = 2L, canonicalRevision = 7L)
        val second = first.copy(canonicalRevision = 8L, sourceContract = "therapy-only")

        assertNotEquals(WearEnvelope.eventIdFor(first), WearEnvelope.eventIdFor(second))
    }

    @Test fun `transport payload is bounded and retains newest state`() {
        val now = 2_000_000_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    (0 until 600).map {
                        GlucoseSample(
                            100.0 + it % 40,
                            now - (599 - it) * 300_000L,
                            sensorId = "sensor-very-long-identifier",
                            sessionId = "session-very-long-identifier",
                        )
                    },
                therapyHistory =
                    (0 until 600).map {
                        TherapyHistorySample(
                            now - (599 - it) * 300_000L,
                            totalIob = it / 100.0,
                            cobGrams = it.toDouble(),
                            basalUnitsPerHour = 0.7,
                        )
                    },
                loop = LoopState(suggestedPayload = "s".repeat(120_000), enactedPayload = "e".repeat(120_000)),
            )

        val payload = WearProtocol.encodeStateForTransport(state)
        val decoded = WearProtocol.decode(payload)

        assertTrue(payload.size <= WearProtocol.MAX_STATE_PAYLOAD_BYTES)
        assertEquals(123.0, decoded.glucose?.valueMgDl)
        assertEquals(now, decoded.glucoseHistory.last().measuredAtEpochMs)
        assertEquals(null, decoded.loop?.suggestedPayload)
        assertEquals(null, decoded.loop?.enactedPayload)
    }

    @Test fun migratesProtocolOneContractStoredInVersionField() {
        val legacy = """{"protocolVersion":1,"state":{"schemaVersion":1,"source":"ANDROID_APS","sourceVersion":"AAPS_EXTENDED_STATUS_V1","receivedAtEpochMs":2}}"""
        val migrated = WearProtocol.decode(legacy.encodeToByteArray())
        assertEquals(TherapyDisplayState.CURRENT_SCHEMA, migrated.schemaVersion)
        assertEquals("AAPS_EXTENDED_STATUS_V1", migrated.sourceContract)
        assertNull(migrated.sourceVersion)
    }

    @Test fun rejectsFutureProtocol() {
        val future = """{"protocolVersion":999,"state":{"receivedAtEpochMs":2}}"""
        assertFailsWith<IllegalArgumentException> { WearProtocol.decode(future.encodeToByteArray()) }
    }

    @Test fun graphColorsRoundTripWithCurrentWatchConfigSchema() {
        val colors =
            WatchGraphColors(
                graphBackground = 0xFF101010.toInt(),
                rangeLow = 0xFFAA0000.toInt(),
                rangeInRange = 0xFF00AA00.toInt(),
                rangeHigh = 0xFFAAAA00.toInt(),
                cgmLow = 0xFFBB0000.toInt(),
                cgmInRange = 0xFF00BB00.toInt(),
                cgmHigh = 0xFFBBBB00.toInt(),
                divider = 0xFF888888.toInt(),
                highLine = 0xFF887700.toInt(),
                lowLine = 0xFF880022.toInt(),
                axisLabel = 0xFF778899.toInt(),
                axisTick = 0xFF667788.toInt(),
                nowLine = 0xFF556677.toInt(),
                outline = 0xFF121212.toInt(),
            )
        val config = WatchConfig(graphColors = colors, sentAtEpochMs = 123)
        assertEquals(config, WearProtocol.decodeConfig(WearProtocol.encodeConfig(config)))
    }

    @Test fun `current and historical dot outlines round trip independently`() {
        val style =
            WatchGraphStyle(
                cgmHistoricalDotOutlineEnabled = false,
                cgmCurrentDotOutlineEnabled = true,
                cgmHistoricalDotOutlineWidthDp = 0.45f,
                cgmCurrentDotOutlineWidthDp = 1.85f,
            )
        val config = WatchConfig(graphStyle = style)
        assertEquals(style, WearProtocol.decodeConfig(WearProtocol.encodeConfig(config)).graphStyle)
    }

    @Test fun `legacy common dot outline width migrates into both independent widths`() {
        val legacy =
            """{"schemaVersion":10,"graphStyle":{"cgmDotOutlineWidthDp":1.65,"cgmCurrentDotOutlineEnabled":false}}"""

        val style = WearProtocol.decodeConfig(legacy.encodeToByteArray()).graphStyle

        assertEquals(1.65f, style.cgmHistoricalDotOutlineWidthDp)
        assertEquals(1.65f, style.cgmCurrentDotOutlineWidthDp)
    }

    @Test fun legacyWatchConfigUsesDefaultBasalColor() {
        val legacy = """{"schemaVersion":4,"uiColors":{}}"""
        val decoded = WearProtocol.decodeConfig(legacy.encodeToByteArray())
        assertEquals(WatchUiColors().basal, decoded.uiColors.basal)
    }

    @Test fun cgmSetupAndDataSourceRoundTrip() {
        val command = CgmSetupCommand("1234", "SERIAL", "00386270000000")
        assertEquals(command, WearProtocol.decodeCgmSetup(WearProtocol.encodeCgmSetup(command)))
        val config = WatchConfig(dataSource = WatchDataSource.DEXCOM_G7_WATCH)
        assertEquals(WatchDataSource.DEXCOM_G7_WATCH, WearProtocol.decodeConfig(WearProtocol.encodeConfig(config)).dataSource)
        assertFailsWith<IllegalArgumentException> { CgmSetupCommand("12") }
    }

    @Test fun runtimeStatusAllowsSixthSugarliciousFace() {
        val sixth = WatchRuntimeStatus(activeSugarliciousFaceIndex = 5, activeComplicationIds = listOf(1, 1, 2))
        val decoded = WearProtocol.decodeRuntimeStatus(WearProtocol.encodeRuntimeStatus(sixth))
        assertEquals(5, decoded.activeSugarliciousFaceIndex)
        assertEquals(listOf(1, 2), decoded.activeComplicationIds)
    }

    @Test fun versionedWatchColorsRoundTrip() {
        val colors = WatchGraphColors(targetValue = 0xFF123456.toInt(), signalLoss = 0x44112233)
        val sync = WatchColorSync(graphColors = colors, sentAtEpochMs = 500L)
        assertEquals(sync, WearProtocol.decodeWatchColorSync(WearProtocol.encodeWatchColorSync(sync)))
    }
}
