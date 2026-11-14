package app.sugarwear.debug.playground.editor

import app.sugarwear.ui.visual.VisualStyleValidation

/**
 * Validates debug style overrides using VisualStyleValidation logic.
 */
object PlaygroundStyleValidator {
    fun validate(overrides: PlaygroundStyleOverrides): PlaygroundStyleOverrides {
        val validGraph = VisualStyleValidation.validateGraphStyle(overrides.graphStyle)
        val validHome = VisualStyleValidation.validateHomeStyle(overrides.homeStyle)
        val validAlarm = VisualStyleValidation.validateAlarmStyle(overrides.alarmStyle)

        return overrides.copy(
            graphStyle = validGraph,
            homeStyle = validHome,
            alarmStyle = validAlarm,
        )
    }
}
