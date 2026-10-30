package app.aapswear.g7watch

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.aapswear.model.AppearanceMode
import app.aapswear.model.AppearanceTerminology
import app.aapswear.model.GlucoseTrendSizing
import app.aapswear.model.SettingsSchemaVersions
import app.aapswear.model.TrendArrowStyle
import app.aapswear.storage.TrendArrowStylePreferences
import app.aapswear.storage.ensureSettingsSchema
import app.aapswear.uishared.DirectToWatchGraphDefaults
import app.aapswear.uishared.SharedWearCgmGraphStyle

enum class G7AppearanceSection(
    val label: String,
) {
    MENU("Menü"),
    GLUCOSE("Zuckerwert"),
    GRAPH("Graph"),
}

enum class G7AppearanceRole(
    val key: String,
    val label: String,
    val section: G7AppearanceSection,
    val defaultArgb: Int,
    val lightArgb: Int = defaultArgb,
) {
    MENU_BACKGROUND(
        "menu_background",
        AppearanceTerminology.APP_BACKGROUND,
        G7AppearanceSection.MENU,
        0xFF000000.toInt(),
        0xFF000000.toInt(),
    ),
    MENU_SURFACE(
        "menu_surface",
        AppearanceTerminology.SURFACE_BACKGROUND,
        G7AppearanceSection.MENU,
        0xFF242424.toInt(),
        0xFFFFFFFF.toInt(),
    ),
    MENU_BORDER("menu_border", AppearanceTerminology.SURFACE_BORDER, G7AppearanceSection.MENU, 0, 0),
    MENU_TEXT_PRIMARY(
        "menu_text_primary",
        AppearanceTerminology.PRIMARY_TEXT,
        G7AppearanceSection.MENU,
        0xFFF5F5F5.toInt(),
        0xFF252525.toInt(),
    ),
    MENU_TEXT_SECONDARY(
        "menu_text_secondary",
        AppearanceTerminology.SECONDARY_TEXT,
        G7AppearanceSection.MENU,
        0xFFB5B5B5.toInt(),
        0xFF666666.toInt(),
    ),
    MENU_PRIMARY("menu_primary", "Primär / Sugarlicious", G7AppearanceSection.MENU, 0xFF22EB60.toInt()),

    GLUCOSE_LOW("glucose_low", AppearanceTerminology.GLUCOSE_LOW, G7AppearanceSection.GLUCOSE, 0xFFFF5C69.toInt()),
    GLUCOSE_VERY_LOW("glucose_very_low", AppearanceTerminology.GLUCOSE_VERY_LOW, G7AppearanceSection.GLUCOSE, 0xFFFF5C69.toInt()),
    GLUCOSE_IN_RANGE(
        "glucose_in_range",
        AppearanceTerminology.GLUCOSE_IN_RANGE,
        G7AppearanceSection.GLUCOSE,
        0xFFFFFFFF.toInt(),
        0xFF202020.toInt(),
    ),
    GLUCOSE_HIGH("glucose_high", AppearanceTerminology.GLUCOSE_HIGH, G7AppearanceSection.GLUCOSE, 0xFFFFD040.toInt()),
    GLUCOSE_VERY_HIGH("glucose_very_high", AppearanceTerminology.GLUCOSE_VERY_HIGH, G7AppearanceSection.GLUCOSE, 0xFFFFD040.toInt()),
    GLUCOSE_TREND("glucose_trend", AppearanceTerminology.TREND_ARROW, G7AppearanceSection.GLUCOSE, 0xFFFFFFFF.toInt()),
    GLUCOSE_DELTA("glucose_delta", AppearanceTerminology.DELTA_UNIT, G7AppearanceSection.GLUCOSE, 0xFFB5B5B5.toInt(), 0xFF666666.toInt()),
    GLUCOSE_DELAYED("glucose_delayed", "DELAYED", G7AppearanceSection.GLUCOSE, 0xFFF4DE00.toInt()),
    GLUCOSE_STALE("glucose_stale", "STALE", G7AppearanceSection.GLUCOSE, 0xFFFF9D18.toInt()),
    GLUCOSE_NO_SOURCE("glucose_no_source", "NO_SOURCE", G7AppearanceSection.GLUCOSE, 0xFF969696.toInt()),
    GLUCOSE_ERROR("glucose_error", "ERROR", G7AppearanceSection.GLUCOSE, 0xFFFF5C69.toInt()),

    GRAPH_BACKGROUND(
        "graph_background",
        AppearanceTerminology.GRAPH_BACKGROUND,
        G7AppearanceSection.GRAPH,
        0xFF202020.toInt(),
        0xFFFFFFFF.toInt(),
    ),
    GRAPH_TARGET_AREA("graph_target_area", AppearanceTerminology.GRAPH_TARGET_AREA, G7AppearanceSection.GRAPH, 0x665C5C5C),
    GRAPH_HIGH_AREA("graph_high_area", AppearanceTerminology.GRAPH_HIGH_AREA, G7AppearanceSection.GRAPH, 0x45FFD040),
    GRAPH_LOW_AREA("graph_low_area", AppearanceTerminology.GRAPH_LOW_AREA, G7AppearanceSection.GRAPH, 0x45FF5C69),
    GRAPH_HIGH_LINE("graph_high_line", AppearanceTerminology.GRAPH_HIGH_LINE, G7AppearanceSection.GRAPH, 0xFFFFD040.toInt()),
    GRAPH_LOW_LINE("graph_low_line", AppearanceTerminology.GRAPH_LOW_LINE, G7AppearanceSection.GRAPH, 0xFFFF5C69.toInt()),
    GRAPH_DOT_HIGH("graph_dot_high", AppearanceTerminology.GRAPH_DOT_HIGH, G7AppearanceSection.GRAPH, 0xFFFFD040.toInt()),
    GRAPH_DOT_IN_RANGE(
        "graph_dot_in_range",
        AppearanceTerminology.GRAPH_DOT_IN_RANGE,
        G7AppearanceSection.GRAPH,
        0xFFFFFFFF.toInt(),
        0xFF202020.toInt(),
    ),
    GRAPH_DOT_LOW("graph_dot_low", AppearanceTerminology.GRAPH_DOT_LOW, G7AppearanceSection.GRAPH, 0xFFFF5C69.toInt()),
    GRAPH_DOT_OUTLINE("graph_dot_outline", AppearanceTerminology.GRAPH_DOT_OUTLINE, G7AppearanceSection.GRAPH, 0xFF000000.toInt()),
    GRAPH_AXIS_TEXT("graph_axis_text", AppearanceTerminology.GRAPH_AXIS_TEXT, G7AppearanceSection.GRAPH, 0xFFD2D2D2.toInt()),
    GRAPH_GRID("graph_grid", "Grid / Divider", G7AppearanceSection.GRAPH, 0xFF464646.toInt()),
    GRAPH_TILE_BORDER("graph_tile_border", "Graphkontur", G7AppearanceSection.GRAPH, 0),
}

data class G7AppearancePalette(
    private val values: Map<G7AppearanceRole, Int>,
    val mode: AppearanceMode = AppearanceMode.DARK,
) {
    fun argb(role: G7AppearanceRole): Int {
        val resolved = if (role == G7AppearanceRole.MENU_SURFACE) G7AppearanceRole.GRAPH_BACKGROUND else role
        return values[resolved] ?: if (mode == AppearanceMode.LIGHT) resolved.lightArgb else resolved.defaultArgb
    }
}

class G7AppearanceStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        app.aapswear.uishared.SugarWearDisplayDefaults
            .preferences(appContext, PREFERENCES)
    private val tileGlucosePreferences: SharedPreferences =
        app.aapswear.uishared.SugarWearDisplayDefaults
            .preferences(appContext, TILE_GLUCOSE_PREFERENCES)

    init {
        preferences.ensureSettingsSchema(SettingsSchemaVersions.COLLECTOR)
        if (!preferences.getBoolean(KEY_TILE_VISIBLE_AXES_MIGRATED, false)) {
            preferences.edit {
                putBoolean(KEY_TILE_TIME_AXIS, true)
                putBoolean(KEY_TILE_VISIBLE_AXES_MIGRATED, true)
            }
        }
    }

    fun activeMode(): AppearanceMode =
        preferences
            .getString(KEY_ACTIVE_MODE, null)
            ?.let { stored -> AppearanceMode.entries.firstOrNull { it.storageKey == stored } }
            ?: AppearanceMode.DARK

    @SuppressLint("ApplySharedPref") // The immediately resumed activity must observe this mode synchronously.
    fun setActiveMode(mode: AppearanceMode) {
        // The next activity draw must see the selection immediately, even when Android pauses us.
        preferences.edit(commit = true) { putString(KEY_ACTIVE_MODE, mode.storageKey) }
        notifyTileChanged()
    }

    fun glucoseScalePercent(): Int =
        preferences
            .getInt(KEY_GLUCOSE_SCALE, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
            .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)

    fun trendScalePercent(): Int =
        preferences
            .getInt(KEY_TREND_SCALE, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
            .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)

    fun trendArrowStyle(mode: AppearanceMode = activeMode()): TrendArrowStyle =
        TrendArrowStylePreferences.read(
            preferences,
            mode,
            0xFFFFFFFF.toInt(),
            legacyScaleKey = KEY_TREND_SCALE,
            legacyFillKey = colorKey(mode, G7AppearanceRole.GLUCOSE_TREND),
        )

    fun saveTrendArrowStyle(
        mode: AppearanceMode,
        style: TrendArrowStyle,
    ) {
        TrendArrowStylePreferences.write(preferences, mode, style)
        notifyTileChanged()
    }

    fun resetTrendArrowStyle(mode: AppearanceMode) {
        TrendArrowStylePreferences.reset(preferences, mode)
        notifyTileChanged()
    }

    fun setGlucoseScalePercent(value: Int) {
        preferences.edit {
            putInt(
                KEY_GLUCOSE_SCALE,
                value.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            )
        }
        notifyTileChanged()
    }

    fun setTrendScalePercent(value: Int) {
        preferences.edit {
            putInt(
                KEY_TREND_SCALE,
                value.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            )
        }
        notifyTileChanged()
    }

    fun tileGlucoseScalePercent(): Int =
        tileGlucosePreferences
            .getInt(KEY_TILE_GLUCOSE_SCALE, GlucoseTrendSizing.DEFAULT_SCALE_PERCENT)
            .coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT)

    fun setTileGlucoseScalePercent(value: Int) {
        tileGlucosePreferences.edit {
            putInt(KEY_TILE_GLUCOSE_SCALE, value.coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT))
        }
        notifyTileChanged()
    }

    fun tileTrendArrowStyle(mode: AppearanceMode = activeMode()): TrendArrowStyle =
        TrendArrowStylePreferences.read(
            tileGlucosePreferences,
            mode,
            0xFFFFFFFF.toInt(),
        )

    fun saveTileTrendArrowStyle(
        mode: AppearanceMode,
        style: TrendArrowStyle,
    ) {
        TrendArrowStylePreferences.write(tileGlucosePreferences, mode, style)
        notifyTileChanged()
    }

    fun resetTileTrendArrowStyle(mode: AppearanceMode) {
        TrendArrowStylePreferences.reset(tileGlucosePreferences, mode)
        notifyTileChanged()
    }

    fun tileGlucosePalette(mode: AppearanceMode = activeMode()): G7AppearancePalette {
        val base = load(mode)
        return G7AppearancePalette(
            G7AppearanceRole.entries.associateWith { role ->
                if (role.section == G7AppearanceSection.GLUCOSE) {
                    tileGlucosePreferences.getInt(colorKey(mode, role), base.argb(role))
                } else {
                    base.argb(role)
                }
            },
            mode,
        )
    }

    fun saveTileGlucoseColor(
        mode: AppearanceMode,
        role: G7AppearanceRole,
        argb: Int,
    ) {
        require(role.section == G7AppearanceSection.GLUCOSE)
        tileGlucosePreferences.edit { putInt(colorKey(mode, role), argb) }
        notifyTileChanged()
    }

    fun resetTileGlucoseAppearance(mode: AppearanceMode) {
        tileGlucosePreferences.edit {
            remove(KEY_TILE_GLUCOSE_SCALE)
            G7AppearanceRole.entries.filter { it.section == G7AppearanceSection.GLUCOSE }.forEach { remove(colorKey(mode, it)) }
        }
        TrendArrowStylePreferences.reset(tileGlucosePreferences, mode)
        notifyTileChanged()
    }

    fun historicalDotOutlineEnabled(): Boolean = preferences.getBoolean(KEY_HISTORICAL_DOT_OUTLINE, true)

    fun currentDotOutlineEnabled(): Boolean = true

    fun setHistoricalDotOutlineEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_HISTORICAL_DOT_OUTLINE, value) }
        notifyTileChanged()
    }

    fun setCurrentDotOutlineEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_CURRENT_DOT_OUTLINE, true) }
        notifyTileChanged()
    }

    fun setInAppDotOutlineWidths(historical: Float, current: Float) {
        preferences.edit {
            putFloat(KEY_HISTORICAL_OUTLINE_WIDTH, historical.coerceIn(.25f, 3f))
            putFloat(KEY_CURRENT_OUTLINE_WIDTH, current.coerceIn(.25f, 3f))
        }
        notifyTileChanged()
    }

    fun load(): G7AppearancePalette = load(activeMode())

    fun load(mode: AppearanceMode): G7AppearancePalette {
        migrateLegacy()
        return G7AppearancePalette(
            G7AppearanceRole.entries.associateWith { role ->
                preferences.getInt(colorKey(mode, role), if (mode == AppearanceMode.LIGHT) role.lightArgb else role.defaultArgb)
            },
            mode,
        )
    }

    fun save(
        role: G7AppearanceRole,
        argb: Int,
    ) {
        save(activeMode(), role, argb)
    }

    fun save(
        mode: AppearanceMode,
        role: G7AppearanceRole,
        argb: Int,
    ) {
        migrateLegacy()
        preferences.edit { putInt(colorKey(mode, role), argb) }
        notifyTileChanged()
    }

    fun reset(role: G7AppearanceRole) {
        reset(activeMode(), role)
    }

    fun reset(
        mode: AppearanceMode,
        role: G7AppearanceRole,
    ) {
        preferences.edit { remove(colorKey(mode, role)) }
        notifyTileChanged()
    }

    fun resetAll() {
        app.aapswear.uishared.SugarWearDisplayDefaults
            .reset(appContext)
        notifyTileChanged()
    }

    fun graphHours(): Int =
        preferences
            .getInt(KEY_GRAPH_HOURS, DEFAULT_GRAPH_HOURS)
            .takeIf { it in ALLOWED_GRAPH_HOURS }
            ?: DEFAULT_GRAPH_HOURS

    fun setGraphHours(hours: Int) {
        preferences.edit { putInt(KEY_GRAPH_HOURS, hours.takeIf { it in ALLOWED_GRAPH_HOURS } ?: DEFAULT_GRAPH_HOURS) }
        notifyTileChanged()
    }

    fun nextGraphHours(): Int {
        val current = graphHours()
        val next = ALLOWED_GRAPH_HOURS[(ALLOWED_GRAPH_HOURS.indexOf(current) + 1) % ALLOWED_GRAPH_HOURS.size]
        setGraphHours(next)
        return next
    }

    fun tileGraphHours(): Int =
        preferences.getInt(KEY_TILE_GRAPH_HOURS, DEFAULT_GRAPH_HOURS).takeIf { it in ALLOWED_GRAPH_HOURS } ?: DEFAULT_GRAPH_HOURS

    fun setTileGraphHours(hours: Int) {
        preferences.edit { putInt(KEY_TILE_GRAPH_HOURS, hours.takeIf { it in ALLOWED_GRAPH_HOURS } ?: DEFAULT_GRAPH_HOURS) }
        notifyTileChanged()
    }

    fun inAppGraphStyle(): SharedWearCgmGraphStyle =
        DirectToWatchGraphDefaults.style().copy(
            historicalDotOutlineEnabled = historicalDotOutlineEnabled(),
            currentDotOutlineEnabled = true,
            historicalDotOutlineWidthDp = preferences.getFloat(KEY_HISTORICAL_OUTLINE_WIDTH, .35f).coerceIn(.25f, 3f),
            currentDotOutlineWidthDp = preferences.getFloat(KEY_CURRENT_OUTLINE_WIDTH, .35f).coerceIn(.25f, 3f),
            timeAxisEnabled = preferences.getBoolean(KEY_IN_APP_TIME_AXIS, true),
            borderEnabled = preferences.getBoolean(KEY_IN_APP_BORDER, true),
        )

    fun setInAppGraphTimeAxisEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_IN_APP_TIME_AXIS, value) }
        notifyTileChanged()
    }

    fun setInAppGraphBorderEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_IN_APP_BORDER, value) }
        notifyTileChanged()
    }

    fun tileGraphStyle(defaultCornerRadiusDp: Float = DirectToWatchGraphDefaults.style().cornerRadiusDp): SharedWearCgmGraphStyle {
        val defaults =
            DirectToWatchGraphDefaults
                .style()
                .copy(
                    cornerRadiusDp = defaultCornerRadiusDp,
                    timeAxisEnabled = true,
                )
        return defaults.copy(
            dotRadiusDp = preferences.getFloat(KEY_TILE_DOT_RADIUS, defaults.dotRadiusDp).coerceIn(1.5f, 6f),
            historicalDotOutlineEnabled = preferences.getBoolean(KEY_TILE_HISTORY_OUTLINE, defaults.historicalDotOutlineEnabled),
            currentDotOutlineEnabled = true,
            dotOutlineWidthDp = preferences.getFloat(KEY_TILE_OUTLINE_WIDTH, defaults.dotOutlineWidthDp).coerceIn(.25f, 3f),
            historicalDotOutlineWidthDp = preferences.getFloat(KEY_TILE_HISTORICAL_OUTLINE_WIDTH, preferences.getFloat(KEY_TILE_OUTLINE_WIDTH, defaults.dotOutlineWidthDp)).coerceIn(.25f, 3f),
            currentDotOutlineWidthDp = preferences.getFloat(KEY_TILE_CURRENT_OUTLINE_WIDTH, preferences.getFloat(KEY_TILE_OUTLINE_WIDTH, defaults.dotOutlineWidthDp)).coerceIn(.25f, 3f),
            cornerRadiusDp = preferences.getFloat(KEY_TILE_CORNER_RADIUS, defaults.cornerRadiusDp).coerceIn(0f, 40f),
            borderEnabled = preferences.getBoolean(KEY_TILE_BORDER, true),
            timeAxisEnabled = preferences.getBoolean(KEY_TILE_TIME_AXIS, defaults.timeAxisEnabled),
            scaleLaneOpacityPercent = preferences.getInt(KEY_TILE_SCALE_LANE_OPACITY, defaults.scaleLaneOpacityPercent).coerceIn(0, 100),
        )
    }

    fun saveTileGraphStyle(style: SharedWearCgmGraphStyle) {
        preferences.edit {
            putFloat(KEY_TILE_DOT_RADIUS, style.dotRadiusDp.coerceIn(1.5f, 6f))
            putBoolean(KEY_TILE_HISTORY_OUTLINE, style.historicalDotOutlineEnabled)
            putBoolean(KEY_TILE_CURRENT_OUTLINE, true)
            putFloat(KEY_TILE_OUTLINE_WIDTH, style.dotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_TILE_HISTORICAL_OUTLINE_WIDTH, style.historicalDotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_TILE_CURRENT_OUTLINE_WIDTH, style.currentDotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_TILE_CORNER_RADIUS, style.cornerRadiusDp.coerceIn(0f, 40f))
            putBoolean(KEY_TILE_TIME_AXIS, style.timeAxisEnabled)
            putBoolean(KEY_TILE_BORDER, style.borderEnabled)
            putInt(KEY_TILE_SCALE_LANE_OPACITY, style.scaleLaneOpacityPercent.coerceIn(0, 100))
        }
        notifyTileChanged()
    }

    fun tileGraphPalette(mode: AppearanceMode = activeMode()): G7AppearancePalette {
        val base = load(mode)
        return G7AppearancePalette(
            G7AppearanceRole.entries.associateWith { role ->
                if (role.section == G7AppearanceSection.GRAPH) preferences.getInt(tileColorKey(mode, role), base.argb(role)) else base.argb(role)
            },
            mode,
        )
    }

    fun saveTileGraphColor(
        role: G7AppearanceRole,
        argb: Int,
        mode: AppearanceMode = activeMode(),
    ) {
        require(role.section == G7AppearanceSection.GRAPH)
        preferences.edit { putInt(tileColorKey(mode, role), argb) }
        notifyTileChanged()
    }

    fun resetTileGraph() {
        preferences.edit {
            preferences.all.keys
                .filter { it.startsWith("tile_graph.") }
                .forEach(::remove)
        }
        notifyTileChanged()
    }

    private fun colorKey(role: G7AppearanceRole): String = "color.${role.key}"

    private fun colorKey(
        mode: AppearanceMode,
        role: G7AppearanceRole,
    ): String = "color.${mode.storageKey}.${role.key}"

    private fun tileColorKey(
        mode: AppearanceMode,
        role: G7AppearanceRole,
    ): String = "tile_graph.color.${mode.storageKey}.${role.key}"

    private fun notifyTileChanged() {
        G7CollectorTileService.requestUpdate(appContext)
    }

    private fun migrateLegacy() {
        if (preferences.getBoolean("appearance_profiles_v1", false)) return
        preferences.edit {
            G7AppearanceRole.entries.forEach { role ->
                if (!preferences.contains(colorKey(role))) return@forEach
                val value = preferences.getInt(colorKey(role), role.defaultArgb)
                AppearanceMode.entries.forEach { mode ->
                    if (!preferences.contains(colorKey(mode, role))) putInt(colorKey(mode, role), value)
                }
            }
            putBoolean("appearance_profiles_v1", true)
        }
    }

    companion object {
        val ALLOWED_GRAPH_HOURS = listOf(1, 2, 3, 6, 12, 24)
        const val DEFAULT_GRAPH_HOURS = 2
        private const val PREFERENCES = "g7_appearance"
        private const val TILE_GLUCOSE_PREFERENCES = "g7_tile_glucose_appearance"
        private const val KEY_TILE_GLUCOSE_SCALE = "tile_glucose.scale"
        private const val KEY_ACTIVE_MODE = "active_mode"
        private const val KEY_GLUCOSE_SCALE = "glucose_scale_percent"
        private const val KEY_TREND_SCALE = "trend_scale_percent"
        private const val KEY_GRAPH_HOURS = "graph_hours"
        private const val KEY_HISTORICAL_DOT_OUTLINE = "graph_historical_dot_outline_enabled"
        private const val KEY_CURRENT_DOT_OUTLINE = "graph_current_dot_outline_enabled"
        private const val KEY_TILE_GRAPH_HOURS = "tile_graph.hours"
        private const val KEY_IN_APP_TIME_AXIS = "in_app_graph.time_axis"
        private const val KEY_IN_APP_BORDER = "in_app_graph.border"
        private const val KEY_TILE_DOT_RADIUS = "tile_graph.dot_radius"
        private const val KEY_TILE_HISTORY_OUTLINE = "tile_graph.history_outline"
        private const val KEY_TILE_CURRENT_OUTLINE = "tile_graph.current_outline"
        private const val KEY_TILE_OUTLINE_WIDTH = "tile_graph.outline_width"
        private const val KEY_TILE_HISTORICAL_OUTLINE_WIDTH = "tile_graph.historical_outline_width"
        private const val KEY_TILE_CURRENT_OUTLINE_WIDTH = "tile_graph.current_outline_width"
        private const val KEY_HISTORICAL_OUTLINE_WIDTH = "graph.historical_outline_width"
        private const val KEY_CURRENT_OUTLINE_WIDTH = "graph.current_outline_width"
        private const val KEY_TILE_CORNER_RADIUS = "tile_graph.corner_radius"
        private const val KEY_TILE_TIME_AXIS = "tile_graph.time_axis"
        private const val KEY_TILE_BORDER = "tile_graph.border"
        private const val KEY_TILE_VISIBLE_AXES_MIGRATED = "tile_graph.visible_axes_migrated_v1"
        private const val KEY_TILE_SCALE_LANE_OPACITY = "tile_graph.scale_lane_opacity"
    }
}
