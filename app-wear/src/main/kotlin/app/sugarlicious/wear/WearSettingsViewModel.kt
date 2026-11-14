package app.sugarlicious.wear

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.complications.ComplicationUpdatePlanner
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.protocol.WatchUiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class WearSettingsSnapshot(
    val mode: AppearanceMode = AppearanceMode.DARK,
    val preferences: WearDisplayPreferences = WearDisplayPreferences(),
    val tileColors: Map<WearTileKind, WatchUiColors> = WearTileKind.entries.associateWith { WatchUiColors() },
    val tileContent: Map<WearTileKind, WearTileContent> = WearTileKind.entries.associateWith { WearTileContent.GLUCOSE },
    val therapy: TherapyProgressAppearance = TherapyProgressAppearance(),
)

internal data class WearSettingsUiState(
    val snapshot: WearSettingsSnapshot? = null,
    val category: String? = null,
    val categoryListScrollY: Int = 0,
    val scrollY: Int = 0,
    val colorEditorOpen: Boolean = false,
    val renderRevision: Long = 0,
    val storageError: Boolean = false,
)

internal sealed interface WearSettingsWrite {
    data class Display(
        val value: WearDisplayPreferences,
    ) : WearSettingsWrite

    data class TileColors(
        val kind: WearTileKind,
        val colors: WatchUiColors,
    ) : WearSettingsWrite

    data class TileContent(
        val kind: WearTileKind,
        val content: WearTileContent,
    ) : WearSettingsWrite

    data class Therapy(
        val value: TherapyProgressAppearance,
    ) : WearSettingsWrite

    data object ResetTrend : WearSettingsWrite

    data object ActivateAppearance : WearSettingsWrite
}

internal interface WearSettingsRepository {
    suspend fun load(mode: AppearanceMode? = null): WearSettingsSnapshot

    suspend fun execute(mode: AppearanceMode, action: WearSettingsWrite)
}

/** Uses the established stores and their migrations; no parallel preference source. */
internal class AndroidWearSettingsRepository(
    context: Context,
) : WearSettingsRepository {
    private val context = context.applicationContext

    override suspend fun load(mode: AppearanceMode?): WearSettingsSnapshot =
        withContext(Dispatchers.IO) {
            val selected = mode ?: WearDisplayPreferences.activeAppearanceMode(context)
            WearSettingsSnapshot(
                mode = selected,
                preferences = WearDisplayPreferences.read(context, selected),
                tileColors = WearTileKind.entries.associateWith { WearTileAppearanceStore.read(context, it, selected) },
                tileContent = WearTileKind.entries.associateWith { WearTileContentStore.read(context, it) },
                therapy = TherapyTileGeometryStore.read(context),
            )
        }

    override suspend fun execute(mode: AppearanceMode, action: WearSettingsWrite) =
        withContext(Dispatchers.IO) {
            when (action) {
                is WearSettingsWrite.Display -> {
                    val trendChanged = WearDisplayPreferences.read(context, mode).trendScalePercent != action.value.trendScalePercent
                    WearDisplayPreferences.saveLocal(context, mode, action.value)
                    requestSugarliciousTileUpdates(context)
                    if (trendChanged) {
                        ComplicationUpdatePlanner.allManagedProviders.forEach { provider ->
                            app.sugarlicious.complications.ActiveProviderUpdates
                                .request(context, provider)
                        }
                    }
                }
                is WearSettingsWrite.TileColors -> {
                    WearTileAppearanceStore.write(context, action.kind, mode, action.colors)
                    requestSugarliciousTileUpdates(context)
                }
                is WearSettingsWrite.TileContent -> {
                    WearTileContentStore.write(context, action.kind, action.content)
                    requestSugarliciousTileUpdates(context)
                }
                is WearSettingsWrite.Therapy -> {
                    TherapyTileGeometryStore.write(context, action.value.validated())
                    requestSugarliciousTileUpdates(context, setOf(TherapyTileService::class.java))
                }
                WearSettingsWrite.ResetTrend -> WearDisplayPreferences.resetTrendArrowStyle(context, mode)
                WearSettingsWrite.ActivateAppearance -> WearDisplayPreferences.setActiveAppearanceMode(context, mode)
            }
        }
}

/** Controller state survives Activity recreation; commands are ordered, including palette changes. */
@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = WearSettingsViewModel.Factory::class)
internal class WearSettingsViewModel(
    private val repository: WearSettingsRepository,
    scope: CoroutineScope? = null,
    initialCategory: String? = null,
    initialListScrollY: Int = 0,
) : ViewModel() {
    @dagger.assisted.AssistedInject
    constructor(
        repository: WearSettingsRepository,
        @dagger.assisted.Assisted initialCategory: String?,
        @dagger.assisted.Assisted initialListScrollY: Int,
    ) : this(repository, scope = null, initialCategory = initialCategory, initialListScrollY = initialListScrollY)

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(initialCategory: String?, initialListScrollY: Int): WearSettingsViewModel
    }

    private val mutableState = MutableStateFlow(WearSettingsUiState(category = initialCategory, categoryListScrollY = initialListScrollY))
    val uiState = mutableState.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var editRevision = 0L
    private var failedCommand: (suspend () -> Unit)? = null

    init {
        commands.trySend { reload(null) }
        (scope ?: viewModelScope).launch {
            for (command in commands) {
                try {
                    command()
                } catch (error: IOException) {
                    java.util.logging.Logger
                        .getLogger("WearSettings")
                        .warning("Settings storage unavailable: ${error.javaClass.simpleName}")
                    failedCommand = command
                    mutableState.value = uiState.value.copy(storageError = true, renderRevision = uiState.value.renderRevision + 1)
                }
            }
        }
    }

    fun openCategory(category: String, listScrollY: Int) {
        mutableState.value = uiState.value.copy(category = category, categoryListScrollY = listScrollY, scrollY = 0, renderRevision = uiState.value.renderRevision + 1)
    }

    fun navigateBack(): Boolean {
        if (uiState.value.category == null) return false
        mutableState.value = uiState.value.copy(category = null, scrollY = uiState.value.categoryListScrollY, renderRevision = uiState.value.renderRevision + 1)
        return true
    }

    fun rememberScroll(scrollY: Int) {
        mutableState.value = uiState.value.copy(scrollY = scrollY)
    }

    fun setColorEditorOpen(open: Boolean) {
        mutableState.value = uiState.value.copy(colorEditorOpen = open)
        if (!open) requestRender()
    }

    fun requestRender() {
        mutableState.value = uiState.value.copy(renderRevision = uiState.value.renderRevision + 1)
    }

    fun retry() {
        val failed = failedCommand
        failedCommand = null
        commands.trySend {
            if (failed != null) failed() else reload(uiState.value.snapshot?.mode)
            mutableState.value = uiState.value.copy(storageError = false)
            requestRender()
        }
    }

    fun selectAppearance(mode: AppearanceMode) {
        commands.trySend {
            repository.execute(mode, WearSettingsWrite.ActivateAppearance)
            reload(mode)
        }
    }

    fun save(value: WearDisplayPreferences, rebuild: Boolean = true) {
        val snapshot = uiState.value.snapshot ?: return
        write(WearSettingsWrite.Display(value), snapshot.copy(preferences = value), rebuild)
    }

    fun updateTileColors(kind: WearTileKind, transform: (WatchUiColors) -> WatchUiColors) {
        val snapshot = uiState.value.snapshot ?: return
        val colors = transform(snapshot.tileColors.getValue(kind))
        write(WearSettingsWrite.TileColors(kind, colors), snapshot.copy(tileColors = snapshot.tileColors + (kind to colors)))
    }

    fun setTileContent(kind: WearTileKind, content: WearTileContent) {
        val snapshot = uiState.value.snapshot ?: return
        write(WearSettingsWrite.TileContent(kind, content), snapshot.copy(tileContent = snapshot.tileContent + (kind to content)))
    }

    fun saveTherapy(value: TherapyProgressAppearance) {
        val snapshot = uiState.value.snapshot ?: return
        write(WearSettingsWrite.Therapy(value), snapshot.copy(therapy = value.validated()))
    }

    fun resetTrend() {
        val mode = uiState.value.snapshot?.mode ?: return
        commands.trySend {
            repository.execute(mode, WearSettingsWrite.ResetTrend)
            reload(mode)
        }
    }

    private fun write(action: WearSettingsWrite, optimistic: WearSettingsSnapshot, rebuild: Boolean = true) {
        val revision = ++editRevision
        mutableState.value = uiState.value.copy(snapshot = optimistic, storageError = false)
        commands.trySend {
            repository.execute(optimistic.mode, action)
            val stored = repository.load(optimistic.mode)
            // A completed earlier write must never roll back an edit already queued by a control.
            if (revision == editRevision && uiState.value.snapshot?.mode == optimistic.mode) {
                mutableState.value = uiState.value.copy(snapshot = stored)
            }
            if (rebuild && !uiState.value.colorEditorOpen) requestRender()
        }
    }

    override fun onCleared() {
        commands.close()
    }

    private suspend fun reload(mode: AppearanceMode?) {
        val loaded = repository.load(mode)
        mutableState.value = uiState.value.copy(snapshot = loaded, storageError = false, renderRevision = uiState.value.renderRevision + 1)
    }
}
