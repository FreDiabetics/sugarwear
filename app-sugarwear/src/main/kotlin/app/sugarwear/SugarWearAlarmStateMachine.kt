package app.sugarwear

internal object SugarWearAlarmStateMachine {
    fun update(
        previous: SugarWearAlarmInstance?,
        type: SugarWearAlarmType,
        rule: SugarWearAlarmRule,
        conditionMet: Boolean,
        glucoseMgDl: Double?,
        sensorId: String?,
        sessionId: String?,
        source: String?,
        measuredAtEpochMs: Long?,
        receivedAtEpochMs: Long?,
        technicalErrorSignature: String?,
        nowEpochMs: Long,
        trend: String? = null,
    ): SugarWearAlarmInstance {
        val current =
            previous ?: SugarWearAlarmInstance(
                type = type,
                state = SugarWearAlarmState.INACTIVE,
                sensorId = sensorId,
                sessionId = sessionId,
                source = source,
            )

        val updatedBase =
            current.copy(
                sensorId = sensorId ?: current.sensorId,
                sessionId = sessionId ?: current.sessionId,
                source = source ?: current.source,
                measuredAtEpochMs = measuredAtEpochMs ?: current.measuredAtEpochMs,
                receivedAtEpochMs = receivedAtEpochMs ?: current.receivedAtEpochMs,
                technicalErrorSignature = technicalErrorSignature ?: current.technicalErrorSignature,
            )

        return when (current.state) {
            SugarWearAlarmState.INACTIVE, SugarWearAlarmState.RESOLVED -> {
                if (!conditionMet) {
                    updatedBase.copy(state = SugarWearAlarmState.INACTIVE)
                } else if (type == SugarWearAlarmType.HIGH && rule.highDelayMinutes > 0) {
                    updatedBase.copy(
                        state = SugarWearAlarmState.PENDING_DELAY,
                        conditionFirstMetAtEpochMs = nowEpochMs,
                        triggerGlucoseMgDl = glucoseMgDl,
                        triggerTrend = trend,
                    )
                } else {
                    updatedBase.copy(
                        state = SugarWearAlarmState.ACTIVE,
                        triggeredAtEpochMs = nowEpochMs,
                        lastNotifiedAtEpochMs = nowEpochMs,
                        triggerGlucoseMgDl = glucoseMgDl,
                        triggerTrend = trend,
                    )
                }
            }
            SugarWearAlarmState.PENDING_DELAY -> {
                if (!conditionMet) {
                    updatedBase.copy(
                        state = SugarWearAlarmState.INACTIVE,
                        conditionFirstMetAtEpochMs = null,
                    )
                } else {
                    val firstMet = current.conditionFirstMetAtEpochMs ?: nowEpochMs
                    val delayMs = rule.highDelayMinutes * 60_000L
                    if (nowEpochMs - firstMet >= delayMs) {
                        updatedBase.copy(
                            state = SugarWearAlarmState.ACTIVE,
                            triggeredAtEpochMs = nowEpochMs,
                            lastNotifiedAtEpochMs = nowEpochMs,
                            triggerGlucoseMgDl = glucoseMgDl,
                            triggerTrend = trend,
                        )
                    } else {
                        updatedBase.copy(
                            state = SugarWearAlarmState.PENDING_DELAY,
                            conditionFirstMetAtEpochMs = firstMet,
                            triggerGlucoseMgDl = glucoseMgDl,
                            triggerTrend = trend,
                        )
                    }
                }
            }
            SugarWearAlarmState.ACTIVE -> {
                if (!conditionMet) {
                    updatedBase.copy(state = SugarWearAlarmState.RESOLVED)
                } else {
                    current
                }
            }
            SugarWearAlarmState.ACKNOWLEDGED -> {
                if (!conditionMet) {
                    updatedBase.copy(state = SugarWearAlarmState.RESOLVED)
                } else {
                    current
                }
            }
            SugarWearAlarmState.SNOOZED -> {
                if (!conditionMet) {
                    updatedBase.copy(
                        state = SugarWearAlarmState.RESOLVED,
                        snoozedUntilEpochMs = null,
                    )
                } else {
                    val snoozedUntil = current.snoozedUntilEpochMs
                    if (snoozedUntil != null && nowEpochMs >= snoozedUntil) {
                        updatedBase.copy(
                            state = SugarWearAlarmState.ACTIVE,
                            triggeredAtEpochMs = nowEpochMs,
                            lastNotifiedAtEpochMs = nowEpochMs,
                            snoozedUntilEpochMs = null,
                            triggerGlucoseMgDl = glucoseMgDl ?: current.triggerGlucoseMgDl,
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }
}
