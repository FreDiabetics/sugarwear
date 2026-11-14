package app.sugarlicious.mobile

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.TrendArrowStyle
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal enum class AppearanceFloat(
    val default: Float,
) {
    CGM_RADIUS(2.4f),
    HISTORICAL_WIDTH(.95f),
    CURRENT_WIDTH(.95f),
    PREDICTION_RADIUS(MobileGraphSystemDefaults.PREDICTION_DOT_RADIUS_DP),
    PREDICTION_WIDTH(MobileGraphSystemDefaults.PREDICTION_OUTLINE_WIDTH_DP),
    GLUCOSE_SIZE(GlucoseTrendSizing.DEFAULT_SCALE_PERCENT.toFloat()),
    TREND_SIZE(DashboardSystemDefaults.TREND_SCALE_PERCENT.toFloat()),
    NOTIFICATION_SCALE(30f),
}

internal enum class AppearanceSettingsError { STORAGE, WATCH_SYNC }

internal enum class AppearanceColorTarget { MOBILE, WIDGET, NOTIFICATION }

internal enum class AppearanceReset { COLOR, ALL_COLORS, COPY_MOBILE, NOTIFICATION_GRAPH }

internal val notificationAppearanceRoles =
    listOf(
        SugarliciousColorRole.CGM_DOT_LOW,
        SugarliciousColorRole.CGM_DOT_IN_RANGE,
        SugarliciousColorRole.CGM_DOT_HIGH,
        SugarliciousColorRole.GRAPH_CURRENT_OUTLINE,
        SugarliciousColorRole.GRAPH_BACKGROUND,
        SugarliciousColorRole.GRAPH_DIVIDER,
        SugarliciousColorRole.RANGE_LOW,
        SugarliciousColorRole.RANGE_IN_RANGE,
        SugarliciousColorRole.RANGE_HIGH,
        SugarliciousColorRole.TARGET_VALUE,
        SugarliciousColorRole.GRAPH_SIGNAL_LOSS,
    )

internal data class AppearanceSettingsSnapshot(
    val mode: AppearanceMode = AppearanceMode.DARK,
    val palette: SugarliciousPalette = SugarliciousPalette.defaults(),
    val widgetPalette: WidgetPalette = WidgetPalette(emptyMap()),
    val notificationColors: Map<SugarliciousColorRole, Int> = emptyMap(),
    val floats: Map<AppearanceFloat, Float> = AppearanceFloat.entries.associateWith { it.default },
    val historicalOutlineEnabled: Boolean = true,
    val trend: TrendArrowStyle = TrendArrowStyle.defaults(AppearanceMode.DARK, -1),
    val deviceVendorCompact: Boolean = false,
    val notificationDots: Map<NotificationGraphProfile, NotificationGraphDotStyle> = emptyMap(),
    val notificationLayouts: Map<NotificationGraphProfile, NotificationLayoutSettings> = emptyMap(),
)

internal data class AppearanceSettingsUiState(
    val snapshot: AppearanceSettingsSnapshot? = null,
    val editingRole: SugarliciousColorRole? = null,
    val editingWidgetRole: WidgetColorRole? = null,
    val editingTrendColor: String? = null,
    val error: AppearanceSettingsError? = null,
    val watchSyncStatus: String? = null,
    val syncingWatch: Boolean = false,
)

internal sealed interface AppearanceWrite {
    data class FloatValue(
        val field: AppearanceFloat,
        val value: Float,
    ) : AppearanceWrite

    data class HistoricalOutline(
        val enabled: Boolean,
    ) : AppearanceWrite

    data class Trend(
        val value: TrendArrowStyle,
    ) : AppearanceWrite

    data class Color(
        val target: AppearanceColorTarget,
        val role: SugarliciousColorRole?,
        val widgetRole: WidgetColorRole?,
        val argb: Int,
    ) : AppearanceWrite

    data class Reset(
        val target: AppearanceColorTarget,
        val operation: AppearanceReset,
        val role: SugarliciousColorRole? = null,
        val widgetRole: WidgetColorRole? = null,
    ) : AppearanceWrite

    data class Compact(
        val enabled: Boolean,
    ) : AppearanceWrite

    data class Dots(
        val profile: NotificationGraphProfile,
        val value: NotificationGraphDotStyle,
    ) : AppearanceWrite

    data class Layout(
        val profile: NotificationGraphProfile,
        val value: NotificationLayoutSettings,
    ) : AppearanceWrite
}

internal interface AppearanceSettingsRepository {
    suspend fun load(mode: AppearanceMode? = null): AppearanceSettingsSnapshot

    suspend fun execute(mode: AppearanceMode, action: AppearanceWrite)

    suspend fun sendWatchColors()
}

internal interface AppearanceSettingsEffects {
    suspend fun refreshWidgets()

    suspend fun refreshNotifications()

    suspend fun applyPalette(palette: SugarliciousPalette)

    suspend fun publishWatchColors()
}

private class AndroidAppearanceSettingsEffects(
    private val context: Context,
) : AppearanceSettingsEffects {
    override suspend fun refreshWidgets() {
        SugarliciousWidgets.update(context)
    }

    override suspend fun refreshNotifications() {
        PersistentBridgeService.refresh(context)
    }

    override suspend fun applyPalette(palette: SugarliciousPalette) {
        withContext(Dispatchers.Main) { SugarliciousColors.apply(palette) }
    }

    override suspend fun publishWatchColors() {
        app.sugarlicious.mobile.publishWatchColors(context)
    }
}

internal class AndroidAppearanceSettingsRepository(
    context: Context,
    private val effects: AppearanceSettingsEffects = AndroidAppearanceSettingsEffects(context.applicationContext),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val target: AppearanceColorTarget = AppearanceColorTarget.MOBILE,
) : AppearanceSettingsRepository {
    private val context = context.applicationContext

    private fun preferences() = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    override suspend fun load(mode: AppearanceMode?): AppearanceSettingsSnapshot =
        withContext(ioDispatcher) {
            val p = preferences()
            val selected = mode ?: SugarliciousColorStore.activeMode(p)
            val palette = SugarliciousColorStore.load(p, selected)
            AppearanceSettingsSnapshot(
                mode = selected,
                palette = palette,
                widgetPalette = if (target == AppearanceColorTarget.WIDGET) WidgetColorStore.load(context, selected) else WidgetPalette(emptyMap()),
                notificationColors =
                    notificationAppearanceRoles.associateWith { role ->
                        val key = notificationColorKey(selected, role)
                        if (p.contains(key)) p.getInt(key, palette.argb(role)) else NotificationGraphColorDefaults.argb(palette, role)
                    },
                floats =
                    mapOf(
                        AppearanceFloat.CGM_RADIUS to p.getFloat(graphAppearanceKey(selected, "dotRadiusDp"), p.getFloat("cgm.dotRadiusDp", 2.4f)).coerceIn(1.5f, 6f),
                        AppearanceFloat.HISTORICAL_WIDTH to p.getFloat(graphAppearanceKey(selected, "historicalDotOutlineWidthDp"), p.getFloat("cgm.dotOutlineWidthDp", .95f)).coerceIn(.25f, 3f),
                        AppearanceFloat.CURRENT_WIDTH to p.getFloat(graphAppearanceKey(selected, "currentDotOutlineWidthDp"), p.getFloat("cgm.dotOutlineWidthDp", .95f)).coerceIn(.25f, 3f),
                        AppearanceFloat.PREDICTION_RADIUS to readMobilePredictionDotRadius(p, selected),
                        AppearanceFloat.PREDICTION_WIDTH to readMobilePredictionDotOutlineWidth(p, selected),
                        AppearanceFloat.GLUCOSE_SIZE to p.getInt(DashboardUiPreferences.MOBILE_GLUCOSE_SCALE_KEY, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT).coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT).toFloat(),
                        AppearanceFloat.TREND_SIZE to p.getInt(DashboardUiPreferences.MOBILE_TREND_SCALE_KEY, DashboardSystemDefaults.TREND_SCALE_PERCENT).coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT).toFloat(),
                        AppearanceFloat.NOTIFICATION_SCALE to p.getInt("notification.graph.scale_lane_opacity_percent", 30).toFloat(),
                    ),
                historicalOutlineEnabled = p.getBoolean(graphAppearanceKey(selected, "historicalDotOutlineEnabled"), p.getBoolean("cgm.dotOutlineEnabled", true)),
                trend = MobileTrendArrowAppearance.load(p, selected),
                deviceVendorCompact = target == AppearanceColorTarget.WIDGET && WidgetLayoutSizeStore.isDeviceVendorCompact(context),
                notificationDots = if (target == AppearanceColorTarget.NOTIFICATION) NotificationGraphProfile.entries.associateWith { NotificationGraphDotStyleStore.read(p, selected, it) } else emptyMap(),
                notificationLayouts = if (target == AppearanceColorTarget.NOTIFICATION) NotificationGraphProfile.entries.associateWith { NotificationLayoutSettingsStore.read(p, it) } else emptyMap(),
            )
        }

    override suspend fun execute(mode: AppearanceMode, action: AppearanceWrite) =
        withContext(ioDispatcher) {
            val p = preferences()
            when (action) {
                is AppearanceWrite.FloatValue -> {
                    p.edit {
                        when (action.field) {
                            AppearanceFloat.GLUCOSE_SIZE -> putInt(DashboardUiPreferences.MOBILE_GLUCOSE_SCALE_KEY, action.value.toInt())
                            AppearanceFloat.TREND_SIZE -> putInt(DashboardUiPreferences.MOBILE_TREND_SCALE_KEY, action.value.toInt())
                            AppearanceFloat.NOTIFICATION_SCALE -> putInt("notification.graph.scale_lane_opacity_percent", action.value.toInt())
                            else ->
                                putFloat(
                                    graphAppearanceKey(
                                        mode,
                                        when (action.field) {
                                            AppearanceFloat.CGM_RADIUS -> "dotRadiusDp"
                                            AppearanceFloat.HISTORICAL_WIDTH -> "historicalDotOutlineWidthDp"
                                            AppearanceFloat.CURRENT_WIDTH -> "currentDotOutlineWidthDp"
                                            AppearanceFloat.PREDICTION_RADIUS -> "prediction.dotRadiusDp"
                                            AppearanceFloat.PREDICTION_WIDTH -> "prediction.dotOutlineWidthDp"
                                        },
                                    ),
                                    action.value,
                                )
                        }
                    }
                    if (action.field == AppearanceFloat.NOTIFICATION_SCALE) effects.refreshNotifications()
                }
                is AppearanceWrite.HistoricalOutline -> p.edit { putBoolean(graphAppearanceKey(mode, "historicalDotOutlineEnabled"), action.enabled) }
                is AppearanceWrite.Trend -> MobileTrendArrowAppearance.save(p, mode, action.value)
                is AppearanceWrite.Color ->
                    when (action.target) {
                        AppearanceColorTarget.MOBILE -> SugarliciousColorStore.save(p, mode, requireNotNull(action.role), action.argb)
                        AppearanceColorTarget.WIDGET -> WidgetColorStore.save(context, mode, requireNotNull(action.widgetRole), action.argb)
                        AppearanceColorTarget.NOTIFICATION -> p.edit { putInt(notificationColorKey(mode, requireNotNull(action.role)), action.argb) }
                    }
                is AppearanceWrite.Reset ->
                    when (action.target) {
                        AppearanceColorTarget.MOBILE -> if (action.operation == AppearanceReset.COLOR) SugarliciousColorStore.reset(p, mode, requireNotNull(action.role)) else SugarliciousColorStore.resetAll(p)
                        AppearanceColorTarget.WIDGET ->
                            when (action.operation) {
                                AppearanceReset.COLOR -> WidgetColorStore.reset(context, mode, requireNotNull(action.widgetRole))
                                AppearanceReset.COPY_MOBILE -> WidgetColorStore.copyFromMobileGraph(context, mode)
                                else -> WidgetColorStore.resetAll(context)
                            }
                        AppearanceColorTarget.NOTIFICATION -> {
                            if (action.operation == AppearanceReset.NOTIFICATION_GRAPH) NotificationGraphDotStyleStore.resetProfiles(p)
                            val roles = if (action.operation == AppearanceReset.COLOR) listOf(requireNotNull(action.role)) else notificationAppearanceRoles
                            val palette = SugarliciousColorStore.load(p, mode)
                            p.edit {
                                roles.forEach { role ->
                                    if (action.operation == AppearanceReset.COPY_MOBILE) {
                                        putInt(notificationColorKey(mode, role), palette.argb(role))
                                    } else {
                                        remove(notificationColorKey(mode, role))
                                        remove("notification.color.override." + role.preferenceKey)
                                    }
                                }
                            }
                        }
                    }
                is AppearanceWrite.Compact -> WidgetLayoutSizeStore.setDeviceVendorCompact(context, action.enabled)
                is AppearanceWrite.Dots -> NotificationGraphDotStyleStore.save(p, mode, action.profile, action.value)
                is AppearanceWrite.Layout -> {
                    NotificationLayoutSettingsStore.save(p, action.profile, action.value)
                    effects.refreshNotifications()
                }
            }
            if (action is AppearanceWrite.Compact || (action is AppearanceWrite.Color && action.target == AppearanceColorTarget.WIDGET) || (action is AppearanceWrite.Reset && action.target == AppearanceColorTarget.WIDGET)) {
                effects.refreshWidgets()
            }
            if ((action is AppearanceWrite.Color && action.target == AppearanceColorTarget.MOBILE) || (action is AppearanceWrite.Reset && action.target == AppearanceColorTarget.MOBILE)) {
                if (mode == SugarliciousColorStore.activeMode(p)) {
                    val palette = SugarliciousColorStore.load(p, mode)
                    effects.applyPalette(palette)
                }
            }
        }

    override suspend fun sendWatchColors() {
        withContext(ioDispatcher) { effects.publishWatchColors() }
    }

    private fun notificationColorKey(mode: AppearanceMode, role: SugarliciousColorRole) = "notification.color.${mode.storageKey}." + role.preferenceKey
}

/** All gesture drafts are immediate; only finish actions cross the storage boundary. */
@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = AppearanceSettingsViewModel.Factory::class)
internal class AppearanceSettingsViewModel(
    private val repository: AppearanceSettingsRepository,
    scope: CoroutineScope? = null,
    private val colorTarget: AppearanceColorTarget = AppearanceColorTarget.MOBILE,
) : ViewModel() {
    @dagger.assisted.AssistedInject
    constructor(
        repositories: AppearanceRepositoryFactory,
        @dagger.assisted.Assisted target: AppearanceColorTarget,
    ) : this(repositories.create(target), colorTarget = target)

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(target: AppearanceColorTarget): AppearanceSettingsViewModel
    }

    private val mutableState = MutableStateFlow(AppearanceSettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var failedCommand: (suspend () -> Unit)? = null
    private var revision = 0L
    private val workScope = scope ?: viewModelScope

    init {
        commands.trySend { reload(null) }
        workScope.launch {
            for (command in commands) {
                try {
                    command()
                } catch (error: IOException) {
                    failedCommand = command
                    mutableState.value = uiState.value.copy(error = AppearanceSettingsError.STORAGE)
                }
            }
        }
    }

    private fun update(transform: (AppearanceSettingsSnapshot) -> AppearanceSettingsSnapshot) {
        val current = uiState.value.snapshot ?: return
        revision++
        mutableState.value = uiState.value.copy(snapshot = transform(current))
    }

    fun refreshActiveMode() {
        commands.trySend { reload(null) }
    }

    fun selectMode(mode: AppearanceMode) {
        commands.trySend { reload(mode) }
    }

    fun editColor(role: SugarliciousColorRole?) {
        mutableState.value = uiState.value.copy(editingRole = role)
    }

    fun editWidgetColor(role: WidgetColorRole?) {
        mutableState.value = uiState.value.copy(editingWidgetRole = role)
    }

    fun editTrendColor(field: String?) {
        mutableState.value = uiState.value.copy(editingTrendColor = field)
    }

    fun setFloat(field: AppearanceFloat, value: Float) {
        if (value.isFinite()) update { it.copy(floats = it.floats + (field to value)) }
    }

    fun saveFloat(field: AppearanceFloat) {
        enqueue(AppearanceWrite.FloatValue(field, requireNotNull(uiState.value.snapshot).floats.getValue(field)))
    }

    fun setTrend(value: TrendArrowStyle) {
        update { it.copy(trend = value) }
    }

    fun saveTrend() {
        enqueue(AppearanceWrite.Trend(requireNotNull(uiState.value.snapshot).trend))
    }

    fun setHistoricalOutline(value: Boolean) {
        update { it.copy(historicalOutlineEnabled = value) }
        enqueue(AppearanceWrite.HistoricalOutline(value))
    }

    fun setCompact(value: Boolean) {
        update { it.copy(deviceVendorCompact = value) }
        enqueue(AppearanceWrite.Compact(value))
    }

    fun setDots(profile: NotificationGraphProfile, value: NotificationGraphDotStyle) {
        update { it.copy(notificationDots = it.notificationDots + (profile to value)) }
    }

    fun saveDots(profile: NotificationGraphProfile) {
        val draft = requireNotNull(uiState.value.snapshot).notificationDots.getValue(profile)
        val value = draft.copy(cgmOutlineEnabled = draft.historicalOutlineEnabled, cgmOutlineWidthDp = draft.historicalOutlineWidthDp)
        setDots(profile, value)
        enqueue(AppearanceWrite.Dots(profile, value))
    }

    fun setLayout(profile: NotificationGraphProfile, value: NotificationLayoutSettings) {
        update { it.copy(notificationLayouts = it.notificationLayouts + (profile to value)) }
        enqueue(AppearanceWrite.Layout(profile, value))
    }

    fun copyCollapsedDots() {
        val collapsed = requireNotNull(uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.COLLAPSED)
        setDots(NotificationGraphProfile.EXPANDED, collapsed)
        saveDots(NotificationGraphProfile.EXPANDED)
    }

    fun saveColor(role: SugarliciousColorRole, argb: Int) {
        update { snapshot ->
            if (colorTarget == AppearanceColorTarget.NOTIFICATION) {
                snapshot.copy(notificationColors = snapshot.notificationColors + (role to argb))
            } else {
                snapshot.copy(palette = SugarliciousPalette(SugarliciousColorRole.entries.associateWith { if (it == role) argb else snapshot.palette.argb(it) }, snapshot.palette.isLight))
            }
        }
        enqueue(AppearanceWrite.Color(colorTarget, role, null, argb))
    }

    fun saveWidgetColor(role: WidgetColorRole, argb: Int) {
        update { it.copy(widgetPalette = it.widgetPalette.with(role, argb)) }
        enqueue(AppearanceWrite.Color(AppearanceColorTarget.WIDGET, null, role, argb))
    }

    fun reset(operation: AppearanceReset, role: SugarliciousColorRole? = null, widgetRole: WidgetColorRole? = null): Int {
        enqueue(AppearanceWrite.Reset(colorTarget, operation, role, widgetRole), reload = true)
        val snapshot = requireNotNull(uiState.value.snapshot)
        return when (colorTarget) {
            AppearanceColorTarget.MOBILE -> snapshot.palette.argb(requireNotNull(role))
            AppearanceColorTarget.WIDGET -> snapshot.widgetPalette.argb(requireNotNull(widgetRole))
            AppearanceColorTarget.NOTIFICATION -> snapshot.notificationColors.getValue(requireNotNull(role))
        }
    }

    fun sendWatchColors() {
        if (uiState.value.syncingWatch) return
        mutableState.value = uiState.value.copy(syncingWatch = true, watchSyncStatus = "Wird gesendet ...", error = null)
        commands.trySend {
            // Await an operation child: publication follows all earlier writes, while an
            // individually cancelled send does not cancel the settings command worker.
            coroutineScope {
                launch {
                    try {
                        repository.sendWatchColors()
                        mutableState.value = uiState.value.copy(watchSyncStatus = "Graphfarben wurden an die Watch gesendet.")
                    } catch (error: IOException) {
                        mutableState.value = uiState.value.copy(error = AppearanceSettingsError.WATCH_SYNC, watchSyncStatus = "Watch-Farben konnten nicht gesendet werden.")
                    } catch (error: com.google.android.gms.common.api.ApiException) {
                        mutableState.value = uiState.value.copy(error = AppearanceSettingsError.WATCH_SYNC, watchSyncStatus = "Watch-Farben konnten nicht gesendet werden.")
                    } finally {
                        mutableState.value = uiState.value.copy(syncingWatch = false)
                    }
                }.join()
            }
        }
    }

    fun retry() {
        val failed = failedCommand
        failedCommand = null
        commands.trySend {
            if (failed == null) reload(uiState.value.snapshot?.mode) else failed()
            mutableState.value = uiState.value.copy(error = null)
        }
    }

    private fun enqueue(action: AppearanceWrite, reload: Boolean = false) {
        val mode = uiState.value.snapshot?.mode ?: return
        val actionRevision = revision
        commands.trySend {
            repository.execute(mode, action)
            if (reload) {
                val snapshot = repository.load(mode)
                if (revision == actionRevision && uiState.value.snapshot?.mode == mode) mutableState.value = uiState.value.copy(snapshot = snapshot)
            }
            mutableState.value = uiState.value.copy(error = null)
        }
    }

    private suspend fun reload(mode: AppearanceMode?) {
        val snapshot = repository.load(mode)
        mutableState.value = uiState.value.copy(snapshot = snapshot, error = null)
    }

    override fun onCleared() {
        commands.close()
    }
}

/** UI bindings hold no state: all reads and actions go to the immutable feature state. */
internal class AppearanceBinding<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = read()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = write(value)
}

@Composable
internal fun rememberAppearanceSettingsModel(key: String, target: AppearanceColorTarget): AppearanceSettingsViewModel {
    val context = LocalContext.current
    val owner = appearanceViewModelStoreOwner(LocalView.current, context)
    return remember(owner, key, target) {
        injectedViewModelProvider(owner) { extras ->
            extras.withCreationCallback<AppearanceSettingsViewModel.Factory> { it.create(target) }
        }[key, AppearanceSettingsViewModel::class.java]
    }
}

internal fun AppearanceSettingsViewModel.floatBinding(field: AppearanceFloat) =
    AppearanceBinding(
        { requireNotNull(uiState.value.snapshot).floats.getValue(field) },
        { value: Float -> setFloat(field, value) },
    )

internal fun AppearanceSettingsViewModel.updateDots(profile: NotificationGraphProfile, transform: (NotificationGraphDotStyle) -> NotificationGraphDotStyle) {
    setDots(profile, transform(requireNotNull(uiState.value.snapshot).notificationDots.getValue(profile)))
}

/** AndroidView hosts can supply a themed application Context; ownership comes from the view tree. */
internal fun appearanceViewModelStoreOwner(view: android.view.View, context: Context): ViewModelStoreOwner =
    view.findViewTreeViewModelStoreOwner()
        ?: generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ViewModelStoreOwner>().firstOrNull()
        ?: error("Appearance settings require the host ViewModelStoreOwner")
