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
    TECHNICAL_WARNING("technical_warning", "Technische Warnungen"),
    ;

    companion object {
        fun fromId(id: String): SugarWearAlarmType? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
enum class SugarWearAlertDeliveryMode(val labelGerman: String) {
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
) {
    fun ruleFor(type: SugarWearAlarmType): SugarWearAlarmRule =
        rules[type] ?: SugarWearAlarmDefaults.defaultRule(type)
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
    val technicalErrorSignature: String? = null,
)

@Serializable
data class SugarWearAlarmSnapshot(
    val sensorId: String? = null,
    val sessionId: String? = null,
    val alarms: List<SugarWearAlarmInstance> = emptyList(),
)
