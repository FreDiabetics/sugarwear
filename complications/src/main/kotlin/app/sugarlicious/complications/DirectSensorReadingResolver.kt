package app.sugarlicious.complications

import android.content.Context
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CanonicalCgmSourceResolver
import app.sugarlicious.model.CgmCanonicalSource
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmResolverMemory
import app.sugarlicious.model.CgmSourceCandidate
import app.sugarlicious.model.CgmSourceMode
import app.sugarlicious.model.CgmSourcePolicy
import app.sugarlicious.model.CgmSourceState
import app.sugarlicious.model.DataCapability
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.ValueProvenance
import app.sugarlicious.protocol.WatchDataSource

/**
 * Canonical Wear-side CGM resolver.
 *
 * The standalone G7 collector is an independent input. This resolver only chooses which already
 * available CGM stream is canonical. It never starts, stops, scans, pairs, bonds, or authenticates
 * the G7 collector. In AUTOMATIC mode Mobile is primary, Watch Direct is fallback, Mobile timeout
 * is 15 minutes, and returning Mobile data must pass recovery hysteresis.
 */
object DirectSensorReadingResolver {
    private const val PREFS = "canonical_cgm_resolver"
    private const val KEY_STATE = "state"
    private const val KEY_RECOVERY_COUNT = "recovery_count"
    private const val KEY_RECOVERY_MOBILE_TIMESTAMP = "recovery_mobile_timestamp"
    private const val KEY_RECOVERY_REQUIRED = "recovery_required"

    val defaultPolicy = CgmSourcePolicy()

    @Synchronized
    fun resolve(
        context: Context,
        fallback: TherapyDisplayState?,
        nowEpochMs: Long = System.currentTimeMillis(),
        dataSource: WatchDataSource? = null,
    ): TherapyDisplayState? =
        resolve(
            context = context,
            fallback = fallback,
            nowEpochMs = nowEpochMs,
            dataSource = dataSource,
            provider = AndroidDirectCgmProvider(context.applicationContext),
        )

    internal fun resolve(
        context: Context,
        fallback: TherapyDisplayState?,
        nowEpochMs: Long,
        dataSource: WatchDataSource?,
        provider: DirectCgmProvider,
    ): TherapyDisplayState? {
        val selectedSource =
            dataSource ?: runCatching {
                WatchDataSource.valueOf(
                    context
                        .getSharedPreferences("watch_display", Context.MODE_PRIVATE)
                        .getString("data_source", WatchDataSource.AUTOMATIC.name)!!,
                )
            }.getOrDefault(WatchDataSource.AUTOMATIC)

        val providerOutcome = provider.read()
        recordProviderOutcome(context, providerOutcome, nowEpochMs)
        val snapshot = (providerOutcome as? DirectProviderOutcome.Success)?.snapshot
        val directRows = snapshot?.readings.orEmpty()
        val directStatus = snapshot?.status
        // Persisted rows remain history during a manual pause, never a live source candidate.
        val activeSession =
            directStatus?.takeIf {
                it.collectorEnabled &&
                    !it.sensorId.isNullOrBlank() &&
                    !it.sessionId.isNullOrBlank() &&
                    it.sensorState !in setOf("UNKNOWN", "ENDED", "NOT_ACTIVE")
            }
        val latestDirectEvent =
            activeSession?.let { session ->
                directRows
                    .filter { it.sensorId == session.sensorId && it.sessionId == session.sessionId && it.hasValidTimes(nowEpochMs, defaultPolicy) }
                    .maxByOrNull(DirectReading::measuredAt)
            }
        val latestDirect = latestDirectEvent?.takeIf { it.quality == CgmQuality.VALID }
        val mobileCandidate =
            fallback?.glucose?.takeIf { it.quality == CgmQuality.VALID }?.let { glucose ->
                CgmSourceCandidate(
                    source = CgmCanonicalSource.MOBILE_AAPS,
                    glucoseMgDl = glucose.valueMgDl,
                    measuredAtEpochMs = glucose.measuredAtEpochMs,
                    receivedAtEpochMs = glucose.receivedAtEpochMs ?: fallback.receivedAtEpochMs,
                    sensorId = glucose.sensorId,
                    sessionId = glucose.sessionId,
                    sequenceNumber = glucose.sequenceNumber,
                )
            }
        val watchCandidate = latestDirect?.toCandidate()

        if (fallback == null && latestDirectEvent == null && directStatus == null && directRows.isEmpty()) return null

        val automatic = selectedSource == WatchDataSource.AUTOMATIC
        val previous = if (automatic) readMemory(context) else CgmResolverMemory()
        val resolution =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobileCandidate,
                watch = watchCandidate,
                nowEpochMs = nowEpochMs,
                previous = previous,
                mode = selectedSource.toResolverMode(),
                policy = defaultPolicy,
            )
        if (automatic) writeMemory(context, resolution.memory)

        val watchOnly = selectedSource == WatchDataSource.DEXCOM_G7_WATCH
        val directSensorError =
            latestDirectEvent
                ?.takeIf {
                    selectedSource != WatchDataSource.PHONE &&
                        it.quality == CgmQuality.SENSOR_ERROR &&
                        it.isRecent(nowEpochMs, defaultPolicy)
                }?.toGlucoseState()
        val fallbackSensorError =
            fallback
                ?.glucose
                ?.takeIf { !watchOnly && it.quality == CgmQuality.SENSOR_ERROR }
        val chosenGlucose =
            when (resolution.canonicalSource) {
                CgmCanonicalSource.MOBILE_AAPS -> fallback?.glucose
                CgmCanonicalSource.WATCH_G7_DIRECT -> latestDirect?.toGlucoseState()
                CgmCanonicalSource.NONE ->
                    directSensorError ?: latestDirect?.takeIf { watchOnly }?.toGlucoseState() ?: fallbackSensorError
            }

        val chosenSource =
            when (resolution.canonicalSource) {
                CgmCanonicalSource.MOBILE_AAPS -> fallback?.source ?: DataSourceId.ANDROID_APS
                CgmCanonicalSource.WATCH_G7_DIRECT -> DataSourceId.DEXCOM_G7_WATCH
                CgmCanonicalSource.NONE ->
                    chosenGlucose?.source ?: when (selectedSource) {
                        WatchDataSource.PHONE -> DataSourceId.ANDROID_APS
                        WatchDataSource.DEXCOM_G7_WATCH -> DataSourceId.DEXCOM_G7_WATCH
                        WatchDataSource.AUTOMATIC -> fallback?.source ?: DataSourceId.OTHER
                    }
            }

        val sourceVersion =
            when (resolution.canonicalSource) {
                CgmCanonicalSource.WATCH_G7_DIRECT -> "SugarWear"
                CgmCanonicalSource.MOBILE_AAPS -> fallback?.sourceVersion
                CgmCanonicalSource.NONE ->
                    if (directSensorError != null || (watchOnly && latestDirectEvent != null)) "SugarWear" else fallback?.sourceVersion
            }

        val capabilities =
            if (chosenGlucose?.quality == CgmQuality.VALID) {
                (if (watchOnly) emptySet() else fallback?.capabilities.orEmpty()) +
                    setOf(
                        DataCapability.GLUCOSE,
                        DataCapability.TREND,
                        DataCapability.DELTA,
                    )
            } else {
                (if (watchOnly) emptySet() else fallback?.capabilities.orEmpty()) -
                    setOf(
                        DataCapability.GLUCOSE,
                        DataCapability.TREND,
                        DataCapability.DELTA,
                        DataCapability.AVERAGE_DELTA,
                    )
            }

        val mergedHistory =
            mergeHistory(
                phone = fallback?.glucoseHistory.orEmpty(),
                direct = directRows,
                nowEpochMs = nowEpochMs,
                watchIsCanonical = watchOnly || resolution.canonicalSource == CgmCanonicalSource.WATCH_G7_DIRECT,
                preferredSource = chosenSource,
                watchOnly = watchOnly,
            )

        return TherapyDisplayState(
            source = chosenSource,
            sourceVersion = sourceVersion,
            sourceContract =
                "CANONICAL_CGM_V2:${resolution.state.name}:${resolution.reason}:SENSOR_${directStatus?.sensorState ?: "UNKNOWN"}:SESSION_${if (directStatus?.collectorEnabled == false) "PAUSED" else directStatus?.sessionState ?: "UNINITIALIZED"}:PROVIDER_${providerOutcome.code}",
            receivedAtEpochMs =
                resolution.reading?.receivedAtEpochMs
                    ?: chosenGlucose?.receivedAtEpochMs
                    ?: fallback?.receivedAtEpochMs
                    ?: nowEpochMs,
            glucose = chosenGlucose,
            glucoseHistory = mergedHistory,
            glucosePredictions = fallback?.glucosePredictions.orEmpty(),
            therapyHistory = fallback?.therapyHistory.orEmpty(),
            targetHistory = fallback?.targetHistory.orEmpty(),
            insulin = fallback?.insulin,
            carbs = fallback?.carbs,
            basal = fallback?.basal,
            target = fallback?.target,
            loop = fallback?.loop,
            pump = fallback?.pump,
            device = fallback?.device,
            profile = fallback?.profile,
            capabilities = capabilities,
        )
    }

    /** Resolves the collector stream without ever exposing phone/AAPS glucose or history. */
    fun resolveWatchDirect(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TherapyDisplayState? =
        resolve(
            context = context,
            fallback = null,
            nowEpochMs = nowEpochMs,
            dataSource = WatchDataSource.DEXCOM_G7_WATCH,
        )

    fun sourceState(state: TherapyDisplayState?): CgmSourceState? =
        state
            ?.sourceContract
            ?.takeIf { it.startsWith("CANONICAL_CGM_V2:") }
            ?.substringAfter("CANONICAL_CGM_V2:")
            ?.substringBefore(':')
            ?.let { runCatching { CgmSourceState.valueOf(it) }.getOrNull() }

    fun directSensorState(state: TherapyDisplayState?): String? =
        state
            ?.sourceContract
            ?.substringAfter(":SENSOR_", "")
            ?.takeIf(String::isNotBlank)
            ?.substringBefore(':')

    fun directSessionState(state: TherapyDisplayState?): String? =
        state
            ?.sourceContract
            ?.substringAfter(":SESSION_", "")
            ?.takeIf(String::isNotBlank)
            ?.substringBefore(':')

    private fun mergeHistory(
        phone: List<GlucoseSample>,
        direct: List<DirectReading>,
        nowEpochMs: Long,
        watchIsCanonical: Boolean,
        preferredSource: DataSourceId,
        watchOnly: Boolean,
    ): List<GlucoseSample> {
        val directSamples =
            direct.map {
                GlucoseSample(
                    valueMgDl = it.value,
                    measuredAtEpochMs = it.measuredAt,
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = it.sensorId,
                    sessionId = it.sessionId,
                    sequenceNumber = it.sequenceNumber,
                    receivedAtEpochMs = it.receivedAt,
                    quality = it.quality,
                )
            }
        return canonicalWearHistory(
            phone = if (watchOnly) emptyList() else phone,
            direct = directSamples,
            nowEpochMs = nowEpochMs,
            watchIsCanonical = watchIsCanonical,
            preferredSource = preferredSource,
        )
    }

    private fun readMemory(context: Context): CgmResolverMemory {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state =
            prefs
                .getString(KEY_STATE, CgmSourceState.NO_SOURCE.name)
                ?.let { runCatching { CgmSourceState.valueOf(it) }.getOrNull() }
                ?: CgmSourceState.NO_SOURCE
        val lastRecoveryTimestamp =
            prefs
                .getLong(KEY_RECOVERY_MOBILE_TIMESTAMP, Long.MIN_VALUE)
                .takeUnless { it == Long.MIN_VALUE }
        return CgmResolverMemory(
            state = state,
            recoveryReadingCount = prefs.getInt(KEY_RECOVERY_COUNT, 0),
            lastRecoveryMobileTimestampEpochMs = lastRecoveryTimestamp,
            recoveryRequired = prefs.getBoolean(KEY_RECOVERY_REQUIRED, state == CgmSourceState.WATCH_DIRECT || state == CgmSourceState.MOBILE_RECOVERY),
        )
    }

    private fun writeMemory(
        context: Context,
        memory: CgmResolverMemory,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, memory.state.name)
            .putInt(KEY_RECOVERY_COUNT, memory.recoveryReadingCount)
            .putBoolean(KEY_RECOVERY_REQUIRED, memory.recoveryRequired)
            .apply {
                val timestamp = memory.lastRecoveryMobileTimestampEpochMs
                if (timestamp == null) {
                    remove(KEY_RECOVERY_MOBILE_TIMESTAMP)
                } else {
                    putLong(KEY_RECOVERY_MOBILE_TIMESTAMP, timestamp)
                }
            }.apply()
    }

    private fun WatchDataSource.toResolverMode(): CgmSourceMode =
        when (this) {
            WatchDataSource.AUTOMATIC -> CgmSourceMode.AUTOMATIC
            WatchDataSource.PHONE -> CgmSourceMode.MOBILE_ONLY
            WatchDataSource.DEXCOM_G7_WATCH -> CgmSourceMode.WATCH_ONLY
        }

    private fun DirectReading.toCandidate(): CgmSourceCandidate =
        CgmSourceCandidate(
            source = CgmCanonicalSource.WATCH_G7_DIRECT,
            glucoseMgDl = value,
            measuredAtEpochMs = measuredAt,
            receivedAtEpochMs = receivedAt,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
        )

    private fun DirectReading.isRecent(
        nowEpochMs: Long,
        policy: CgmSourcePolicy,
    ): Boolean =
        hasValidTimes(nowEpochMs, policy) &&
            (nowEpochMs - measuredAt).coerceAtLeast(0L) <= policy.watchFreshAfterMs

    private fun DirectReading.hasValidTimes(nowEpochMs: Long, policy: CgmSourcePolicy): Boolean =
        measuredAt <= nowEpochMs + policy.futureToleranceMs &&
            receivedAt >= measuredAt - policy.futureToleranceMs &&
            receivedAt <= nowEpochMs + policy.futureToleranceMs

    private fun DirectReading.toGlucoseState(): GlucoseState =
        GlucoseState(
            valueMgDl = value,
            displayUnit = GlucoseUnit.MG_DL,
            trend = trend,
            measuredAtEpochMs = measuredAt,
            deltaMgDl = delta,
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
            receivedAtEpochMs = receivedAt,
            quality = quality,
            trendRateMgDlPerMinute = trendRateMgDlPerMinute,
            trendOrigin = if (trend == Trend.UNKNOWN) ValueProvenance.UNAVAILABLE else ValueProvenance.DERIVED,
        )
}

private val DirectProviderOutcome.code: String
    get() =
        when (this) {
            is DirectProviderOutcome.Success -> "SUCCESS"
            DirectProviderOutcome.Empty -> "EMPTY"
            is DirectProviderOutcome.Failure -> kind.name
        }

private fun recordProviderOutcome(
    context: Context,
    outcome: DirectProviderOutcome,
    nowEpochMs: Long,
) {
    val prefs = context.getSharedPreferences("direct_provider_diagnostics", Context.MODE_PRIVATE)
    val code = outcome.code
    val previousCode = prefs.getString("last_code", null)
    val previousAt = prefs.getLong("last_at", Long.MIN_VALUE)
    if (code == previousCode && nowEpochMs - previousAt < 5 * 60_000L) return
    prefs
        .edit()
        .putString("last_code", code)
        .putLong("last_at", nowEpochMs)
        .apply()
}

/** Keeps collector persistence independent from the one canonical history exposed to Wear UI. */
internal fun canonicalWearHistory(
    phone: List<GlucoseSample>,
    direct: List<GlucoseSample>,
    nowEpochMs: Long,
    watchIsCanonical: Boolean,
    preferredSource: DataSourceId,
): List<GlucoseSample> =
    CanonicalCgmHistory.merge(
        samples = if (watchIsCanonical) direct + phone else phone,
        nowEpochMs = nowEpochMs,
        preferredSource = preferredSource,
        windowMs = 24 * 60 * 60_000L,
        futureToleranceMs = CgmSourcePolicy().futureToleranceMs,
        maxPoints = 300,
    )
