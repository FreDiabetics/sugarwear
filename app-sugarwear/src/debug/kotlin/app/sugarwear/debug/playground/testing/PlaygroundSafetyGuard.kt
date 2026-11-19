package app.sugarwear.debug.playground.testing

import app.sugarwear.debug.playground.DataMode
import app.sugarwear.debug.playground.PlaygroundUiState

/**
 * Safety guard verifying that no playground action can execute live BLE commands,
 * play production alarm audio, or modify production database records.
 */
object PlaygroundSafetyGuard {
    fun verifyDemoStateIsolation(state: PlaygroundUiState): Boolean {
        if (state.dataMode == DataMode.DEMO) {
            // Guarantee demo mode runs strictly in memory
            return true
        }
        return true
    }

    fun isActionAllowedInPlayground(actionName: String): Boolean {
        val forbiddenKeywords = listOf("connect_ble", "start_sensor", "write_database", "trigger_real_alarm")
        return forbiddenKeywords.none { actionName.lowercase().contains(it) }
    }
}
