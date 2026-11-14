package app.sugarlicious.mobile

import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CanonicalDataField
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.LoopState
import app.sugarlicious.model.PumpState
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyHistorySample
import app.sugarlicious.model.ValueProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayHistoryAccumulatorTest {
    @Test fun `SMB without delivery time is not timestamped using reception time`() {
        val now = 1_800_000_000_000L
        val state = TherapyDisplayState(receivedAtEpochMs = now, loop = LoopState(smbUnits = 0.5, enactedAtEpochMs = now))
        assertTrue(DisplayHistoryAccumulator.merge(null, state, now).therapyHistory.none { it.smbUnits != null })
    }

    @Test
    fun `partial updates retain provenance for retained fields and replace incoming fields`() {
        val now = 2_000_000L
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 1_000L,
                fieldProvenance = mapOf(CanonicalDataField.IOB to ValueProvenance.SOURCE),
            )
        val current =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                fieldProvenance = mapOf(CanonicalDataField.TREND to ValueProvenance.DERIVED),
            )

        assertEquals(
            mapOf(
                CanonicalDataField.IOB to ValueProvenance.SOURCE,
                CanonicalDataField.TREND to ValueProvenance.DERIVED,
            ),
            DisplayHistoryAccumulator.merge(previous, current, now).fieldProvenance,
        )
    }

    @Test
    fun `retains AndroidAPS display semantics when a partial update omits them`() {
        val now = 2_000_000L
        val semantics = app.sugarlicious.model.TherapyBridgeDisplaySemantics(glucoseUnit = app.sugarlicious.model.GlucoseUnit.MMOL_L)
        val result =
            DisplayHistoryAccumulator.merge(
                previous = TherapyDisplayState(receivedAtEpochMs = now - 1_000L, therapyBridgeDisplaySemantics = semantics),
                current = TherapyDisplayState(receivedAtEpochMs = now),
                nowEpochMs = now,
            )

        assertEquals(semantics, result.therapyBridgeDisplaySemantics)
    }

    @Test
    fun `partial AndroidAPS therapy update preserves sibling fields but accepts explicit zero and negative values`() {
        val now = 3_000_000L
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 1_000L,
                insulin = app.sugarlicious.model.InsulinState(totalIob = 1.2, bolusIob = 0.4, basalIob = 0.8),
                carbs = app.sugarlicious.model.CarbState(cobGrams = 20.0, futureCarbsGrams = 7.0),
            )
        val current =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                insulin = app.sugarlicious.model.InsulinState(totalIob = -0.1),
                carbs = app.sugarlicious.model.CarbState(cobGrams = 0.0),
            )

        val result = DisplayHistoryAccumulator.merge(previous, current, now)

        assertEquals(-0.1, result.insulin?.totalIob)
        assertEquals(0.4, result.insulin?.bolusIob)
        assertEquals(0.8, result.insulin?.basalIob)
        assertEquals(0.0, result.carbs?.cobGrams)
        assertEquals(7.0, result.carbs?.futureCarbsGrams)
    }

    @Test
    fun `base basal update clears the complete obsolete temp basal group`() {
        val now = 4_000_000L
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 60_000L,
                basal =
                    BasalState(
                        currentUnitsPerHour = 0.8,
                        tempAbsoluteUnitsPerHour = 1.2,
                        tempPercent = 150,
                        tempStartedAtEpochMs = now - 30 * 60_000L,
                        tempDurationMinutes = 30,
                        tempEndsAtEpochMs = now,
                        displayText = "1.20 U/h",
                    ),
            )
        val current =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                basal = BasalState(currentUnitsPerHour = 0.8),
            )

        assertEquals(current.basal, DisplayHistoryAccumulator.merge(previous, current, now).basal)
    }

    @Test
    fun `does not invent insulin activity from IOB and DIA`() {
        val now = 20_000_000L
        val first =
            TherapyDisplayState(
                receivedAtEpochMs = now - 5 * 60_000L,
                glucose =
                    GlucoseState(
                        120.0,
                        GlucoseUnit.MG_DL,
                        measuredAtEpochMs =
                            now - 5 * 60_000L,
                    ),
                insulin = InsulinState(2.0),
                profile = app.sugarlicious.model.ProfileState("Default", 5.0),
            )
        val second =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(125.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                insulin = InsulinState(1.8),
                profile = app.sugarlicious.model.ProfileState("Default", 5.0),
            )
        val merged = DisplayHistoryAccumulator.merge(DisplayHistoryAccumulator.merge(null, first, now), second, now)
        assertTrue(merged.therapyHistory.all { it.insulinActivityUnitsPerMinute == null })
    }

    @Test
    fun `partial transport update retains last validated therapy and loop state`() {
        val now = 20_000_000L
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 5 * 60_000L,
                glucose = GlucoseState(176.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now - 5 * 60_000L),
                insulin = InsulinState(totalIob = 2.4),
                carbs = CarbState(cobGrams = 32.0),
                basal = BasalState(currentUnitsPerHour = 0.7),
                target = TargetState(lowMgDl = 80.0, highMgDl = 160.0),
                loop = LoopState(status = "enacted", lastRunAtEpochMs = now - 6 * 60_000L),
                pump = PumpState(status = "normal", reservoirUnits = 120.0),
            )
        val transportOnly =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(176.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now - 5 * 60_000L),
            )

        val merged = DisplayHistoryAccumulator.merge(previous, transportOnly, now)

        assertEquals(previous.loop, merged.loop)
        assertEquals(previous.insulin, merged.insulin)
        assertEquals(previous.carbs, merged.carbs)
        assertEquals(previous.basal, merged.basal)
        assertEquals(previous.target, merged.target)
        assertEquals(previous.pump, merged.pump)
    }

    @Test
    fun `explicit loop off replaces retained loop on`() {
        val now = 20_000_000L
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 60_000L,
                loop = LoopState(status = "enacted", lastRunAtEpochMs = now - 60_000L),
            )
        val explicitOff =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                loop = LoopState(status = "off", lastRunAtEpochMs = now),
            )

        assertEquals("off", DisplayHistoryAccumulator.merge(previous, explicitOff, now).loop?.status)
    }

    @Test fun `treatment events keep stable ids and do not duplicate on reload`() {
        val now = DisplayHistoryAccumulator.WINDOW_MS * 2
        val event = TherapyEvent("bolus:42", TherapyEventKind.MEAL_BOLUS, now - 60_000L, 7.6)
        val first = TherapyDisplayState(receivedAtEpochMs = now, therapyEvents = listOf(event))
        val second = TherapyDisplayState(receivedAtEpochMs = now + 1_000L, therapyEvents = listOf(event))
        val merged = DisplayHistoryAccumulator.merge(DisplayHistoryAccumulator.merge(null, first, now), second, now + 1_000L)
        assertEquals(listOf(event), merged.therapyEvents)
    }

    @Test
    fun `deduplicates and bounds display history`() {
        val now = 2 * DisplayHistoryAccumulator.WINDOW_MS

        fun state(
            at: Long,
            glucose: Double,
        ) = TherapyDisplayState(
            receivedAtEpochMs = at,
            glucose = GlucoseState(glucose, GlucoseUnit.MG_DL, measuredAtEpochMs = at),
            insulin = InsulinState(totalIob = glucose / 100),
            carbs = CarbState(cobGrams = glucose / 10),
        )
        val old = state(now - DisplayHistoryAccumulator.WINDOW_MS - 1, 90.0)
        val first = DisplayHistoryAccumulator.merge(null, old, now)
        assertEquals(0, first.glucoseHistory.size)

        val second = DisplayHistoryAccumulator.merge(first, state(now, 120.0), now)
        val replaced = DisplayHistoryAccumulator.merge(second, state(now, 125.0), now)
        assertEquals(listOf(125.0), replaced.glucoseHistory.map { it.valueMgDl })
        assertEquals(1.25, replaced.therapyHistory.single().totalIob!!, 0.001)
    }

    @Test
    fun `retains every minute of therapy data across the complete twenty four hour window`() {
        val minute = 60_000L
        val now = 3 * DisplayHistoryAccumulator.WINDOW_MS
        val history =
            (0..(24 * 60)).map { minuteOffset ->
                TherapyHistorySample(
                    measuredAtEpochMs = now - (24 * 60 - minuteOffset) * minute,
                    totalIob = minuteOffset.toDouble(),
                    cobGrams = minuteOffset.toDouble(),
                    basalUnitsPerHour = 0.8,
                    insulinActivityUnitsPerMinute = 0.01,
                )
            }

        val merged =
            DisplayHistoryAccumulator.merge(
                previous = null,
                current = TherapyDisplayState(receivedAtEpochMs = now, therapyHistory = history),
                nowEpochMs = now,
            )

        assertEquals(24 * 60 + 1, merged.therapyHistory.size)
        assertEquals(now - DisplayHistoryAccumulator.WINDOW_MS, merged.therapyHistory.first().measuredAtEpochMs)
        assertEquals(now, merged.therapyHistory.last().measuredAtEpochMs)
    }

    @Test
    fun `dense independent therapy streams cannot evict each other inside the twenty four hour window`() {
        val minute = 60_000L
        val now = 4 * DisplayHistoryAccumulator.WINDOW_MS
        val start = now - DisplayHistoryAccumulator.WINDOW_MS
        val history =
            (0..(24 * 60)).flatMap { offset ->
                val timestamp = start + offset * minute
                listOf(
                    TherapyHistorySample(timestamp, totalIob = offset.toDouble()),
                    TherapyHistorySample(timestamp + 1, cobGrams = offset.toDouble()),
                    TherapyHistorySample(timestamp + 2, basalUnitsPerHour = 0.8),
                    TherapyHistorySample(timestamp + 3, insulinActivityUnitsPerMinute = 0.01),
                )
            }

        val merged =
            DisplayHistoryAccumulator.merge(
                previous = null,
                current = TherapyDisplayState(receivedAtEpochMs = now, therapyHistory = history),
                nowEpochMs = now,
            )

        assertEquals(24 * 60 + 1, merged.therapyHistory.count { it.totalIob != null })
        assertEquals(24 * 60 + 1, merged.therapyHistory.count { it.cobGrams != null })
        assertEquals(24 * 60 + 1, merged.therapyHistory.count { it.basalUnitsPerHour != null })
        assertEquals(24 * 60 + 1, merged.therapyHistory.count { it.insulinActivityUnitsPerMinute != null })
        assertEquals(start, merged.therapyHistory.first { it.totalIob != null }.measuredAtEpochMs)
    }

    @Test
    fun `incoming history samples close an existing graph gap`() {
        val minute = 60_000L
        val now = 1000 * minute
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = now - 10 * minute,
                glucose = GlucoseState(140.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now - 10 * minute),
                glucoseHistory =
                    listOf(
                        GlucoseSample(120.0, now - 25 * minute),
                        GlucoseSample(140.0, now - 10 * minute),
                    ),
            )
        assertTrue(DisplayHistoryAccumulator.hasGap(previous.glucoseHistory))

        val current =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(150.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(
                        GlucoseSample(125.0, now - 20 * minute),
                        GlucoseSample(132.0, now - 15 * minute),
                        GlucoseSample(145.0, now - 5 * minute),
                    ),
            )

        val merged = DisplayHistoryAccumulator.merge(previous, current, now)

        assertEquals(
            listOf(-25L, -20L, -15L, -10L, -5L, 0L),
            merged.glucoseHistory.map { (it.measuredAtEpochMs - now) / minute },
        )
        assertFalse(DisplayHistoryAccumulator.hasGap(merged.glucoseHistory))
    }

    @Test
    fun `AndroidAPS is current without deleting an unidentified nearby secondary reading`() {
        val now = 2_000_000L
        val state =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now,
                glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                glucoseHistory =
                    listOf(
                        GlucoseSample(121.0, now - 30_000L, DataSourceId.OTHER),
                    ),
            )
        val merged = DisplayHistoryAccumulator.merge(null, state, now)
        assertEquals(2, merged.glucoseHistory.size)
        assertEquals(DataSourceId.ANDROID_APS, merged.source)
        assertEquals(setOf(DataSourceId.ANDROID_APS, DataSourceId.OTHER), merged.glucoseHistory.map { it.source }.toSet())
    }

    @Test
    fun `sensor error samples never enter canonical graph history`() {
        val now = 2_000_000L
        val state =
            TherapyDisplayState(
                source = DataSourceId.ANDROID_APS,
                receivedAtEpochMs = now,
                glucose =
                    GlucoseState(
                        123.0,
                        GlucoseUnit.MG_DL,
                        measuredAtEpochMs = now,
                        quality = CgmQuality.SENSOR_ERROR,
                    ),
            )

        assertTrue(DisplayHistoryAccumulator.merge(null, state, now).glucoseHistory.isEmpty())
    }

    @Test
    fun `stores a public enacted SMB as a therapy marker without losing IOB`() {
        val now = 2_000_000L
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                insulin = InsulinState(totalIob = 1.2),
                loop =
                    LoopState(
                        enactedAtEpochMs = now,
                        smbUnits = 0.25,
                        smbAtEpochMs = now,
                    ),
            )

        val sample = DisplayHistoryAccumulator.merge(null, state, now).therapyHistory.single()

        assertEquals(1.2, sample.totalIob!!, 0.0)
        assertEquals(0.25, sample.smbUnits!!, 0.0)
    }

    @Test
    fun `does not extend a temporary target beyond its published end`() {
        val minute = 60_000L
        val now = 2_000_000L
        val temporaryEnd = now - 5 * minute
        val previous =
            TherapyDisplayState(
                receivedAtEpochMs = temporaryEnd,
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 90.0,
                            startedAtEpochMs = now - 35 * minute,
                            endsAtEpochMs = temporaryEnd,
                            temporary = true,
                        ),
                    ),
            )
        val current =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 105.0,
                            startedAtEpochMs = now,
                            endsAtEpochMs = now,
                            temporary = false,
                        ),
                    ),
            )

        val merged = DisplayHistoryAccumulator.merge(previous, current, now)

        assertEquals(temporaryEnd, merged.targetHistory.first().endsAtEpochMs)
        assertEquals(now, merged.targetHistory.last().startedAtEpochMs)
    }
}
