package app.sugarlicious.mobile

import android.content.SharedPreferences
import androidx.core.content.edit
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.model.AppearanceMode

private val cgmGraphColorRoles =
    setOf(
        SugarliciousColorRole.RANGE_LOW,
        SugarliciousColorRole.RANGE_VERY_LOW,
        SugarliciousColorRole.GLUCOSE_VERY_LOW,
        SugarliciousColorRole.RANGE_IN_RANGE,
        SugarliciousColorRole.RANGE_HIGH,
        SugarliciousColorRole.RANGE_VERY_HIGH,
        SugarliciousColorRole.GLUCOSE_VERY_HIGH,
        SugarliciousColorRole.TARGET_BAND,
        SugarliciousColorRole.CGM_DOT_LOW,
        SugarliciousColorRole.CGM_DOT_VERY_LOW,
        SugarliciousColorRole.CGM_DOT_IN_RANGE,
        SugarliciousColorRole.CGM_DOT_HIGH,
        SugarliciousColorRole.CGM_DOT_VERY_HIGH,
        SugarliciousColorRole.GRAPH_DIVIDER,
        SugarliciousColorRole.GRAPH_BORDER,
        SugarliciousColorRole.GRAPH_SIGNAL_LOSS,
        SugarliciousColorRole.GRAPH_VERY_LOW_LINE,
        SugarliciousColorRole.GRAPH_VERY_HIGH_LINE,
    )

internal fun colorRoleVisible(
    role: SugarliciousColorRole,
    showCgmGraph: Boolean,
    showMetabolicGraph: Boolean,
    isLight: Boolean = false,
): Boolean {
    if (!role.configurable) return false
    if (!showCgmGraph && role in cgmGraphColorRoles) return false
    return role != SugarliciousColorRole.GRAPH_BACKGROUND || showCgmGraph || showMetabolicGraph
}

internal fun graphAppearanceKey(mode: AppearanceMode, suffix: String): String = "cgm.${mode.storageKey}.$suffix"

internal fun migrateGraphAppearance(preferences: SharedPreferences) {
    if (preferences.getBoolean("cgm.appearance.profiles.v1", false)) {
        migrateSplitDotOutlineWidths(preferences)
        return
    }
    val legacy =
        listOf(
            "dotRadiusDp" to "cgm.dotRadiusDp",
            "dotOutlineEnabled" to "cgm.dotOutlineEnabled",
            "dotOutlineWidthDp" to "cgm.dotOutlineWidthDp",
            "historicalDotOutlineWidthDp" to "cgm.dotOutlineWidthDp",
            "currentDotOutlineWidthDp" to "cgm.dotOutlineWidthDp",
            "historicalDotOutlineEnabled" to "cgm.dotOutlineEnabled",
            "prediction.dotRadiusDp" to "cgm.prediction.dotRadiusDp",
            "prediction.dotOutlineWidthDp" to "cgm.prediction.dotOutlineWidthDp",
        )
    if (legacy.none { (_, oldKey) -> preferences.contains(oldKey) }) return
    preferences.edit {
        legacy.forEach { (suffix, oldKey) ->
            if (!preferences.contains(oldKey)) return@forEach
            AppearanceMode.entries.forEach { mode ->
                val target = graphAppearanceKey(mode, suffix)
                if (preferences.contains(target)) return@forEach
                when (suffix) {
                    "dotOutlineEnabled", "historicalDotOutlineEnabled" -> putBoolean(target, preferences.getBoolean(oldKey, true))
                    else ->
                        putFloat(
                            target,
                            preferences.getFloat(
                                oldKey,
                                when (suffix) {
                                    "dotRadiusDp" -> 2.4f
                                    "prediction.dotRadiusDp" -> 1.75f
                                    "prediction.dotOutlineWidthDp" -> 0.70f
                                    else -> 0.95f
                                },
                            ),
                        )
                }
            }
        }
        putBoolean("cgm.appearance.profiles.v1", true)
    }
    migrateSplitDotOutlineWidths(preferences)
}

private fun migrateSplitDotOutlineWidths(preferences: SharedPreferences) {
    if (preferences.getBoolean("cgm.appearance.dotOutlines.v2", false)) return
    preferences.edit {
        AppearanceMode.entries.forEach { mode ->
            val legacyKey = graphAppearanceKey(mode, "dotOutlineWidthDp")
            val legacyWidth =
                preferences.getFloat(
                    legacyKey,
                    preferences.getFloat("cgm.dotOutlineWidthDp", 0.95f),
                )
            listOf("historicalDotOutlineWidthDp", "currentDotOutlineWidthDp").forEach { suffix ->
                val target = graphAppearanceKey(mode, suffix)
                if (!preferences.contains(target)) putFloat(target, legacyWidth)
            }
        }
        putBoolean("cgm.appearance.dotOutlines.v2", true)
    }
}
