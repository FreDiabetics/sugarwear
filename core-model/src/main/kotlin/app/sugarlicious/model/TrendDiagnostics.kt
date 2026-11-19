package app.sugarlicious.model

object TrendDiagnostics {
    fun format(
        glucoseMgDl: Double,
        deltaMgDl: Double?,
        elapsedMinutes: Double?,
        rateMgDlPerMinute: Double?,
        sourceTrend: Trend,
        canonicalTrend: Trend,
        source: DataSourceId,
    ): String =
        buildString {
            append("glucose=").append(glucoseMgDl)
            append(" delta=").append(deltaMgDl ?: "UNKNOWN")
            append(" periodMin=").append(elapsedMinutes ?: "UNKNOWN")
            append(" rateMgDlMin=").append(rateMgDlPerMinute ?: "UNKNOWN")
            append(" sourceTrend=").append(sourceTrend.name)
            append(" canonical=").append(canonicalTrend.name)
            append(" source=").append(source.name)
            append(" asset=").append(TrendVisuals.spec(canonicalTrend)?.asset?.name ?: "NONE")
        }
}
