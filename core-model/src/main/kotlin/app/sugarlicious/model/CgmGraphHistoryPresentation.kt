package app.sugarlicious.model

/** Styling of verified sensor changes, independent of canonical source selection. */
data class CgmGraphSensorIdentity(
    val source: DataSourceId,
    val sensorId: String? = null,
    val sessionId: String? = null,
)

object CgmGraphHistoryPresentation {
    const val RETIRED_OUTLINE_WIDTH_DP = 1f
    const val RETIRED_ALPHA = 110

    fun identity(glucose: GlucoseState?): CgmGraphSensorIdentity? =
        glucose?.let { knownIdentity(it.source, it.sensorId, it.sessionId) }

    fun latestIdentity(history: List<GlucoseSample>): CgmGraphSensorIdentity? =
        history.filter { it.quality == CgmQuality.VALID }.maxByOrNull(GlucoseSample::measuredAtEpochMs)?.let { knownIdentity(it.source, it.sensorId, it.sessionId) }

    fun isRetired(sample: GlucoseSample, active: CgmGraphSensorIdentity?): Boolean {
        if (active == null || sample.source != active.source) return false
        val sensorChanged = !sample.sensorId.isNullOrBlank() && !active.sensorId.isNullOrBlank() && sample.sensorId != active.sensorId
        val sessionChanged = !sample.sessionId.isNullOrBlank() && !active.sessionId.isNullOrBlank() && sample.sessionId != active.sessionId
        return sensorChanged || sessionChanged
    }

    private fun knownIdentity(source: DataSourceId, sensor: String?, session: String?): CgmGraphSensorIdentity? =
        if (sensor.isNullOrBlank() && session.isNullOrBlank()) null else CgmGraphSensorIdentity(source, sensor, session)
}
