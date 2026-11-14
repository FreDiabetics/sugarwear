package app.sugarlicious.mobile

import app.sugarlicious.model.*
import org.junit.Assert.*
import org.junit.Test

class MobileSourceCompositionTest {
    private val now = 1_800_000_000_000L

    private fun state(source: DataSourceId, value: Double) =
        TherapyDisplayState(
            source = source,
            receivedAtEpochMs = now,
            glucose = GlucoseState(value, GlucoseUnit.MG_DL, Trend.FLAT, now - 60_000, source = source),
            insulin = if (source == DataSourceId.ANDROID_APS) InsulinState(totalIob = 2.0) else null,
        )

    @Test fun `Manufacturer dots and AndroidAPS forecasts are independent streams`() {
        val forecast = GlucosePrediction(PredictionKind.IOB, listOf(GlucoseSample(120.0, now), GlucoseSample(110.0, now + 300_000)))
        val therapyBridge = state(DataSourceId.ANDROID_APS, 120.0).copy(glucosePredictions = listOf(forecast))
        val manufacturer = state(DataSourceId.DEXCOM_G7_APP, 123.0)
        val inputs = mapOf(therapyBridge.source to therapyBridge, manufacturer.source to manufacturer)
        val config = MobileSourceConfiguration(enabledGlucoseSources = setOf(MobileGlucoseSource.DEXCOM_BROADCAST), therapyBridgeTherapyEnabled = false, therapyBridgePredictionsEnabled = true)
        val result = MobileSourceComposition.compose(inputs, config, now)
        assertEquals(manufacturer.glucose, result.glucose)
        assertEquals(listOf(forecast), result.glucosePredictions)
        assertTrue(DataCapability.PREDICTIONS in result.capabilities)
        assertNull(result.insulin)
        val disabled = MobileSourceComposition.compose(inputs, config.copy(therapyBridgePredictionsEnabled = false), now)
        assertTrue(disabled.glucosePredictions.isEmpty())
        assertFalse(DataCapability.PREDICTIONS in disabled.capabilities)
        assertEquals(manufacturer.glucose, disabled.glucose)
    }

    @Test fun `new sources compete only when enabled and preserve AndroidAPS therapy`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 120.0)
        val xdrip = state(DataSourceId.XDRIP_PLUS, 125.0).copy(glucose = state(DataSourceId.XDRIP_PLUS, 125.0).glucose!!.copy(measuredAtEpochMs = now))
        val sources = mapOf(therapyBridge.source to therapyBridge, xdrip.source to xdrip)
        assertEquals(DataSourceId.ANDROID_APS, MobileSourceComposition.compose(sources, MobileSourceConfiguration(), now).glucose!!.source)
        val selected = MobileSourceComposition.compose(sources, MobileSourceConfiguration(enabledGlucoseSources = setOf(MobileGlucoseSource.XDRIP_PLUS)), now)
        assertEquals(DataSourceId.XDRIP_PLUS, selected.glucose!!.source)
        assertEquals(therapyBridge.insulin, selected.insulin)
    }

    @Test fun `equal timestamp keeps incoming glucose metadata but older readings cannot replace current`() {
        val previous = state(DataSourceId.ANDROID_APS, 120.0)
        val updated = previous.copy(glucose = previous.glucose!!.copy(displayUnit = GlucoseUnit.MMOL_L, deltaMgDl = 3.0, averageDeltaMgDl = 2.0))
        assertEquals(updated.glucose, mergeMobileSourceInput(previous, updated, now).glucose)
        val older = updated.copy(glucose = updated.glucose!!.copy(measuredAtEpochMs = now - 300_000))
        assertEquals(previous.glucose, mergeMobileSourceInput(previous, older, now).glucose)
    }

    @Test fun `history retention preserves authoritative treatment filtering and refreshed attribution`() {
        val oldEvent = TherapyEvent("aaps", TherapyEventKind.MEAL_BOLUS, now - 60_000, 2.0, source = TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT)
        val external = oldEvent.copy(id = "external", source = TherapyEventSource.NIGHTSCOUT_ONLY)
        val previous = state(DataSourceId.ANDROID_APS, 120.0).copy(therapyEvents = listOf(oldEvent, external))
        val current = previous.copy(therapyEvents = listOf(oldEvent.copy(source = TherapyEventSource.AAPS_ONLY)))
        assertEquals(current.therapyEvents, retainMobileDisplayHistory(previous, current, now).therapyEvents)
    }

    @Test fun `valid AndroidAPS HIGH boundary values remain available`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 401.0)
        val result = MobileSourceComposition.compose(mapOf(therapyBridge.source to therapyBridge), MobileSourceConfiguration(), now)
        assertEquals(401.0, result.glucose!!.valueMgDl, 0.0)
    }

    @Test fun `stored older dots fill historical gaps without introducing disabled live data`() {
        val previous = state(DataSourceId.DEXCOM_SHARE, 120.0).copy(glucoseHistory = listOf(GlucoseSample(120.0, now - 300_000, DataSourceId.DEXCOM_SHARE)))
        val raw =
            state(DataSourceId.ANDROID_APS, 130.0).copy(
                glucoseHistory =
                    listOf(
                        GlucoseSample(130.0, now - 80_000_000, DataSourceId.ANDROID_APS),
                        GlucoseSample(130.0, now, DataSourceId.ANDROID_APS),
                    ),
            )
        val restored = requireNotNull(restoreStoredMobileHistory(previous, mapOf(raw.source to raw), now))
        assertTrue(restored.glucoseHistory.any { it.measuredAtEpochMs == now - 80_000_000 })
        assertFalse(restored.glucoseHistory.any { it.measuredAtEpochMs == now })
        assertEquals(previous.glucose, restored.glucose)
    }

    @Test fun `source composition retains every stored dot beyond fifteen hours`() {
        val points =
            (0..288).flatMap { index ->
                val timestamp = now - 86_400_000L + index * 300_000L
                listOf(GlucoseSample(120.0, timestamp, DataSourceId.ANDROID_APS, sensorId = "a"), GlucoseSample(121.0, timestamp, DataSourceId.ANDROID_APS, sensorId = "b"))
            }
        val therapyBridge = state(DataSourceId.ANDROID_APS, 120.0).copy(glucoseHistory = points)
        val composed = MobileSourceComposition.compose(mapOf(therapyBridge.source to therapyBridge), MobileSourceConfiguration(), now)
        assertTrue(composed.glucoseHistory.size >= 578)
        assertEquals(now - 86_400_000L, composed.glucoseHistory.first().measuredAtEpochMs)
        val retained = retainMobileDisplayHistory(composed, composed, now)
        assertEquals(composed.glucoseHistory, retained.glucoseHistory)
    }

    @Test fun `source transition preserves displayed history and therapy while appending new source`() {
        val old =
            state(DataSourceId.ANDROID_APS, 150.0).copy(
                glucose = state(DataSourceId.ANDROID_APS, 150.0).glucose!!.copy(measuredAtEpochMs = now - 300_000),
                glucoseHistory = listOf(GlucoseSample(140.0, now - 600_000, DataSourceId.ANDROID_APS)),
                therapyHistory = listOf(TherapyHistorySample(now - 600_000, totalIob = 2.0)),
            )
        val empty = MobileSourceComposition.compose(mapOf(old.source to old), MobileSourceConfiguration(MobileGlucoseSource.NONE), now)
        val waiting = retainMobileDisplayHistory(old, empty, now)
        assertEquals(2, waiting.glucoseHistory.size)
        assertTrue(waiting.therapyHistory.any { it.measuredAtEpochMs == now - 600_000 && it.totalIob == 2.0 })
        assertEquals(old.insulin, waiting.insulin)
        assertNull(waiting.glucose)
        val next = state(DataSourceId.DEXCOM_SHARE, 120.0).let { it.copy(glucose = it.glucose!!.copy(measuredAtEpochMs = now), glucoseHistory = listOf(GlucoseSample(120.0, now, DataSourceId.DEXCOM_SHARE))) }
        val joined = retainMobileDisplayHistory(waiting, next, now)
        assertEquals(3, joined.glucoseHistory.size)
        assertTrue(joined.therapyHistory.any { it.measuredAtEpochMs == now - 600_000 && it.totalIob == 2.0 })
        assertEquals(DataSourceId.DEXCOM_SHARE, joined.glucose!!.source)
        assertEquals(joined.glucoseHistory, retainMobileDisplayHistory(joined, next, now).glucoseHistory)
    }

    @Test fun `freshest enabled valid source wins without mixing trend streams`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 150.0)
        val share = state(DataSourceId.DEXCOM_SHARE, 120.0).let { it.copy(glucose = it.glucose!!.copy(measuredAtEpochMs = now - 30_000)) }
        val config = MobileSourceConfiguration(enabledGlucoseSources = setOf(MobileGlucoseSource.ANDROID_APS, MobileGlucoseSource.DEXCOM_SHARE))
        val inputs = mapOf(therapyBridge.source to therapyBridge, share.source to share)
        assertEquals(DataSourceId.DEXCOM_SHARE, MobileSourceComposition.compose(inputs, config, now).glucose!!.source)
        val invalid = share.copy(glucose = share.glucose!!.copy(quality = CgmQuality.INVALID))
        assertEquals(DataSourceId.ANDROID_APS, MobileSourceComposition.compose(inputs + (share.source to invalid), config, now).glucose!!.source)
        assertNull(MobileSourceComposition.compose(inputs, config, now + 1_000_000).glucose)
    }

    @Test fun `disabled glucose streams cannot publish new graph samples`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 150.0)
        val manufacturer = state(DataSourceId.DEXCOM_G7_APP, 120.0)
        val inputs = mapOf(therapyBridge.source to therapyBridge, manufacturer.source to manufacturer)
        val disabled = MobileSourceComposition.compose(inputs, MobileSourceConfiguration(MobileGlucoseSource.NONE, true), now)
        assertTrue(disabled.glucoseHistory.isEmpty())
        val selected = MobileSourceComposition.compose(inputs, MobileSourceConfiguration(MobileGlucoseSource.DEXCOM_BROADCAST, true), now)
        assertTrue(selected.glucoseHistory.all { it.source == DataSourceId.DEXCOM_G7_APP })
        assertEquals(1, selected.glucoseHistory.size)
    }

    @Test fun `Manufacturer glucose and AndroidAPS therapy retain separate upstream attribution`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 150.0)
        val manufacturer = state(DataSourceId.DEXCOM_G7_APP, 120.0)
        val result = MobileSourceComposition.compose(mapOf(therapyBridge.source to therapyBridge, manufacturer.source to manufacturer), MobileSourceConfiguration(MobileGlucoseSource.DEXCOM_BROADCAST, true), now)
        assertEquals(120.0, result.glucose!!.valueMgDl, 0.0)
        assertEquals(2.0, result.insulin!!.totalIob!!, 0.0)
        assertEquals(DataSourceId.DEXCOM_G7_APP, result.fieldSources[CanonicalDataField.GLUCOSE])
        assertEquals(DataSourceId.ANDROID_APS, result.fieldSources[CanonicalDataField.IOB])
    }

    @Test fun `disabled glucose is not restored from AndroidAPS while therapy stays enabled`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 150.0)
        val result = MobileSourceComposition.compose(mapOf(therapyBridge.source to therapyBridge), MobileSourceConfiguration(MobileGlucoseSource.DEXCOM_BROADCAST, true), now)
        assertNull(result.glucose)
        assertEquals(2.0, result.insulin!!.totalIob!!, 0.0)
        assertFalse(DataCapability.GLUCOSE in result.capabilities)
    }

    @Test fun `disabled therapy cannot leak from an otherwise selected AndroidAPS input`() {
        val therapyBridge = state(DataSourceId.ANDROID_APS, 150.0)
        val result = MobileSourceComposition.compose(mapOf(therapyBridge.source to therapyBridge), MobileSourceConfiguration(MobileGlucoseSource.ANDROID_APS, false), now)
        assertNotNull(result.glucose)
        assertNull(result.insulin)
        assertTrue(result.therapyHistory.isEmpty())
        assertFalse(DataCapability.IOB in result.capabilities)
    }

    @Test fun `older batches never replace a newer current glucose`() {
        val latest = state(DataSourceId.DEXCOM_G7_APP, 120.0)
        val delayed = latest.copy(glucose = latest.glucose!!.copy(valueMgDl = 110.0, measuredAtEpochMs = now - 600_000))
        val result = mergeMobileSourceInput(latest, delayed, now)
        assertEquals(latest.glucose!!.measuredAtEpochMs, result.glucose!!.measuredAtEpochMs)
        assertEquals(120.0, result.glucose!!.valueMgDl, 0.0)
    }
}
