package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.debug.playground.editor.PlaygroundStyleOverrides

enum class DataMode {
    DEMO,
    LIVE,
}

enum class PlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
}

data class PlaygroundUiState(
    val selectedEntry: PlaygroundEntry? = null,
    val selectedScenario: DemoScenario = DemoScenario.STABLE,
    val dataMode: DataMode = DataMode.DEMO,
    val styleOverrides: PlaygroundStyleOverrides = PlaygroundStyleOverrides(),
    val playbackState: PlaybackState = PlaybackState.STOPPED,
    val playbackSpeedMultiplier: Float = 1.0f,
    val currentReadingIndex: Int = 0,
    val liveModeAvailable: Boolean = true,
    val errorMessage: String? = null,
    val isEditorOpen: Boolean = false,
    val activeTab: PlaygroundCategory = PlaygroundCategory.HOME,
)
