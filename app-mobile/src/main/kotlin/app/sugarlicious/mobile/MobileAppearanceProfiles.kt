package app.sugarlicious.mobile

import android.content.SharedPreferences
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.protocol.WatchGraphStyle

internal fun activeGraphAppearanceMode(preferences: SharedPreferences): AppearanceMode = SugarliciousColorStore.activeMode(preferences)

internal object MobileGraphSystemDefaults {
    fun dotRadius(mode: AppearanceMode) = if (mode == AppearanceMode.LIGHT) 2.6f else 3.0f

    fun historicalOutlineEnabled(mode: AppearanceMode) = mode != AppearanceMode.LIGHT

    fun historicalOutlineWidth(mode: AppearanceMode) = if (mode == AppearanceMode.LIGHT) 1.0f else 0.6f

    fun currentOutlineWidth(mode: AppearanceMode) = if (mode == AppearanceMode.LIGHT) 1.0f else 1.2f

    const val PREDICTION_DOT_RADIUS_DP = 1.6f
    const val PREDICTION_OUTLINE_WIDTH_DP = 0.2f
}

internal fun readMobileGraphStyle(
    preferences: SharedPreferences,
    mode: AppearanceMode = activeGraphAppearanceMode(preferences),
): WatchGraphStyle =
    WatchGraphStyle(
        cgmDotRadiusDp =
            preferences
                .getFloat(
                    graphAppearanceKey(mode, "dotRadiusDp"),
                    preferences.getFloat("cgm.dotRadiusDp", MobileGraphSystemDefaults.dotRadius(mode)),
                ).coerceIn(1.5f, 6f),
        cgmHistoricalDotOutlineEnabled =
            preferences.getBoolean(
                graphAppearanceKey(mode, "historicalDotOutlineEnabled"),
                preferences.getBoolean("cgm.dotOutlineEnabled", MobileGraphSystemDefaults.historicalOutlineEnabled(mode)),
            ),
        cgmHistoricalDotOutlineWidthDp =
            preferences
                .getFloat(
                    graphAppearanceKey(mode, "historicalDotOutlineWidthDp"),
                    preferences.getFloat(
                        graphAppearanceKey(mode, "dotOutlineWidthDp"),
                        preferences.getFloat("cgm.dotOutlineWidthDp", MobileGraphSystemDefaults.historicalOutlineWidth(mode)),
                    ),
                ).coerceIn(0.25f, 3f),
        cgmCurrentDotOutlineWidthDp =
            preferences
                .getFloat(
                    graphAppearanceKey(mode, "currentDotOutlineWidthDp"),
                    preferences.getFloat(
                        graphAppearanceKey(mode, "dotOutlineWidthDp"),
                        preferences.getFloat("cgm.dotOutlineWidthDp", MobileGraphSystemDefaults.currentOutlineWidth(mode)),
                    ),
                ).coerceIn(0.25f, 3f),
    )

internal fun readMobilePredictionDotRadius(
    preferences: SharedPreferences,
    mode: AppearanceMode = activeGraphAppearanceMode(preferences),
): Float =
    preferences
        .getFloat(
            graphAppearanceKey(mode, "prediction.dotRadiusDp"),
            preferences.getFloat("cgm.prediction.dotRadiusDp", MobileGraphSystemDefaults.PREDICTION_DOT_RADIUS_DP),
        ).coerceIn(1.0f, 6.0f)

internal fun readMobilePredictionDotOutlineWidth(
    preferences: SharedPreferences,
    mode: AppearanceMode = activeGraphAppearanceMode(preferences),
): Float =
    preferences
        .getFloat(
            graphAppearanceKey(mode, "prediction.dotOutlineWidthDp"),
            preferences.getFloat("cgm.prediction.dotOutlineWidthDp", MobileGraphSystemDefaults.PREDICTION_OUTLINE_WIDTH_DP),
        ).coerceIn(0.0f, 3.0f)
