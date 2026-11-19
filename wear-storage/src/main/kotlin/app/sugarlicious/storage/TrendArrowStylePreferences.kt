package app.sugarlicious.storage

import android.content.SharedPreferences
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.TrendArrowStyle

/** Shared persistence codec. Callers provide their own app/surface preference file. */
object TrendArrowStylePreferences {
    private const val PREFIX = "appearance.trend."

    fun read(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        defaultFill: Int,
        legacyScaleKey: String? = null,
        legacyFillKey: String? = null,
    ): TrendArrowStyle {
        val defaults = TrendArrowStyle.defaults(mode, defaultFill)
        val prefix = prefix(mode)
        return TrendArrowStyle(
            fillColor =
                preferences.getInt(
                    prefix + "fill",
                    legacyFillKey?.takeIf(preferences::contains)?.let { preferences.getInt(it, defaultFill) } ?: defaults.fillColor,
                ),
            outlineEnabled = preferences.getBoolean(prefix + "outline.enabled", defaults.outlineEnabled),
            outlineColor = preferences.getInt(prefix + "outline.color", defaults.outlineColor),
            outlineThicknessDp = preferences.getFloat(prefix + "outline.thicknessDp", defaults.outlineThicknessDp),
            sizePercent =
                preferences.getInt(
                    prefix + "sizePercent",
                    legacyScaleKey?.takeIf(preferences::contains)?.let { preferences.getInt(it, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT) }
                        ?: defaults.sizePercent,
                ),
            alpha = preferences.getFloat(prefix + "alpha", defaults.alpha),
        ).normalized()
    }

    fun write(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        style: TrendArrowStyle,
    ) {
        val value = style.normalized()
        preferences
            .edit()
            .putInt(prefix(mode) + "fill", value.fillColor)
            .putBoolean(prefix(mode) + "outline.enabled", value.outlineEnabled)
            .putInt(prefix(mode) + "outline.color", value.outlineColor)
            .putFloat(prefix(mode) + "outline.thicknessDp", value.outlineThicknessDp)
            .putInt(prefix(mode) + "sizePercent", value.sizePercent)
            .putFloat(prefix(mode) + "alpha", value.alpha)
            .apply()
    }

    fun reset(
        preferences: SharedPreferences,
        mode: AppearanceMode,
    ) {
        preferences
            .edit()
            .apply {
                listOf("fill", "outline.enabled", "outline.color", "outline.thicknessDp", "sizePercent", "alpha")
                    .forEach { remove(prefix(mode) + it) }
            }.apply()
    }

    fun hasOverride(
        preferences: SharedPreferences,
        mode: AppearanceMode,
    ): Boolean = preferences.contains(prefix(mode) + "fill")

    private fun prefix(mode: AppearanceMode) = "$PREFIX${mode.storageKey}."
}
