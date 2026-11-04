package app.aapswear.g7watch

import app.aapswear.g7.CgmAlarmSettings
import app.aapswear.g7.CgmAlarmType
import java.io.IOException

/** Semantic edits are applied to the latest canonical settings inside the ordered IO worker. */
internal sealed interface G7AlarmSettingsEdit {
    data class Sound(
        val enabled: Boolean,
    ) : G7AlarmSettingsEdit

    data class Vibration(
        val enabled: Boolean,
    ) : G7AlarmSettingsEdit

    data class Repeat(
        val enabled: Boolean,
    ) : G7AlarmSettingsEdit

    data class AdjustRepeat(
        val deltaMinutes: Int,
    ) : G7AlarmSettingsEdit

    data class AlarmEnabled(
        val type: CgmAlarmType,
        val enabled: Boolean,
    ) : G7AlarmSettingsEdit

    data class Threshold(
        val type: CgmAlarmType,
        val value: Double,
    ) : G7AlarmSettingsEdit
}

internal fun applyG7AlarmSettingsEdit(current: CgmAlarmSettings, edit: G7AlarmSettingsEdit): CgmAlarmSettings =
    when (edit) {
        is G7AlarmSettingsEdit.Sound -> current.copy(soundEnabled = edit.enabled)
        is G7AlarmSettingsEdit.Vibration -> current.copy(vibrationEnabled = edit.enabled)
        is G7AlarmSettingsEdit.Repeat -> current.copy(repeatEnabled = edit.enabled)
        is G7AlarmSettingsEdit.AdjustRepeat -> current.copy(repeatIntervalMinutes = (current.repeatIntervalMinutes + edit.deltaMinutes).coerceIn(5, 120))
        is G7AlarmSettingsEdit.AlarmEnabled -> withAlarmEnabled(current, edit.type, edit.enabled)
        is G7AlarmSettingsEdit.Threshold -> withThreshold(current, edit.type, edit.value) ?: throw IOException("Alarm settings changed; threshold needs revalidation")
    }
