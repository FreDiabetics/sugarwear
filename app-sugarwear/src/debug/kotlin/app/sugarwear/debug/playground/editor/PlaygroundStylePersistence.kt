package app.sugarwear.debug.playground.editor

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

/**
 * Persists debug playground visual style overrides in SharedPreferences.
 * Strictly isolated to debug preferences; never alters production settings.
 */
object PlaygroundStylePersistence {
    private const val PREFS_NAME = "sugarwear_debug_playground_styles"
    private const val KEY_GRAPH_LINE_WIDTH = "graph_line_width"
    private const val KEY_GRAPH_POINT_RADIUS = "graph_point_radius"
    private const val KEY_HOME_GLUCOSE_SCALE = "home_glucose_scale"
    private const val KEY_HOME_CARD_RADIUS = "home_card_radius"

    fun save(context: Context, overrides: PlaygroundStyleOverrides) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_GRAPH_LINE_WIDTH, overrides.graphStyle.lineWidthDp.value)
            putFloat(KEY_GRAPH_POINT_RADIUS, overrides.graphStyle.pointRadiusDp.value)
            putInt(KEY_HOME_GLUCOSE_SCALE, overrides.homeStyle.glucoseScalePercent)
            putFloat(KEY_HOME_CARD_RADIUS, overrides.homeStyle.cardCornerRadiusDp.value)
            apply()
        }
    }

    fun load(context: Context): PlaygroundStyleOverrides {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_GRAPH_LINE_WIDTH)) {
            return PlaygroundStyleOverrides()
        }

        val baseGraph =
            app.sugarwear.ui.visual.VisualStyleDefaults
                .graph()
        val baseHome =
            app.sugarwear.ui.visual.VisualStyleDefaults
                .home()

        val loadedGraph =
            baseGraph.copy(
                lineWidthDp = prefs.getFloat(KEY_GRAPH_LINE_WIDTH, baseGraph.lineWidthDp.value).dp,
                pointRadiusDp = prefs.getFloat(KEY_GRAPH_POINT_RADIUS, baseGraph.pointRadiusDp.value).dp,
            )

        val loadedHome =
            baseHome.copy(
                glucoseScalePercent = prefs.getInt(KEY_HOME_GLUCOSE_SCALE, baseHome.glucoseScalePercent),
                cardCornerRadiusDp = prefs.getFloat(KEY_HOME_CARD_RADIUS, baseHome.cardCornerRadiusDp.value).dp,
            )

        return PlaygroundStyleOverrides(
            graphStyle = loadedGraph,
            homeStyle = loadedHome,
            isModified = true,
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
