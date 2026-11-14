package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.TrendArrowStyleOverride

internal data class ComplicationAppearanceSettings(
    val trendScalePercent: Int? = null,
    val trendOffsetXPercent: Int = 0,
    val trendOffsetYPercent: Int = 0,
    val trendStyleOverride: TrendArrowStyleOverride = TrendArrowStyleOverride(),
)

internal object ComplicationAppearanceSettingsStore {
    private const val PREFS = "complication_appearance"

    fun load(
        context: Context,
        catalogId: Int,
    ): ComplicationAppearanceSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scaleKey = "$catalogId.trendScale"
        return ComplicationAppearanceSettings(
            trendScalePercent =
                if (prefs.contains(scaleKey)) {
                    prefs
                        .getInt(scaleKey, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
                        .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)
                } else {
                    null
                },
            trendOffsetXPercent = prefs.getInt("$catalogId.trendX", 0).coerceIn(-50, 50),
            trendOffsetYPercent = prefs.getInt("$catalogId.trendY", 0).coerceIn(-50, 50),
            trendStyleOverride =
                TrendArrowStyleOverride(
                    fillColor = prefs.getInt("$catalogId.trendFill", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                    outlineEnabled =
                        if (prefs.contains(
                                "$catalogId.trendOutlineEnabled",
                            )
                        ) {
                            prefs.getBoolean("$catalogId.trendOutlineEnabled", false)
                        } else {
                            null
                        },
                    outlineColor = prefs.getInt("$catalogId.trendOutlineColor", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                    outlineThicknessDp = prefs.getFloat("$catalogId.trendOutlineThickness", Float.NaN).takeUnless(Float::isNaN),
                    sizePercent = if (prefs.contains(scaleKey)) prefs.getInt(scaleKey, 100) else null,
                    alpha = prefs.getFloat("$catalogId.trendAlpha", Float.NaN).takeUnless(Float::isNaN),
                ),
        )
    }

    fun save(
        context: Context,
        catalogId: Int,
        value: ComplicationAppearanceSettings,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (value.trendScalePercent == null) {
                remove("$catalogId.trendScale")
            } else {
                putInt("$catalogId.trendScale", value.trendScalePercent)
            }
            putInt("$catalogId.trendX", value.trendOffsetXPercent)
            putInt("$catalogId.trendY", value.trendOffsetYPercent)

            fun putIntOrRemove(
                key: String,
                raw: Int?,
            ) {
                if (raw == null) remove(key) else putInt(key, raw)
            }

            fun putFloatOrRemove(
                key: String,
                raw: Float?,
            ) {
                if (raw == null) remove(key) else putFloat(key, raw)
            }
            putIntOrRemove("$catalogId.trendFill", value.trendStyleOverride.fillColor)
            value.trendStyleOverride.outlineEnabled?.let { putBoolean("$catalogId.trendOutlineEnabled", it) }
                ?: remove("$catalogId.trendOutlineEnabled")
            putIntOrRemove("$catalogId.trendOutlineColor", value.trendStyleOverride.outlineColor)
            putFloatOrRemove("$catalogId.trendOutlineThickness", value.trendStyleOverride.outlineThicknessDp)
            putFloatOrRemove("$catalogId.trendAlpha", value.trendStyleOverride.alpha)
        }
    }
}
