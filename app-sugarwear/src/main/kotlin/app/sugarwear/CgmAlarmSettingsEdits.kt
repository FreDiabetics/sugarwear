package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmAlarmSettings
import app.sugarlicious.sensor.protocol.CgmAlarmType
import java.io.IOException

/** Semantic edits are applied to the latest canonical settings inside the ordered IO worker. */
internal sealed interface CgmAlarmSettingsEdit {
    data class Sound(
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class Vibration(
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class Repeat(
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class AdjustRepeat(
        val deltaMinutes: Int,
    ) : CgmAlarmSettingsEdit

    data class AlarmEnabled(
        val type: CgmAlarmType,
        val enabled: Boolean,
    ) : CgmAlarmSettingsEdit

    data class Threshold(
        val type: CgmAlarmType,
        val value: Double,
    ) : CgmAlarmSettingsEdit
}

internal fun applyCgmAlarmSettingsEdit(current: CgmAlarmSettings, edit: CgmAlarmSettingsEdit): CgmAlarmSettings =
    when (edit) {
        is CgmAlarmSettingsEdit.Sound -> current.copy(soundEnabled = edit.enabled)
        is CgmAlarmSettingsEdit.Vibration -> current.copy(vibrationEnabled = edit.enabled)
        is CgmAlarmSettingsEdit.Repeat -> current.copy(repeatEnabled = edit.enabled)
        is CgmAlarmSettingsEdit.AdjustRepeat -> current.copy(repeatIntervalMinutes = (current.repeatIntervalMinutes + edit.deltaMinutes).coerceIn(5, 120))
        is CgmAlarmSettingsEdit.AlarmEnabled -> withAlarmEnabled(current, edit.type, edit.enabled)
        is CgmAlarmSettingsEdit.Threshold -> withThreshold(current, edit.type, edit.value) ?: throw IOException("Alarm settings changed; threshold needs revalidation")
    }
