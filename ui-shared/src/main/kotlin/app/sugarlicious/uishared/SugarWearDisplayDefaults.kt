package app.sugarlicious.uishared

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Galaxy reference captured 2026-10-27; contains display settings only, never sensor credentials/history. */
object SugarWearDisplayDefaults {
    private val baselineReferences: Map<String, Map<String, Any>> =
        mapOf(
            "g7_appearance" to
                mapOf(
                    "active_mode" to "dark",
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 85,
                    "color.dark.glucose_high" to -12224,
                    "color.dark.glucose_low" to -41879,
                    "color.dark.glucose_very_high" to -12224,
                    "color.dark.glucose_very_low" to -41879,
                    "color.dark.graph_dot_high" to -1,
                    "color.dark.graph_dot_in_range" to -1,
                    "color.dark.graph_dot_low" to -1,
                    "color.dark.graph_tile_border" to 0,
                    "color.dark.menu_background" to -16777216,
                    "color.dark.menu_border" to 0,
                    "color.dark.menu_primary" to -14488736,
                    "color.light.glucose_high" to -12224,
                    "color.light.glucose_low" to -41879,
                    "color.light.glucose_very_high" to -12224,
                    "color.light.glucose_very_low" to -41879,
                    "color.light.graph_dot_high" to -1,
                    "color.light.graph_dot_in_range" to -1,
                    "color.light.graph_dot_low" to -1,
                    "color.light.graph_tile_border" to 0,
                    "color.light.menu_background" to -16777216,
                    "color.light.menu_border" to 0,
                    "color.light.menu_primary" to -14488736,
                    "glucose_scale_percent" to 115,
                    "graph.current_outline_width" to 2.0f,
                    "graph.historical_outline_width" to 1.9f,
                    "graph_historical_dot_outline_enabled" to false,
                    "graph_hours" to 2,
                    "in_app_graph.border" to false,
                    "tile_graph.border" to false,
                    "tile_graph.color.dark.graph_dot_high" to -1,
                    "tile_graph.color.dark.graph_dot_in_range" to -1,
                    "tile_graph.color.dark.graph_dot_low" to -1,
                    "tile_graph.color.dark.graph_tile_border" to 0,
                    "tile_graph.color.light.graph_dot_high" to -1,
                    "tile_graph.color.light.graph_dot_in_range" to -1,
                    "tile_graph.color.light.graph_dot_low" to -1,
                    "tile_graph.color.light.graph_tile_border" to 0,
                    "tile_graph.corner_radius" to 22.2f,
                    "tile_graph.current_outline" to true,
                    "tile_graph.dot_radius" to 2.4f,
                    "tile_graph.history_outline" to false,
                    "tile_graph.hours" to 2,
                    "tile_graph.outline_width" to 0.35f,
                    "tile_graph.scale_lane_opacity" to 30,
                    "tile_graph.time_axis" to true,
                    "trend_scale_percent" to 70,
                ),
            "direct_to_watch" to
                mapOf(
                    "appearance.active_mode" to "dark",
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 100,
                    "graph.hours" to 2,
                    "graph_color_axis_label" to -6908266,
                    "graph_color_axis_tick" to -6908266,
                    "graph_color_background" to -14935012,
                    "graph_color_border" to 0,
                    "graph_color_cgm_high" to -1,
                    "graph_color_cgm_in" to -1,
                    "graph_color_cgm_low" to -1,
                    "graph_color_cgm_very_high" to -1,
                    "graph_color_cgm_very_low" to -1,
                    "graph_color_divider" to -6908266,
                    "graph_color_high_line" to -12224,
                    "graph_color_low_line" to -574139,
                    "graph_color_now_line" to -6908266,
                    "graph_color_outline" to -16777216,
                    "graph_color_prediction_cob" to -729600,
                    "graph_color_prediction_iob" to -11353601,
                    "graph_color_prediction_uam" to -20961,
                    "graph_color_prediction_zero_temp" to -13575202,
                    "graph_color_range_high" to -210629,
                    "graph_color_range_in" to 1717328988,
                    "graph_color_range_low" to -574139,
                    "graph_color_signal_loss" to 1191140457,
                    "graph_color_target_value" to -657931,
                    "graph_style_border_enabled" to false,
                    "graph_style_corner_radius" to 20.0f,
                    "graph_style_current_dot_outline_enabled" to true,
                    "graph_style_current_dot_outline_width" to 0.90000004f,
                    "graph_style_dot_outline_enabled" to true,
                    "graph_style_dot_outline_width" to 1.37f,
                    "graph_style_dot_radius" to 2.0f,
                    "graph_style_historical_dot_outline_enabled" to true,
                    "graph_style_historical_dot_outline_width" to 0.45000002f,
                    "graph_style_maximum_scale_enabled" to false,
                    "graph_style_scale_lane_opacity_percent" to 30,
                    "graph_style_time_axis_enabled" to true,
                    "target.high_mg_dl" to 160.0f,
                    "target.low_mg_dl" to 80.0f,
                    "watchface.clock_bold" to true,
                    "watchface.clock_color" to -1,
                    "watchface.clock_size_percent" to 150,
                    "watchface.scale_size_enabled" to true,
                    "watchface.scale_size_percent" to 70,
                    "watchface.status_bold" to false,
                    "watchface.status_color" to -1,
                    "watchface.status_size_percent" to 100,
                ),
            "g7_graph_colors" to
                mapOf(
                    "dark.border" to 0,
                    "dark.cgm_high" to -1,
                    "dark.cgm_in" to -1,
                    "dark.cgm_low" to -1,
                    "dark.cgm_very_high" to -1,
                    "dark.cgm_very_low" to -1,
                    "light.border" to 0,
                    "light.cgm_high" to -1,
                    "light.cgm_in" to -1,
                    "light.cgm_low" to -1,
                    "light.cgm_very_high" to -1,
                    "light.cgm_very_low" to -1,
                    "threshold_high" to 160.0f,
                    "threshold_low" to 80.0f,
                    "threshold_very_high" to 200.0f,
                    "threshold_very_low" to 50.0f,
                ),
            "g7_tile_glucose_appearance" to
                mapOf(
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 80,
                    "color.dark.glucose_high" to -12224,
                    "color.dark.glucose_low" to -41879,
                    "color.dark.glucose_very_high" to -12224,
                    "color.dark.glucose_very_low" to -41879,
                    "color.light.glucose_high" to -12224,
                    "color.light.glucose_low" to -41879,
                    "color.light.glucose_very_high" to -12224,
                    "color.light.glucose_very_low" to -41879,
                    "tile_glucose.scale" to 90,
                ),
        )

    // Latest explicit Galaxy appearance overrides, captured before this update.
    private val latestCaptured: Map<String, Map<String, Any>> =
        mapOf(
            "g7_appearance" to
                mapOf(
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 85,
                    "color.dark.graph_tile_border" to 6052956,
                    "color.dark.menu_background" to -16777216,
                    "color.dark.menu_border" to 4210752,
                    "glucose_scale_percent" to 115,
                    "graph.current_outline_width" to 2.0f,
                    "graph.historical_outline_width" to 1.9f,
                    "graph_historical_dot_outline_enabled" to false,
                    "in_app_graph.border" to false,
                    "tile_graph.color.dark.graph_tile_border" to 4210752,
                    "tile_graph.corner_radius" to 22.2f,
                    "tile_graph.current_outline" to true,
                    "tile_graph.dot_radius" to 2.4f,
                    "tile_graph.history_outline" to false,
                    "tile_graph.outline_width" to 0.35f,
                    "tile_graph.scale_lane_opacity" to 30,
                    "tile_graph.time_axis" to true,
                    "trend_scale_percent" to 70,
                ),
            "direct_to_watch" to
                mapOf(
                    "appearance.active_mode" to "dark",
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 100,
                    "graph.hours" to 2,
                    "graph_color_axis_label" to -6908266,
                    "graph_color_axis_tick" to -6908266,
                    "graph_color_background" to -14935012,
                    "graph_color_border" to -6908266,
                    "graph_color_cgm_high" to -1,
                    "graph_color_cgm_in" to -1,
                    "graph_color_cgm_low" to -1,
                    "graph_color_cgm_very_high" to -1,
                    "graph_color_cgm_very_low" to -1,
                    "graph_color_divider" to -6908266,
                    "graph_color_high_line" to -12224,
                    "graph_color_low_line" to -574139,
                    "graph_color_now_line" to -6908266,
                    "graph_color_outline" to -16777216,
                    "graph_color_prediction_cob" to -729600,
                    "graph_color_prediction_iob" to -11353601,
                    "graph_color_prediction_uam" to -20961,
                    "graph_color_prediction_zero_temp" to -13575202,
                    "graph_color_range_high" to -210629,
                    "graph_color_range_in" to 1717328988,
                    "graph_color_range_low" to -574139,
                    "graph_color_signal_loss" to 1191140457,
                    "graph_color_target_value" to -657931,
                    "graph_style_border_enabled" to false,
                    "graph_style_corner_radius" to 20.0f,
                    "graph_style_current_dot_outline_enabled" to true,
                    "graph_style_current_dot_outline_width" to 0.90000004f,
                    "graph_style_dot_outline_enabled" to true,
                    "graph_style_dot_outline_width" to 1.37f,
                    "graph_style_dot_radius" to 2.0f,
                    "graph_style_historical_dot_outline_enabled" to true,
                    "graph_style_historical_dot_outline_width" to 0.45000002f,
                    "graph_style_maximum_scale_enabled" to false,
                    "graph_style_scale_lane_opacity_percent" to 30,
                    "graph_style_time_axis_enabled" to true,
                    "target.high_mg_dl" to 160.0f,
                    "target.low_mg_dl" to 80.0f,
                    "watchface.clock_bold" to true,
                    "watchface.clock_color" to -1,
                    "watchface.clock_size_percent" to 150,
                    "watchface.scale_size_enabled" to true,
                    "watchface.scale_size_percent" to 70,
                    "watchface.status_bold" to false,
                    "watchface.status_color" to -1,
                    "watchface.status_size_percent" to 100,
                ),
            "g7_graph_colors" to
                mapOf(
                    "threshold_high" to 160.0f,
                    "threshold_low" to 80.0f,
                    "threshold_very_high" to 200.0f,
                    "threshold_very_low" to 50.0f,
                ),
            "g7_tile_glucose_appearance" to
                mapOf(
                    "appearance.trend.dark.alpha" to 1.0f,
                    "appearance.trend.dark.fill" to -1,
                    "appearance.trend.dark.outline.color" to -1375731712,
                    "appearance.trend.dark.outline.enabled" to false,
                    "appearance.trend.dark.outline.thicknessDp" to 0.65f,
                    "appearance.trend.dark.sizePercent" to 80,
                    "color.dark.glucose_very_high" to -12224,
                    "color.dark.glucose_very_low" to -41879,
                    "tile_glucose.scale" to 90,
                ),
        )
    private val references = baselineReferences.mapValues { (name, values) -> values + latestCaptured[name].orEmpty() }

    fun preferences(context: Context, name: String): SharedPreferences =
        ReferencePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), references[name].orEmpty())

    fun reset(context: Context) {
        references.keys.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
    }
}

/** Removing an override reveals the reference, including through per-setting reset actions. */
private class ReferencePreferences(
    private val delegate: SharedPreferences,
    private val defaults: Map<String, Any>,
) : SharedPreferences by delegate {
    override fun getAll(): MutableMap<String, *> = (defaults + delegate.all).toMutableMap()

    override fun contains(key: String?): Boolean = delegate.contains(key) || defaults.containsKey(key)

    override fun getInt(key: String?, defValue: Int): Int = if (delegate.contains(key)) delegate.getInt(key, defValue) else (defaults[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = if (delegate.contains(key)) delegate.getLong(key, defValue) else (defaults[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = if (delegate.contains(key)) delegate.getFloat(key, defValue) else (defaults[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = if (delegate.contains(key)) delegate.getBoolean(key, defValue) else (defaults[key] as? Boolean) ?: defValue

    override fun getString(key: String?, defValue: String?): String? = if (delegate.contains(key)) delegate.getString(key, defValue) else (defaults[key] as? String) ?: defValue
}
