package app.sugarlicious.datasource.therapybridge
import app.sugarlicious.model.*
import kotlin.test.*

class TherapyBridgePayloadAdapterTest {
    @Test
    fun `marks every supplied AndroidAPS field as source provenance`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 123.0,
                        "glucoseTimeStamp" to 900_000L,
                        "deltaMgdl" to -2.0,
                        "iob" to -0.1,
                        "cob" to 12.0,
                        "baseBasal" to 0.9,
                        "insulinActivity" to 0.001,
                        "low" to 80.0,
                        "profile" to "Default",
                        "pumpStatus" to "connected",
                        "pumpReservoir" to 100.0,
                        "phoneBattery" to 80,
                    ),
                    1_000_000L,
                ),
            )

        listOf(
            CanonicalDataField.GLUCOSE,
            CanonicalDataField.DELTA,
            CanonicalDataField.IOB,
            CanonicalDataField.COB,
            CanonicalDataField.BASAL,
            CanonicalDataField.INSULIN_ACTIVITY,
            CanonicalDataField.TARGET,
            CanonicalDataField.PROFILE,
            CanonicalDataField.PUMP_STATUS,
            CanonicalDataField.RESERVOIR,
            CanonicalDataField.PHONE_BATTERY,
        ).forEach { field -> assertEquals(ValueProvenance.SOURCE, state.provenanceOf(field), field.name) }
        assertEquals(ValueProvenance.UNAVAILABLE, state.provenanceOf(CanonicalDataField.PREDICTIONS))
    }

    @Test
    fun `marks an explicit AndroidAPS trend as source provenance`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L, "slopeArrow" to "DoubleUp"),
                    1_000_000L,
                ),
            )

        assertEquals(ValueProvenance.SOURCE, state.glucose?.trendOrigin)
    }

    @Test
    fun `marks a missing AndroidAPS trend as unavailable provenance`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L),
                    1_000_000L,
                ),
            )

        assertEquals(ValueProvenance.UNAVAILABLE, state.glucose?.trendOrigin)
    }

    @Test
    fun `attaches the AndroidAPS visible unit and precision contract`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 123.0,
                        "glucoseTimeStamp" to 900_000L,
                        "units" to "mmol/L",
                        "iob" to "-0.100",
                        "cob" to "0",
                        "baseBasal" to "0.900",
                    ),
                    1_000_000L,
                ),
            )

        assertEquals(GlucoseUnit.MMOL_L, state.therapyBridgeDisplaySemantics?.glucoseUnit)
        assertEquals(2, state.therapyBridgeDisplaySemantics?.insulinDigits)
        assertEquals(0, state.therapyBridgeDisplaySemantics?.carbDigits)
        assertEquals(2, state.therapyBridgeDisplaySemantics?.basalDigits)
        assertEquals(-0.1, state.insulin?.totalIob)
        assertEquals(0.0, state.carbs?.cobGrams)
    }

    @Test
    fun `preserves signed finite AndroidAPS therapy values without local clamping`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 123.0,
                        "glucoseTimeStamp" to 900_000L,
                        "cob" to -1.0,
                        "insulinActivity" to -0.0012,
                        "baseBasal" to 0.0,
                    ),
                    1_000_000L,
                ),
            )

        assertEquals(-1.0, state.carbs?.cobGrams)
        assertEquals(-0.0012, state.therapyHistory.single().insulinActivityUnitsPerMinute)
        assertEquals(0.0, state.basal?.currentUnitsPerHour)
    }

    @Test
    fun `preserves negative AndroidAPS IOB exactly`() {
        val state =
            requireNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L, "iob" to -0.1),
                    1_000_000L,
                ),
            )

        assertEquals(-0.1, state.insulin?.totalIob)
        assertEquals(-0.1, state.therapyHistory.single().totalIob)
    }

    @Test fun `explicit loop state is distinct from missing transport data`() {
        val base = mapOf<String, Any?>("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L)
        assertNull(TherapyBridgePayloadAdapter.parse(base, 1_000_000L)?.loop)
        assertEquals("on", TherapyBridgePayloadAdapter.parse(base + ("loopEnabled" to true), 1_000_000L)?.loop?.status)
        assertEquals("off", TherapyBridgePayloadAdapter.parse(base + ("loopEnabled" to false), 1_000_000L)?.loop?.status)
    }

    @Test fun parsesDocumentedBroadcast() {
        val b =
            mapOf(
                "glucoseMgdl" to 123.0,
                "glucoseTimeStamp" to 900_000L,
                "units" to "mmol",
                "slopeArrow" to "↗",
                "iob" to 1.25,
                "cob" to 18.0,
                "profile" to "Default",
                "dia" to 5.0,
            )
        ; val s = assertNotNull(TherapyBridgePayloadAdapter.parse(b, 1_000_000))
        assertEquals(Trend.FORTY_FIVE_UP, s.glucose?.trend)
        assertEquals(GlucoseUnit.MMOL_L, s.glucose?.displayUnit)
        assertEquals(5.0, s.profile?.diaHours)
        assertTrue(
            DataCapability.IOB in s.capabilities,
        )
    }

    @Test fun `uses explicit AAPS insulin activity without deriving it from IOB`() {
        val explicit =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 123.0,
                        "glucoseTimeStamp" to 900_000L,
                        "iob" to 1.25,
                        "insulinActivity" to 0.012,
                    ),
                    1_000_000L,
                ),
            )
        assertEquals(0.012, explicit.therapyHistory.single().insulinActivityUnitsPerMinute)
        val absent =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(mapOf("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L, "iob" to 1.25), 1_000_000L),
            )
        assertNull(absent.therapyHistory.single().insulinActivityUnitsPerMinute)
    }

    @Test fun `reconstructs AndroidAPS activity from transmitted BGI and ISF`() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 156.0,
                        "glucoseTimeStamp" to 1_776_926_453_834L,
                        "iob" to 5.145,
                        "suggested" to
                            """{"algorithm":"SMB","reason":"COB: 28.6, Dev: 222, BGI: -26, ISF: 120, CR: 5.8, Target: 80"}""",
                    ),
                    1_776_926_455_000L,
                ),
            )

        assertEquals(26.0 / (120.0 * 5.0), state.therapyHistory.single().insulinActivityUnitsPerMinute!!, 0.0000001)
        assertEquals(ValueProvenance.DERIVED, state.fieldProvenance[CanonicalDataField.INSULIN_ACTIVITY])
    }

    @Test fun `AAPS algorithm activity fails closed for incomplete or non physical inputs`() {
        listOf(
            """{"reason":"BGI: -26"}""",
            """{"reason":"BGI: -26, ISF: 0"}""",
            """{"reason":"BGI: 26, ISF: 120"}""",
            """{"reason":"BGI: nonsense, ISF: 120"}""",
        ).forEach { suggested ->
            val state =
                assertNotNull(
                    TherapyBridgePayloadAdapter.parse(
                        mapOf("glucoseMgdl" to 123.0, "glucoseTimeStamp" to 900_000L, "iob" to 1.25, "suggested" to suggested),
                        1_000_000L,
                    ),
                )
            assertNull(state.therapyHistory.single().insulinActivityUnitsPerMinute, suggested)
        }
    }

    @Test fun rejectsMissingAndWrongTypes() {
        assertNull(TherapyBridgePayloadAdapter.parse(emptyMap(), 1))
        assertNull(
            TherapyBridgePayloadAdapter.parse(
                mapOf(
                    "glucoseMgdl" to "oops",
                    "glucoseTimeStamp" to 1L,
                ),
                1,
            ),
        )
    }

    @Test fun unknownFieldsAreIgnored() {
        assertNotNull(
            TherapyBridgePayloadAdapter.parse(
                mapOf(
                    "glucoseMgdl" to 100.0,
                    "glucoseTimeStamp" to 1L,
                    "futureThing" to Any(),
                ),
                2,
            ),
        )
    }

    @Test fun parsesCurrentDevExtendedPayload() {
        val values =
            mapOf<String, Any?>(
                "glucoseMgdl" to 145.0,
                "glucoseTimeStamp" to 1_000L,
                "units" to "mg/dl",
                "slopeArrow" to "→",
                "deltaMgdl" to 2.0,
                "avgDeltaMgdl" to 1.5,
                "high" to 180.0,
                "low" to 70.0,
                "bolusIob" to 0.8,
                "basalIob" to 0.4,
                "iob" to 1.2,
                "cob" to 12.0,
                "futureCarbs" to 5.0,
                "phoneBattery" to 75,
                "rigBattery" to 80,
                "suggestedTimeStamp" to 900L,
                "suggested" to "{}",
                "enactedTimeStamp" to 950L,
                "enacted" to "{}",
                "baseBasal" to 0.9,
                "profile" to "Default",
                "tempBasalStart" to 1_000L,
                "tempBasalDurationInMinutes" to 30L,
                "tempBasalAbsolute" to 1.1,
                "tempBasalString" to "1.10U/h",
                "pumpTimeStamp" to 990L,
                "pumpBattery" to 60,
                "pumpReservoir" to 120.0,
                "pumpStatus" to "OK",
            )
        val state = assertNotNull(TherapyBridgePayloadAdapter.parse(values, 1_100L))
        assertEquals("AAPS_EXTENDED_STATUS_V1", state.sourceContract)
        assertNull(state.sourceVersion)
        assertEquals(2.0, state.glucose?.deltaMgDl)
        assertEquals(0.8, state.insulin?.bolusIob)
        assertEquals(5.0, state.carbs?.futureCarbsGrams)
        assertEquals(1_801_000L, state.basal?.tempEndsAtEpochMs)
        assertEquals(950L, state.loop?.lastRunAtEpochMs)
        assertEquals(120.0, state.pump?.reservoirUnits)
        assertEquals(75, state.device?.phoneBatteryPercent)
        assertTrue(DataCapability.PUMP in state.capabilities)
        assertTrue(DataCapability.TEMP_BASAL in state.capabilities)
        assertTrue(
            DataCapability.AVERAGE_DELTA in state.capabilities,
        )
    }

    @Test fun fallsBackToEnactedTargetWhenSuggestedHasNoTarget() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf<String, Any?>(
                        "glucoseMgdl" to 110.0,
                        "glucoseTimeStamp" to 1_000L,
                        "suggested" to "{}",
                        "enacted" to "{\"targetBG\":140}",
                    ),
                    1_100L,
                ),
            )
        assertEquals(140.0, state.target?.valueMgDl)
        assertTrue(DataCapability.TARGET in state.capabilities)
    }

    @Test fun missingOptionalAndInvalidBatteryDoNotCrash() {
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf(
                        "glucoseMgdl" to 90.0,
                        "glucoseTimeStamp" to 1L,
                        "pumpBattery" to 150,
                    ),
                    2,
                ),
            )
        ; assertNull(state.pump)
        assertTrue(
            DataCapability.PUMP_BATTERY !in state.capabilities,
        )
    }

    @Test fun rejectsImplausibleFutureTimestamp() {
        assertNull(
            TherapyBridgePayloadAdapter.parse(
                mapOf(
                    "glucoseMgdl" to 100.0,
                    "glucoseTimeStamp" to 400_001L,
                ),
                100_000L,
            ),
        )
    }

    @Test fun preservesRealTempTargetStartAndDurationWithoutExtendingIt() {
        val start = 1_000_000L
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf<String, Any?>(
                        "glucoseMgdl" to 110.0,
                        "glucoseTimeStamp" to start,
                        "suggestedTimeStamp" to start,
                        "suggested" to "{\"targetBG\":140,\"reason\":\"active temp target\"}",
                        "tempTargetStart" to start,
                        "tempTargetDurationInMinutes" to 30L,
                    ),
                    start + 1_000L,
                ),
            )
        assertTrue(state.target?.temporary == true)
        assertEquals(start, state.target?.startedAtEpochMs)
        assertEquals(start + 30 * 60_000L, state.target?.endsAtEpochMs)
        assertEquals(listOf(TargetSample(140.0, start, start + 30 * 60_000L, true)), state.targetHistory)
    }

    @Test fun tempTargetWithoutPublicTimingDoesNotInventAnExpiry() {
        val measured = 1_000_000L
        val state =
            assertNotNull(
                TherapyBridgePayloadAdapter.parse(
                    mapOf<String, Any?>(
                        "glucoseMgdl" to 110.0,
                        "glucoseTimeStamp" to measured,
                        "suggested" to "{\"targetBG\":140,\"isTempTarget\":true}",
                    ),
                    measured + 1_000L,
                ),
            )
        assertNull(state.target?.startedAtEpochMs)
        assertNull(state.target?.endsAtEpochMs)
        assertEquals(measured, state.targetHistory.single().startedAtEpochMs)
        assertEquals(measured, state.targetHistory.single().endsAtEpochMs)
    }
}
