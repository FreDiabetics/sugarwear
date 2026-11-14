package app.sugarlicious.datasource.therapybridge

import android.os.Bundle
import app.sugarlicious.model.*

object TherapyBridgePayloadAdapter {
    const val ACTION = "info.nightscout.androidaps.status"

    @Suppress("DEPRECATION")
    fun parse(
        bundle: Bundle,
        receivedAtEpochMs: Long,
    ): TherapyDisplayState? = parse(bundle.keySet().associateWith { key -> bundle.get(key) }, receivedAtEpochMs)

    fun parse(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ): TherapyDisplayState? {
        if (!TherapyPayloadValidator().isValid(values, receivedAtEpochMs)) return null
        val value = values.number("glucoseMgdl") ?: return null
        val measured = values.number("glucoseTimeStamp")?.toLong() ?: return null
        val trend = CanonicalTrendPolicy.fromDirection(values["slopeArrow"] as? String)
        val unit = if ((values["units"] as? String)?.startsWith("mmol", true) == true) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL
        val delta = values.number("deltaMgdl")
        val averageDelta = values.number("avgDeltaMgdl")
        val low = values.number("low")
        val high = values.number("high")
        val iob = values.number("iob")
        val bolusIob = values.number("bolusIob")
        val basalIob = values.number("basalIob")
        val explicitInsulinActivity =
            sequenceOf("insulinActivity", "iobActivity", "activity")
                .mapNotNull { key -> values.number(key) }
                .firstOrNull()
        val cob = values.number("cob")
        val futureCarbs = values.number("futureCarbs")
        val profile = values["profile"] as? String
        val diaHours =
            sequenceOf("dia", "diaHours", "insulinActionDurationHours")
                .mapNotNull { key -> values.number(key) }
                .firstOrNull()
                ?.takeIf { it in 1.0..24.0 }
        val baseBasal = values.number("baseBasal")
        val tempStart = values.number("tempBasalStart")?.toLong()
        val tempDuration = values.number("tempBasalDurationInMinutes")?.toLong()?.takeIf { it >= 0 }
        val tempAbsolute = values.number("tempBasalAbsolute")
        val tempPercent = values.number("tempBasalPercent")?.toInt()
        val suggestedAt = values.number("suggestedTimeStamp")?.toLong()?.takeIf { it > 0 }
        val enactedAt = values.number("enactedTimeStamp")?.toLong()?.takeIf { it > 0 }
        val suggestedPayload = values["suggested"] as? String
        val enactedPayload = values["enacted"] as? String
        val insulinActivity = explicitInsulinActivity ?: reconstructInsulinActivity(suggestedPayload ?: enactedPayload)
        val explicitLoopEnabled = values.boolean("loopEnabled")
        val explicitLoopStatus = (values["loopStatus"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val parsedTarget = TherapyBridgeTargetParser.parseTarget(suggestedPayload) ?: TherapyBridgeTargetParser.parseTarget(enactedPayload)
        val targetValue = parsedTarget?.valueMgDl
        val targetStart = values.number("tempTargetStart")?.toLong()?.takeIf { it > 0 }
        val targetDuration = values.number("tempTargetDurationInMinutes")?.toLong()?.takeIf { it > 0 }
        val targetEnd =
            values.number("tempTargetEnd")?.toLong()?.takeIf { it > 0 }
                ?: targetStart?.let { start -> targetDuration?.let { duration -> start + duration * 60_000L } }
        val smb = TherapyBridgeSmbParser.parse(enactedPayload, enactedAt)
        val therapyEvents = TherapyBridgeTherapyEventParser.parse(values["therapyEvents"] as? String)
        val predictions = TherapyBridgePredictionParser.parse(suggestedPayload ?: enactedPayload, suggestedAt ?: enactedAt ?: measured)
        val pumpStatus = values["pumpStatus"] as? String
        val reservoir = values.number("pumpReservoir")
        val pumpBattery = values.number("pumpBattery")?.toInt()?.takeIf { it in 0..100 }
        val phoneBattery = values.number("phoneBattery")?.toInt()?.takeIf { it in 0..100 }
        val rigBattery = values.number("rigBattery")?.toInt()?.takeIf { it in 0..100 }
        val caps =
            buildSet {
                add(DataCapability.GLUCOSE)
                if (trend !=
                    Trend.UNKNOWN
                ) {
                    add(DataCapability.TREND)
                }
                if (delta !=
                    null
                ) {
                    add(DataCapability.DELTA)
                }
                if (averageDelta != null)add(DataCapability.AVERAGE_DELTA)
                if (low != null ||
                    high != null ||
                    targetValue != null
                ) {
                    add(DataCapability.TARGET)
                }
                if (parsedTarget?.temporary == true ||
                    targetStart != null
                ) {
                    add(DataCapability.TEMP_TARGET)
                }
                if (iob !=
                    null
                ) {
                    add(DataCapability.IOB)
                }
                if (bolusIob !=
                    null
                ) {
                    add(DataCapability.BOLUS_IOB)
                }
                if (basalIob != null)add(DataCapability.BASAL_IOB)
                if (smb !=
                    null
                ) {
                    add(DataCapability.SMB)
                }
                if (cob !=
                    null
                ) {
                    add(DataCapability.COB)
                }
                if (futureCarbs !=
                    null
                ) {
                    add(DataCapability.FUTURE_CARBS)
                }
                if (therapyEvents.isNotEmpty())add(DataCapability.TREATMENTS)
                if (baseBasal !=
                    null
                ) {
                    add(DataCapability.BASAL)
                }
                if (tempStart != null ||
                    tempAbsolute != null ||
                    tempPercent != null
                ) {
                    add(DataCapability.TEMP_BASAL)
                }
                if (predictions.isNotEmpty())add(DataCapability.PREDICTIONS)
                if (profile != null ||
                    diaHours != null
                ) {
                    add(DataCapability.PROFILE)
                }
                if (suggestedAt != null ||
                    enactedAt != null ||
                    explicitLoopEnabled != null ||
                    explicitLoopStatus != null
                ) {
                    add(DataCapability.LOOP)
                }
                if (pumpStatus !=
                    null
                ) {
                    add(DataCapability.PUMP)
                }
                if (reservoir !=
                    null
                ) {
                    add(DataCapability.RESERVOIR)
                }
                if (pumpBattery !=
                    null
                ) {
                    add(DataCapability.PUMP_BATTERY)
                }
                if (phoneBattery != null)add(DataCapability.PHONE_BATTERY)
            }
        val detectedContract = TherapyBridgeCapabilityDetector.detectContract(values).id
        val fieldProvenance =
            buildMap {
                fun sourceIf(field: CanonicalDataField, present: Boolean) {
                    if (present) put(field, ValueProvenance.SOURCE)
                }
                put(CanonicalDataField.GLUCOSE, ValueProvenance.SOURCE)
                sourceIf(CanonicalDataField.TREND, trend != Trend.UNKNOWN)
                sourceIf(CanonicalDataField.DELTA, delta != null)
                sourceIf(CanonicalDataField.AVERAGE_DELTA, averageDelta != null)
                sourceIf(CanonicalDataField.TARGET, low != null || high != null || targetValue != null)
                sourceIf(CanonicalDataField.IOB, iob != null)
                sourceIf(CanonicalDataField.BOLUS_IOB, bolusIob != null)
                sourceIf(CanonicalDataField.BASAL_IOB, basalIob != null)
                sourceIf(CanonicalDataField.COB, cob != null)
                sourceIf(CanonicalDataField.FUTURE_CARBS, futureCarbs != null)
                sourceIf(CanonicalDataField.BASAL, baseBasal != null)
                sourceIf(CanonicalDataField.TEMP_BASAL, tempStart != null || tempAbsolute != null || tempPercent != null)
                when {
                    explicitInsulinActivity != null -> put(CanonicalDataField.INSULIN_ACTIVITY, ValueProvenance.SOURCE)
                    insulinActivity != null -> put(CanonicalDataField.INSULIN_ACTIVITY, ValueProvenance.DERIVED)
                }
                sourceIf(
                    CanonicalDataField.LOOP,
                    suggestedAt != null || enactedAt != null || explicitLoopEnabled != null || explicitLoopStatus != null,
                )
                sourceIf(CanonicalDataField.PROFILE, profile != null || diaHours != null)
                sourceIf(CanonicalDataField.PUMP_STATUS, pumpStatus != null)
                sourceIf(CanonicalDataField.RESERVOIR, reservoir != null)
                sourceIf(CanonicalDataField.PUMP_BATTERY, pumpBattery != null)
                sourceIf(CanonicalDataField.PHONE_BATTERY, phoneBattery != null)
                sourceIf(CanonicalDataField.PREDICTIONS, predictions.isNotEmpty())
                sourceIf(CanonicalDataField.THERAPY_EVENTS, therapyEvents.isNotEmpty())
            }
        val loopState =
            if (suggestedAt != null || enactedAt != null || explicitLoopEnabled != null || explicitLoopStatus != null) {
                LoopState(
                    status =
                        when {
                            explicitLoopEnabled == false -> "off"
                            explicitLoopStatus != null -> explicitLoopStatus
                            enactedAt != null -> "enacted"
                            suggestedAt != null -> "suggested"
                            explicitLoopEnabled == true -> "on"
                            else -> null
                        },
                    lastRunAtEpochMs = enactedAt ?: suggestedAt,
                    suggestedAtEpochMs = suggestedAt,
                    enactedAtEpochMs = enactedAt,
                    suggestedPayload = suggestedPayload,
                    enactedPayload = enactedPayload,
                    smbUnits = smb?.units,
                    smbAtEpochMs = smb?.deliveredAtEpochMs,
                )
            } else {
                null
            }
        return TherapyDisplayState(
            receivedAtEpochMs = receivedAtEpochMs,
            sourceContract = detectedContract,
            therapyBridgeDisplaySemantics = TherapyBridgeDisplaySemantics(glucoseUnit = unit),
            glucose =
                GlucoseState(
                    value,
                    unit,
                    trend,
                    measured,
                    delta,
                    averageDelta,
                    source = DataSourceId.ANDROID_APS,
                    receivedAtEpochMs = receivedAtEpochMs,
                    trendOrigin = if (trend == Trend.UNKNOWN) ValueProvenance.UNAVAILABLE else ValueProvenance.SOURCE,
                ),
            targetHistory =
                targetValue
                    ?.let { target ->
                        val observedAt = targetStart ?: suggestedAt ?: enactedAt ?: measured
                        listOf(TargetSample(target, observedAt, targetEnd ?: observedAt, parsedTarget.temporary || targetStart != null))
                    }.orEmpty(),
            glucosePredictions = predictions,
            therapyHistory =
                if (iob != null || cob != null || baseBasal != null || tempAbsolute != null || insulinActivity != null) {
                    listOf(
                        TherapyHistorySample(
                            measuredAtEpochMs = measured,
                            totalIob = iob,
                            cobGrams = cob,
                            basalUnitsPerHour = tempAbsolute ?: baseBasal,
                            baseBasalUnitsPerHour = baseBasal,
                            tempBasalUnitsPerHour = tempAbsolute,
                            insulinActivityUnitsPerMinute = insulinActivity,
                        ),
                    )
                } else {
                    emptyList()
                },
            therapyEvents = therapyEvents,
            insulin = if (iob != null || bolusIob != null || basalIob != null) InsulinState(iob, bolusIob, basalIob) else null,
            carbs = if (cob != null || futureCarbs != null) CarbState(cob, futureCarbs) else null,
            basal =
                if (baseBasal != null ||
                    tempStart != null ||
                    tempAbsolute != null ||
                    tempPercent != null
                ) {
                    BasalState(
                        baseBasal,
                        tempAbsolute,
                        tempPercent,
                        tempStart,
                        tempDuration,
                        tempStart?.let { s ->
                            tempDuration?.let { d ->
                                s +
                                    d * 60_000
                            }
                        },
                        values["tempBasalString"] as? String,
                    )
                } else {
                    null
                },
            target =
                if (low != null ||
                    high != null ||
                    targetValue != null
                ) {
                    TargetState(low, high, parsedTarget?.temporary == true || targetStart != null, targetValue, targetStart, targetEnd)
                } else {
                    null
                },
            loop = loopState,
            pump =
                if (pumpStatus != null ||
                    reservoir != null ||
                    pumpBattery != null
                ) {
                    PumpState(pumpStatus, reservoir, pumpBattery)
                } else {
                    null
                },
            device = if (phoneBattery != null || rigBattery != null) DeviceState(phoneBattery, rigBattery) else null,
            profile = if (profile != null || diaHours != null) ProfileState(profile, diaHours) else null,
            capabilities = caps,
            fieldProvenance = fieldProvenance,
        )
    }

    private fun Map<String, Any?>.number(key: String): Double? =
        get(key)
            ?.let {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull()
                    else -> null
                }
            }?.takeIf { it.isFinite() }

    private fun reconstructInsulinActivity(payload: String?): Double? {
        val reason = payload ?: return null
        val bgi =
            BGI_PATTERN
                .find(reason)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: return null
        val isf =
            ISF_PATTERN
                .find(reason)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: return null
        if (!bgi.isFinite() || !isf.isFinite() || bgi > 0.0 || isf <= 0.0) return null
        return (-bgi / (isf * FIVE_MINUTES)).takeIf(Double::isFinite)
    }

    private const val FIVE_MINUTES = 5.0
    private val BGI_PATTERN = Regex("""\bBGI:\s*(-?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
    private val ISF_PATTERN = Regex("""\bISF:\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)

    private fun Map<String, Any?>.boolean(key: String): Boolean? =
        when (val value = get(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String ->
                when (value.trim().lowercase()) {
                    "true", "1", "on", "enabled" -> true
                    "false", "0", "off", "disabled" -> false
                    else -> null
                }
            else -> null
        }
}
