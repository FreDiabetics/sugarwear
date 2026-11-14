package app.sugarwear

import java.io.IOException

/** Semantic edits applied to canonical settings inside the ordered worker. */
internal sealed interface CgmAlarmSettingsEdit {
    data class GlobalMuteAll(val enabled: Boolean) : CgmAlarmSettingsEdit
    data class GlobalVibrateOnly(val enabled: Boolean) : CgmAlarmSettingsEdit
    data class AlarmEnabled(val type: SugarWearAlarmType, val enabled: Boolean) : CgmAlarmSettingsEdit
    data class Threshold(val type: SugarWearAlarmType, val value: Double) : CgmAlarmSettingsEdit
    data class RateStep(val type: SugarWearAlarmType, val step: Int) : CgmAlarmSettingsEdit
    data class AdditionalGlucoseThreshold(val type: SugarWearAlarmType, val value: Double) : CgmAlarmSettingsEdit
    data class HighDelay(val type: SugarWearAlarmType, val minutes: Int) : CgmAlarmSettingsEdit
    data class SnoozeEnabled(val type: SugarWearAlarmType, val enabled: Boolean) : CgmAlarmSettingsEdit
    data class SnoozeMinutes(val type: SugarWearAlarmType, val minutes: Int) : CgmAlarmSettingsEdit
    data class DurationMinutes(val type: SugarWearAlarmType, val minutes: Int) : CgmAlarmSettingsEdit
    data class DeliveryMode(val type: SugarWearAlarmType, val mode: SugarWearAlertDeliveryMode) : CgmAlarmSettingsEdit
    object ResetAll : CgmAlarmSettingsEdit
}

internal fun applyCgmAlarmSettingsEdit(current: SugarWearAlarmSettings, edit: CgmAlarmSettingsEdit): SugarWearAlarmSettings {
    return when (edit) {
        is CgmAlarmSettingsEdit.GlobalMuteAll -> current.copy(globalMuteAll = edit.enabled)
        is CgmAlarmSettingsEdit.GlobalVibrateOnly -> current.copy(globalVibrateOnly = edit.enabled)
        is CgmAlarmSettingsEdit.ResetAll -> SugarWearAlarmDefaults.defaultSettings()
        is CgmAlarmSettingsEdit.AlarmEnabled -> {
            // Reject domain-level disable for URGENT_LOW and TECHNICAL_WARNING
            if (edit.type == SugarWearAlarmType.URGENT_LOW || edit.type == SugarWearAlarmType.TECHNICAL_WARNING) {
                return current
            }
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(enabled = edit.enabled)
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.Threshold -> {
            val rule = current.ruleFor(edit.type)
            val validated = when (edit.type) {
                SugarWearAlarmType.LOW -> edit.value.coerceIn(60.0, 100.0)
                SugarWearAlarmType.HIGH -> edit.value.coerceIn(120.0, 400.0)
                else -> edit.value
            }
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(thresholdMgDl = validated)
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.RateStep -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(rateStep = edit.step.coerceIn(1, 2))
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.AdditionalGlucoseThreshold -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(additionalGlucoseThresholdMgDl = edit.value.coerceIn(40.0, 400.0))
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.HighDelay -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(highDelayMinutes = edit.minutes.coerceIn(0, 240))
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.SnoozeEnabled -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(snoozeEnabled = edit.enabled)
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.SnoozeMinutes -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(snoozeMinutes = edit.minutes.coerceIn(5, 120))
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.DurationMinutes -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(durationMinutes = edit.minutes.coerceIn(5, 120))
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.DeliveryMode -> {
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(deliveryMode = edit.mode)
            current.copy(rules = updatedRules)
        }
    }
}
