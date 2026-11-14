package app.sugarlicious.model

/** Shared AAPS-style target timeline used by both phone and Watch graph renderers. */
object TargetStepTimeline {
    private const val PROFILE_OBSERVATION_TOLERANCE_MS = 7L * 60_000L + 30_000L

    fun build(
        samples: List<TargetSample>,
        start: Long,
        end: Long,
        continuityToleranceMs: Long = 90_000L,
    ): List<List<Pair<Long, Double>>> {
        if (end <= start || continuityToleranceMs < 0L) return emptyList()
        val normalized =
            samples
                .asSequence()
                .filter { it.valueMgDl.isFinite() && it.endsAtEpochMs >= start && it.startedAtEpochMs <= end }
                .sortedBy(TargetSample::startedAtEpochMs)
                .map { sample ->
                    Segment(
                        from = sample.startedAtEpochMs.coerceIn(start, end),
                        to = sample.endsAtEpochMs.coerceIn(start, end),
                        value = sample.valueMgDl,
                        temporary = sample.temporary,
                    )
                }.filter { it.to >= it.from }
                .toList()
        if (normalized.isEmpty()) return emptyList()

        val paths = mutableListOf<MutableList<Pair<Long, Double>>>()
        var previous: Segment? = null
        normalized.forEach { segment ->
            val currentPath = paths.lastOrNull()
            val prior = previous
            val allowedGap =
                if (prior?.temporary == true && !segment.temporary) {
                    maxOf(continuityToleranceMs, PROFILE_OBSERVATION_TOLERANCE_MS)
                } else {
                    continuityToleranceMs
                }
            val continuous = currentPath != null && prior != null && segment.from <= prior.to + allowedGap
            if (!continuous) {
                paths += mutableListOf(segment.from to segment.value, segment.to to segment.value)
            } else {
                // A temp target has an authoritative end. If the first profile-target observation
                // follows a little later, switch exactly at that explicit end instead of leaving a
                // visual gap or extending the temp target until the next CGM packet.
                val transitionAt =
                    if (prior.temporary && !segment.temporary && segment.from >= prior.to) prior.to else segment.from
                val priorValue = currentPath.last().second
                when {
                    currentPath.last().first > transitionAt -> currentPath[currentPath.lastIndex] = transitionAt to priorValue
                    currentPath.last().first < transitionAt -> currentPath += transitionAt to priorValue
                }
                if (segment.value != priorValue) currentPath += transitionAt to segment.value
                if (currentPath.last().first < segment.to || currentPath.last().second != segment.value) {
                    currentPath += segment.to to segment.value
                }
            }
            previous = if (prior == null || segment.to >= prior.to) segment else prior
        }
        return paths
    }

    private data class Segment(
        val from: Long,
        val to: Long,
        val value: Double,
        val temporary: Boolean,
    )
}
