package app.sugarlicious.model

/** Complete missing live metadata after history arrives, without advancing the measurement. */
fun TherapyDisplayState.withHistoryDerivedDelta(profile: TrendRateProfile): TherapyDisplayState {
    val current = glucose?.takeIf { it.source == source } ?: return this
    if (current.deltaMgDl?.isFinite() == true) return this
    val sample = GlucoseSample(current.valueMgDl, current.measuredAtEpochMs, current.source, current.sensorId, current.sessionId, current.sequenceNumber, current.receivedAtEpochMs, current.quality)
    val derived = CanonicalTrendPolicy.derive(sample, glucoseHistory, profile) ?: return this
    val missingTrend = current.trend == Trend.UNKNOWN
    return copy(
        glucose =
            current.copy(
                deltaMgDl = derived.deltaMgDl,
                trendRateMgDlPerMinute = current.trendRateMgDlPerMinute?.takeIf { it.isFinite() } ?: derived.rateMgDlPerMinute,
                trend = if (missingTrend) derived.trend else current.trend,
                trendOrigin = if (missingTrend) ValueProvenance.DERIVED else current.trendOrigin,
            ),
        capabilities = capabilities + DataCapability.DELTA + if (missingTrend) setOf(DataCapability.TREND) else emptySet(),
        fieldSources = fieldSources + (CanonicalDataField.DELTA to current.source) + if (missingTrend) mapOf(CanonicalDataField.TREND to current.source) else emptyMap(),
        fieldProvenance = fieldProvenance + (CanonicalDataField.DELTA to ValueProvenance.DERIVED) + if (missingTrend) mapOf(CanonicalDataField.TREND to ValueProvenance.DERIVED) else emptyMap(),
    )
}
