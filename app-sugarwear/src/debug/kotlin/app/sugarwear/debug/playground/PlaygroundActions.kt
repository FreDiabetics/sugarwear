package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.ui.visual.AlarmVisualStyle
import app.sugarwear.ui.visual.GraphVisualStyle
import app.sugarwear.ui.visual.HomeVisualStyle
import app.sugarwear.ui.visual.TileVisualStyle

/**
 * Sealed interface representing user actions in the SugarWear UI Playground.
 */
sealed interface PlaygroundAction {
    data class SelectEntry(val entry: PlaygroundEntry) : PlaygroundAction
    data class SelectScenario(val scenario: DemoScenario) : PlaygroundAction
    data class SetDataMode(val mode: DataMode) : PlaygroundAction
    data class SelectCategory(val category: PlaygroundCategory) : PlaygroundAction
    object Play : PlaygroundAction
    object Pause : PlaygroundAction
    object StepForward : PlaygroundAction
    object ResetPlayback : PlaygroundAction
    data class SetPlaybackSpeed(val multiplier: Float) : PlaygroundAction
    data class UpdateGraphStyle(val style: GraphVisualStyle) : PlaygroundAction
    data class UpdateHomeStyle(val style: HomeVisualStyle) : PlaygroundAction
    data class UpdateAlarmStyle(val style: AlarmVisualStyle) : PlaygroundAction
    data class UpdateTileStyle(val style: TileVisualStyle) : PlaygroundAction
    object ResetStyles : PlaygroundAction
    data class ToggleEditor(val open: Boolean) : PlaygroundAction
}
