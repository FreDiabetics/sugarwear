package app.sugarlicious.wear

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.SettingsSchemaVersions
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.protocol.WatchAppearanceProfile
import app.sugarlicious.protocol.WatchColorSync
import app.sugarlicious.protocol.WatchConfig
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.protocol.WatchGlucoseUnit
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.protocol.WatchGraphStyle
import app.sugarlicious.protocol.WatchUiColors
import app.sugarlicious.storage.TrendArrowStylePreferences
import app.sugarlicious.storage.ensureSettingsSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun shouldApplyCgmCollectorSourceTransition(
    previous: WatchDataSource,
    current: WatchDataSource,
): Boolean = previous != current

internal data class WearDisplayPreferences(
    val graphHours: Int = 6,
    val showPredictions: Boolean = false,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val dataSource: WatchDataSource = WatchDataSource.PHONE,
    val showTherapyStats: Boolean = true,
    val syncedAtEpochMs: Long = 0L,
    val graphColors: WatchGraphColors = WatchGraphColors(),
    val graphStyle: WatchGraphStyle = WatchGraphStyle(),
    val uiColors: WatchUiColors = WatchUiColors(),
    val cgmThresholds: CgmThresholds = CgmThresholds.DEFAULT,
    val glucoseScalePercent: Int = GlucoseTrendSizing.DEFAULT_SCALE_PERCENT,
    val trendScalePercent: Int = GlucoseTrendSizing.DEFAULT_SCALE_PERCENT,
    val trendArrowStyle: TrendArrowStyle = TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt()),
) {
    companion object {
        const val PREFS = "watch_display"
        private const val KEY_GRAPH_HOURS = "graph_hours"
        private const val KEY_SHOW_PREDICTIONS = "show_predictions"
        private const val KEY_GLUCOSE_UNIT = "glucose_unit"
        private const val KEY_DATA_SOURCE = "data_source"
        private const val KEY_SHOW_THERAPY_STATS = "show_therapy_stats"
        private const val KEY_GLUCOSE_SCALE = "glucose_scale_percent"
        private const val KEY_TREND_SCALE = "trend_scale_percent"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_LOCAL_CUSTOMIZED = "local_customized"
        private const val KEY_ACTIVE_APPEARANCE_MODE = "active_appearance_mode"
        private const val COLOR_PREFIX = "graph_color_"
        private const val UI_PREFIX = "ui_color_"
        private const val STYLE_DOT_RADIUS = "cgm_dot_radius_dp"
        private const val STYLE_OUTLINE_ENABLED = "cgm_dot_outline_enabled"
        private const val STYLE_HISTORICAL_OUTLINE_ENABLED = "cgm_historical_dot_outline_enabled"
        private const val STYLE_CURRENT_OUTLINE_ENABLED = "cgm_current_dot_outline_enabled"
        private const val STYLE_OUTLINE_WIDTH = "cgm_dot_outline_width_dp"
        private const val STYLE_HISTORICAL_OUTLINE_WIDTH = "cgm_historical_dot_outline_width_dp"
        private const val STYLE_CURRENT_OUTLINE_WIDTH = "cgm_current_dot_outline_width_dp"
        private const val STYLE_SCALE_LANE_OPACITY = "scale_lane_opacity_percent"
        private const val STYLE_BORDER_ENABLED = "graph_border_enabled"
        private const val THRESHOLD_VERY_HIGH = "threshold_very_high"
        private const val THRESHOLD_HIGH = "threshold_high"
        private const val THRESHOLD_LOW = "threshold_low"
        private const val THRESHOLD_VERY_LOW = "threshold_very_low"
        val allowedGraphHours = listOf(1, 2, 3, 6, 12, 24)

        fun activeAppearanceMode(context: Context): AppearanceMode {
            val stored =
                context
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ACTIVE_APPEARANCE_MODE, null)
                    ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
            // Wear surfaces are dark by default. The explicit in-app selector is authoritative;
            // DeviceVendor's transient system uiMode must not silently pin the app to the light profile.
            return stored ?: AppearanceMode.DARK
        }

        fun setActiveAppearanceMode(
            context: Context,
            mode: AppearanceMode,
        ) {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_ACTIVE_APPEARANCE_MODE, mode.name)
                }
        }

        private fun appearancePrefix(mode: AppearanceMode) = "appearance.${mode.storageKey}."

        fun read(context: Context): WearDisplayPreferences = read(context, activeAppearanceMode(context))

        fun resetTrendArrowStyle(
            context: Context,
            mode: AppearanceMode,
        ) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            TrendArrowStylePreferences.reset(preferences, mode)
            preferences.edit { remove(KEY_TREND_SCALE) }
        }

        fun read(
            context: Context,
            mode: AppearanceMode,
        ): WearDisplayPreferences {
            val preferences =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )
            preferences.ensureSettingsSchema(SettingsSchemaVersions.WEAR)
            migrateAppearanceProfiles(preferences)
            val prefix = appearancePrefix(mode)
            val graphDefaults = WatchGraphColors()
            val styleDefaults = WatchGraphStyle()
            val uiDefaults = WatchUiColors()

            val unit =
                runCatching {
                    WatchGlucoseUnit.valueOf(
                        preferences.getString(
                            KEY_GLUCOSE_UNIT,
                            WatchGlucoseUnit.AAPS.name,
                        ) ?: WatchGlucoseUnit.AAPS.name,
                    )
                }.getOrDefault(WatchGlucoseUnit.AAPS)

            val trendStyle =
                TrendArrowStylePreferences.read(
                    preferences,
                    mode,
                    0xFFFFFFFF.toInt(),
                    legacyScaleKey = KEY_TREND_SCALE,
                )
            return WearDisplayPreferences(
                graphHours =
                    preferences
                        .getInt(KEY_GRAPH_HOURS, 6)
                        .takeIf { it in allowedGraphHours }
                        ?: 6,
                showPredictions =
                    preferences.getBoolean(KEY_SHOW_PREDICTIONS, false),
                glucoseUnit = unit,
                // Sugarlicious Wear is AndroidAPS/phone-fed. Direct sensor collection belongs to
                // the separate SugarWear app, so legacy Automatic/Direct values must not revive
                // a second CGM stream after a reboot or package update.
                dataSource = WatchDataSource.PHONE,
                showTherapyStats =
                    preferences.getBoolean(KEY_SHOW_THERAPY_STATS, true),
                glucoseScalePercent =
                    preferences
                        .getInt(KEY_GLUCOSE_SCALE, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
                        .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
                trendScalePercent =
                    preferences
                        .getInt(KEY_TREND_SCALE, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
                        .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
                trendArrowStyle = trendStyle,
                syncedAtEpochMs =
                    preferences.getLong(KEY_SYNCED_AT, 0L),
                graphColors =
                    WatchGraphColors(
                        graphBackground = preferences.getInt(prefix + COLOR_PREFIX + "background", graphDefaults.graphBackground),
                        rangeLow = preferences.getInt(prefix + COLOR_PREFIX + "range_low", graphDefaults.rangeLow),
                        rangeInRange = preferences.getInt(prefix + COLOR_PREFIX + "range_in", graphDefaults.rangeInRange),
                        rangeHigh = preferences.getInt(prefix + COLOR_PREFIX + "range_high", graphDefaults.rangeHigh),
                        cgmLow = preferences.getInt(prefix + COLOR_PREFIX + "cgm_low", graphDefaults.cgmLow),
                        cgmInRange = preferences.getInt(prefix + COLOR_PREFIX + "cgm_in", graphDefaults.cgmInRange),
                        cgmHigh = preferences.getInt(prefix + COLOR_PREFIX + "cgm_high", graphDefaults.cgmHigh),
                        cgmVeryLow = preferences.getInt(prefix + COLOR_PREFIX + "cgm_very_low", graphDefaults.cgmVeryLow),
                        cgmVeryHigh = preferences.getInt(prefix + COLOR_PREFIX + "cgm_very_high", graphDefaults.cgmVeryHigh),
                        divider = preferences.getInt(prefix + COLOR_PREFIX + "divider", graphDefaults.divider),
                        border = preferences.getInt(prefix + COLOR_PREFIX + "border", graphDefaults.border),
                        highLine = preferences.getInt(prefix + COLOR_PREFIX + "high_line", graphDefaults.highLine),
                        lowLine = preferences.getInt(prefix + COLOR_PREFIX + "low_line", graphDefaults.lowLine),
                        axisLabel = preferences.getInt(prefix + COLOR_PREFIX + "axis_label", graphDefaults.axisLabel),
                        axisTick = preferences.getInt(prefix + COLOR_PREFIX + "axis_tick", graphDefaults.axisTick),
                        nowLine = preferences.getInt(prefix + COLOR_PREFIX + "now_line", graphDefaults.nowLine),
                        outline = preferences.getInt(prefix + COLOR_PREFIX + "outline", graphDefaults.outline),
                        predictionIob = preferences.getInt(prefix + COLOR_PREFIX + "prediction_iob", graphDefaults.predictionIob),
                        predictionCob = preferences.getInt(prefix + COLOR_PREFIX + "prediction_cob", graphDefaults.predictionCob),
                        predictionUam = preferences.getInt(prefix + COLOR_PREFIX + "prediction_uam", graphDefaults.predictionUam),
                        predictionZeroTemp =
                            preferences.getInt(
                                prefix + COLOR_PREFIX + "prediction_zero_temp",
                                graphDefaults.predictionZeroTemp,
                            ),
                        targetValue = preferences.getInt(prefix + COLOR_PREFIX + "target_value", graphDefaults.targetValue),
                        signalLoss = preferences.getInt(prefix + COLOR_PREFIX + "signal_loss", graphDefaults.signalLoss),
                    ),
                graphStyle =
                    WatchGraphStyle(
                        cgmDotRadiusDp =
                            preferences
                                .getFloat(prefix + STYLE_DOT_RADIUS, styleDefaults.cgmDotRadiusDp)
                                .coerceIn(1.5f, 6.0f),
                        cgmDotOutlineEnabled =
                            preferences.getBoolean(prefix + STYLE_OUTLINE_ENABLED, false),
                        cgmHistoricalDotOutlineEnabled =
                            preferences.getBoolean(
                                prefix + STYLE_HISTORICAL_OUTLINE_ENABLED,
                                preferences.getBoolean(prefix + STYLE_OUTLINE_ENABLED, false),
                            ),
                        cgmCurrentDotOutlineEnabled = true,
                        cgmDotOutlineWidthDp =
                            preferences
                                .getFloat(prefix + STYLE_OUTLINE_WIDTH, styleDefaults.cgmDotOutlineWidthDp)
                                .coerceIn(0.25f, 3.0f),
                        cgmHistoricalDotOutlineWidthDp =
                            preferences
                                .getFloat(
                                    prefix + STYLE_HISTORICAL_OUTLINE_WIDTH,
                                    preferences.getFloat(prefix + STYLE_OUTLINE_WIDTH, styleDefaults.cgmDotOutlineWidthDp),
                                ).coerceIn(0.25f, 3.0f),
                        cgmCurrentDotOutlineWidthDp =
                            preferences
                                .getFloat(
                                    prefix + STYLE_CURRENT_OUTLINE_WIDTH,
                                    preferences.getFloat(prefix + STYLE_OUTLINE_WIDTH, styleDefaults.cgmDotOutlineWidthDp),
                                ).coerceIn(0.25f, 3.0f),
                        scaleLaneOpacityPercent =
                            preferences
                                .getInt(
                                    prefix + STYLE_SCALE_LANE_OPACITY,
                                    styleDefaults.scaleLaneOpacityPercent,
                                ).coerceIn(0, 100),
                        borderEnabled = preferences.getBoolean(prefix + STYLE_BORDER_ENABLED, styleDefaults.borderEnabled),
                    ),
                uiColors =
                    WatchUiColors(
                        background = preferences.getInt(prefix + UI_PREFIX + "background", uiDefaults.background),
                        tileBackground = preferences.getInt(prefix + UI_PREFIX + "tile_background", uiDefaults.tileBackground),
                        tileBorder = preferences.getInt(prefix + UI_PREFIX + "tile_border", uiDefaults.tileBorder),
                        textPrimary = preferences.getInt(prefix + UI_PREFIX + "text_primary", uiDefaults.textPrimary),
                        textSecondary = preferences.getInt(prefix + UI_PREFIX + "text_secondary", uiDefaults.textSecondary),
                        deltaUnit = preferences.getInt(prefix + UI_PREFIX + "delta_unit", uiDefaults.deltaUnit),
                        accent = preferences.getInt(prefix + UI_PREFIX + "accent", uiDefaults.accent),
                        glucoseLow = preferences.getInt(prefix + UI_PREFIX + "glucose_low", uiDefaults.glucoseLow),
                        glucoseInRange = preferences.getInt(prefix + UI_PREFIX + "glucose_in_range", uiDefaults.glucoseInRange),
                        glucoseHigh = preferences.getInt(prefix + UI_PREFIX + "glucose_high", uiDefaults.glucoseHigh),
                        glucoseVeryLow = preferences.getInt(prefix + UI_PREFIX + "glucose_very_low", uiDefaults.glucoseVeryLow),
                        glucoseVeryHigh = preferences.getInt(prefix + UI_PREFIX + "glucose_very_high", uiDefaults.glucoseVeryHigh),
                        glucoseBarLow = preferences.getInt(prefix + UI_PREFIX + "glucose_bar_low", uiDefaults.glucoseBarLow),
                        glucoseBarTarget = preferences.getInt(prefix + UI_PREFIX + "glucose_bar_target", uiDefaults.glucoseBarTarget),
                        glucoseBarHigh = preferences.getInt(prefix + UI_PREFIX + "glucose_bar_high", uiDefaults.glucoseBarHigh),
                        glucoseBarUnderlay = preferences.getInt(prefix + UI_PREFIX + "glucose_bar_underlay", uiDefaults.glucoseBarUnderlay),
                        iob = preferences.getInt(prefix + UI_PREFIX + "iob", uiDefaults.iob),
                        cob = preferences.getInt(prefix + UI_PREFIX + "cob", uiDefaults.cob),
                        basal = preferences.getInt(prefix + UI_PREFIX + "basal", uiDefaults.basal),
                    ),
                cgmThresholds =
                    CgmThresholds(
                        veryHighMgDl = preferences.getFloat(THRESHOLD_VERY_HIGH, 250f).toDouble(),
                        highMgDl = preferences.getFloat(THRESHOLD_HIGH, 180f).toDouble(),
                        lowMgDl = preferences.getFloat(THRESHOLD_LOW, 70f).toDouble(),
                        veryLowMgDl = preferences.getFloat(THRESHOLD_VERY_LOW, 50f).toDouble(),
                    ).takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT,
            )
        }

        /**
         * Records operational sync metadata without changing this app's display settings.
         *
         * Wear appearance, graph scale, units and visibility options are independent local state.
         * A phone may still provide data and select the active source, but routine synchronization
         * must never act as a remote appearance editor.
         */
        fun save(
            context: Context,
            config: WatchConfig,
        ) {
            val previousSource = read(context).dataSource
            val preferences =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )
            val syncedAt =
                config.sentAtEpochMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis()

            preferences
                .edit {
                    putLong(KEY_SYNCED_AT, syncedAt)
                        .putString(KEY_DATA_SOURCE, WatchDataSource.PHONE.name)
                }
            notifyCgmCollectorSourceTransition(context, previousSource, WatchDataSource.PHONE)
        }

        fun saveLocal(
            context: Context,
            value: WearDisplayPreferences,
        ) = saveLocal(context, activeAppearanceMode(context), value)

        fun saveLocal(
            context: Context,
            mode: AppearanceMode,
            value: WearDisplayPreferences,
        ) {
            write(
                context = context,
                mode = mode,
                value = value,
                markLocal = true,
            )
        }

        /** Explicit Mobile action: graph colors are authoritative even after local Watch edits. */
        fun applySyncedColors(
            context: Context,
            sync: WatchColorSync,
        ) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val fallback = WatchAppearanceProfile(graphColors = sync.graphColors)
            preferences
                .edit {
                    putLong(KEY_SYNCED_AT, sync.sentAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis())
                    putAppearanceProfile(AppearanceMode.LIGHT, sync.lightProfile ?: fallback)
                    putAppearanceProfile(AppearanceMode.DARK, sync.darkProfile ?: fallback)
                }
        }

        private fun notifyCgmCollectorSourceTransition(
            context: Context,
            previous: WatchDataSource,
            current: WatchDataSource,
        ) {
            if (!shouldApplyCgmCollectorSourceTransition(previous, current)) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                resolveAndPublishCurrentCgmAlertMode(context.applicationContext, current)
            }
        }

        private fun write(
            context: Context,
            mode: AppearanceMode,
            value: WearDisplayPreferences,
            markLocal: Boolean,
        ) {
            val graphHours =
                value.graphHours.takeIf { it in allowedGraphHours } ?: 3
            val style =
                value.graphStyle.copy(
                    cgmDotRadiusDp = value.graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f),
                    cgmDotOutlineWidthDp = value.graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f),
                )

            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit {
                    putInt(KEY_GRAPH_HOURS, graphHours)
                    putBoolean(KEY_SHOW_PREDICTIONS, value.showPredictions)
                    putString(KEY_GLUCOSE_UNIT, value.glucoseUnit.name)
                    putString(KEY_DATA_SOURCE, WatchDataSource.PHONE.name)
                    putBoolean(KEY_SHOW_THERAPY_STATS, value.showTherapyStats)
                    putInt(
                        KEY_GLUCOSE_SCALE,
                        value.glucoseScalePercent.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
                    )
                    putInt(
                        KEY_TREND_SCALE,
                        value.trendScalePercent.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
                    )
                    putLong(KEY_SYNCED_AT, value.syncedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis())
                    putBoolean(KEY_LOCAL_CUSTOMIZED, markLocal)
                    putFloat(THRESHOLD_VERY_HIGH, value.cgmThresholds.veryHighMgDl.toFloat())
                    putFloat(THRESHOLD_HIGH, value.cgmThresholds.highMgDl.toFloat())
                    putFloat(THRESHOLD_LOW, value.cgmThresholds.lowMgDl.toFloat())
                    putFloat(THRESHOLD_VERY_LOW, value.cgmThresholds.veryLowMgDl.toFloat())
                    putAppearanceProfile(mode, WatchAppearanceProfile(value.graphColors, style, value.uiColors))
                }
            TrendArrowStylePreferences.write(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                mode,
                value.trendArrowStyle.copy(sizePercent = value.trendScalePercent),
            )
        }

        private fun android.content.SharedPreferences.Editor.putAppearanceProfile(
            mode: AppearanceMode,
            profile: WatchAppearanceProfile,
        ) {
            val prefix = appearancePrefix(mode)
            val colors = profile.graphColors
            putInt(prefix + COLOR_PREFIX + "background", colors.graphBackground)
            putInt(prefix + COLOR_PREFIX + "range_low", colors.rangeLow)
            putInt(prefix + COLOR_PREFIX + "range_in", colors.rangeInRange)
            putInt(prefix + COLOR_PREFIX + "range_high", colors.rangeHigh)
            putInt(prefix + COLOR_PREFIX + "cgm_low", colors.cgmLow)
            putInt(prefix + COLOR_PREFIX + "cgm_in", colors.cgmInRange)
            putInt(prefix + COLOR_PREFIX + "cgm_high", colors.cgmHigh)
            putInt(prefix + COLOR_PREFIX + "cgm_very_low", colors.cgmVeryLow)
            putInt(prefix + COLOR_PREFIX + "cgm_very_high", colors.cgmVeryHigh)
            putInt(prefix + COLOR_PREFIX + "divider", colors.divider)
            putInt(prefix + COLOR_PREFIX + "border", colors.border)
            putInt(prefix + COLOR_PREFIX + "high_line", colors.highLine)
            putInt(prefix + COLOR_PREFIX + "low_line", colors.lowLine)
            putInt(prefix + COLOR_PREFIX + "axis_label", colors.axisLabel)
            putInt(prefix + COLOR_PREFIX + "axis_tick", colors.axisTick)
            putInt(prefix + COLOR_PREFIX + "now_line", colors.nowLine)
            putInt(prefix + COLOR_PREFIX + "outline", colors.outline)
            putInt(prefix + COLOR_PREFIX + "prediction_iob", colors.predictionIob)
            putInt(prefix + COLOR_PREFIX + "prediction_cob", colors.predictionCob)
            putInt(prefix + COLOR_PREFIX + "prediction_uam", colors.predictionUam)
            putInt(prefix + COLOR_PREFIX + "prediction_zero_temp", colors.predictionZeroTemp)
            putInt(prefix + COLOR_PREFIX + "target_value", colors.targetValue)
            putInt(prefix + COLOR_PREFIX + "signal_loss", colors.signalLoss)
            putFloat(prefix + STYLE_DOT_RADIUS, profile.graphStyle.cgmDotRadiusDp)
            putBoolean(prefix + STYLE_OUTLINE_ENABLED, profile.graphStyle.cgmDotOutlineEnabled)
            putBoolean(prefix + STYLE_HISTORICAL_OUTLINE_ENABLED, profile.graphStyle.cgmHistoricalDotOutlineEnabled)
            putBoolean(prefix + STYLE_CURRENT_OUTLINE_ENABLED, true)
            putFloat(prefix + STYLE_OUTLINE_WIDTH, profile.graphStyle.cgmDotOutlineWidthDp)
            putFloat(prefix + STYLE_HISTORICAL_OUTLINE_WIDTH, profile.graphStyle.cgmHistoricalDotOutlineWidthDp)
            putFloat(prefix + STYLE_CURRENT_OUTLINE_WIDTH, profile.graphStyle.cgmCurrentDotOutlineWidthDp)
            putInt(prefix + STYLE_SCALE_LANE_OPACITY, profile.graphStyle.scaleLaneOpacityPercent.coerceIn(0, 100))
            putBoolean(prefix + STYLE_BORDER_ENABLED, profile.graphStyle.borderEnabled)
            val ui = profile.uiColors
            putInt(prefix + UI_PREFIX + "background", ui.background)
            putInt(prefix + UI_PREFIX + "tile_background", ui.tileBackground)
            putInt(prefix + UI_PREFIX + "tile_border", ui.tileBorder)
            putInt(prefix + UI_PREFIX + "text_primary", ui.textPrimary)
            putInt(prefix + UI_PREFIX + "text_secondary", ui.textSecondary)
            putInt(prefix + UI_PREFIX + "delta_unit", ui.deltaUnit)
            putInt(prefix + UI_PREFIX + "accent", ui.accent)
            putInt(prefix + UI_PREFIX + "glucose_low", ui.glucoseLow)
            putInt(prefix + UI_PREFIX + "glucose_in_range", ui.glucoseInRange)
            putInt(prefix + UI_PREFIX + "glucose_high", ui.glucoseHigh)
            putInt(prefix + UI_PREFIX + "glucose_very_low", ui.glucoseVeryLow)
            putInt(prefix + UI_PREFIX + "glucose_very_high", ui.glucoseVeryHigh)
            putInt(prefix + UI_PREFIX + "glucose_bar_low", ui.glucoseBarLow)
            putInt(prefix + UI_PREFIX + "glucose_bar_target", ui.glucoseBarTarget)
            putInt(prefix + UI_PREFIX + "glucose_bar_high", ui.glucoseBarHigh)
            putInt(prefix + UI_PREFIX + "glucose_bar_underlay", ui.glucoseBarUnderlay)
            putInt(prefix + UI_PREFIX + "iob", ui.iob)
            putInt(prefix + UI_PREFIX + "cob", ui.cob)
            putInt(prefix + UI_PREFIX + "basal", ui.basal)
        }

        private fun migrateAppearanceProfiles(preferences: android.content.SharedPreferences) {
            if (preferences.getBoolean("appearance_profiles_v1", false)) return
            val prefixes = listOf(COLOR_PREFIX, UI_PREFIX)
            preferences
                .edit {
                    preferences.all.forEach { (key, raw) ->
                        if (prefixes.none(key::startsWith) &&
                            key !in
                            setOf(
                                STYLE_DOT_RADIUS,
                                STYLE_OUTLINE_ENABLED,
                                STYLE_HISTORICAL_OUTLINE_ENABLED,
                                STYLE_CURRENT_OUTLINE_ENABLED,
                                STYLE_OUTLINE_WIDTH,
                                STYLE_HISTORICAL_OUTLINE_WIDTH,
                                STYLE_CURRENT_OUTLINE_WIDTH,
                                STYLE_BORDER_ENABLED,
                            )
                        ) {
                            return@forEach
                        }
                        AppearanceMode.entries.forEach { mode ->
                            val target = appearancePrefix(mode) + key
                            if (preferences.contains(target)) return@forEach
                            when (raw) {
                                is Int -> putInt(target, raw)
                                is Float -> putFloat(target, raw)
                                is Boolean -> putBoolean(target, raw)
                            }
                        }
                    }
                    putBoolean("appearance_profiles_v1", true)
                }
        }
    }
}
