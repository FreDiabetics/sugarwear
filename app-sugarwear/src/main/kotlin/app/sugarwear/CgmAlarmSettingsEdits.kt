package app.sugarwear

/** Semantic edits applied to canonical settings inside the ordered worker. */
internal sealed interface CgmAlarmSettingsEdit {
    data class GlobalMuteAll(
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class GlobalVibrateOnly(
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class AlarmEnabled(
        val type: SugarWearAlarmType,
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class Threshold(
        val type: SugarWearAlarmType,
        val value: Double,
    ) : CgmAlarmSettingsEdit

    data class RateStep(
        val type: SugarWearAlarmType,
        val step: Int,
    ) : CgmAlarmSettingsEdit

    data class AdditionalGlucoseThreshold(
        val type: SugarWearAlarmType,
        val value: Double,
    ) : CgmAlarmSettingsEdit

    data class HighDelay(
        val type: SugarWearAlarmType,
        val minutes: Int,
    ) : CgmAlarmSettingsEdit

    data class SnoozeEnabled(
        val type: SugarWearAlarmType,
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class SnoozeMinutes(
        val type: SugarWearAlarmType,
        val minutes: Int,
    ) : CgmAlarmSettingsEdit

    data class DurationMinutes(
        val type: SugarWearAlarmType,
        val minutes: Int,
    ) : CgmAlarmSettingsEdit

    data class DeliveryMode(
        val type: SugarWearAlarmType,
        val mode: SugarWearAlertDeliveryMode,
    ) : CgmAlarmSettingsEdit

    data class ManualProfile(
        val profile: SugarWearAlarmProfile,
    ) : CgmAlarmSettingsEdit

    data class Schedule(
        val schedule: SugarWearAlarmSchedule,
    ) : CgmAlarmSettingsEdit

    object ResetAll : CgmAlarmSettingsEdit
}

internal fun applyCgmAlarmSettingsEdit(
    current: SugarWearAlarmSettings,
    edit: CgmAlarmSettingsEdit,
    profile: SugarWearAlarmProfile = SugarWearAlarmProfile.DAY,
): SugarWearAlarmSettings =
    when (edit) {
        is CgmAlarmSettingsEdit.ManualProfile ->
            current.copy(
                manualProfile = edit.profile,
                scheduleEnabled = false,
                nightRules = current.nightRules ?: current.rules.takeIf { edit.profile == SugarWearAlarmProfile.NIGHT },
            )
        is CgmAlarmSettingsEdit.Schedule ->
            current.copy(
                schedule = edit.schedule,
                scheduleEnabled = true,
                nightRules = current.nightRules ?: current.rules,
            )
        is CgmAlarmSettingsEdit.GlobalMuteAll,
        is CgmAlarmSettingsEdit.GlobalVibrateOnly,
        CgmAlarmSettingsEdit.ResetAll,
        -> editAlarmRules(current, edit)
        else -> {
            if (profile == SugarWearAlarmProfile.NIGHT) {
                current.copy(nightRules = editAlarmRules(current.forProfile(profile), edit).rules)
            } else {
                editAlarmRules(current, edit)
            }
        }
    }

private fun editAlarmRules(current: SugarWearAlarmSettings, edit: CgmAlarmSettingsEdit): SugarWearAlarmSettings {
    return when (edit) {
        is CgmAlarmSettingsEdit.ManualProfile, is CgmAlarmSettingsEdit.Schedule -> error("Profile selection is applied before editing rules")
        is CgmAlarmSettingsEdit.GlobalMuteAll -> current.copy(globalMuteAll = edit.enabled)
        is CgmAlarmSettingsEdit.GlobalVibrateOnly -> current.copy(globalVibrateOnly = edit.enabled)
        is CgmAlarmSettingsEdit.ResetAll -> SugarWearAlarmDefaults.defaultSettings()
        is CgmAlarmSettingsEdit.AlarmEnabled -> {
            // Reject domain-level disable for URGENT_LOW and TECHNICAL_WARNING
            if (edit.type.isMandatory) {
                return current
            }
            val rule = current.ruleFor(edit.type)
            val updatedRules = current.rules.toMutableMap()
            updatedRules[edit.type] = rule.copy(enabled = edit.enabled)
            current.copy(rules = updatedRules)
        }
        is CgmAlarmSettingsEdit.Threshold -> {
            val rule = current.ruleFor(edit.type)
            val validated =
                when (edit.type) {
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
