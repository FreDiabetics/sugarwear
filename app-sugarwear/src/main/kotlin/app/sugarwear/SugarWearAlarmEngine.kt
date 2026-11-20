package app.sugarwear

import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus

internal data class SugarWearAlarmEvaluationResult(
    val updatedInstances: Map<SugarWearAlarmType, SugarWearAlarmInstance>,
    val newlyActiveTypes: List<SugarWearAlarmType>,
    val resolvedTypes: List<SugarWearAlarmType>,
)

internal object SugarWearAlarmEngine {
    fun evaluate(
        reading: CgmReading?,
        technicalError: CgmCollectorError?,
        signalLossDurationMinutes: Long,
        sensorErrorDurationMinutes: Long,
        previousState: Map<SugarWearAlarmType, SugarWearAlarmInstance>,
        settings: SugarWearAlarmSettings,
        nowEpochMs: Long = System.currentTimeMillis(),
        evaluateGlucose: Boolean = true,
    ): SugarWearAlarmEvaluationResult {
        val updated = mutableMapOf<SugarWearAlarmType, SugarWearAlarmInstance>()
        val newlyActive = mutableListOf<SugarWearAlarmType>()
        val resolved = mutableListOf<SugarWearAlarmType>()
        val freshReading = reading?.takeIf { it.isFreshAlarmReading(nowEpochMs) }

        SugarWearAlarmType.entries.forEach { type ->
            val prev = previousState[type]
            val rule = settings.ruleFor(type)
            val samePendingHighReading =
                type == SugarWearAlarmType.HIGH &&
                    rule.enabled &&
                    rule.highDelayMinutes > 0 &&
                    prev?.state == SugarWearAlarmState.PENDING_DELAY &&
                    freshReading != null &&
                    prev.measuredAtEpochMs?.let { freshReading.timestampEpochMs <= it } == true
            if ((!evaluateGlucose && type.usesGlucoseReading) || samePendingHighReading) {
                prev?.let { updated[type] = it }
                return@forEach
            }

            val isMet =
                isConditionMet(
                    type = type,
                    rule = rule,
                    validReading = freshReading,
                    sensorErrorReading = reading?.status == CgmReadingStatus.SENSOR_ERROR,
                    technicalError = technicalError,
                    signalLossDurationMinutes = signalLossDurationMinutes,
                    sensorErrorDurationMinutes = sensorErrorDurationMinutes,
                )

            val prevActive = prev?.state == SugarWearAlarmState.ACTIVE
            val glucoseVal = freshReading?.glucoseMgDl

            val nextInstance =
                SugarWearAlarmStateMachine.update(
                    previous = prev,
                    type = type,
                    rule = rule,
                    conditionMet = isMet,
                    glucoseMgDl = glucoseVal,
                    sensorId = reading?.sensorId,
                    sessionId = reading?.sessionId,
                    source = reading?.origin?.name,
                    measuredAtEpochMs = reading?.timestampEpochMs,
                    receivedAtEpochMs = reading?.receivedAtEpochMs,
                    technicalErrorSignature = technicalError?.let { cgmErrorSignature(it) },
                    nowEpochMs = nowEpochMs,
                    trend = freshReading?.trend?.name,
                )

            updated[type] = nextInstance

            if (nextInstance.state == SugarWearAlarmState.ACTIVE && !prevActive) {
                newlyActive.add(type)
            } else if (nextInstance.state == SugarWearAlarmState.RESOLVED && prevActive) {
                resolved.add(type)
            }
        }

        return SugarWearAlarmEvaluationResult(
            updatedInstances = updated,
            newlyActiveTypes = newlyActive,
            resolvedTypes = resolved,
        )
    }

    private fun isConditionMet(
        type: SugarWearAlarmType,
        rule: SugarWearAlarmRule,
        validReading: CgmReading?,
        sensorErrorReading: Boolean,
        technicalError: CgmCollectorError?,
        signalLossDurationMinutes: Long,
        sensorErrorDurationMinutes: Long,
    ): Boolean {
        return when (type) {
            SugarWearAlarmType.URGENT_LOW -> {
                // Fixed 55 mg/dL, non-disableable
                validReading != null && validReading.glucoseMgDl <= SugarWearAlarmDefaults.URGENT_LOW_THRESHOLD_MG_DL
            }
            SugarWearAlarmType.URGENT_LOW_SOON -> {
                if (!rule.enabled) return false
                validReading != null && validReading.predictedMgDl?.let { it <= SugarWearAlarmDefaults.URGENT_LOW_SOON_THRESHOLD_MG_DL } == true
            }
            SugarWearAlarmType.LOW -> {
                if (!rule.enabled) return false
                validReading != null && validReading.glucoseMgDl <= rule.thresholdMgDl
            }
            SugarWearAlarmType.HIGH -> {
                if (!rule.enabled) return false
                validReading != null && validReading.glucoseMgDl >= rule.thresholdMgDl
            }
            SugarWearAlarmType.RAPID_RISE -> {
                if (!rule.enabled || validReading == null) return false
                if (validReading.glucoseMgDl <= rule.additionalGlucoseThresholdMgDl) return false
                val rate = validReading.trendRateMgDlPerMinute ?: return false
                if (rule.rateStep == 1) {
                    rate >= SugarWearAlarmDefaults.RAPID_RISE_STEP1_MIN && rate < SugarWearAlarmDefaults.RAPID_RISE_STEP2_MIN
                } else {
                    rate >= SugarWearAlarmDefaults.RAPID_RISE_STEP2_MIN
                }
            }
            SugarWearAlarmType.RAPID_FALL -> {
                if (!rule.enabled || validReading == null) return false
                if (validReading.glucoseMgDl >= rule.additionalGlucoseThresholdMgDl) return false
                val rate = validReading.trendRateMgDlPerMinute ?: return false
                if (rule.rateStep == 1) {
                    rate <= -SugarWearAlarmDefaults.RAPID_FALL_STEP1_MIN && rate > -SugarWearAlarmDefaults.RAPID_FALL_STEP2_MIN
                } else {
                    rate <= -SugarWearAlarmDefaults.RAPID_FALL_STEP2_MIN
                }
            }
            SugarWearAlarmType.SIGNAL_LOSS -> {
                if (!rule.enabled) return false
                signalLossDurationMinutes >= rule.durationMinutes
            }
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> {
                if (!rule.enabled) return false
                sensorErrorReading && sensorErrorDurationMinutes >= rule.durationMinutes
            }
            SugarWearAlarmType.TECHNICAL_WARNING -> {
                // Fixed non-disableable for critical technical events
                technicalError != null && !technicalError.recoverable
            }
        }
    }
}

internal fun CgmReading.isFreshAlarmReading(now: Long): Boolean =
    status == CgmReadingStatus.VALID &&
        !displayOnly &&
        glucoseMgDl.isFinite() &&
        timestampEpochMs > 0 &&
        now - timestampEpochMs in 0..FreshnessPolicy.CURRENT_MAX_MS
