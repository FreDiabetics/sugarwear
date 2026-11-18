package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus

internal data class SugarWearAlarmEvaluationResult(
    val updatedInstances: Map<SugarWearAlarmType, SugarWearAlarmInstance>,
    val newlyActiveTypes: List<SugarWearAlarmType>,
    val resolvedTypes: List<SugarWearAlarmType>,
)

internal object SugarWearAlarmEngine {
    private val glucoseAlarmTypes =
        setOf(
            SugarWearAlarmType.URGENT_LOW,
            SugarWearAlarmType.URGENT_LOW_SOON,
            SugarWearAlarmType.LOW,
            SugarWearAlarmType.HIGH,
            SugarWearAlarmType.RAPID_RISE,
            SugarWearAlarmType.RAPID_FALL,
        )

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

        SugarWearAlarmType.entries.forEach { type ->
            if (!evaluateGlucose && type in glucoseAlarmTypes) {
                previousState[type]?.let { updated[type] = it }
                return@forEach
            }

            val rule = settings.ruleFor(type)
            val isMet =
                isConditionMet(
                    type = type,
                    rule = rule,
                    reading = reading,
                    technicalError = technicalError,
                    signalLossDurationMinutes = signalLossDurationMinutes,
                    sensorErrorDurationMinutes = sensorErrorDurationMinutes,
                )

            val prev = previousState[type]
            val prevActive = prev?.state == SugarWearAlarmState.ACTIVE
            val glucoseVal = reading?.takeIf { it.status == CgmReadingStatus.VALID }?.glucoseMgDl

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
        reading: CgmReading?,
        technicalError: CgmCollectorError?,
        signalLossDurationMinutes: Long,
        sensorErrorDurationMinutes: Long,
    ): Boolean {
        val validReading = reading?.takeIf { it.status == CgmReadingStatus.VALID }

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
                reading != null && reading.status == CgmReadingStatus.SENSOR_ERROR && sensorErrorDurationMinutes >= rule.durationMinutes
            }
            SugarWearAlarmType.TECHNICAL_WARNING -> {
                // Fixed non-disableable for critical technical events
                technicalError != null && !technicalError.recoverable
            }
        }
    }
}
