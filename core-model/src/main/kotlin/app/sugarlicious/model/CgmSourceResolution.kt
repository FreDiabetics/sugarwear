package app.sugarlicious.model

/** Canonical CGM inputs known to Sugarlicious Wear. */
enum class CgmCanonicalSource {
    MOBILE_AAPS,
    WATCH_G7_DIRECT,
    NONE,
}

/**
 * Source-health state used by the Wear-side canonical CGM resolver.
 *
 * The state describes the resolver, not the BLE collector lifecycle. In particular, WATCH_DIRECT
 * means that the already available direct Watch stream is canonical; it never means "start BLE".
 */
enum class CgmSourceState {
    MOBILE_PRIMARY,
    MOBILE_DEGRADED,
    WATCH_DIRECT,
    MOBILE_RECOVERY,
    NO_SOURCE,
}

enum class CgmSourceMode {
    AUTOMATIC,
    MOBILE_ONLY,
    WATCH_ONLY,
}

data class CgmSourcePolicy(
    val mobileDegradedAfterMs: Long = 7 * 60_000L,
    val mobileFailoverAfterMs: Long = 15 * 60_000L,
    val watchFreshAfterMs: Long = 12 * 60_000L,
    val recoveryReadingsRequired: Int = 2,
    val futureToleranceMs: Long = 5 * 60_000L,
) {
    init {
        require(mobileDegradedAfterMs > 0L)
        require(mobileFailoverAfterMs > mobileDegradedAfterMs)
        require(watchFreshAfterMs > 0L)
        require(recoveryReadingsRequired > 0)
        require(futureToleranceMs >= 0L)
    }
}

data class CgmSourceCandidate(
    val source: CgmCanonicalSource,
    val glucoseMgDl: Double,
    val measuredAtEpochMs: Long,
    val receivedAtEpochMs: Long,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val sequenceNumber: Long? = null,
) {
    init {
        require(source != CgmCanonicalSource.NONE)
    }
}

data class CgmResolverMemory(
    val state: CgmSourceState = CgmSourceState.NO_SOURCE,
    val recoveryReadingCount: Int = 0,
    val lastRecoveryMobileTimestampEpochMs: Long? = null,
    val recoveryRequired: Boolean = false,
)

data class CgmSourceResolution(
    val state: CgmSourceState,
    val canonicalSource: CgmCanonicalSource,
    val reading: CgmSourceCandidate?,
    val memory: CgmResolverMemory,
    val deduplicatedSameMeasurement: Boolean = false,
    val reason: String,
)

/**
 * Deterministic Wear-side CGM source state machine.
 *
 * MOBILE_AAPS is preferred while it is healthy. WATCH_G7_DIRECT is a continuously independent
 * input when available, but it becomes canonical only when Mobile has timed out or while Mobile
 * is still proving recovery. The resolver never starts,
 * stops, pairs, scans, bonds, or authenticates a BLE collector.
 */
object CanonicalCgmSourceResolver {
    fun resolve(
        mobile: CgmSourceCandidate?,
        watch: CgmSourceCandidate?,
        nowEpochMs: Long,
        previous: CgmResolverMemory = CgmResolverMemory(),
        mode: CgmSourceMode = CgmSourceMode.AUTOMATIC,
        policy: CgmSourcePolicy = CgmSourcePolicy(),
    ): CgmSourceResolution {
        val validMobile = mobile?.takeIf { it.isValid(nowEpochMs, policy.futureToleranceMs) }
        val validWatch = watch?.takeIf { it.isValid(nowEpochMs, policy.futureToleranceMs) }
        val mobileAge = validMobile?.ageAt(nowEpochMs)
        val watchAge = validWatch?.ageAt(nowEpochMs)
        val mobileUsable = validMobile?.takeIf { mobileAge != null && mobileAge <= policy.mobileFailoverAfterMs }
        val watchUsable = validWatch?.takeIf { watchAge != null && watchAge <= policy.watchFreshAfterMs }
        val sameMeasurement = CanonicalCgmIdentity.sameMeasurement(mobileUsable, watchUsable)

        return when (mode) {
            CgmSourceMode.MOBILE_ONLY ->
                mobileOnly(
                    mobile = mobileUsable,
                    ageMs = mobileAge,
                    policy = policy,
                    previous = previous,
                )

            CgmSourceMode.WATCH_ONLY ->
                if (watchUsable != null) {
                    resolution(
                        state = CgmSourceState.WATCH_DIRECT,
                        reading = watchUsable,
                        previous = previous,
                        reason = "watch_only_fresh",
                    )
                } else {
                    noSource(previous, "watch_only_no_fresh_reading")
                }

            CgmSourceMode.AUTOMATIC ->
                automatic(
                    mobile = mobileUsable,
                    watch = watchUsable,
                    mobileAgeMs = mobileAge,
                    previous = previous,
                    policy = policy,
                    sameMeasurement = sameMeasurement,
                )
        }
    }

    private fun automatic(
        mobile: CgmSourceCandidate?,
        watch: CgmSourceCandidate?,
        mobileAgeMs: Long?,
        previous: CgmResolverMemory,
        policy: CgmSourcePolicy,
        sameMeasurement: Boolean,
    ): CgmSourceResolution {
        if (mobile == null) {
            return if (watch != null) {
                resolution(
                    state = CgmSourceState.WATCH_DIRECT,
                    reading = watch,
                    previous = previous,
                    reason = "mobile_missing_or_timed_out",
                )
            } else {
                noSource(previous, "no_fresh_source")
            }
        }

        if (previous.requiresRecovery()) {
            val mobileFreshForRecovery =
                mobileAgeMs != null && mobileAgeMs <= policy.mobileDegradedAfterMs
            val nextRecoveryCount =
                if (!mobileFreshForRecovery) {
                    0
                } else if (
                    previous.lastRecoveryMobileTimestampEpochMs == null ||
                    mobile.measuredAtEpochMs > previous.lastRecoveryMobileTimestampEpochMs
                ) {
                    previous.recoveryReadingCount + 1
                } else {
                    previous.recoveryReadingCount
                }

            if (nextRecoveryCount < policy.recoveryReadingsRequired) {
                val nextState = if (watch == null) CgmSourceState.NO_SOURCE else CgmSourceState.MOBILE_RECOVERY
                return CgmSourceResolution(
                    state = nextState,
                    canonicalSource = watch?.source ?: CgmCanonicalSource.NONE,
                    reading = watch,
                    memory =
                        CgmResolverMemory(
                            state = nextState,
                            recoveryReadingCount = nextRecoveryCount,
                            lastRecoveryMobileTimestampEpochMs =
                                if (mobileFreshForRecovery) maxOf(previous.lastRecoveryMobileTimestampEpochMs ?: Long.MIN_VALUE, mobile.measuredAtEpochMs) else null,
                            recoveryRequired = true,
                        ),
                    deduplicatedSameMeasurement = sameMeasurement,
                    reason = "mobile_recovery_hysteresis",
                )
            }
        }

        if (mobileAgeMs != null && mobileAgeMs > policy.mobileDegradedAfterMs) {
            return resolution(
                state = CgmSourceState.MOBILE_DEGRADED,
                reading = mobile,
                previous = previous,
                reason = "mobile_degraded_but_inside_failover_timeout",
                deduplicated = sameMeasurement,
            )
        }

        return resolution(
            state = CgmSourceState.MOBILE_PRIMARY,
            reading = mobile,
            previous = previous,
            reason = if (sameMeasurement) "same_measurement_mobile_preferred" else "mobile_primary_fresh",
            deduplicated = sameMeasurement,
        )
    }

    private fun mobileOnly(
        mobile: CgmSourceCandidate?,
        ageMs: Long?,
        policy: CgmSourcePolicy,
        previous: CgmResolverMemory,
    ): CgmSourceResolution {
        if (mobile == null || ageMs == null) return noSource(previous, "mobile_only_no_fresh_reading")
        val state =
            if (ageMs > policy.mobileDegradedAfterMs) {
                CgmSourceState.MOBILE_DEGRADED
            } else {
                CgmSourceState.MOBILE_PRIMARY
            }
        return resolution(
            state = state,
            reading = mobile,
            previous = previous,
            reason = if (state == CgmSourceState.MOBILE_PRIMARY) "mobile_only_fresh" else "mobile_only_degraded",
        )
    }

    private fun resolution(
        state: CgmSourceState,
        reading: CgmSourceCandidate,
        previous: CgmResolverMemory,
        reason: String,
        deduplicated: Boolean = false,
    ): CgmSourceResolution =
        CgmSourceResolution(
            state = state,
            canonicalSource = reading.source,
            reading = reading,
            memory =
                CgmResolverMemory(
                    state = state,
                    recoveryReadingCount = 0,
                    lastRecoveryMobileTimestampEpochMs = null,
                    recoveryRequired = state == CgmSourceState.WATCH_DIRECT,
                ),
            deduplicatedSameMeasurement = deduplicated,
            reason = reason,
        )

    private fun noSource(
        previous: CgmResolverMemory,
        reason: String,
    ): CgmSourceResolution =
        CgmSourceResolution(
            state = CgmSourceState.NO_SOURCE,
            canonicalSource = CgmCanonicalSource.NONE,
            reading = null,
            memory = CgmResolverMemory(state = CgmSourceState.NO_SOURCE, recoveryRequired = previous.requiresRecovery()),
            reason = reason,
        )

    private fun CgmResolverMemory.requiresRecovery(): Boolean =
        recoveryRequired || state == CgmSourceState.WATCH_DIRECT || state == CgmSourceState.MOBILE_RECOVERY

    private fun CgmSourceCandidate.isValid(
        nowEpochMs: Long,
        futureToleranceMs: Long,
    ): Boolean =
        glucoseMgDl.isFinite() &&
            glucoseMgDl in 20.0..1000.0 &&
            measuredAtEpochMs <= nowEpochMs + futureToleranceMs &&
            receivedAtEpochMs >= measuredAtEpochMs - futureToleranceMs &&
            receivedAtEpochMs <= nowEpochMs + futureToleranceMs

    private fun CgmSourceCandidate.ageAt(nowEpochMs: Long): Long = (nowEpochMs - measuredAtEpochMs).coerceAtLeast(0L)
}
