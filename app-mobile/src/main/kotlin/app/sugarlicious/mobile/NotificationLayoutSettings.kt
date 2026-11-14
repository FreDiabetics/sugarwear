package app.sugarlicious.mobile

import android.content.SharedPreferences
import androidx.core.content.edit
import app.sugarlicious.model.GlucoseTrendSizing

internal data class NotificationLayoutSettings(
    val glucoseScalePercent: Int = 100,
    val glucoseXPercent: Int = 0,
    val glucoseYPercent: Int = 0,
    val trendScalePercent: Int? = null,
    val trendXPercent: Int = 0,
    val trendYPercent: Int = 0,
    val metaScalePercent: Int = 100,
) {
    fun resolvedTrendPercent(systemPercent: Int): Int = (trendScalePercent ?: systemPercent).coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)
}

internal object NotificationLayoutSettingsStore {
    private const val PREFIX = "notification.layout."

    fun read(
        preferences: SharedPreferences,
        profile: NotificationGraphProfile,
    ): NotificationLayoutSettings {
        val p = PREFIX + profile.name.lowercase() + "."
        return NotificationLayoutSettings(
            glucoseScalePercent = preferences.getInt(p + "glucoseScale", 100).coerceIn(70, 200),
            glucoseXPercent = preferences.getInt(p + "glucoseX", 0).coerceIn(-40, 40),
            glucoseYPercent = preferences.getInt(p + "glucoseY", 0).coerceIn(-40, 40),
            trendScalePercent = preferences.getInt(p + "trendScale", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }?.coerceIn(70, 200),
            trendXPercent = preferences.getInt(p + "trendX", 0).coerceIn(-40, 40),
            trendYPercent = preferences.getInt(p + "trendY", 0).coerceIn(-40, 40),
            metaScalePercent = preferences.getInt(p + "metaScale", 100).coerceIn(70, 160),
        )
    }

    fun save(
        preferences: SharedPreferences,
        profile: NotificationGraphProfile,
        value: NotificationLayoutSettings,
    ) {
        val p = PREFIX + profile.name.lowercase() + "."
        preferences.edit {
            putInt(p + "glucoseScale", value.glucoseScalePercent.coerceIn(70, 200))
            putInt(p + "glucoseX", value.glucoseXPercent.coerceIn(-40, 40))
            putInt(p + "glucoseY", value.glucoseYPercent.coerceIn(-40, 40))
            value.trendScalePercent?.let { putInt(p + "trendScale", it.coerceIn(70, 200)) } ?: remove(p + "trendScale")
            putInt(p + "trendX", value.trendXPercent.coerceIn(-40, 40))
            putInt(p + "trendY", value.trendYPercent.coerceIn(-40, 40))
            putInt(p + "metaScale", value.metaScalePercent.coerceIn(70, 160))
        }
    }
}
