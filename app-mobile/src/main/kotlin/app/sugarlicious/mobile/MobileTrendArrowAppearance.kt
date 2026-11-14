package app.sugarlicious.mobile

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.storage.TrendArrowStylePreferences

internal object MobileTrendArrowAppearance {
    var style by mutableStateOf(TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt()))
        private set

    fun load(
        preferences: SharedPreferences,
        mode: AppearanceMode = SugarliciousColorStore.activeMode(preferences),
    ): TrendArrowStyle =
        TrendArrowStylePreferences.read(
            preferences,
            mode,
            0xFFFFFFFF.toInt(),
            legacyScaleKey = DashboardUiPreferences.MOBILE_TREND_SCALE_KEY,
        )

    fun apply(preferences: SharedPreferences) {
        style = load(preferences)
    }

    fun save(
        preferences: SharedPreferences,
        mode: AppearanceMode,
        value: TrendArrowStyle,
    ) {
        TrendArrowStylePreferences.write(preferences, mode, value)
        style = value.normalized()
    }
}
