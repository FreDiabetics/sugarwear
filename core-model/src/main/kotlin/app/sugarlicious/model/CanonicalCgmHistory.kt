package app.sugarlicious.model

import java.util.TreeMap

/** Shared validation, ordering and identity-safe deduplication for canonical CGM history. */
object CanonicalCgmHistory {
    const val DEFAULT_WINDOW_MS = 24L * 60L * 60_000L
    const val DEFAULT_FUTURE_TOLERANCE_MS = 5L * 60_000L
    const val DEFAULT_MAX_POINTS = 300

    fun merge(
        samples: List<GlucoseSample>,
        nowEpochMs: Long,
        preferredSource: DataSourceId? = null,
        windowMs: Long = DEFAULT_WINDOW_MS,
        futureToleranceMs: Long = DEFAULT_FUTURE_TOLERANCE_MS,
        maxPoints: Int = DEFAULT_MAX_POINTS,
    ): List<GlucoseSample> {
        require(windowMs > 0L)
        require(futureToleranceMs >= 0L)
        require(maxPoints > 0)
        val result = mutableListOf<GlucoseSample>()
        // Identity can only match within the timestamp tolerance. Keep original result
        // indices so ambiguous matches retain the same first-match/preference semantics.
        val timeIndex = TreeMap<Long, MutableSet<Int>>()

        fun index(timestamp: Long, position: Int) {
            timeIndex.getOrPut(timestamp) { mutableSetOf() }.add(position)
        }

        samples
            .asSequence()
            .filter {
                it.quality == CgmQuality.VALID &&
                    it.valueMgDl.isFinite() &&
                    it.valueMgDl in 20.0..1_000.0 &&
                    nowEpochMs - it.measuredAtEpochMs <= windowMs &&
                    it.measuredAtEpochMs <= nowEpochMs + futureToleranceMs &&
                    (
                        it.receivedAtEpochMs == null ||
                            (
                                it.receivedAtEpochMs >= it.measuredAtEpochMs - futureToleranceMs &&
                                    it.receivedAtEpochMs <= nowEpochMs + futureToleranceMs
                            )
                    )
            }.sortedWith(CANONICAL_ORDER)
            .forEach { candidate ->
                val tolerance = CanonicalCgmIdentity.HISTORY_TIMESTAMP_TOLERANCE_MS
                val duplicateIndex =
                    timeIndex
                        .subMap(candidate.measuredAtEpochMs - tolerance, true, candidate.measuredAtEpochMs + tolerance, true)
                        .values
                        .asSequence()
                        .flatMap { it.asSequence() }
                        .filter { CanonicalCgmIdentity.sameMeasurement(result[it], candidate) }
                        .minOrNull() ?: -1
                if (duplicateIndex < 0) {
                    index(candidate.measuredAtEpochMs, result.size)
                    result += candidate
                } else {
                    val previous = result[duplicateIndex]
                    val preferred = prefer(previous, candidate, preferredSource)
                    if (preferred.measuredAtEpochMs != previous.measuredAtEpochMs) {
                        timeIndex[previous.measuredAtEpochMs]?.let { positions ->
                            positions.remove(duplicateIndex)
                            if (positions.isEmpty()) timeIndex.remove(previous.measuredAtEpochMs)
                        }
                        index(preferred.measuredAtEpochMs, duplicateIndex)
                    }
                    result[duplicateIndex] = preferred
                }
            }

        return result.sortedWith(CANONICAL_ORDER).takeLast(maxPoints)
    }

    private fun prefer(
        existing: GlucoseSample,
        candidate: GlucoseSample,
        preferredSource: DataSourceId?,
    ): GlucoseSample =
        when {
            // A phone-originated value is the canonical historical representation whenever the same
            // real measurement also arrived through Watch Direct. The Watch copy remains persisted
            // for offline gap filling, but must not produce a second dot or replace the Phone dot when
            // the live resolver temporarily switches to WATCH_DIRECT.
            existing.source.isPhoneHistorySource() && candidate.source == DataSourceId.DEXCOM_G7_WATCH -> existing
            candidate.source.isPhoneHistorySource() && existing.source == DataSourceId.DEXCOM_G7_WATCH -> candidate
            existing.source == preferredSource && candidate.source != preferredSource -> existing
            candidate.source == preferredSource && existing.source != preferredSource -> candidate
            (candidate.receivedAtEpochMs ?: candidate.measuredAtEpochMs) >=
                (existing.receivedAtEpochMs ?: existing.measuredAtEpochMs) -> candidate
            else -> existing
        }

    private fun DataSourceId.isPhoneHistorySource(): Boolean = this != DataSourceId.DEXCOM_G7_WATCH

    private val CANONICAL_ORDER =
        compareBy<GlucoseSample>(
            GlucoseSample::measuredAtEpochMs,
            { it.sensorId.orEmpty() },
            { it.sessionId.orEmpty() },
            { it.source.name },
            { it.sequenceNumber ?: Long.MIN_VALUE },
            { it.receivedAtEpochMs ?: Long.MIN_VALUE },
            GlucoseSample::valueMgDl,
        )
}
