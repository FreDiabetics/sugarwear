package app.sugarwear

import kotlinx.serialization.Serializable

@Serializable
enum class SugarWearAlarmType(
    val id: String,
    val titleGerman: String,
) {
    URGENT_LOW("urgent_low", "Niedriger Wert (dringend)"),
    URGENT_LOW_SOON("urgent_low_soon", "Bald niedriger Wert (dringend)"),
    LOW("low", "Niedrig"),
    HIGH("high", "Hoch"),
    RAPID_RISE("rapid_rise", "Schnell steigend"),
    RAPID_FALL("rapid_fall", "Schnell fallend"),
    SIGNAL_LOSS("signal_loss", "Signalverlust"),
    TEMPORARY_SENSOR_PROBLEM("temporary_sensor_problem", "Kurzes Sensorproblem"),
    TECHNICAL_WARNING("technical_warning", "Technisches Problem"),
    SENSOR_END_24H("sensor_end_24h", "Sensor läuft in 24 Stunden ab"),
    SENSOR_GRACE_START("sensor_grace_start", "Toleranzzeit"),
    ;

    val lifetimeDescription: String?
        get() = when (this) {
            SENSOR_END_24H -> "Dann erhalten Sie eine Toleranzzeit von 12 Stunden, um den Sensor zu wechseln."
            SENSOR_GRACE_START -> "Sensor befindet sich nun in der Toleranzzeit und läuft in 12h endgültig ab."
            else -> null
        }

    val isSensorLifetime: Boolean
        get() = this == SENSOR_END_24H || this == SENSOR_GRACE_START

    val usesGlucoseReading: Boolean
        get() =
            when (this) {
                SIGNAL_LOSS, TEMPORARY_SENSOR_PROBLEM, TECHNICAL_WARNING, SENSOR_END_24H, SENSOR_GRACE_START -> false
                else -> true
            }

    val isMandatory: Boolean
        get() = this == URGENT_LOW || this == TECHNICAL_WARNING || isSensorLifetime

    companion object {
        fun fromId(id: String): SugarWearAlarmType? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
enum class SugarWearAlertDeliveryMode(
    val labelGerman: String,
) {
    SOUND("Ton"),
    VIBRATE("Vibrieren"),
    MATCH_DEVICE("Geräteeinstellungen verwenden"),
}

@Serializable
data class SugarWearAlarmRule(
    val type: SugarWearAlarmType,
    val enabled: Boolean,
    val thresholdMgDl: Double = 0.0,
    val rateStep: Int = 2, // 1: 2..<3 mg/dL/min, 2: >=3 mg/dL/min
    val additionalGlucoseThresholdMgDl: Double = 0.0,
    val highDelayMinutes: Int = 15,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 60,
    val durationMinutes: Int = 20, // for system warnings
    val deliveryMode: SugarWearAlertDeliveryMode = SugarWearAlertDeliveryMode.MATCH_DEVICE,
)

@Serializable
data class SugarWearAlarmSettings(
    val globalVibrateOnly: Boolean = false,
    val globalMuteAll: Boolean = false,
    val rules: Map<SugarWearAlarmType, SugarWearAlarmRule> = emptyMap(),
    val nightRules: Map<SugarWearAlarmType, SugarWearAlarmRule>? = null,
    val manualProfile: SugarWearAlarmProfile = SugarWearAlarmProfile.DAY,
    val schedule: SugarWearAlarmSchedule = SugarWearAlarmSchedule(),
    val scheduleEnabled: Boolean = false,
) {
    fun forProfile(profile: SugarWearAlarmProfile): SugarWearAlarmSettings =
        if (profile == SugarWearAlarmProfile.NIGHT && nightRules != null) copy(rules = nightRules) else this

    internal fun activeProfile(now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): ActiveSugarWearAlarmProfile =
        if (scheduleEnabled) schedule.activeAt(now, zone) else ActiveSugarWearAlarmProfile(manualProfile)

    fun ruleFor(type: SugarWearAlarmType): SugarWearAlarmRule {
        val rule = rules[type] ?: SugarWearAlarmDefaults.defaultRule(type)
        return if (type.isMandatory && !rule.enabled) rule.copy(enabled = true) else rule
    }
}

@Serializable
enum class SugarWearAlarmState {
    INACTIVE,
    PENDING_DELAY,
    ACTIVE,
    ACKNOWLEDGED,
    SNOOZED,
    RESOLVED,
}

@Serializable
data class SugarWearAlarmInstance(
    val type: SugarWearAlarmType,
    val state: SugarWearAlarmState = SugarWearAlarmState.INACTIVE,
    val sensorId: String? = null,
    val sessionId: String? = null,
    val source: String? = null,
    val measuredAtEpochMs: Long? = null,
    val receivedAtEpochMs: Long? = null,
    val conditionFirstMetAtEpochMs: Long? = null,
    val triggeredAtEpochMs: Long? = null,
    val acknowledgedAtEpochMs: Long? = null,
    val snoozedUntilEpochMs: Long? = null,
    val lastNotifiedAtEpochMs: Long? = null,
    val triggerGlucoseMgDl: Double? = null,
    /** Trend captured with the triggering reading; never replaced by later CGM samples. */
    val triggerTrend: String? = null,
    val technicalErrorSignature: String? = null,
)

@Serializable
data class SugarWearAlarmSnapshot(
    val sensorId: String? = null,
    val sessionId: String? = null,
    val alarms: List<SugarWearAlarmInstance> = emptyList(),
    val sensorErrorSinceEpochMs: Long? = null,
    val activeProfile: ActiveSugarWearAlarmProfile = ActiveSugarWearAlarmProfile(SugarWearAlarmProfile.DAY),
    val glucoseProfileStartedAtEpochMs: Long? = null,
)
