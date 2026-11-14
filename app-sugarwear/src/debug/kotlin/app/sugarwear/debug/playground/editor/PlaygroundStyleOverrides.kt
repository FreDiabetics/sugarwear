package app.sugarwear.debug.playground.editor

import app.sugarwear.ui.visual.AlarmVisualStyle
import app.sugarwear.ui.visual.GraphVisualStyle
import app.sugarwear.ui.visual.HomeVisualStyle
import app.sugarwear.ui.visual.TileVisualStyle
import app.sugarwear.ui.visual.VisualStyleDefaults

/**
 * Encapsulates all active visual style overrides within the debug playground.
 */
data class PlaygroundStyleOverrides(
    val graphStyle: GraphVisualStyle = VisualStyleDefaults.graph(),
    val homeStyle: HomeVisualStyle = VisualStyleDefaults.home(),
    val alarmStyle: AlarmVisualStyle = VisualStyleDefaults.alarm(),
    val tileStyle: TileVisualStyle = VisualStyleDefaults.tile(),
    val isModified: Boolean = false,
)
