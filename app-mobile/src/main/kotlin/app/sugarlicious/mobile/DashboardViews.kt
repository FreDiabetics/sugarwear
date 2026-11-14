package app.sugarlicious.mobile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousTheme
import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.PredictionKind
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class DashboardScreen { OVERVIEW, WATCH, SETTINGS }

enum class DisplayUnitPreference { AAPS, MG_DL, MMOL_L }

enum class DashboardThemeMode { SYSTEM, LIGHT, DARK }

enum class GlucoseTileDetailMode { TIR, THERAPY }

enum class DataSourcePreference { AUTOMATIC, ANDROID_APS, DEXCOM_G7_WATCH }

internal fun migrateDataSourcePreference(raw: String?): DataSourcePreference =
    DataSourcePreference.entries.firstOrNull { it.name == raw && it != DataSourcePreference.DEXCOM_G7_WATCH }
        ?: DataSourcePreference.ANDROID_APS

internal fun thresholdForUi(
    valueMgDl: Double,
    unit: DisplayUnitPreference,
): Float = if (unit == DisplayUnitPreference.MMOL_L) (valueMgDl / 18.0).toFloat() else valueMgDl.toFloat()

internal fun thresholdFromUi(
    value: Float,
    unit: DisplayUnitPreference,
): Double = if (unit == DisplayUnitPreference.MMOL_L) value * 18.0 else value.toDouble()

internal fun formatThreshold(
    valueMgDl: Double,
    unit: DisplayUnitPreference,
): String =
    if (unit == DisplayUnitPreference.MMOL_L) {
        String.format(Locale.GERMANY, "%.1f mmol/L", valueMgDl / 18.0)
    } else {
        "${valueMgDl.toInt()} mg/dL"
    }

internal object DashboardSystemDefaults {
    val unit = DisplayUnitPreference.MG_DL
    val glucoseTileDetailMode = GlucoseTileDetailMode.THERAPY
    const val COB_PROGRESS_MAXIMUM_GRAMS = 80.1f
    const val COB_GRAPH_MAXIMUM_GRAMS = 100f
    const val GRAPH_HOURS = 2
    val graphScaleMode = CgmGraphScaleMode.DYNAMIC
    const val GRAPH_MAXIMUM_MG_DL = 251f
    const val NOTIFICATION_GRAPH_HOURS = 2
    const val TREND_SCALE_PERCENT = 120
}

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DashboardSystemDefaults.unit,
    val glucoseTileDetailMode: GlucoseTileDetailMode = DashboardSystemDefaults.glucoseTileDetailMode,
    val iobProgressMaximumUnits: Float = 10f,
    val cobProgressMaximumGrams: Float = DashboardSystemDefaults.COB_PROGRESS_MAXIMUM_GRAMS,
    val iobGraphMaximumUnits: Float = 10f,
    val cobGraphMaximumGrams: Float = DashboardSystemDefaults.COB_GRAPH_MAXIMUM_GRAMS,
    val showCgmGraph: Boolean = true,
    val showCgmTargetValue: Boolean = true,
    val showCgmCurrentBasal: Boolean = true,
    val showCgmBaseBasal: Boolean = true,
    val showCgmActivity: Boolean = true,
    val showCgmPredictions: Boolean = true,
    val showCgmPredictionIob: Boolean = true,
    val showCgmPredictionCob: Boolean = true,
    val showCgmPredictionUam: Boolean = true,
    val showCgmPredictionZeroTemp: Boolean = true,
    val showMetabolicGraph: Boolean = true,
    val showMealBolusMarkers: Boolean = true,
    val showCorrectionMarkers: Boolean = true,
    val showSmbMarkers: Boolean = true,
    val showMealCarbMarkers: Boolean = true,
    val showECarbMarkers: Boolean = true,
    val cgmDotRadiusDp: Float = 2.4f,
    val cgmDotOutlineEnabled: Boolean = true,
    val cgmDotOutlineWidthDp: Float = 0.95f,
    val cgmHistoricalDotOutlineEnabled: Boolean = cgmDotOutlineEnabled,
    val cgmHistoricalDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
    val cgmCurrentDotOutlineWidthDp: Float = cgmDotOutlineWidthDp,
    val predictionDotRadiusDp: Float = 1.75f,
    val predictionDotOutlineWidthDp: Float = 0.70f,
    val compact: Boolean = true,
    val graphHours: Int = DashboardSystemDefaults.GRAPH_HOURS,
    val graphScaleMode: CgmGraphScaleMode = DashboardSystemDefaults.graphScaleMode,
    val iobGraphScaleMode: CgmGraphScaleMode = graphScaleMode,
    val cobGraphScaleMode: CgmGraphScaleMode = graphScaleMode,
    val graphMinimumMgDl: Double = 0.0,
    val graphMaximumMgDl: Double = DashboardSystemDefaults.GRAPH_MAXIMUM_MG_DL.toDouble(),
    val liveNotification: Boolean = true,
    val notificationGraphEnabled: Boolean = true,
    val notificationGraphHours: Int = DashboardSystemDefaults.NOTIFICATION_GRAPH_HOURS,
    val watchFaceIndex: Int = 1,
    val dataSource: DataSourcePreference = DataSourcePreference.ANDROID_APS,
    val themeMode: DashboardThemeMode = DashboardThemeMode.SYSTEM,
    val cgmThresholds: CgmThresholds = CgmThresholds.DEFAULT,
    val glucoseScalePercent: Int = GlucoseTrendSizing.DEFAULT_SCALE_PERCENT,
    val trendScalePercent: Int = DashboardSystemDefaults.TREND_SCALE_PERCENT,
    val showTir: Boolean = glucoseTileDetailMode == GlucoseTileDetailMode.TIR,
    val showTitr: Boolean = false,
    val showTherapyProgress: Boolean = glucoseTileDetailMode == GlucoseTileDetailMode.THERAPY,
) {
    val enabledPredictionKinds: Set<PredictionKind>
        get() =
            buildSet {
                if (showCgmPredictionIob) add(PredictionKind.IOB)
                if (showCgmPredictionCob) {
                    add(PredictionKind.COB)
                    add(PredictionKind.ACOB)
                }
                if (showCgmPredictionUam) add(PredictionKind.UAM)
                if (showCgmPredictionZeroTemp) add(PredictionKind.ZERO_TEMP)
            }
    val predictionsEnabled: Boolean
        get() = showCgmPredictions && enabledPredictionKinds.isNotEmpty()
    val showPredictionDivider: Boolean
        get() = predictionsEnabled
    val anyCgmPredictionEnabled: Boolean
        get() = predictionsEnabled
    val showCgmBasal: Boolean
        get() = showCgmCurrentBasal || showCgmBaseBasal

    fun unitFor(state: TherapyDisplayState?): GlucoseUnit =
        when (unit) {
            DisplayUnitPreference.AAPS -> state?.glucose?.displayUnit ?: GlucoseUnit.MG_DL
            DisplayUnitPreference.MG_DL -> GlucoseUnit.MG_DL
            DisplayUnitPreference.MMOL_L -> GlucoseUnit.MMOL_L
        }

    companion object {
        fun read(preferences: SharedPreferences) =
            DashboardUiPreferences(
                unit =
                    runCatching {
                        DisplayUnitPreference.valueOf(preferences.getString("unit", DashboardSystemDefaults.unit.name)!!)
                    }.getOrDefault(DashboardSystemDefaults.unit),
                glucoseTileDetailMode = legacyGlucoseTileMode(preferences),
                showTir = preferences.getBoolean("overview.showTir", legacyGlucoseTileMode(preferences) == GlucoseTileDetailMode.TIR),
                showTitr = preferences.getBoolean("overview.showTitr", false),
                showTherapyProgress = preferences.getBoolean("overview.showTherapyProgress", legacyGlucoseTileMode(preferences) == GlucoseTileDetailMode.THERAPY),
                iobProgressMaximumUnits = preferences.getFloat(IOB_PROGRESS_MAXIMUM_KEY, 10f).coerceIn(0f, 30f),
                cobProgressMaximumGrams = preferences.getFloat(COB_PROGRESS_MAXIMUM_KEY, DashboardSystemDefaults.COB_PROGRESS_MAXIMUM_GRAMS).coerceIn(10f, 500f),
                iobGraphMaximumUnits = preferences.getFloat(IOB_GRAPH_MAXIMUM_KEY, 10f).coerceIn(1f, 30f),
                cobGraphMaximumGrams = preferences.getFloat(COB_GRAPH_MAXIMUM_KEY, DashboardSystemDefaults.COB_GRAPH_MAXIMUM_GRAMS).coerceIn(10f, 500f),
                showCgmGraph = preferences.getBoolean("showCgmGraph", true),
                showCgmTargetValue = preferences.getBoolean("cgm.targetValue", true),
                showCgmCurrentBasal = preferences.getBoolean(CGM_CURRENT_BASAL_KEY, preferences.getBoolean("cgm.basal", true)),
                showCgmBaseBasal = preferences.getBoolean(CGM_BASE_BASAL_KEY, preferences.getBoolean("cgm.basal", true)),
                showCgmActivity = preferences.getBoolean("cgm.activity", true),
                showCgmPredictions = preferences.getBoolean("showPredictions", true),
                showCgmPredictionIob = preferences.getBoolean("cgm.prediction.iob", true),
                showCgmPredictionCob = preferences.getBoolean("cgm.prediction.cob", true),
                showCgmPredictionUam = preferences.getBoolean("cgm.prediction.uam", true),
                showCgmPredictionZeroTemp = preferences.getBoolean("cgm.prediction.zeroTemp", true),
                showMetabolicGraph = preferences.getBoolean("showMetabolicGraph", true),
                showMealBolusMarkers = preferences.getBoolean("treatment.mealBolus", true),
                showCorrectionMarkers = preferences.getBoolean("treatment.correction", true),
                showSmbMarkers = preferences.getBoolean("treatment.smb", true),
                showMealCarbMarkers = preferences.getBoolean("treatment.mealCarbs", true),
                showECarbMarkers = preferences.getBoolean("treatment.eCarbs", true),
                cgmDotRadiusDp = readMobileGraphStyle(preferences).cgmDotRadiusDp,
                cgmDotOutlineEnabled = readMobileGraphStyle(preferences).cgmDotOutlineEnabled,
                cgmDotOutlineWidthDp = readMobileGraphStyle(preferences).cgmDotOutlineWidthDp,
                cgmHistoricalDotOutlineEnabled = readMobileGraphStyle(preferences).cgmHistoricalDotOutlineEnabled,
                cgmHistoricalDotOutlineWidthDp = readMobileGraphStyle(preferences).cgmHistoricalDotOutlineWidthDp,
                cgmCurrentDotOutlineWidthDp = readMobileGraphStyle(preferences).cgmCurrentDotOutlineWidthDp,
                predictionDotRadiusDp = readMobilePredictionDotRadius(preferences),
                predictionDotOutlineWidthDp = readMobilePredictionDotOutlineWidth(preferences),
                compact = preferences.getBoolean("compact", true),
                graphHours = preferences.getInt("graphHours", DashboardSystemDefaults.GRAPH_HOURS).takeIf { it in OVERVIEW_GRAPH_HOUR_OPTIONS } ?: DashboardSystemDefaults.GRAPH_HOURS,
                graphScaleMode =
                    runCatching {
                        CgmGraphScaleMode.valueOf(preferences.getString(GRAPH_SCALE_MODE_KEY, DashboardSystemDefaults.graphScaleMode.name)!!)
                    }.getOrDefault(DashboardSystemDefaults.graphScaleMode),
                iobGraphScaleMode = readScaleMode(preferences, IOB_GRAPH_SCALE_MODE_KEY, GRAPH_SCALE_MODE_KEY),
                cobGraphScaleMode = readScaleMode(preferences, COB_GRAPH_SCALE_MODE_KEY, GRAPH_SCALE_MODE_KEY),
                graphMinimumMgDl = 0.0,
                graphMaximumMgDl = preferences.getFloat(GRAPH_MAXIMUM_KEY, DashboardSystemDefaults.GRAPH_MAXIMUM_MG_DL).toDouble().coerceIn(180.0, 600.0),
                liveNotification = preferences.getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true),
                notificationGraphEnabled = preferences.getBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED, true),
                notificationGraphHours =
                    preferences.getInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, DashboardSystemDefaults.NOTIFICATION_GRAPH_HOURS).takeIf { it in 1..3 } ?: DashboardSystemDefaults.NOTIFICATION_GRAPH_HOURS,
                watchFaceIndex = preferences.getInt("watchFaceIndex", 1).coerceIn(sugarliciousWatchFaceCards.indices),
                dataSource = DataSourcePreference.ANDROID_APS,
                themeMode =
                    runCatching {
                        DashboardThemeMode.valueOf(preferences.getString("themeMode", "SYSTEM")!!)
                    }.getOrDefault(DashboardThemeMode.SYSTEM),
                cgmThresholds = CgmThresholdPreferences.read(preferences),
                glucoseScalePercent =
                    preferences
                        .getInt(MOBILE_GLUCOSE_SCALE_KEY, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
                        .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
                trendScalePercent =
                    preferences
                        .getInt(MOBILE_TREND_SCALE_KEY, DashboardSystemDefaults.TREND_SCALE_PERCENT)
                        .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            )

        private fun legacyGlucoseTileMode(preferences: SharedPreferences): GlucoseTileDetailMode =
            runCatching { GlucoseTileDetailMode.valueOf(preferences.getString(GLUCOSE_TILE_DETAIL_MODE_KEY, DashboardSystemDefaults.glucoseTileDetailMode.name)!!) }
                .getOrDefault(DashboardSystemDefaults.glucoseTileDetailMode)

        const val MOBILE_GLUCOSE_SCALE_KEY = "visual.mobile.glucoseScalePercent"
        const val MOBILE_TREND_SCALE_KEY = "visual.mobile.trendScalePercent"
        const val GLUCOSE_TILE_DETAIL_MODE_KEY = "overview.glucoseTileDetailMode"
        const val IOB_PROGRESS_MAXIMUM_KEY = "overview.iobProgressMaximumUnits"
        const val COB_PROGRESS_MAXIMUM_KEY = "overview.cobProgressMaximumGrams"
        const val IOB_GRAPH_MAXIMUM_KEY = "graph.iobMaximumUnits"
        const val COB_GRAPH_MAXIMUM_KEY = "graph.cobMaximumGrams"
        const val GRAPH_MAXIMUM_KEY = "graph.maximumMgDl"
        const val GRAPH_MINIMUM_KEY = "graph.minimumMgDl"
        const val GRAPH_SCALE_MODE_KEY = "graph.scaleMode"
        const val IOB_GRAPH_SCALE_MODE_KEY = "graph.iobScaleMode"
        const val COB_GRAPH_SCALE_MODE_KEY = "graph.cobScaleMode"
        const val CGM_CURRENT_BASAL_KEY = "cgm.basal.current"
        const val CGM_BASE_BASAL_KEY = "cgm.basal.base"
    }
}

private fun readScaleMode(
    preferences: SharedPreferences,
    key: String,
    legacyKey: String,
): CgmGraphScaleMode =
    runCatching {
        CgmGraphScaleMode.valueOf(
            preferences.getString(key, preferences.getString(legacyKey, DashboardSystemDefaults.graphScaleMode.name))!!,
        )
    }.getOrDefault(DashboardSystemDefaults.graphScaleMode)

private fun graphScaleModeLabel(mode: CgmGraphScaleMode): String =
    when (mode) {
        CgmGraphScaleMode.STATIC -> "Statisch"
        CgmGraphScaleMode.DYNAMIC -> "Dynamisch"
        CgmGraphScaleMode.LOGARITHMIC -> "Logarithmisch"
        CgmGraphScaleMode.LOGARITHMIC_DYNAMIC -> "Logarithmisch-dynamisch"
    }

data class DiagnosticsSnapshot(
    val sourceVersion: String?,
    val sourceContract: String?,
    val sourcePackage: String?,
    val receivedAt: Long,
    val measuredAt: Long,
    val reachableWatches: Int,
    val lastSyncAt: Long,
    val syncStatus: String?,
    val syncError: String?,
    val connectedWatchModels: String = "",
) {
    companion object {
        fun read(preferences: SharedPreferences) =
            DiagnosticsSnapshot(
                sourceVersion = preferences.getString("sourceVersion", null),
                sourceContract = preferences.getString("contract", null),
                sourcePackage = preferences.getString("sourcePackage", null),
                receivedAt = preferences.getLong("received", 0L),
                measuredAt = preferences.getLong("measurement", 0L),
                reachableWatches = preferences.getInt("reachableWatches", 0),
                lastSyncAt = preferences.getLong("lastSyncAt", 0L),
                syncStatus = preferences.getString("lastSyncStatus", null),
                syncError = preferences.getString("lastSyncError", null),
                connectedWatchModels = preferences.getString("connectedWatchModels", "").orEmpty(),
            )
    }
}

data class DashboardCallbacks(
    val navigate: (DashboardScreen) -> Unit,
    val setUnit: (DisplayUnitPreference) -> Unit,
    val setDataSource: (DataSourcePreference) -> Unit,
    val openCloudEndpointTreatments: () -> Unit,
    val openDiagnostics: () -> Unit,
    val setThemeMode: (DashboardThemeMode) -> Unit,
    val setGlucoseTileDetailMode: (GlucoseTileDetailMode) -> Unit,
    val setShowCgmGraph: (Boolean) -> Unit,
    val setGraphHours: (Int) -> Unit,
    val setCgmStream: (String, Boolean) -> Unit = { _, _ -> },
    val setShowMetabolicGraph: (Boolean) -> Unit,
    val setCompact: (Boolean) -> Unit,
    val setLiveNotification: (Boolean) -> Unit,
    val setNotificationGraphEnabled: (Boolean) -> Unit,
    val setNotificationGraphHours: (Int) -> Unit,
    val setWatchFaceIndex: (Int) -> Unit,
    val syncNow: () -> Unit,
    val connectHealthConnect: () -> Unit,
    val syncHealthConnect: () -> Unit,
    val manageHealthConnect: () -> Unit,
    val requestNotificationAccess: () -> Unit,
    val requestUnrestrictedBattery: () -> Unit,
    val exportSettings: () -> Unit,
    val importSettings: () -> Unit,
    val openProjectGitHub: () -> Unit,
    val openContactEmail: () -> Unit,
)

internal class DashboardViewFactory(
    private val context: Context,
    private val callbacks: DashboardCallbacks,
    private val onSettingsPageChanged: () -> Unit = {},
    private val dashboardState: StateFlow<DashboardUiState>,
    private val settingsViewModel: MobileSettingsViewModel,
    private val sourceSettingsViewModel: MobileSourceSettingsViewModel,
) {
    private var activeComposeScreen: DashboardScreen? = null
    private var activeComposeView: androidx.compose.ui.platform.ComposeView? = null
    private val settingsState get() = settingsViewModel.uiState.value
    private val notificationGraphSettingsExpanded get() = settingsState.notificationGraphExpanded
    val settingsTitle: String get() = settingsState.title
    val settingsIsRoot: Boolean get() = settingsState.category == null
    val settingsCategory: String? get() = settingsState.category
    private var renderedSettingsPreferences: DashboardUiPreferences? = null
    var settingsBody: LinearLayout? = null
        private set

    fun clearDataSourceExpansion() = settingsViewModel.clearDataSourceExpansion()

    fun restoreSettingsPage(category: String, title: String) = settingsViewModel.open(category, title)

    fun backWithinSettings(): Boolean = settingsViewModel.back()

    private val density = context.resources.displayMetrics.density
    private val text: Int get() = MobileSettingsStyle.text(SugarliciousColors.palette.isLight)
    private val secondary: Int get() = MobileSettingsStyle.secondary(SugarliciousColors.palette.isLight)
    private val accent: Int get() =
        app.sugarlicious.mobile.ui.theme
            .mobileMenuAccentArgb(context, dark = !SugarliciousColors.palette.isLight)

    fun render(
        parent: LinearLayout,
        screen: DashboardScreen,
        state: TherapyDisplayState?,
        preferences: DashboardUiPreferences,
    ) {
        if (screen != DashboardScreen.SETTINGS || settingsCategory != "general") settingsViewModel.clearDataSourceExpansion()
        when (screen) {
            DashboardScreen.OVERVIEW -> renderOverview(parent)
            DashboardScreen.WATCH -> renderWatch(parent)
            DashboardScreen.SETTINGS -> renderSettings(parent, state, preferences)
        }
    }

    private fun renderOverview(
        parent: LinearLayout,
    ) {
        collapseSettingsSections()
        if (activeComposeScreen == DashboardScreen.OVERVIEW && activeComposeView?.parent === parent) return
        parent.removeAllViews()
        val composeView =
            androidx.compose.ui.platform.ComposeView(context).apply {
                setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    SugarliciousTheme {
                        val rendered by dashboardState.collectAsStateWithLifecycle()
                        run {
                            SugarliciousOverviewScreen(
                                state = rendered.therapy,
                                diagnostics = rendered.diagnostics,
                                preferences = rendered.preferences,
                                now = rendered.nowEpochMs,
                                callbacks = callbacks,
                                observationError = rendered.observationError,
                            )
                        }
                    }
                }
            }
        attachViewportCompose(parent, composeView)
        activeComposeScreen = DashboardScreen.OVERVIEW
        activeComposeView = composeView
    }

    private fun renderWatch(
        parent: LinearLayout,
    ) {
        collapseSettingsSections()
        if (activeComposeScreen == DashboardScreen.WATCH && activeComposeView?.parent === parent) return
        parent.removeAllViews()
        val composeView =
            androidx.compose.ui.platform.ComposeView(context).apply {
                setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    SugarliciousTheme {
                        val rendered by dashboardState.collectAsStateWithLifecycle()
                        run {
                            OneUiWidgetSettingsTheme(dark = !SugarliciousColors.palette.isLight) {
                                SugarliciousWatchScreen(
                                    state = rendered.therapy,
                                    preferences = rendered.preferences,
                                    diagnostics = rendered.diagnostics,
                                    onSelectedFace = callbacks.setWatchFaceIndex,
                                )
                            }
                        }
                    }
                }
            }
        attachViewportCompose(parent, composeView)
        activeComposeScreen = DashboardScreen.WATCH
        activeComposeView = composeView
    }

    private fun attachViewportCompose(parent: LinearLayout, composeView: androidx.compose.ui.platform.ComposeView) {
        val viewport = parent.parent as View
        // Bound the FIRST measure too: posting a resize after attaching is too late,
        // because ScrollView measures a wrap-content ComposeView with infinite height.
        val initialViewportHeight = viewport.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        parent.addView(
            composeView,
            fullWidth().apply { height = (initialViewportHeight - parent.paddingTop - parent.paddingBottom).coerceAtLeast(1) },
        )
        val resize =
            android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val height = (viewport.height - parent.paddingTop - parent.paddingBottom).coerceAtLeast(1)
                if (composeView.layoutParams.height != height) {
                    composeView.layoutParams = composeView.layoutParams.apply { this.height = height }
                }
            }
        viewport.addOnLayoutChangeListener(resize)
        composeView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    viewport.removeOnLayoutChangeListener(resize)
                }
            },
        )
        composeView.post { resize.onLayoutChange(viewport, 0, 0, 0, 0, 0, 0, 0, 0) }
    }

    private fun renderSettings(
        parent: LinearLayout,
        state: TherapyDisplayState?,
        preferences: DashboardUiPreferences,
    ) {
        if (activeComposeScreen == DashboardScreen.SETTINGS && activeComposeView?.parent === parent) {
            settingsBody?.let { renderSettingsControls(it, state, preferences) }
            return
        }
        parent.removeAllViews()
        val nativeBody =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
            }
        settingsBody = nativeBody
        renderSettingsControls(nativeBody, state, preferences)
        val host =
            androidx.compose.ui.platform.ComposeView(context).apply {
                setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val page by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    val rendered by dashboardState.collectAsStateWithLifecycle()
                    // Navigation and expansion are retained above the disposable rendering adapters.
                    androidx.compose.runtime.LaunchedEffect(page.category) { onSettingsPageChanged() }
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { nativeBody },
                        update = { body ->
                            if (renderedSettingsPreferences != rendered.preferences) {
                                renderSettingsControls(body, rendered.therapy, rendered.preferences)
                            }
                        },
                    )
                }
            }
        parent.addView(host, fullWidth())
        activeComposeView = host
        activeComposeScreen = DashboardScreen.SETTINGS
    }

    private fun renderSettingsControls(parent: LinearLayout, state: TherapyDisplayState?, preferences: DashboardUiPreferences) {
        renderedSettingsPreferences = preferences
        parent.removeAllViews()
        if (settingsIsRoot) parent.addView(screenTitle("Einstellungen").apply { tag = "settings_scroll_title" })

        val colorContainer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val colorSettings =
                    androidx.compose.ui.platform.ComposeView(context).apply {
                        setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                        setContent {
                            OneUiWidgetSettingsTheme(dark = !SugarliciousColors.palette.isLight) {
                                SugarliciousColorSettingsPanel(
                                    showCgmGraph = preferences.showCgmGraph,
                                    showMetabolicGraph = preferences.showMetabolicGraph,
                                    themeMode = preferences.themeMode,
                                    onThemeModeSelected = callbacks.setThemeMode,
                                )
                            }
                        }
                    }
                addView(colorSettings, fullWidth())
            }
        val notificationGraphCustomization =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (notificationGraphSettingsExpanded) View.VISIBLE else View.GONE
                addView(
                    tile(null).apply {
                        addView(
                            choiceRow(
                                "Graph-Skalierung",
                                listOf(
                                    Triple("1 h", preferences.notificationGraphHours == 1) { callbacks.setNotificationGraphHours(1) },
                                    Triple("2 h", preferences.notificationGraphHours == 2) { callbacks.setNotificationGraphHours(2) },
                                    Triple("3 h", preferences.notificationGraphHours == 3) { callbacks.setNotificationGraphHours(3) },
                                ),
                            ),
                        )
                    },
                    fullWidth(),
                )
                addView(
                    androidx.compose.ui.platform.ComposeView(context).apply {
                        setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                        setContent { OneUiWidgetSettingsTheme(dark = !SugarliciousColors.palette.isLight) { NotificationGraphSettingsPanel() } }
                    },
                    fullWidth(),
                )
            }

        addSettingsCategory(parent, "general", "Datenquellen", R.drawable.ic_settings) {
            addView(composeSettingsControl { MobileDataSourceSettingsPanel(settingsViewModel, sourceSettingsViewModel) }.apply { tag = "mobile-data-sources" }, cardParams(top = 8))
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("WATCH-VERBINDUNG"))
                    addView(actionRow("Jetzt synchronisieren", "Jetzt") { callbacks.syncNow() })
                },
                cardParams(top = 16),
            )
        }

        addSettingsCategory(parent, "glucose_ranges", "Glukose und Zielbereiche", R.drawable.ic_health_glucose) {
            val selectedThresholdUnit =
                when (preferences.unitFor(state)) {
                    GlucoseUnit.MMOL_L -> DisplayUnitPreference.MMOL_L
                    GlucoseUnit.MG_DL -> DisplayUnitPreference.MG_DL
                }
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("GLUKOSE-EINHEIT"))
                    addView(composeSettingsControl { OneUiGlucoseUnitSelector(preferences.unit, callbacks.setUnit) }, fullWidth())
                    addView(divider())
                    addView(settingsGroupLabel("GLUKOSEBEREICHE"))

                    fun thresholdRow(
                        title: String,
                        value: Double,
                        minimum: Float,
                        maximum: Float,
                        update: (Double) -> CgmThresholds,
                    ) = sugarliciousSliderRow(
                        title = title,
                        value = thresholdForUi(value, selectedThresholdUnit),
                        minimum = thresholdForUi(minimum.toDouble(), selectedThresholdUnit),
                        maximum = thresholdForUi(maximum.toDouble(), selectedThresholdUnit),
                        valueFormatter = { formatThreshold(thresholdFromUi(it, selectedThresholdUnit), selectedThresholdUnit) },
                    ) { entered ->
                        val mgDl = thresholdFromUi(entered, selectedThresholdUnit)
                        settingsViewModel.saveThresholds(update(mgDl))
                    }
                    addView(
                        thresholdRow(
                            "Sehr hoch",
                            preferences.cgmThresholds.veryHighMgDl,
                            100f,
                            400f,
                        ) { preferences.cgmThresholds.copy(veryHighMgDl = it) },
                    )
                    addView(divider())
                    addView(
                        thresholdRow(
                            "Hoch",
                            preferences.cgmThresholds.highMgDl,
                            80f,
                            300f,
                        ) { preferences.cgmThresholds.copy(highMgDl = it) },
                    )
                    addView(divider())
                    addView(
                        thresholdRow("Tief", preferences.cgmThresholds.lowMgDl, 40f, 180f) { preferences.cgmThresholds.copy(lowMgDl = it) },
                    )
                    addView(divider())
                    addView(
                        thresholdRow(
                            "Sehr tief",
                            preferences.cgmThresholds.veryLowMgDl,
                            20f,
                            120f,
                        ) { preferences.cgmThresholds.copy(veryLowMgDl = it) },
                    )
                },
                cardParams(top = 4),
            )
        }

        addSettingsCategory(parent, "notifications", "Benachrichtigungen und Alarme", R.drawable.ic_notification_outlined) {
            val liveStatus = liveNotificationStatus(context, preferences.liveNotification)
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("LIVE-ANZEIGE"))
                    addView(
                        switchRowCompact(
                            "Live-Benachrichtigung",
                            preferences.liveNotification,
                            R.id.dashboard_live_notification_switch,
                            callbacks.setLiveNotification,
                        ),
                    )
                    addView(divider())
                    addView(statusRow("Gerät", liveStatus.device))
                    addView(statusRow("Samsung Now Bar", liveStatus.deviceVendorNowBar))
                    addView(statusRow("Android Live Updates", liveStatus.androidLiveUpdates))
                    addView(statusRow("Promoted Notifications", liveStatus.promotedNotifications))
                    addView(statusRow("Promotable", liveStatus.promotableCharacteristics))
                    addView(statusRow("Samsung Extras", liveStatus.deviceVendorExtras))
                    addView(statusRow("Letzter Post-Versuch", liveStatus.lastPostAttempt))
                    addView(statusRow("Exakte Frischegrenze", liveStatus.exactFreshnessAlarm))
                    addView(statusRow("Hardwaretest", liveStatus.hardwareValidation))
                    addView(divider())
                    addView(settingsGroupLabel("GRAPH"))
                    addView(
                        switchRowCompact(
                            "CGM-Graph anzeigen",
                            preferences.notificationGraphEnabled,
                            View.generateViewId(),
                            callbacks.setNotificationGraphEnabled,
                        ),
                    )
                    if (preferences.notificationGraphEnabled) {
                        addView(divider())
                        addView(
                            actionRow("CGM-Graph", "Anpassen") {
                                settingsViewModel.toggleNotificationGraph()
                                notificationGraphCustomization.visibility =
                                    if (notificationGraphSettingsExpanded) View.VISIBLE else View.GONE
                            },
                        )
                    }
                    addView(divider())
                    addView(settingsGroupLabel("SYSTEMZUGRIFF"))
                    addView(
                        actionRow("Benachrichtigungen", AppRuntimeAccess.notificationLabel(context), callbacks.requestNotificationAccess),
                    )
                },
                cardParams(top = 4),
            )
            addView(notificationGraphCustomization, cardParams(top = 4))
        }

        addSettingsCategory(parent, "overview_graphs", "Übersicht und Graphen", R.drawable.ic_foreground) {
            val graphUnit =
                when (preferences.unitFor(state)) {
                    GlucoseUnit.MMOL_L -> DisplayUnitPreference.MMOL_L
                    GlucoseUnit.MG_DL -> DisplayUnitPreference.MG_DL
                }
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("ÜBERSICHTSKACHELN"))
                    listOf(
                        Triple("Time in Range", "overview.showTir", preferences.showTir),
                        Triple("Time in Tight Range", "overview.showTitr", preferences.showTitr),
                        Triple("Therapie-Progressbars", "overview.showTherapyProgress", preferences.showTherapyProgress),
                    ).forEach { (label, key, enabled) ->
                        addView(switchRowCompact(label, enabled, View.generateViewId()) { settingsViewModel.setBoolean(key, it) })
                    }
                    if (preferences.showTherapyProgress) {
                        addView(divider())
                        addView(settingsGroupLabel("IOB-ANZEIGE"))
                        addView(
                            sugarliciousSliderRow(
                                title = "IOB-Skala",
                                value = preferences.iobProgressMaximumUnits,
                                minimum = 0f,
                                maximum = 30f,
                                valueFormatter = { String.format(Locale.GERMANY, "%.1f U", it) },
                            ) { settingsViewModel.setFloat(DashboardUiPreferences.IOB_PROGRESS_MAXIMUM_KEY, it) },
                        )
                        addView(divider())
                        addView(settingsGroupLabel("COB-ANZEIGE"))
                        addView(
                            sugarliciousSliderRow(
                                title = "COB Progressbar Maximum",
                                value = preferences.cobProgressMaximumGrams,
                                minimum = 10f,
                                maximum = 500f,
                                valueFormatter = { String.format(Locale.GERMANY, "%.0f g", it) },
                            ) { settingsViewModel.setFloat(DashboardUiPreferences.COB_PROGRESS_MAXIMUM_KEY, it) },
                        )
                    }
                    addView(divider())
                    addView(settingsGroupLabel("ÜBERSICHT"))
                    addView(
                        switchRowCompact("Kompakte Übersicht", preferences.compact, R.id.dashboard_compact_switch, callbacks.setCompact),
                    )
                    addView(divider())
                    addView(settingsGroupLabel("CGM-GRAPH"))
                    addView(switchRowCompact("Graph anzeigen", preferences.showCgmGraph, View.generateViewId(), callbacks.setShowCgmGraph))
                    if (preferences.showCgmGraph) {
                        addView(divider())
                        addView(
                            choiceRow(
                                "Skalierung",
                                CgmGraphScaleMode.entries.map { mode ->
                                    Triple(
                                        when (mode) {
                                            CgmGraphScaleMode.STATIC -> "Statisch"
                                            CgmGraphScaleMode.DYNAMIC -> "Dynamisch"
                                            CgmGraphScaleMode.LOGARITHMIC -> "Logarithmisch · sanft"
                                            CgmGraphScaleMode.LOGARITHMIC_DYNAMIC -> "Logarithmisch-dynamisch · sanft"
                                        },
                                        preferences.graphScaleMode == mode,
                                    ) {
                                        settingsViewModel.setString(
                                            DashboardUiPreferences.GRAPH_SCALE_MODE_KEY,
                                            mode.name,
                                        )
                                    }
                                },
                            ),
                        )
                        if (
                            preferences.graphScaleMode == CgmGraphScaleMode.STATIC ||
                            preferences.graphScaleMode == CgmGraphScaleMode.LOGARITHMIC
                        ) {
                            addView(divider())
                            addView(
                                sugarliciousSliderRow(
                                    title = "Graphmaximum",
                                    value = thresholdForUi(preferences.graphMaximumMgDl, graphUnit),
                                    minimum = thresholdForUi(180.0, graphUnit),
                                    maximum = thresholdForUi(600.0, graphUnit),
                                    valueFormatter = { formatThreshold(thresholdFromUi(it, graphUnit), graphUnit) },
                                ) { entered ->
                                    settingsViewModel.setFloat(
                                        DashboardUiPreferences.GRAPH_MAXIMUM_KEY,
                                        thresholdFromUi(entered, graphUnit).toFloat(),
                                    )
                                },
                            )
                        }
                        addView(divider())
                        addView(settingsGroupLabel("ZIELWERT UND BASALRATE"))
                        addView(
                            switchRowCompact("Aktueller Zielwert", preferences.showCgmTargetValue, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.targetValue", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "Aktuelle BR/TBR",
                                preferences.showCgmCurrentBasal,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream(DashboardUiPreferences.CGM_CURRENT_BASAL_KEY, it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "Basis-BR",
                                preferences.showCgmBaseBasal,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream(DashboardUiPreferences.CGM_BASE_BASAL_KEY, it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("Insulinaktivität", preferences.showCgmActivity, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.activity", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "IOB/COB-Graph",
                                preferences.showMetabolicGraph,
                                View.generateViewId(),
                                callbacks.setShowMetabolicGraph,
                            ),
                        )
                        addView(divider())
                        addView(settingsGroupLabel("IOB-GRAPH"))
                        addView(
                            choiceRow(
                                "IOB-Skalierung",
                                CgmGraphScaleMode.entries.map { mode ->
                                    Triple(graphScaleModeLabel(mode), preferences.iobGraphScaleMode == mode) {
                                        settingsViewModel.setString(DashboardUiPreferences.IOB_GRAPH_SCALE_MODE_KEY, mode.name)
                                    }
                                },
                            ),
                        )
                        addView(divider())
                        addView(
                            sugarliciousSliderRow(
                                title = "IOB Graph Maximum",
                                value = preferences.iobGraphMaximumUnits,
                                minimum = 1f,
                                maximum = 30f,
                                valueFormatter = { String.format(Locale.GERMANY, "%.1f U", it) },
                            ) { settingsViewModel.setFloat(DashboardUiPreferences.IOB_GRAPH_MAXIMUM_KEY, it) },
                        )
                        addView(divider())
                        addView(settingsGroupLabel("COB-GRAPH"))
                        addView(
                            choiceRow(
                                "COB-Skalierung",
                                CgmGraphScaleMode.entries.map { mode ->
                                    Triple(graphScaleModeLabel(mode), preferences.cobGraphScaleMode == mode) {
                                        settingsViewModel.setString(DashboardUiPreferences.COB_GRAPH_SCALE_MODE_KEY, mode.name)
                                    }
                                },
                            ),
                        )
                        addView(divider())
                        addView(
                            sugarliciousSliderRow(
                                title = "COB Graph Maximum",
                                value = preferences.cobGraphMaximumGrams,
                                minimum = 10f,
                                maximum = 500f,
                                valueFormatter = { String.format(Locale.GERMANY, "%.0f g", it) },
                            ) { settingsViewModel.setFloat(DashboardUiPreferences.COB_GRAPH_MAXIMUM_KEY, it) },
                        )
                        addView(divider())
                        addView(settingsGroupLabel("VORHERSAGEN"))
                        addView(
                            switchRowCompact("Vorhersagen", preferences.showCgmPredictions, View.generateViewId()) { enabled ->
                                settingsViewModel.setPredictions(enabled)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("IOB-Prognose", preferences.showCgmPredictionIob, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.prediction.iob", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("COB-Prognose", preferences.showCgmPredictionCob, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.prediction.cob", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("UAM-Prognose", preferences.showCgmPredictionUam, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.prediction.uam", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("ZeroTemp-Prognose", preferences.showCgmPredictionZeroTemp, View.generateViewId()) {
                                callbacks.setCgmStream("cgm.prediction.zeroTemp", it)
                            },
                        )
                        addView(divider())
                        addView(settingsGroupLabel("THERAPIE-MARKIERUNGEN"))
                        addView(
                            switchRowCompact("Mahlzeitenboli", preferences.showMealBolusMarkers, View.generateViewId()) {
                                callbacks.setCgmStream("treatment.mealBolus", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("Korrekturboli", preferences.showCorrectionMarkers, View.generateViewId()) {
                                callbacks.setCgmStream("treatment.correction", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "SMB",
                                preferences.showSmbMarkers,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("treatment.smb", it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact("Mahlzeiten-Carbs", preferences.showMealCarbMarkers, View.generateViewId()) {
                                callbacks.setCgmStream("treatment.mealCarbs", it)
                            },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "eCarbs",
                                preferences.showECarbMarkers,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("treatment.eCarbs", it) },
                        )
                        addView(divider())
                        val cloudEndpoint = CloudEndpointConfigurationStore.read(context)
                        addView(
                            actionRow(
                                "Nightscout Treatment-Anreicherung",
                                if (cloudEndpoint.enabled) "Aktiv" else "Aus",
                            ) { callbacks.openCloudEndpointTreatments() },
                        )
                    }
                },
                cardParams(top = 4),
            )
        }

        addSettingsCategory(parent, "appearance", "Farben & Darstellung", R.drawable.ic_settings) {
            colorContainer.visibility = View.VISIBLE
            addView(colorContainer, fullWidth())
        }

        addSettingsCategory(parent, "data", "Datenverwaltung", R.drawable.ic_health_activity) {
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("MOBILE → WEAR"))
                    addView(
                        actionRow("Übertragungsprotokoll", "Öffnen") {
                            context.startActivity(
                                android.content.Intent(context, DiagnosticActivity::class.java).putExtra(WEAR_TRANSFER_ONLY, true),
                            )
                        },
                    )
                    addView(helper("Wann, was und wie an Wear gesendet wurde. API-Annahme ist keine Empfangsbestätigung. Lokal, ohne Rohdaten.", 3))
                    addView(divider())
                    addView(settingsGroupLabel("HINTERGRUNDBETRIEB"))
                    addView(actionRow("Dauerbetrieb", AppRuntimeAccess.batteryLabel(context), callbacks.requestUnrestrictedBattery))
                    addView(
                        helper(
                            "Der sichtbare Sugarlicious-Dienst startet mit der App und nach einem Geräteneustart. Für zuverlässige Watch-Synchronisierung kann die Akku-Optimierung freigegeben werden.",
                            3,
                        ),
                    )
                    addView(divider())
                    addView(settingsGroupLabel("SICHERUNG"))
                    addView(actionRow("Einstellungen sichern", "Exportieren", callbacks.exportSettings))
                    addView(divider())
                    addView(actionRow("Einstellungen wiederherstellen", "Importieren", callbacks.importSettings))
                    addView(
                        helper(
                            "Die Sicherung enthält Darstellung, Verhalten und Watchface-/Complication-Auswahl; Sensorzugänge bleiben ausgeschlossen.",
                            3,
                        ),
                    )
                    addView(divider())
                    addView(settingsGroupLabel("HEALTH CONNECT"))
                    addView(
                        actionRow(
                            "Google Health Connect",
                            HealthConnectIntegration.statusLabel(context),
                        ) { callbacks.connectHealthConnect() },
                    )
                    addView(divider())
                    addView(actionRow("Gesundheitsdaten aktualisieren", "Synchronisieren") { callbacks.syncHealthConnect() })
                    addView(divider())
                    addView(actionRow("Zugriff verwalten", "Öffnen") { callbacks.manageHealthConnect() })
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.END
                            setPadding(0, 8.dp, 0, 4.dp)
                            addView(actionRow("Alle Health-Daten", null) { HealthConnectDataDialog.show(context) })
                        },
                    )
                    addView(helper(HealthConnectIntegration.detailLabel(context), 3))
                },
                cardParams(top = 4),
            )
        }

        addSettingsCategory(parent, "diagnostics", "Diagnose", R.drawable.ic_watch_status) {
            addView(
                tile(null).apply {
                    addView(settingsGroupLabel("LOKALE DIAGNOSE"))
                    val provenance =
                        state
                            ?.therapyEvents
                            .orEmpty()
                            .groupingBy { it.source }
                            .eachCount()
                    addView(
                        actionRow(
                            "Treatment-Quellen",
                            "AAPS ${provenance[app.sugarlicious.model.TherapyEventSource.AAPS_ONLY] ?: 0} · NS ${provenance[app.sugarlicious.model.TherapyEventSource.NIGHTSCOUT_ONLY] ?: 0} · ergänzt ${provenance[app.sugarlicious.model.TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT] ?: 0}",
                        ) {
                            callbacks.openCloudEndpointTreatments()
                        },
                    )
                    addView(divider())
                    addView(actionRow("Ereignisse & Fehlercodes", "Öffnen") { callbacks.openDiagnostics() })
                    addView(
                        helper(
                            "Lokale, begrenzte Ablaufdiagnose von Smartphone und Watch. Sensor- und Authentifizierungs-Rohdaten werden nicht exportiert.",
                            3,
                        ),
                    )
                },
                cardParams(top = 4),
            )
        }

        addSettingsCategory(parent, "about", "Über Sugarlicious", R.drawable.ic_foreground) {
            addView(aboutCard(), cardParams(top = 4, bottom = 10))
        }
    }

    private fun collapseSettingsSections() {
        settingsViewModel.collapse()
    }

    private fun aboutCard(): View =
        tile(null).apply {
            val header =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, 8.dp, 0, 8.dp)
                }
            header.addView(
                sugarliciousIconView(
                    context = context,
                    drawableRes = R.drawable.ic_foreground,
                    contentDescription = context.getString(R.string.brand_logo),
                    tintArgb = SugarliciousColors.argb(SugarliciousColorRole.BRAND_GREEN),
                ),
                LinearLayout.LayoutParams(56.dp, 56.dp),
            )
            header.addView(value("Sugarlicious", text, 20f, 1).apply { gravity = Gravity.CENTER })
            header.addView(helper("by FreDiabetics", 1).apply { gravity = Gravity.CENTER })
            header.addView(helper("Version ${app.sugarlicious.uishared.AppReleaseInfo.version(context)}", 1).apply { gravity = Gravity.CENTER })
            addView(header)
            addView(
                helper(
                    "Neu\n" +
                        app.sugarlicious.uishared.AppReleaseInfo
                            .changes(app.sugarlicious.uishared.AppReleaseInfo.Product.MOBILE)
                            .joinToString("\n") { "• $it" },
                    10,
                ),
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, 8.dp, 0, 1.dp)
                    addView(
                        aboutActionPill(R.drawable.ic_github, "GitHub", callbacks.openProjectGitHub).also { it.id = R.id.dashboard_github },
                        LinearLayout.LayoutParams(0, 44.dp, 1f).apply { marginEnd = 5.dp },
                    )
                    addView(
                        aboutActionPill(R.drawable.ic_mail, "E-Mail", callbacks.openContactEmail).also {
                            it.id =
                                R.id.dashboard_contact_email
                        },
                        LinearLayout.LayoutParams(0, 44.dp, 1f).apply { marginStart = 5.dp },
                    )
                },
            )
        }

    private fun aboutActionPill(
        icon: Int,
        label: String,
        action: () -> Unit,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        foreground = menuRipple()
        background =
            roundedBackground(
                SugarliciousColors.argb(SugarliciousColorRole.SURFACE_HIGH),
                999,
            )
        setOnClickListener { action() }
        addView(
            sugarliciousIconView(context, icon, label, tintArgb = accent),
            LinearLayout.LayoutParams(19.dp, 19.dp).apply { marginEnd = 7.dp },
        )
        addView(value(label, text, 13f, 1))
    }

    private fun addSettingsCategory(
        parent: LinearLayout,
        key: String,
        title: String,
        icon: Int,
        buildContent: LinearLayout.() -> Unit,
    ) {
        if (!settingsIsRoot) {
            if (settingsCategory == key) {
                parent.addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        buildContent()
                        separateSettingsObjectCards(this)
                        // The page header already reserves the gap above its first card.
                        (0 until childCount).map(::getChildAt).firstOrNull { it.isVisible }?.let { first ->
                            (first.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                                first.layoutParams = params.apply { topMargin = 0 }
                            }
                        }
                    },
                    fullWidth(),
                )
            }
            return
        }
        val header =
            LinearLayout(context).apply {
                tag = "settings-category-$key"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = 52.dp
                setPadding(0, 8.dp, 0, 8.dp)
                isClickable = true
                isFocusable = true
                foreground = menuRipple()
                addView(
                    settingsCategoryIcon(context, key, title)
                        ?: sugarliciousIconView(context, icon, title, tintArgb = accent),
                    LinearLayout.LayoutParams(27.dp, 27.dp).apply { marginEnd = 10.dp },
                )
                addView(value(title, text, 16f, 2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                setOnClickListener {
                    settingsViewModel.open(key, title)
                    onSettingsPageChanged()
                }
            }
        val groupKey =
            when (key) {
                "general", "glucose_ranges" -> "general_glucose"
                "overview_graphs", "appearance" -> "overview_appearance"
                "data", "diagnostics" -> "data_diagnostics"
                else -> key
            }
        val group =
            parent.findViewWithTag<LinearLayout>("settings-group-$groupKey")
                ?: tile(null).also {
                    it.tag = "settings-group-$groupKey"
                    it.setPadding(18.dp, 0, 18.dp, 0)
                    parent.addView(it, cardParams(top = 20))
                }
        if (group.isNotEmpty()) {
            group.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply { marginStart = 37.dp })
        }
        group.addView(header, fullWidth())
    }

    /** Keep existing controls and listeners intact while giving each object its own surface. */
    private fun separateSettingsObjectCards(container: LinearLayout) {
        val original = (0 until container.childCount).map { container.getChildAt(it) }
        original.filterIsInstance<LinearLayout>().forEach { child ->
            if (child.tag != "oneui_settings_card") {
                separateSettingsObjectCards(child)
                return@forEach
            }
            val controls = (0 until child.childCount).map { child.getChildAt(it) }
            if (controls.count { it.tag == "settings_snap_heading" } < 2) return@forEach
            val index = container.indexOfChild(child)
            container.removeView(child)
            child.removeAllViews()
            var nextIndex = index
            var card: LinearLayout? = null
            controls.forEach { control ->
                if (control.tag == "settings_snap_heading" || card == null) {
                    // A divider belongs between controls, never to the bottom of a card.
                    card?.let { previous ->
                        while (previous.isNotEmpty() && previous.getChildAt(previous.childCount - 1).javaClass == View::class.java) {
                            previous.removeViewAt(previous.childCount - 1)
                        }
                    }
                    card = tile(null)
                    container.addView(card, nextIndex++, cardParams(top = 12))
                }
                card.addView(control)
            }
        }
    }

    private data class SourceChoice(
        val label: String,
        val icon: Int,
        val selected: Boolean,
        val action: () -> Unit,
    )

    private fun sourceChoiceRow(items: List<SourceChoice>) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 5.dp, 0, 5.dp)
            items.forEach { item ->
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = 56.dp
                        setPadding(0, 5.dp, 0, 5.dp)
                        isClickable = true
                        isFocusable = true
                        foreground = menuRipple()
                        setOnClickListener { item.action() }
                        addView(
                            sugarliciousIconView(
                                context = context,
                                drawableRes = item.icon,
                                contentDescription = item.label,
                                tintArgb =
                                    if (item.icon == R.drawable.ic_sensor) {
                                        null
                                    } else {
                                        secondary
                                    },
                            ),
                            LinearLayout.LayoutParams(27.dp, 27.dp).apply { marginEnd = 9.dp },
                        )
                        addView(
                            value(item.label, text, 16f, 3),
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(
                            android.widget.RadioButton(mobileSettingsControlContext(context)).apply {
                                isChecked = item.selected
                                isClickable = false
                                isFocusable = false
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
                        )
                    },
                    LinearLayout
                        .LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = 6.dp },
                )
            }
        }

    private fun settingsGroupLabel(label: String) =
        TextView(context).apply {
            tag = "settings_snap_heading"
            text = label
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, 12.dp, 0, 8.dp)
        }

    private fun divider() =
        View(context).apply {
            setBackgroundColor(MobileSettingsStyle.divider(SugarliciousColors.palette.isLight))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp)
        }

    private fun choiceRow(
        title: String,
        items: List<Triple<String, Boolean, () -> Unit>>,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 5.dp, 0, 5.dp)
        addView(value(title, text, 16f, 3))
        addView(chipRow(items))
    }

    private fun actionRow(
        title: String,
        valueText: String?,
        action: () -> Unit,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 52.dp
        setPadding(0, 5.dp, 0, 5.dp)
        isClickable = true
        isFocusable = true
        foreground = menuRipple()
        setOnClickListener { action() }
        addView(value(title, text, 16f, 3), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        valueText?.let {
            addView(
                TextView(context).apply {
                    text = it
                    textSize = 13f
                    setTextColor(secondary)
                    gravity = Gravity.END
                    setPadding(8.dp, 0, 8.dp, 0)
                },
            )
        }
    }

    private fun statusRow(
        title: String,
        valueText: String,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 44.dp
        setPadding(0, 3.dp, 0, 3.dp)
        addView(value(title, text, 16f, 3), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            TextView(context).apply {
                text = valueText
                textSize = 13f
                setTextColor(secondary)
                gravity = Gravity.END
                setPadding(8.dp, 0, 0, 0)
            },
        )
    }

    private fun switchRowCompact(
        title: String,
        checked: Boolean,
        id: Int,
        callback: (Boolean) -> Unit,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 52.dp
        setPadding(0, 5.dp, 0, 5.dp)
        addView(value(title, text, 16f, 3), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            mobileSettingsSwitch(context, checked, callback).apply {
                this.id = id
            },
            LinearLayout.LayoutParams(64.dp, 48.dp),
        )
    }

    // This listener only arbitrates parent interception and returns false so
    // SeekBar retains its own click and accessibility handling.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun sugarliciousSliderRow(
        title: String,
        description: String? = null,
        value: Float,
        minimum: Float,
        maximum: Float,
        valueFormatter: (Float) -> String,
        callback: (Float) -> Unit,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = 72.dp
        setPadding(0, 12.dp, 0, 12.dp)
        val valueLabel =
            TextView(context).apply {
                textSize = 12f
                gravity = Gravity.END
                setTextColor(this@DashboardViewFactory.text)
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        val titleRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            this@DashboardViewFactory.value(title, text, 16f, 3).apply {
                                app.sugarlicious.uishared.SystemTypography
                                    .apply(this, false)
                            },
                        )
                        if (!description.isNullOrBlank()) {
                            addView(helper(description, 3).apply { textSize = 13f })
                        }
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(valueLabel, LinearLayout.LayoutParams(72.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        addView(titleRow)
        val steps = 1000

        fun progressToValue(progress: Int): Float = minimum + (maximum - minimum) * progress.toFloat() / steps.toFloat()

        fun valueToProgress(current: Float): Int = (((current.coerceIn(minimum, maximum) - minimum) / (maximum - minimum)) * steps).toInt().coerceIn(0, steps)
        valueLabel.text = valueFormatter(value)
        valueLabel.setOnClickListener {
            val input =
                android.widget.EditText(context).apply {
                    inputType =
                        android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    setText(String.format(Locale.ROOT, "%s", value))
                    setSelectAllOnFocus(true)
                }
            android.app.AlertDialog
                .Builder(context)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen") { _, _ ->
                    input.text
                        .toString()
                        .replace(',', '.')
                        .toFloatOrNull()
                        ?.coerceIn(minimum, maximum)
                        ?.let(callback)
                }.show()
        }
        addView(
            OneUiSeekBar(mobileSettingsControlContext(context), showTicks = false).apply {
                max = steps
                progress = valueToProgress(value)
                palette(accent, MobileSettingsStyle.card(SugarliciousColors.palette.isLight))
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        private var currentValue = value.coerceIn(minimum, maximum)
                        private val interaction =
                            SettingsSliderInteraction(
                                { currentValue = progressToValue(it) },
                                { callback(currentValue) },
                            )

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            valueLabel.text = valueFormatter(progressToValue(progress))
                            interaction.changed(progress, fromUser)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = interaction.started()

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = interaction.stopped()
                    },
                )
            },
            fullWidth(),
        )
    }

    private fun chipRow(items: List<Triple<String, Boolean, () -> Unit>>) =
        LinearLayout(context).apply {
            val choices = items.map { (label, selected, click) -> chip(label, selected, click) }
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            choices.forEach { it.measure(unspecified, unspecified) }
            val horizontal = choices.sumOf { it.measuredWidth + 7.dp } <= context.resources.configuration.screenWidthDp.dp - 72.dp
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(0, 7.dp, 0, 4.dp)
            choices.forEach { choice ->
                addView(
                    choice,
                    if (horizontal) {
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    } else {
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    },
                )
            }
        }

    private fun chip(
        label: String,
        selected: Boolean,
        click: () -> Unit,
    ) = android.widget.RadioButton(mobileSettingsControlContext(context)).apply {
        text = label
        textSize = 14f
        minHeight = 48.dp
        setTextColor(this@DashboardViewFactory.text)
        isChecked = selected
        setOnClickListener { click() }
    }

    private fun composeSettingsControl(content: @androidx.compose.runtime.Composable () -> Unit) =
        androidx.compose.ui.platform.ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val owner = context as? androidx.activity.result.ActivityResultRegistryOwner
                if (owner != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.activity.compose.LocalActivityResultRegistryOwner provides owner,
                    ) {
                        OneUiWidgetSettingsTheme(dark = !SugarliciousColors.palette.isLight, content = content)
                    }
                } else {
                    OneUiWidgetSettingsTheme(dark = !SugarliciousColors.palette.isLight, content = content)
                }
            }
        }

    private fun tile(title: String?): LinearLayout =
        LinearLayout(context).apply {
            tag = "oneui_settings_card"
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 8.dp, 18.dp, 8.dp)
            background =
                roundedBackground(
                    MobileSettingsStyle.card(SugarliciousColors.palette.isLight),
                    28,
                )
            clipToOutline = true
            title?.let { addView(sectionLabel(it)) }
        }

    private fun menuRipple() =
        android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList
                .valueOf(
                    androidx.core.graphics.ColorUtils
                        .setAlphaComponent(accent, 0x24),
                ),
            null,
            null,
        )

    private fun roundedBackground(
        fillColor: Int,
        radiusDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dp.toFloat()
            setColor(fillColor)
        }

    private fun screenTitle(title: String = "Einstellungen") =
        value(title, text, 34f, 2).apply {
            text = brandedSettingsTitle(title)
            gravity = Gravity.CENTER
            minimumHeight = minOf(250, (context.resources.configuration.screenHeightDp * .28f).toInt()).dp
        }

    private fun sectionLabel(label: String) =
        TextView(context).apply {
            tag = "settings_snap_heading"
            text = label
            textSize = 12f
            setTextColor(this@DashboardViewFactory.text)
            letterSpacing = 0.03f
        }

    private fun value(
        value: String,
        color: Int,
        size: Float,
        maxLines: Int = 2,
    ) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        this.maxLines = maxLines
        if (maxLines == 1) ellipsize = TextUtils.TruncateAt.END
    }

    private fun helper(
        value: String,
        maxLines: Int = 2,
        color: Int = secondary,
    ) = TextView(context).apply {
        text = value
        textSize = 13f
        setTextColor(color)
        this.maxLines = maxLines
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardParams(
        top: Int = 6,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top.dp
        bottomMargin = bottom.dp
    }

    private val Int.dp: Int get() = (this * density).toInt()
}
