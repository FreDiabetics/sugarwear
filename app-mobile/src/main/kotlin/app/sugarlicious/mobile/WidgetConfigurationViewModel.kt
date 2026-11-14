package app.sugarlicious.mobile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TrendArrowStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal data class WidgetConfigurationSnapshot(
    val initial: WidgetInstanceConfiguration,
    val value: WidgetInstanceConfiguration = initial,
    val selectedMode: AppearanceMode,
    val palettes: Map<AppearanceMode, WidgetPalette> = emptyMap(),
    val trends: Map<AppearanceMode, TrendArrowStyle> = emptyMap(),
    val dashboard: DashboardUiPreferences = DashboardUiPreferences(),
    val deviceVendorCompact: Boolean = false,
)

internal enum class WidgetConfigurationError { LOAD_FAILED, SAVE_FAILED }

internal data class WidgetConfigurationUiState(
    val snapshot: WidgetConfigurationSnapshot? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: WidgetConfigurationError? = null,
    val editBackground: Boolean = false,
    val editOutline: Boolean = false,
    val editSplitDivider: Boolean = false,
    val editTrendOutline: Boolean = false,
    val editRole: WidgetColorRole? = null,
)

internal interface WidgetConfigurationRepository {
    suspend fun load(): WidgetConfigurationSnapshot

    suspend fun save(initial: WidgetInstanceConfiguration, value: WidgetInstanceConfiguration)
}

internal class AndroidWidgetConfigurationRepository(
    context: Context,
    private val widgetId: Int,
    private val kind: ConfigurableWidgetKind,
    private val useDefaults: Boolean,
) : WidgetConfigurationRepository {
    private val app = context.applicationContext

    override suspend fun load(): WidgetConfigurationSnapshot =
        withContext(Dispatchers.IO) {
            val initial =
                if (!useDefaults && WidgetInstanceConfigurationStore.hasStoredConfiguration(app, widgetId)) {
                    WidgetInstanceConfigurationStore.readIsolated(app, widgetId, kind)
                } else {
                    WidgetSystemDefaults.forNewInstance(app, kind)
                }
            val preferences = app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            WidgetConfigurationSnapshot(
                initial = initial,
                selectedMode = resolveSettingsAppearance(initial.themeMode, deviceUsesDarkMode(app)),
                palettes = AppearanceMode.entries.associateWith { WidgetColorStore.load(app, it) },
                trends = AppearanceMode.entries.associateWith { MobileTrendArrowAppearance.load(preferences, it) },
                dashboard = DashboardUiPreferences.read(preferences),
                deviceVendorCompact = WidgetLayoutSizeStore.isDeviceVendorCompact(app),
            )
        }

    override suspend fun save(initial: WidgetInstanceConfiguration, value: WidgetInstanceConfiguration) =
        withContext(Dispatchers.IO) {
            WidgetRenderTransactions.run(widgetId) {
                val views = withContext(Dispatchers.Default) { SugarliciousWidgets.prepareInstance(app, widgetId, kind, value) }
                // This synchronous commit/publish/rollback block retains the original atomic contract.
                commitWidgetSettings(
                    commit = { WidgetInstanceConfigurationStore.save(app, widgetId, value) },
                    publish = { publishWidgetSurface(app, widgetId, views) },
                    rollback = { WidgetInstanceConfigurationStore.save(app, widgetId, initial) },
                )
            }
        }
}

/** The existing Compose form edits retained draft state and delegates one atomic publication. */
@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = WidgetConfigurationViewModel.Factory::class)
internal class WidgetConfigurationViewModel(
    private val repository: WidgetConfigurationRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    @dagger.assisted.AssistedInject
    constructor(
        repositories: WidgetRepositoryFactory,
        @dagger.assisted.Assisted widgetId: Int,
        @dagger.assisted.Assisted kind: ConfigurableWidgetKind,
        @dagger.assisted.Assisted useDefaults: Boolean,
    ) : this(repositories.create(widgetId, kind, useDefaults))

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(widgetId: Int, kind: ConfigurableWidgetKind, useDefaults: Boolean): WidgetConfigurationViewModel
    }

    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(WidgetConfigurationUiState())
    val state = mutableState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        workScope.launch {
            try {
                val loaded = repository.load()
                mutableState.update { it.copy(snapshot = loaded, error = null) }
            } catch (error: Exception) {
                reportExpected(error)
                mutableState.update { it.copy(error = WidgetConfigurationError.LOAD_FAILED) }
            }
        }
    }

    fun edit(transform: (WidgetInstanceConfiguration) -> WidgetInstanceConfiguration) {
        mutableState.update { current -> current.copy(snapshot = current.snapshot?.let { it.copy(value = transform(it.value)) }) }
    }

    fun save() {
        if (state.value.saving || state.value.saved) return
        val snapshot = state.value.snapshot ?: return
        mutableState.update { it.copy(saving = true, error = null) }
        workScope.launch {
            try {
                repository.save(snapshot.initial, resolvedWidgetConfigurationOnExit(snapshot.initial, snapshot.value, save = true))
                mutableState.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) {
                reportExpected(error)
                mutableState.update { it.copy(saving = false, error = WidgetConfigurationError.SAVE_FAILED) }
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    val configuration = field({ checkNotNull(it.snapshot).value }) { current, value -> current.copy(snapshot = current.snapshot?.copy(value = value)) }
    val mode = field({ checkNotNull(it.snapshot).selectedMode }) { current, value -> current.copy(snapshot = current.snapshot?.copy(selectedMode = value)) }
    val backgroundEditor = field(WidgetConfigurationUiState::editBackground) { current, value -> current.copy(editBackground = value) }
    val outlineEditor = field(WidgetConfigurationUiState::editOutline) { current, value -> current.copy(editOutline = value) }
    val dividerEditor = field(WidgetConfigurationUiState::editSplitDivider) { current, value -> current.copy(editSplitDivider = value) }
    val trendEditor = field(WidgetConfigurationUiState::editTrendOutline) { current, value -> current.copy(editTrendOutline = value) }
    val colorRole = field(WidgetConfigurationUiState::editRole) { current, value -> current.copy(editRole = value) }

    private fun <T> field(read: (WidgetConfigurationUiState) -> T, write: (WidgetConfigurationUiState, T) -> WidgetConfigurationUiState) =
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = read(state.value)

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                mutableState.update { write(it, value) }
            }
        }

    private fun reportExpected(error: Exception) {
        if (error is CancellationException) throw error
        if (error !is IOException && error !is SecurityException && error !is android.database.SQLException && error !is IllegalStateException) throw error
        java.util.logging.Logger
            .getLogger("WidgetConfiguration")
            .warning("Widget settings failed: ${error.javaClass.simpleName}")
    }
}
