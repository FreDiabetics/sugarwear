package app.sugarwear

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit

internal object SugarWearAlarmMigration {
    private const val OLD_PREFERENCES = "g7_cgm_alarm_settings"
    private const val OLD_STATE_PREFERENCES = "g7_cgm_alarm_state"
    private const val NEW_PREFERENCES = "sugarwear_alarm_settings_v2"
    private const val KEY_MIGRATED = "migrated_v2"

    fun migrateIfNeeded(context: Context): SugarWearAlarmSettings {
        val app = context.applicationContext
        val newPrefs = app.getSharedPreferences(NEW_PREFERENCES, Context.MODE_PRIVATE)
        if (newPrefs.getBoolean(KEY_MIGRATED, false)) {
            return readSettings(app)
        }

        val oldPrefs = app.getSharedPreferences(OLD_PREFERENCES, Context.MODE_PRIVATE)
        val defaults = SugarWearAlarmDefaults.defaultSettings()

        val highThreshold = oldPrefs.getFloat("high", 150f).toDouble().coerceIn(100.0, 400.0)
        val lowThreshold = oldPrefs.getFloat("low", 75f).toDouble().coerceIn(40.0, 100.0)
        val highEnabled = oldPrefs.getBoolean("high_enabled", true)
        val lowEnabled = oldPrefs.getBoolean("low_enabled", true)
        val rapidRiseEnabled = oldPrefs.getBoolean("rapid_rise_enabled", false)
        val rapidFallEnabled = oldPrefs.getBoolean("rapid_fall_enabled", true)
        val signalLossEnabled = oldPrefs.getBoolean("signal_loss_enabled", false)
        val vibrationEnabled = oldPrefs.getBoolean("vibration_enabled", true)

        val updatedRules = defaults.rules.toMutableMap()

        updatedRules[SugarWearAlarmType.HIGH] = defaults.ruleFor(SugarWearAlarmType.HIGH).copy(
            enabled = highEnabled,
            thresholdMgDl = highThreshold,
        )
        updatedRules[SugarWearAlarmType.LOW] = defaults.ruleFor(SugarWearAlarmType.LOW).copy(
            enabled = lowEnabled,
            thresholdMgDl = lowThreshold,
        )
        updatedRules[SugarWearAlarmType.RAPID_RISE] = defaults.ruleFor(SugarWearAlarmType.RAPID_RISE).copy(
            enabled = rapidRiseEnabled,
        )
        updatedRules[SugarWearAlarmType.RAPID_FALL] = defaults.ruleFor(SugarWearAlarmType.RAPID_FALL).copy(
            enabled = rapidFallEnabled,
        )
        updatedRules[SugarWearAlarmType.SIGNAL_LOSS] = defaults.ruleFor(SugarWearAlarmType.SIGNAL_LOSS).copy(
            enabled = signalLossEnabled,
        )

        val migratedSettings = SugarWearAlarmSettings(
            globalVibrateOnly = !oldPrefs.getBoolean("sound_enabled", true) && vibrationEnabled,
            globalMuteAll = !oldPrefs.getBoolean("sound_enabled", true) && !vibrationEnabled,
            rules = updatedRules,
        )

        writeSettings(app, migratedSettings)

        // Clear old alarm snapshots/state and legacy notification IDs
        val oldStatePrefs = app.getSharedPreferences(OLD_STATE_PREFERENCES, Context.MODE_PRIVATE)
        oldStatePrefs.edit().clear().apply()

        val nm = app.getSystemService(NotificationManager::class.java)
        nm?.cancelAll()

        newPrefs.edit { putBoolean(KEY_MIGRATED, true) }
        return migratedSettings
    }

    fun readSettings(context: Context): SugarWearAlarmSettings {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(NEW_PREFERENCES, Context.MODE_PRIVATE)
        val jsonString = prefs.getString("settings_json", null) ?: return SugarWearAlarmDefaults.defaultSettings()
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(SugarWearAlarmSettings.serializer(), jsonString)
        } catch (e: Exception) {
            SugarWearAlarmDefaults.defaultSettings()
        }
    }

    fun writeSettings(context: Context, settings: SugarWearAlarmSettings) {
        val app = context.applicationContext
        val jsonString = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.encodeToString(SugarWearAlarmSettings.serializer(), settings)
        app.getSharedPreferences(NEW_PREFERENCES, Context.MODE_PRIVATE).edit {
            putString("settings_json", jsonString)
            putBoolean(KEY_MIGRATED, true)
        }
    }
}
