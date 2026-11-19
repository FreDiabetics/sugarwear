package app.sugarlicious.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DataSourceIdSerializer::class)
enum class DataSourceId { DEXCOM_G7_WATCH, ANDROID_APS, DEXCOM_G7_APP, DEXCOM_SHARE, NIGHTSCOUT, OTHER, XDRIP_PLUS, LIBRE_PATCHED }

object DataSourceIdSerializer : KSerializer<DataSourceId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DataSourceId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DataSourceId) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DataSourceId =
        when (val raw = decoder.decodeString()) {
            else -> DataSourceId.entries.firstOrNull { it.name == raw } ?: DataSourceId.OTHER
        }
}

@Serializable enum class GlucoseUnit { MG_DL, MMOL_L }

@Serializable enum class Trend { DOUBLE_DOWN, SINGLE_DOWN, FORTY_FIVE_DOWN, FLAT, FORTY_FIVE_UP, SINGLE_UP, DOUBLE_UP, UNKNOWN }

@Serializable enum class Freshness { CURRENT, DELAYED, STALE, SIGNAL_LOSS, ERROR, NO_DATA }

@Serializable enum class CgmQuality { VALID, SENSOR_ERROR, INVALID }

/** Describes whether a displayed value came from the upstream source or was derived locally. */
@Serializable enum class ValueProvenance { SOURCE, DERIVED, UNAVAILABLE }

/** Stable field identifiers used to carry origin through persistence and Wear transport. */
@Serializable
enum class CanonicalDataField {
    GLUCOSE,
    TREND,
    DELTA,
    AVERAGE_DELTA,
    TARGET,
    IOB,
    BOLUS_IOB,
    BASAL_IOB,
    COB,
    FUTURE_CARBS,
    BASAL,
    TEMP_BASAL,
    INSULIN_ACTIVITY,
    LOOP,
    PROFILE,
    PUMP_STATUS,
    RESERVOIR,
    PUMP_BATTERY,
    PHONE_BATTERY,
    PREDICTIONS,
    THERAPY_EVENTS,
}

@Serializable enum class DataCapability { GLUCOSE, TREND, DELTA, AVERAGE_DELTA, TARGET, IOB, BOLUS_IOB, BASAL_IOB, SMB, COB, FUTURE_CARBS, TREATMENTS, BASAL, TEMP_BASAL, TEMP_TARGET, PROFILE, LOOP, PUMP, RESERVOIR, PUMP_BATTERY, PHONE_BATTERY, PREDICTIONS }

@Serializable enum class PredictionKind { IOB, COB, ACOB, UAM, ZERO_TEMP }

@Serializable enum class TherapyEventKind { SMB, MANUAL_CORRECTION, MEAL_BOLUS, MEAL_CARBS, ECARBS }

@Serializable enum class TherapyEventSource { AAPS_ONLY, NIGHTSCOUT_ONLY, AAPS_ENRICHED_BY_NIGHTSCOUT }

/** A real treatment emitted by AAPS. Curve changes are never used to manufacture these events. */
@Serializable data class TherapyEvent(
    val id: String,
    val kind: TherapyEventKind,
    val timestampEpochMs: Long,
    val amount: Double,
    val source: TherapyEventSource = TherapyEventSource.AAPS_ONLY,
    val originalSourceId: String? = null,
    val insulinUnits: Double? = null,
    val carbsGrams: Double? = null,
    val durationMinutes: Int? = null,
    val enteredBy: String? = null,
    val eventType: String? = null,
    val validated: Boolean = true,
)

@Serializable data class GlucoseState(
    val valueMgDl: Double,
    val displayUnit: GlucoseUnit,
    val trend: Trend = Trend.UNKNOWN,
    val measuredAtEpochMs: Long,
    val deltaMgDl: Double? = null,
    val averageDeltaMgDl: Double? = null,
    val source: DataSourceId = DataSourceId.ANDROID_APS,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val sequenceNumber: Long? = null,
    val receivedAtEpochMs: Long? = null,
    val quality: CgmQuality = CgmQuality.VALID,
    /** Canonical rate in mg/dL/min; distinct from the per-reading delta. */
    val trendRateMgDlPerMinute: Double? = null,
    /** Provenance is part of the value contract and must survive persistence and Wear transport. */
    val trendOrigin: ValueProvenance = ValueProvenance.UNAVAILABLE,
)

@Serializable data class GlucoseSample(
    val valueMgDl: Double,
    val measuredAtEpochMs: Long,
    val source: DataSourceId = DataSourceId.ANDROID_APS,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val sequenceNumber: Long? = null,
    val receivedAtEpochMs: Long? = null,
    val quality: CgmQuality = CgmQuality.VALID,
)

@Serializable data class GlucosePrediction(
    val kind: PredictionKind,
    val samples: List<GlucoseSample>,
)

@Serializable data class TherapyHistorySample(
    val measuredAtEpochMs: Long,
    val totalIob: Double? = null,
    val cobGrams: Double? = null,
    /** Effective basal value retained for protocol compatibility. */
    val basalUnitsPerHour: Double? = null,
    val baseBasalUnitsPerHour: Double? = null,
    val tempBasalUnitsPerHour: Double? = null,
    /** Insulin activity supplied by the source; never synthesized from sparse IOB observations. */
    val insulinActivityUnitsPerMinute: Double? = null,
    /** Read-only SMB marker normalized from the public AAPS enacted payload. */
    val smbUnits: Double? = null,
    /** Explicit delivery time; null for legacy markers with ambiguous timestamp provenance. */
    val smbDeliveredAtEpochMs: Long? = null,
)

@Serializable data class InsulinState(
    val totalIob: Double? = null,
    val bolusIob: Double? = null,
    val basalIob: Double? = null,
)

@Serializable data class CarbState(
    val cobGrams: Double? = null,
    val futureCarbsGrams: Double? = null,
)

@Serializable data class BasalState(
    val currentUnitsPerHour: Double? = null,
    val tempAbsoluteUnitsPerHour: Double? = null,
    val tempPercent: Int? = null,
    val tempStartedAtEpochMs: Long? = null,
    val tempDurationMinutes: Long? = null,
    val tempEndsAtEpochMs: Long? = null,
    val displayText: String? = null,
)

@Serializable data class TargetState(
    /** Display-range lower boundary exported by AAPS (Overview low mark). */
    val lowMgDl: Double? = null,
    /** Display-range upper boundary exported by AAPS (Overview high mark). */
    val highMgDl: Double? = null,
    val temporary: Boolean = false,
    /** Effective APS target (targetBG), including an active temp target when AAPS applies one. */
    val valueMgDl: Double? = null,
    /** Present only when the source supplies a real target start timestamp. */
    val startedAtEpochMs: Long? = null,
    /** Present only when the source supplies a real target end timestamp. */
    val endsAtEpochMs: Long? = null,
)

@Serializable data class TargetSample(
    val valueMgDl: Double,
    val startedAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val temporary: Boolean = false,
)

@Serializable data class LoopState(
    val status: String? = null,
    val lastRunAtEpochMs: Long? = null,
    val suggestedAtEpochMs: Long? = null,
    val enactedAtEpochMs: Long? = null,
    val suggestedPayload: String? = null,
    val enactedPayload: String? = null,
    val smbUnits: Double? = null,
    val smbAtEpochMs: Long? = null,
)

@Serializable data class PumpState(
    val status: String? = null,
    val reservoirUnits: Double? = null,
    val batteryPercent: Int? = null,
)

@Serializable data class DeviceState(
    val phoneBatteryPercent: Int? = null,
    val rigBatteryPercent: Int? = null,
)

@Serializable data class ProfileState(
    val name: String? = null,
    /** Insulin duration of action supplied by the active profile, in hours. */
    val diaHours: Double? = null,
)

@Serializable data class TherapyDisplayState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    /** Monotonic Mobile commit revision; zero denotes a legacy unrevisioned payload. */
    val canonicalRevision: Long = 0L,
    val source: DataSourceId = DataSourceId.ANDROID_APS,
    val sourceVersion: String? = null,
    val sourceContract: String? = null,
    @SerialName("aapsDisplaySemantics")
    val therapyBridgeDisplaySemantics: TherapyBridgeDisplaySemantics? = null,
    val receivedAtEpochMs: Long,
    val glucose: GlucoseState? = null,
    val glucoseHistory: List<GlucoseSample> = emptyList(),
    val glucosePredictions: List<GlucosePrediction> = emptyList(),
    val therapyHistory: List<TherapyHistorySample> = emptyList(),
    val therapyEvents: List<TherapyEvent> = emptyList(),
    val targetHistory: List<TargetSample> = emptyList(),
    val insulin: InsulinState? = null,
    val carbs: CarbState? = null,
    val basal: BasalState? = null,
    val target: TargetState? = null,
    val loop: LoopState? = null,
    val pump: PumpState? = null,
    val device: DeviceState? = null,
    val profile: ProfileState? = null,
    val capabilities: Set<DataCapability> = emptySet(),
    /** Per-field origin; absent entries are deliberately treated as unavailable. */
    val fieldProvenance: Map<CanonicalDataField, ValueProvenance> = emptyMap(),
    /** Upstream identity is independent from source-versus-derived value provenance. */
    val fieldSources: Map<CanonicalDataField, DataSourceId> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA = 12
    }
}

fun TherapyDisplayState.provenanceOf(field: CanonicalDataField): ValueProvenance =
    fieldProvenance[field] ?: ValueProvenance.UNAVAILABLE

object FreshnessPolicy {
    const val CURRENT_MAX_MS = 6 * 60_000L
    const val DELAYED_MAX_MS = 12 * 60_000L
    const val FUTURE_TOLERANCE_MS = 5 * 60_000L

    fun classify(
        measuredAtEpochMs: Long?,
        nowEpochMs: Long,
    ): Freshness =
        when {
            measuredAtEpochMs == null -> Freshness.NO_DATA
            measuredAtEpochMs > nowEpochMs + FUTURE_TOLERANCE_MS -> Freshness.NO_DATA
            nowEpochMs - measuredAtEpochMs <= CURRENT_MAX_MS -> Freshness.CURRENT
            nowEpochMs - measuredAtEpochMs <= DELAYED_MAX_MS -> Freshness.DELAYED
            nowEpochMs - measuredAtEpochMs < CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS -> Freshness.STALE
            else -> Freshness.SIGNAL_LOSS
        }
}
