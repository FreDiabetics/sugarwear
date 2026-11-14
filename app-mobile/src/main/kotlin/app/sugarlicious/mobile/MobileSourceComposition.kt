package app.sugarlicious.mobile

import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CanonicalDataField
import app.sugarlicious.model.CanonicalTrendPolicy
import app.sugarlicious.model.DataCapability
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendRateProfile
import app.sugarlicious.model.ValueProvenance

internal val MOBILE_INPUT_SOURCES = MobileGlucoseSource.entries.mapNotNull { it.sourceId }.toSet()
private val GLUCOSE_CAPABILITIES = setOf(DataCapability.GLUCOSE, DataCapability.TREND, DataCapability.DELTA, DataCapability.AVERAGE_DELTA)
private val GLUCOSE_FIELDS = setOf(CanonicalDataField.GLUCOSE, CanonicalDataField.TREND, CanonicalDataField.DELTA, CanonicalDataField.AVERAGE_DELTA)

/** Select fields before the one canonical commit; a disabled stream never falls back silently. */
internal object MobileSourceComposition {
    fun compose(inputs: Map<DataSourceId, TherapyDisplayState>, configuration: MobileSourceConfiguration, now: Long): TherapyDisplayState {
        val selected =
            configuration.enabledGlucoseSources
                .mapNotNull { it.sourceId }
                .mapNotNull { source -> inputs[source]?.takeIf { it.source == source } }
                .filter { state -> state.glucose?.let { it.source == state.source && it.quality == app.sugarlicious.model.CgmQuality.VALID && it.valueMgDl.isFinite() && it.valueMgDl in 20.0..1_000.0 && it.measuredAtEpochMs in (now - 900_000L)..now } == true }
                .maxWithOrNull(compareBy<TherapyDisplayState> { it.glucose!!.measuredAtEpochMs }.thenBy { -it.source.ordinal })
                ?.source
        val cgm = inputs[selected]?.takeIf { it.source == selected }
        val therapy = inputs[DataSourceId.ANDROID_APS]?.takeIf { configuration.therapyBridgeTherapyEnabled }
        val glucose = cgm?.glucose
        val cgmCapabilities =
            if (glucose == null) {
                emptySet()
            } else {
                setOf(DataCapability.GLUCOSE) + (cgm.capabilities intersect GLUCOSE_CAPABILITIES) +
                    (if (glucose.trend != Trend.UNKNOWN) setOf(DataCapability.TREND) else emptySet()) +
                    (if (glucose.deltaMgDl != null) setOf(DataCapability.DELTA) else emptySet())
            }
        val fields =
            buildMap {
                if (glucose != null) {
                    put(CanonicalDataField.GLUCOSE, glucose.source)
                    if (glucose.trend != Trend.UNKNOWN) put(CanonicalDataField.TREND, glucose.source)
                    if (glucose.deltaMgDl != null) put(CanonicalDataField.DELTA, glucose.source)
                    if (glucose.averageDeltaMgDl != null) put(CanonicalDataField.AVERAGE_DELTA, glucose.source)
                }
                therapy?.let { value ->
                    if (value.insulin != null) put(CanonicalDataField.IOB, DataSourceId.ANDROID_APS)
                    if (value.carbs != null) put(CanonicalDataField.COB, DataSourceId.ANDROID_APS)
                    if (value.basal != null) put(CanonicalDataField.BASAL, DataSourceId.ANDROID_APS)
                    if (value.target != null) put(CanonicalDataField.TARGET, DataSourceId.ANDROID_APS)
                    if (value.loop != null) put(CanonicalDataField.LOOP, DataSourceId.ANDROID_APS)
                    if (value.pump != null) put(CanonicalDataField.PUMP_STATUS, DataSourceId.ANDROID_APS)
                    if (value.profile != null) put(CanonicalDataField.PROFILE, DataSourceId.ANDROID_APS)
                    value.fieldProvenance
                        .filter { (field, origin) -> field !in GLUCOSE_FIELDS && origin != ValueProvenance.UNAVAILABLE }
                        .forEach { (field, _) -> put(field, DataSourceId.ANDROID_APS) }
                }
            }
        // Forecasts retain AndroidAPS values/timestamps independently of the selected CGM.
        val predictions =
            inputs[DataSourceId.ANDROID_APS]
                ?.let {
                    app.sugarlicious.storage.PersistentPredictionCache
                        .merge(null, it, now)
                        .glucosePredictions
                }.orEmpty()
                .takeIf { configuration.therapyBridgePredictionsEnabled }
                .orEmpty()
        val provenance =
            therapy?.fieldProvenance.orEmpty().filterKeys { it !in GLUCOSE_FIELDS } +
                (if (glucose == null) emptyMap() else cgm.fieldProvenance.filterKeys { it in GLUCOSE_FIELDS })
        return TherapyDisplayState(
            source = selected ?: DataSourceId.OTHER,
            sourceVersion = cgm?.sourceVersion ?: therapy?.sourceVersion,
            sourceContract = "PHONE_SOURCES_V1:CGM=${selected?.name ?: "NONE"}:THERAPY=${if (therapy == null) "NONE" else "ANDROID_APS"}",
            receivedAtEpochMs = maxOf(cgm?.receivedAtEpochMs ?: 0L, therapy?.receivedAtEpochMs ?: 0L).takeIf { it > 0L } ?: now,
            glucose = glucose,
            glucoseHistory = CanonicalCgmHistory.merge(inputs.filterKeys { it == selected }.values.flatMap { value -> value.glucoseHistory + listOfNotNull(value.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality) }) }, now, selected, maxPoints = DisplayHistoryAccumulator.MAX_POINTS),
            glucosePredictions = predictions,
            therapyHistory = therapy?.therapyHistory.orEmpty(),
            therapyEvents = therapy?.therapyEvents.orEmpty(),
            targetHistory = therapy?.targetHistory.orEmpty(),
            insulin = therapy?.insulin,
            carbs = therapy?.carbs,
            basal = therapy?.basal,
            target = therapy?.target,
            loop = therapy?.loop,
            pump = therapy?.pump,
            device = therapy?.device,
            profile = therapy?.profile,
            therapyBridgeDisplaySemantics = therapy?.therapyBridgeDisplaySemantics,
            capabilities = cgmCapabilities + (therapy?.capabilities.orEmpty() - GLUCOSE_CAPABILITIES - DataCapability.PREDICTIONS) + (if (predictions.isNotEmpty()) setOf(DataCapability.PREDICTIONS) else emptySet()) + if (therapy?.insulin != null) setOf(DataCapability.IOB) else emptySet(),
            fieldProvenance = provenance,
            fieldSources = fields,
        )
    }
}

internal fun mergeMobileSourceInput(previous: TherapyDisplayState?, incoming: TherapyDisplayState, now: Long): TherapyDisplayState {
    require(incoming.source in MOBILE_INPUT_SOURCES)
    val prior = previous?.takeIf { it.source == incoming.source }
    val merged = DisplayHistoryAccumulator.merge(prior, incoming, now)
    var glucose = listOfNotNull(incoming.glucose, prior?.glucose).maxByOrNull { it.measuredAtEpochMs }
    if (glucose != null && incoming.source != DataSourceId.ANDROID_APS) {
        val sample = GlucoseSample(glucose.valueMgDl, glucose.measuredAtEpochMs, glucose.source, glucose.sensorId, glucose.sessionId, glucose.sequenceNumber, glucose.receivedAtEpochMs, glucose.quality)
        CanonicalTrendPolicy.derive(sample, merged.glucoseHistory, TrendRateProfile.SUGARLICIOUS)?.let { derived ->
            glucose = glucose.copy(deltaMgDl = derived.deltaMgDl, trend = derived.trend, trendRateMgDlPerMinute = derived.rateMgDlPerMinute, trendOrigin = ValueProvenance.DERIVED)
        }
    }
    return merged.copy(
        glucose = glucose,
        capabilities = merged.capabilities + (if (glucose?.deltaMgDl != null) setOf(DataCapability.DELTA) else emptySet()) + (if (glucose?.trend != null && glucose.trend != Trend.UNKNOWN) setOf(DataCapability.TREND) else emptySet()),
        fieldProvenance = merged.fieldProvenance + (if (glucose?.trendOrigin == ValueProvenance.DERIVED) mapOf(CanonicalDataField.DELTA to ValueProvenance.DERIVED, CanonicalDataField.TREND to ValueProvenance.DERIVED) else emptyMap()),
        fieldSources = merged.fieldSources + (if (glucose?.deltaMgDl != null) mapOf(CanonicalDataField.DELTA to incoming.source) else emptyMap()),
    )
}

/** The display archive survives source selection; raw source caches remain isolated. */
internal fun retainMobileDisplayHistory(previous: TherapyDisplayState?, current: TherapyDisplayState, now: Long): TherapyDisplayState {
    if (previous == null) return current
    val archiveCandidates =
        previous.glucoseHistory +
            listOfNotNull(
                previous.glucose?.let {
                    GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality)
                },
            )
    val archived = CanonicalCgmHistory.merge(archiveCandidates, now, maxPoints = DisplayHistoryAccumulator.MAX_POINTS)

    fun overlaps(first: GlucoseSample, second: GlucoseSample): Boolean =
        first.source != second.source &&
            kotlin.math.abs(first.measuredAtEpochMs - second.measuredAtEpochMs) <= 90_000L &&
            (first.sensorId == null || second.sensorId == null || first.sensorId == second.sensorId) &&
            (first.sessionId == null || second.sessionId == null || first.sessionId == second.sessionId)
    // One displayed representation per overlapping transport window; raw source caches keep
    // every original value. Source priority affects presentation only, never delta derivation.
    val displayed = mutableListOf<GlucoseSample>()
    (archived + current.glucoseHistory.sortedBy { it.measuredAtEpochMs }).forEach { candidate ->
        if (displayed.none {
                overlaps(it, candidate) ||
                    app.sugarlicious.model.CanonicalCgmIdentity
                        .sameMeasurement(it, candidate) ||
                    (it.source == DataSourceId.DEXCOM_SHARE && candidate.source == it.source && it.valueMgDl == candidate.valueMgDl && kotlin.math.abs(it.measuredAtEpochMs - candidate.measuredAtEpochMs) <= 5_000L)
            }
        ) {
            displayed += candidate
        }
    }
    val history = DisplayHistoryAccumulator.merge(previous, current, now)
    return current.copy(
        glucoseHistory = CanonicalCgmHistory.merge(displayed, now, current.source, maxPoints = DisplayHistoryAccumulator.MAX_POINTS),
        therapyHistory = history.therapyHistory,
        // Treatment composition is authoritative, including disabled enrichment filtering.
        therapyEvents = current.therapyEvents,
        targetHistory = history.targetHistory,
    )
}

/** Recover already persisted historical gaps, without letting disabled sources publish live dots. */
internal fun restoreStoredMobileHistory(previous: TherapyDisplayState?, inputs: Map<DataSourceId, TherapyDisplayState>, now: Long): TherapyDisplayState? {
    if (previous == null) return null
    val publishedThrough = (previous.glucoseHistory.map { it.measuredAtEpochMs } + listOfNotNull(previous.glucose?.measuredAtEpochMs)).maxOrNull() ?: return previous
    val existing = previous.glucoseHistory.toMutableList()
    inputs.values.flatMap { it.glucoseHistory }.sortedBy { it.measuredAtEpochMs }.forEach { point ->
        if (point.measuredAtEpochMs <= publishedThrough && existing.none { kotlin.math.abs(it.measuredAtEpochMs - point.measuredAtEpochMs) <= 90_000L }) existing += point
    }
    return previous.copy(glucoseHistory = CanonicalCgmHistory.merge(existing, now, maxPoints = DisplayHistoryAccumulator.MAX_POINTS))
}
