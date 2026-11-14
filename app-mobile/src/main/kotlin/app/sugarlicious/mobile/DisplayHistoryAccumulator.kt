package app.sugarlicious.mobile

import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.DeviceState
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.LoopState
import app.sugarlicious.model.PumpState
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyHistorySample
import app.sugarlicious.storage.PersistentPredictionCache

/** Keeps a bounded graph cache inside the single latest display state. */
internal object DisplayHistoryAccumulator {
    const val WINDOW_MS = 24 * 60 * 60_000L

    // The cache is already bounded by WINDOW_MS. Keep enough room for sources that publish
    // therapy/basal/activity values every minute; 300 points truncated those streams after 5 h.
    const val MAX_POINTS = 2_000
    const val GAP_THRESHOLD_MS = 7 * 60_000L + 30_000L

    /** True when two persisted CGM points are farther apart than a normal 5-minute cycle. */
    fun hasGap(history: List<GlucoseSample>): Boolean =
        history.groupBy { it.sensorId to it.sessionId }.values.any { stream ->
            stream.sortedBy { it.measuredAtEpochMs }.zipWithNext().any { (first, second) ->
                second.measuredAtEpochMs - first.measuredAtEpochMs > GAP_THRESHOLD_MS
            }
        }

    fun merge(
        previous: TherapyDisplayState?,
        current: TherapyDisplayState,
        nowEpochMs: Long,
    ): TherapyDisplayState {
        val profile =
            current.profile?.let { incoming ->
                incoming.copy(diaHours = incoming.diaHours ?: previous?.profile?.diaHours)
            } ?: previous?.profile
        val glucose =
            mergeGlucose(
                buildList {
                    addAll(previous?.glucoseHistory.orEmpty())
                    previous?.glucose?.let { add(it.toSample(previous.source)) }
                    addAll(current.glucoseHistory)
                    current.glucose?.let { add(it.toSample(current.source)) }
                },
                nowEpochMs,
                current.source,
            )

        val earliest = nowEpochMs - WINDOW_MS
        val latest = nowEpochMs + 5 * 60_000L
        val therapyCandidates =
            buildList {
                addAll(previous?.therapyHistory.orEmpty())
                addAll(current.therapyHistory)
                val timestamp = current.glucose?.measuredAtEpochMs ?: current.receivedAtEpochMs
                val sample =
                    TherapyHistorySample(
                        measuredAtEpochMs = timestamp,
                        totalIob = current.insulin?.totalIob,
                        cobGrams = current.carbs?.cobGrams,
                        basalUnitsPerHour =
                            current.basal?.tempAbsoluteUnitsPerHour
                                ?: current.basal?.currentUnitsPerHour,
                        baseBasalUnitsPerHour = current.basal?.currentUnitsPerHour,
                        tempBasalUnitsPerHour = current.basal?.tempAbsoluteUnitsPerHour,
                    )
                if (sample.totalIob != null || sample.cobGrams != null || sample.basalUnitsPerHour != null) add(sample)
                val loop = current.loop
                loop?.smbUnits?.takeIf { it.isFinite() && it > 0.0 }?.let { units ->
                    loop.smbAtEpochMs?.takeIf { it > 0 && it <= nowEpochMs }?.let { deliveredAt ->
                        add(TherapyHistorySample(measuredAtEpochMs = deliveredAt, smbUnits = units, smbDeliveredAtEpochMs = deliveredAt))
                    }
                }
            }.filter { it.measuredAtEpochMs in earliest..latest }
        val therapy = retainTherapyHistory(therapyCandidates)
        val therapyEvents =
            (previous?.therapyEvents.orEmpty() + current.therapyEvents)
                .asSequence()
                .filter { it.timestampEpochMs in earliest..latest && it.amount.isFinite() }
                .distinctBy { it.id }
                .sortedBy { it.timestampEpochMs }
                .toList()

        val retained =
            current.copy(
                // A missing field in a transport update is absence of new information, not a
                // clinical transition to zero/off/unknown. Explicit values still replace prior ones.
                glucose = current.glucose ?: previous?.glucose,
                insulin = mergeInsulin(previous?.insulin, current.insulin),
                carbs = mergeCarbs(previous?.carbs, current.carbs),
                basal = mergeBasal(previous?.basal, current.basal),
                target = mergeTarget(previous?.target, current.target),
                loop = mergeLoop(previous?.loop, current.loop),
                pump = mergePump(previous?.pump, current.pump),
                device = mergeDevice(previous?.device, current.device),
                profile = profile,
                therapyBridgeDisplaySemantics = current.therapyBridgeDisplaySemantics ?: previous?.therapyBridgeDisplaySemantics,
                capabilities = current.capabilities + previous?.capabilities.orEmpty(),
                fieldProvenance = previous?.fieldProvenance.orEmpty() + current.fieldProvenance,
            )

        return PersistentPredictionCache.merge(
            previous = previous,
            incoming =
                retained.copy(
                    glucoseHistory = glucose,
                    therapyHistory = therapy,
                    therapyEvents = therapyEvents,
                    targetHistory = mergeTargetHistory(previous?.targetHistory.orEmpty(), current.targetHistory, nowEpochMs),
                ),
            nowEpochMs = nowEpochMs,
        )
    }

    fun mergeExternalHistory(
        current: TherapyDisplayState,
        external: List<GlucoseSample>,
        nowEpochMs: Long,
    ): TherapyDisplayState =
        current.copy(
            glucoseHistory =
                mergeGlucose(
                    buildList {
                        addAll(current.glucoseHistory)
                        current.glucose?.let { add(it.toSample(current.source)) }
                        addAll(external)
                    },
                    nowEpochMs,
                    current.source,
                ),
        )

    private fun mergeGlucose(
        values: List<GlucoseSample>,
        nowEpochMs: Long,
        preferredSource: DataSourceId,
    ): List<GlucoseSample> =
        CanonicalCgmHistory.merge(
            samples = values,
            nowEpochMs = nowEpochMs,
            preferredSource = preferredSource,
            windowMs = WINDOW_MS,
            maxPoints = MAX_POINTS,
        )

    private fun mergeTargetHistory(
        previous: List<TargetSample>,
        incoming: List<TargetSample>,
        nowEpochMs: Long,
    ): List<TargetSample> {
        val earliest = nowEpochMs - WINDOW_MS
        val latest = nowEpochMs + 5 * 60_000L
        val normalized =
            (previous + incoming)
                .asSequence()
                .filter {
                    it.valueMgDl.isFinite() &&
                        it.valueMgDl in 20.0..1_000.0 &&
                        it.startedAtEpochMs <= latest &&
                        it.endsAtEpochMs >= earliest
                }.map {
                    it.copy(
                        startedAtEpochMs = it.startedAtEpochMs.coerceAtLeast(earliest),
                        endsAtEpochMs = it.endsAtEpochMs.coerceIn(it.startedAtEpochMs, latest),
                    )
                }.sortedBy(TargetSample::startedAtEpochMs)
                .toList()

        val result = mutableListOf<TargetSample>()
        normalized.forEach { sample ->
            val prior = result.lastOrNull()
            if (prior == null) {
                result += sample
                return@forEach
            }

            if (
                prior.valueMgDl == sample.valueMgDl &&
                prior.temporary == sample.temporary &&
                sample.startedAtEpochMs - prior.endsAtEpochMs <= 15L * 60_000L
            ) {
                result[result.lastIndex] =
                    prior.copy(endsAtEpochMs = maxOf(prior.endsAtEpochMs, sample.endsAtEpochMs, sample.startedAtEpochMs))
            } else {
                if (!prior.temporary && prior.endsAtEpochMs < sample.startedAtEpochMs) {
                    result[result.lastIndex] = prior.copy(endsAtEpochMs = sample.startedAtEpochMs)
                }
                result += sample
            }
        }
        return result.takeLast(MAX_POINTS)
    }

    private fun GlucoseState.toSample(fallbackSource: DataSourceId): GlucoseSample =
        GlucoseSample(
            valueMgDl = valueMgDl,
            measuredAtEpochMs = measuredAtEpochMs,
            source = fallbackSource,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
            receivedAtEpochMs = receivedAtEpochMs,
            quality = quality,
        )

    private fun TherapyHistorySample.merge(
        other: TherapyHistorySample,
        timestamp: Long,
    ) = TherapyHistorySample(
        measuredAtEpochMs = timestamp,
        totalIob = other.totalIob ?: totalIob,
        cobGrams = other.cobGrams ?: cobGrams,
        basalUnitsPerHour = other.basalUnitsPerHour ?: basalUnitsPerHour,
        baseBasalUnitsPerHour = other.baseBasalUnitsPerHour ?: baseBasalUnitsPerHour,
        tempBasalUnitsPerHour = other.tempBasalUnitsPerHour ?: tempBasalUnitsPerHour,
        insulinActivityUnitsPerMinute = other.insulinActivityUnitsPerMinute ?: insulinActivityUnitsPerMinute,
        smbUnits = other.smbUnits ?: smbUnits,
        smbDeliveredAtEpochMs = other.smbDeliveredAtEpochMs ?: smbDeliveredAtEpochMs,
    )

    /**
     * Bounds each independently supplied therapy stream instead of the timestamp union. AAPS can
     * publish IOB, COB, basal and activity in separate messages; globally truncating that union
     * allowed a dense stream to evict valid 24-hour history from its siblings.
     */
    private fun retainTherapyHistory(values: List<TherapyHistorySample>): List<TherapyHistorySample> {
        val byTimestamp = sortedMapOf<Long, TherapyHistorySample>()

        fun retain(
            present: (TherapyHistorySample) -> Boolean,
            isolate: (TherapyHistorySample) -> TherapyHistorySample,
        ) {
            values
                .asSequence()
                .filter(present)
                .sortedBy(TherapyHistorySample::measuredAtEpochMs)
                .toList()
                .takeLast(MAX_POINTS)
                .forEach { sample ->
                    val timestamp = sample.measuredAtEpochMs
                    val isolated = isolate(sample)
                    byTimestamp[timestamp] = byTimestamp[timestamp]?.merge(isolated, timestamp) ?: isolated
                }
        }

        retain({ it.totalIob != null }) { TherapyHistorySample(it.measuredAtEpochMs, totalIob = it.totalIob) }
        retain({ it.cobGrams != null }) { TherapyHistorySample(it.measuredAtEpochMs, cobGrams = it.cobGrams) }
        retain({ it.basalUnitsPerHour != null }) { TherapyHistorySample(it.measuredAtEpochMs, basalUnitsPerHour = it.basalUnitsPerHour) }
        retain({ it.baseBasalUnitsPerHour != null }) {
            TherapyHistorySample(it.measuredAtEpochMs, baseBasalUnitsPerHour = it.baseBasalUnitsPerHour)
        }
        retain({ it.tempBasalUnitsPerHour != null }) {
            TherapyHistorySample(it.measuredAtEpochMs, tempBasalUnitsPerHour = it.tempBasalUnitsPerHour)
        }
        retain({ it.insulinActivityUnitsPerMinute != null }) {
            TherapyHistorySample(it.measuredAtEpochMs, insulinActivityUnitsPerMinute = it.insulinActivityUnitsPerMinute)
        }
        retain({ it.smbUnits != null }) { TherapyHistorySample(it.measuredAtEpochMs, smbUnits = it.smbUnits) }

        return byTimestamp.values.toList()
    }

    private fun mergeInsulin(old: InsulinState?, new: InsulinState?): InsulinState? =
        new?.copy(
            totalIob = new.totalIob ?: old?.totalIob,
            bolusIob = new.bolusIob ?: old?.bolusIob,
            basalIob = new.basalIob ?: old?.basalIob,
        ) ?: old

    private fun mergeCarbs(old: CarbState?, new: CarbState?): CarbState? =
        new?.copy(
            cobGrams = new.cobGrams ?: old?.cobGrams,
            futureCarbsGrams = new.futureCarbsGrams ?: old?.futureCarbsGrams,
        ) ?: old

    private fun mergeBasal(old: BasalState?, new: BasalState?): BasalState? {
        if (new == null) return old
        val incomingDefinesBasalLifecycle = new.currentUnitsPerHour != null
        val incomingHasActiveTemp =
            new.tempAbsoluteUnitsPerHour != null ||
                new.tempPercent != null ||
                new.tempStartedAtEpochMs != null ||
                new.tempDurationMinutes != null ||
                new.tempEndsAtEpochMs != null ||
                new.displayText != null
        if (incomingDefinesBasalLifecycle && !incomingHasActiveTemp) {
            return new
        }
        return new.copy(
            currentUnitsPerHour = new.currentUnitsPerHour ?: old?.currentUnitsPerHour,
            tempAbsoluteUnitsPerHour = new.tempAbsoluteUnitsPerHour ?: old?.tempAbsoluteUnitsPerHour,
            tempPercent = new.tempPercent ?: old?.tempPercent,
            tempStartedAtEpochMs = new.tempStartedAtEpochMs ?: old?.tempStartedAtEpochMs,
            tempDurationMinutes = new.tempDurationMinutes ?: old?.tempDurationMinutes,
            tempEndsAtEpochMs = new.tempEndsAtEpochMs ?: old?.tempEndsAtEpochMs,
            displayText = new.displayText ?: old?.displayText,
        )
    }

    private fun mergeTarget(old: TargetState?, new: TargetState?): TargetState? =
        new?.copy(
            lowMgDl = new.lowMgDl ?: old?.lowMgDl,
            highMgDl = new.highMgDl ?: old?.highMgDl,
            valueMgDl = new.valueMgDl ?: old?.valueMgDl,
            startedAtEpochMs = new.startedAtEpochMs ?: old?.startedAtEpochMs,
            endsAtEpochMs = new.endsAtEpochMs ?: old?.endsAtEpochMs,
        ) ?: old

    private fun mergeLoop(old: LoopState?, new: LoopState?): LoopState? =
        new?.copy(
            status = new.status ?: old?.status,
            lastRunAtEpochMs = new.lastRunAtEpochMs ?: old?.lastRunAtEpochMs,
            suggestedAtEpochMs = new.suggestedAtEpochMs ?: old?.suggestedAtEpochMs,
            enactedAtEpochMs = new.enactedAtEpochMs ?: old?.enactedAtEpochMs,
            suggestedPayload = new.suggestedPayload ?: old?.suggestedPayload,
            enactedPayload = new.enactedPayload ?: old?.enactedPayload,
            smbUnits = new.smbUnits ?: old?.smbUnits,
            smbAtEpochMs = new.smbAtEpochMs ?: old?.smbAtEpochMs,
        ) ?: old

    private fun mergePump(old: PumpState?, new: PumpState?): PumpState? =
        new?.copy(
            status = new.status ?: old?.status,
            reservoirUnits = new.reservoirUnits ?: old?.reservoirUnits,
            batteryPercent = new.batteryPercent ?: old?.batteryPercent,
        ) ?: old

    private fun mergeDevice(old: DeviceState?, new: DeviceState?): DeviceState? =
        new?.copy(
            phoneBatteryPercent = new.phoneBatteryPercent ?: old?.phoneBatteryPercent,
            rigBatteryPercent = new.rigBatteryPercent ?: old?.rigBatteryPercent,
        ) ?: old
}
