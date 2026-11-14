package app.sugarwear.debug.playground

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sugarwear.debug.playground.data.DemoPlaygroundDataSource
import app.sugarwear.debug.playground.data.LivePlaygroundDataSource
import app.sugarwear.debug.playground.demo.DemoPlaybackController
import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.debug.playground.editor.PlaygroundStylePersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Main ViewModel managing state, scenario switching, visual overrides, and playback for Playground.
 * Pure debug tool; no production state mutation.
 */
class PlaygroundViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        PlaygroundUiState(
            selectedEntry = PlaygroundCatalog.entries.firstOrNull(),
            styleOverrides = PlaygroundStylePersistence.load(application),
        )
    )
    val uiState: StateFlow<PlaygroundUiState> = _uiState.asStateFlow()

    val demoPlaybackController = DemoPlaybackController(viewModelScope)
    val demoDataSource = DemoPlaygroundDataSource(viewModelScope, demoPlaybackController)
    val liveDataSource = LivePlaygroundDataSource(application, viewModelScope)

    init {
        demoDataSource.setScenario(DemoScenario.STABLE)
    }

    fun onAction(action: PlaygroundAction) {
        when (action) {
            is PlaygroundAction.SelectEntry -> {
                _uiState.update { it.copy(selectedEntry = action.entry, activeTab = action.entry.category) }
            }
            is PlaygroundAction.SelectCategory -> {
                val entry = PlaygroundNavigation.defaultEntryForCategory(action.category)
                _uiState.update { it.copy(activeTab = action.category, selectedEntry = entry) }
            }
            is PlaygroundAction.SelectScenario -> {
                _uiState.update { it.copy(selectedScenario = action.scenario) }
                demoDataSource.setScenario(action.scenario)
            }
            is PlaygroundAction.SetDataMode -> {
                if (action.mode == DataMode.LIVE) {
                    liveDataSource.refresh()
                }
                _uiState.update { it.copy(dataMode = action.mode) }
            }
            is PlaygroundAction.Play -> {
                demoPlaybackController.play()
                _uiState.update { it.copy(playbackState = PlaybackState.PLAYING) }
            }
            is PlaygroundAction.Pause -> {
                demoPlaybackController.pause()
                _uiState.update { it.copy(playbackState = PlaybackState.PAUSED) }
            }
            is PlaygroundAction.StepForward -> {
                demoPlaybackController.stepForward()
            }
            is PlaygroundAction.ResetPlayback -> {
                demoPlaybackController.reset()
                _uiState.update { it.copy(playbackState = PlaybackState.STOPPED) }
            }
            is PlaygroundAction.SetPlaybackSpeed -> {
                demoPlaybackController.setSpeed(action.multiplier)
                _uiState.update { it.copy(playbackSpeedMultiplier = action.multiplier) }
            }
            is PlaygroundAction.UpdateGraphStyle -> {
                _uiState.update { state ->
                    val newOverrides = state.styleOverrides.copy(graphStyle = action.style, isModified = true)
                    PlaygroundStylePersistence.save(getApplication(), newOverrides)
                    state.copy(styleOverrides = newOverrides)
                }
            }
            is PlaygroundAction.UpdateHomeStyle -> {
                _uiState.update { state ->
                    val newOverrides = state.styleOverrides.copy(homeStyle = action.style, isModified = true)
                    PlaygroundStylePersistence.save(getApplication(), newOverrides)
                    state.copy(styleOverrides = newOverrides)
                }
            }
            is PlaygroundAction.UpdateAlarmStyle -> {
                _uiState.update { state ->
                    val newOverrides = state.styleOverrides.copy(alarmStyle = action.style, isModified = true)
                    PlaygroundStylePersistence.save(getApplication(), newOverrides)
                    state.copy(styleOverrides = newOverrides)
                }
            }
            is PlaygroundAction.UpdateTileStyle -> {
                _uiState.update { state ->
                    val newOverrides = state.styleOverrides.copy(tileStyle = action.style, isModified = true)
                    PlaygroundStylePersistence.save(getApplication(), newOverrides)
                    state.copy(styleOverrides = newOverrides)
                }
            }
            is PlaygroundAction.ResetStyles -> {
                PlaygroundStylePersistence.clear(getApplication())
                _uiState.update { it.copy(styleOverrides = app.sugarwear.debug.playground.editor.PlaygroundStyleOverrides()) }
            }
            is PlaygroundAction.ToggleEditor -> {
                _uiState.update { it.copy(isEditorOpen = action.open) }
            }
        }
    }
}
