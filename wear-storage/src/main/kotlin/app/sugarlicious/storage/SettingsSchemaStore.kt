package app.sugarlicious.storage

import android.content.SharedPreferences

const val SETTINGS_SCHEMA_VERSION_KEY = "_settings_schema_version"

/** Records monotonic schema progress. Existing per-version migrations run before this marker advances. */
fun SharedPreferences.ensureSettingsSchema(
    currentVersion: Int,
    migrate: (fromVersion: Int, toVersion: Int) -> Unit = { _, _ -> },
): Int {
    require(currentVersion > 0)
    val stored = getInt(SETTINGS_SCHEMA_VERSION_KEY, 0)
    if (stored >= currentVersion) return stored
    migrate(stored, currentVersion)
    check(edit().putInt(SETTINGS_SCHEMA_VERSION_KEY, currentVersion).commit()) {
        "Settings schema marker could not be persisted"
    }
    return currentVersion
}
