package app.sugarwear

internal object SugarWearAlarmDefaults {
    const val URGENT_LOW_THRESHOLD_MG_DL = 55.0
    const val URGENT_LOW_SOON_THRESHOLD_MG_DL = 55.0
    const val URGENT_LOW_SOON_HORIZON_MINUTES = 20
    const val LOW_DEFAULT_THRESHOLD_MG_DL = 75.0
    const val HIGH_DEFAULT_THRESHOLD_MG_DL = 150.0
    const val HIGH_DEFAULT_DELAY_MINUTES = 15
    const val RAPID_RISE_STEP1_MIN = 2.0
    const val RAPID_RISE_STEP2_MIN = 3.0
    const val RAPID_RISE_ADDITIONAL_GLUCOSE_MIN = 100.0
    const val RAPID_FALL_STEP1_MIN = 2.0
    const val RAPID_FALL_STEP2_MIN = 3.0
    const val RAPID_FALL_ADDITIONAL_GLUCOSE_MAX = 190.0
    const val SIGNAL_LOSS_DEFAULT_MINUTES = 20
    const val TEMPORARY_SENSOR_PROBLEM_DEFAULT_MINUTES = 20

    const val REPEAT_LOW_MINUTES = 60
    const val REPEAT_HIGH_MINUTES = 60
    const val REPEAT_URGENT_LOW_MINUTES = 30
    const val REPEAT_URGENT_LOW_SOON_MINUTES = 30

    fun defaultRule(type: SugarWearAlarmType): SugarWearAlarmRule =
        when (type) {
            SugarWearAlarmType.URGENT_LOW ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = true, // Fixed, non-disableable
                    thresholdMgDl = URGENT_LOW_THRESHOLD_MG_DL,
                    snoozeMinutes = REPEAT_URGENT_LOW_MINUTES,
                )
            SugarWearAlarmType.URGENT_LOW_SOON ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = false,
                    thresholdMgDl = URGENT_LOW_SOON_THRESHOLD_MG_DL,
                    durationMinutes = URGENT_LOW_SOON_HORIZON_MINUTES,
                    snoozeMinutes = REPEAT_URGENT_LOW_SOON_MINUTES,
                )
            SugarWearAlarmType.LOW ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = true,
                    thresholdMgDl = LOW_DEFAULT_THRESHOLD_MG_DL,
                    snoozeMinutes = REPEAT_LOW_MINUTES,
                )
            SugarWearAlarmType.HIGH ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = true,
                    thresholdMgDl = HIGH_DEFAULT_THRESHOLD_MG_DL,
                    highDelayMinutes = HIGH_DEFAULT_DELAY_MINUTES,
                    snoozeMinutes = REPEAT_HIGH_MINUTES,
                )
            SugarWearAlarmType.RAPID_RISE ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = false,
                    rateStep = 2, // >= 3 mg/dL/min
                    additionalGlucoseThresholdMgDl = RAPID_RISE_ADDITIONAL_GLUCOSE_MIN,
                    snoozeMinutes = 60,
                )
            SugarWearAlarmType.RAPID_FALL ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = true,
                    rateStep = 2, // >= 3 mg/dL/min
                    additionalGlucoseThresholdMgDl = RAPID_FALL_ADDITIONAL_GLUCOSE_MAX,
                    snoozeMinutes = 60,
                )
            SugarWearAlarmType.SIGNAL_LOSS ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = false,
                    durationMinutes = SIGNAL_LOSS_DEFAULT_MINUTES,
                    snoozeMinutes = 60,
                )
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = false,
                    durationMinutes = TEMPORARY_SENSOR_PROBLEM_DEFAULT_MINUTES,
                    snoozeMinutes = 60,
                )
            SugarWearAlarmType.TECHNICAL_WARNING ->
                SugarWearAlarmRule(
                    type = type,
                    enabled = true, // Fixed, non-disableable
                    snoozeMinutes = 30,
                )
        }

    fun defaultSettings(): SugarWearAlarmSettings =
        SugarWearAlarmSettings(
            globalVibrateOnly = false,
            globalMuteAll = false,
            rules = SugarWearAlarmType.entries.associateWith { defaultRule(it) },
        )

    fun audioAssetPath(type: SugarWearAlarmType): String =
        when (type) {
            SugarWearAlarmType.URGENT_LOW -> "alarms/alerts_sounds_urgent_low_alarm.mp4"
            SugarWearAlarmType.URGENT_LOW_SOON -> "alarms/alerts_sounds_urgent_low_alarm.mp4"
            SugarWearAlarmType.LOW -> "alarms/alerts_sounds_low.mp4"
            SugarWearAlarmType.HIGH -> "alarms/high_alert.mp4"
            SugarWearAlarmType.RAPID_RISE -> "alarms/alerts_sounds_rise_rate.mp4"
            SugarWearAlarmType.RAPID_FALL -> "alarms/alerts_sounds_fall_rate.mp4"
            SugarWearAlarmType.SIGNAL_LOSS -> "alarms/alerts_sounds_signal_loss_alert.mp4"
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> "alarms/alerts_sounds_beep.mp4"
            SugarWearAlarmType.TECHNICAL_WARNING -> "alarms/alerts_sounds_beep.mp4"
        }

    /**
     * Original Dexcom-style audio supplied for SugarWear. Optional until all binary
     * assets are bundled; callers must fall back to audioAssetPath for older builds.
     * System-alert is reserved for non-CGM watch system events (not a tenth CGM alarm).
     */
    fun preferredAudioAssetPath(type: SugarWearAlarmType): String =
        when (type) {
            SugarWearAlarmType.URGENT_LOW -> "alarms/alerts_sounds_medium_urgent_low.mp4"
            SugarWearAlarmType.URGENT_LOW_SOON -> "alarms/alerts_sounds_medium_urgent_low_soon.mp4"
            SugarWearAlarmType.LOW -> "alarms/alerts_sounds_medium_low.mp4"
            SugarWearAlarmType.HIGH -> "alarms/alerts_sounds_medium_high.mp4"
            SugarWearAlarmType.RAPID_RISE -> "alarms/alerts_sounds_medium_rising_rate.mp4"
            SugarWearAlarmType.RAPID_FALL -> "alarms/alerts_sounds_medium_falling_rate.mp4"
            SugarWearAlarmType.SIGNAL_LOSS -> "alarms/alerts_sounds_medium_signal_loss.mp4"
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> "alarms/alerts_sounds_medium_bsi.mp4"
            SugarWearAlarmType.TECHNICAL_WARNING -> "alarms/alerts_sounds_medium_technical_alerts.mp4"
        }

    const val SYSTEM_ALERT_AUDIO_ASSET = "alarms/alerts_sounds_medium_system_alert.mp4"

    fun priority(type: SugarWearAlarmType): Int =
        when (type) {
            SugarWearAlarmType.URGENT_LOW -> 100
            SugarWearAlarmType.TECHNICAL_WARNING -> 90
            SugarWearAlarmType.URGENT_LOW_SOON -> 80
            SugarWearAlarmType.LOW -> 70
            SugarWearAlarmType.RAPID_FALL -> 60
            SugarWearAlarmType.HIGH -> 50
            SugarWearAlarmType.RAPID_RISE -> 40
            SugarWearAlarmType.SIGNAL_LOSS -> 30
            SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> 20
        }
}
