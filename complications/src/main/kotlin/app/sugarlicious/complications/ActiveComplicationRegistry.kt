package app.sugarlicious.complications

import android.content.Context

/**
 * Best-effort registry of complication provider instances currently requested by Wear OS.
 * Wear OS does not expose arbitrary watch-face slot assignments to companion apps, but provider
 * activation is enough to report the real active Sugarlicious data sources instead of mock images.
 */
object ActiveComplicationRegistry {
    private const val PREFS = "active_sugarlicious_complications"
    private const val PREFIX = "instance."

    fun activate(
        context: Context,
        instanceId: Int,
        catalogId: Int?,
    ) {
        if (catalogId == null) return
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREFIX + instanceId, catalogId)
            .apply()
    }

    fun deactivate(
        context: Context,
        instanceId: Int,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX + instanceId)
            .apply()
    }

    fun activeCatalogIds(context: Context): List<Int> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .all
            .asSequence()
            .filter { (key, value) -> key.startsWith(PREFIX) && value is Int }
            .map { (_, value) -> value as Int }
            .distinct()
            .sorted()
            .toList()
}
