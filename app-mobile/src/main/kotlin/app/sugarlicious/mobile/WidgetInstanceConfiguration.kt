package app.sugarlicious.mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.model.AppearanceControlContract
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyMetricAppearance
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.model.TrendArrowStyleOverride
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

internal enum class WidgetScaleMode { STATIC, DYNAMIC, LOGARITHMIC }

internal enum class WidgetShapeMode { STANDARD, PILL }

internal fun widgetUsesNativeHostBounds(providerClassName: String?, options: Bundle): Boolean =
    options.containsKey("semWidgetSize") &&
        options.getFloat("hsResizeRatio", 0f) == 1f &&
        providerClassName?.substringAfterLast('.') in
        setOf(
            "GlucoseWidgetReceiver",
            "GraphWidgetReceiver",
            "MetabolicWidgetReceiver",
            "GlucoseGraphWidgetReceiver",
            "GlucoseFamily2x1Receiver",
            "GraphFamily2x1Receiver",
            "TherapyFamily2x1Receiver",
            "AdditionalWidgetReceiver",
        )

internal fun resolvedWidgetConfigurationOnExit(
    initial: WidgetInstanceConfiguration,
    edited: WidgetInstanceConfiguration,
    save: Boolean,
): WidgetInstanceConfiguration = if (save) edited else initial

internal enum class ConfigurableWidgetKind(
    val hasGraph: Boolean,
) {
    GLUCOSE(false),
    GRAPH(true),
    GLUCOSE_GRAPH(true),
    METABOLIC(false),
}

internal fun configurableWidgetKind(providerClassName: String?): ConfigurableWidgetKind =
    when {
        providerClassName?.endsWith("GlucoseGraphWidgetReceiver") == true -> ConfigurableWidgetKind.GLUCOSE_GRAPH
        providerClassName?.contains("TherapyFamily") == true -> ConfigurableWidgetKind.METABOLIC
        providerClassName?.contains("GraphWidget") == true -> ConfigurableWidgetKind.GRAPH
        providerClassName?.contains("GraphFamily") == true -> ConfigurableWidgetKind.GRAPH
        providerClassName?.endsWith("MetabolicWidgetReceiver") == true -> ConfigurableWidgetKind.METABOLIC
        else -> ConfigurableWidgetKind.GLUCOSE
    }

internal const val ADDITIONAL_WIDGET_KIND = "sugarlicious.additional.kind"

internal fun configurableWidgetKind(context: Context, id: Int, providerClassName: String?): ConfigurableWidgetKind {
    if (providerClassName?.endsWith("AdditionalWidgetReceiver") != true) return configurableWidgetKind(providerClassName)
    val name = AppWidgetManager.getInstance(context).getAppWidgetOptions(id).getString(ADDITIONAL_WIDGET_KIND)
    return ConfigurableWidgetKind.entries.firstOrNull { it.name == name && it != ConfigurableWidgetKind.GLUCOSE_GRAPH }
        ?: ConfigurableWidgetKind.GLUCOSE
}

internal fun widgetUsesAutomaticShape(providerClassName: String?): Boolean =
    providerClassName?.let {
        it.endsWith("GlucoseWidgetReceiver") ||
            it.endsWith("GraphWidgetReceiver") ||
            it.endsWith("MetabolicWidgetReceiver") ||
            it.endsWith("TherapyFamily2x2Receiver") ||
            it.endsWith("TherapyFamily4x2Receiver") ||
            it.endsWith("GlucoseWidget2x2Receiver") ||
            it.endsWith("GlucoseWidget4x2Receiver") ||
            it.endsWith("GraphWidget2x2Receiver") ||
            it.endsWith("GraphWidget4x2Receiver")
    } == true

internal fun widgetProviderPreset(providerClassName: String?): WidgetFamilyLayout =
    when {
        providerClassName?.contains("4x2") == true -> WidgetFamilyLayout.WIDE_4X2
        providerClassName?.contains("2x2") == true -> WidgetFamilyLayout.DETAIL_2X2
        else -> WidgetFamilyLayout.COMPACT_2X1
    }

internal data class WidgetInstanceConfiguration(
    val graphHours: Int = 3,
    val showTimeAxis: Boolean = true,
    val scaleMode: WidgetScaleMode = WidgetScaleMode.STATIC,
    val backgroundArgb: Int = Color.BLACK,
    val launchPackage: String? = "app.sugarlicious",
    val backgroundEnabled: Boolean = true,
    val outlineEnabled: Boolean = false,
    val outlineArgb: Int = Color.DKGRAY,
    val splitDividerArgb: Int = 0xFF606060.toInt(),
    val splitDividerHeightPercent: Int = 90,
    val cornerRadiusDp: Int = 0,
    val graphCornerRadiusDp: Int = DEFAULT_WIDGET_GRAPH_CORNER_RADIUS_DP,
    val scaleLaneOpacityPercent: Int = 30,
    val shapeMode: WidgetShapeMode = WidgetShapeMode.STANDARD,
    val glucoseScalePercent: Int = 100,
    val trendScalePercent: Int = 100,
    val colorOverrides: Map<WidgetColorRole, Int> = emptyMap(),
    val lightBackgroundEnabled: Boolean = backgroundEnabled,
    val lightOutlineEnabled: Boolean = outlineEnabled,
    val lightBackgroundArgb: Int = backgroundArgb,
    val lightOutlineArgb: Int = outlineArgb,
    val lightColorOverrides: Map<WidgetColorRole, Int> = colorOverrides,
    val glucoseGraphValuePercent: Int = DEFAULT_COMBINED_WIDGET_VALUE_PERCENT,
    val showGlucoseUnit: Boolean = true,
    val glucoseBold: Boolean = true,
    val deltaUnitBold: Boolean = true,
    val deltaBold: Boolean = false,
    val ageBold: Boolean = false,
    val detailDeltaBold: Boolean = true,
    val detailAgeBold: Boolean = true,
    val tirBold: Boolean = true,
    val graphLabelBold: Boolean = false,
    val graphTimeAxisBold: Boolean = false,
    val graphValueAxisBold: Boolean = false,
    val therapyTextBold: Map<String, Boolean> = TherapyProgressMetric.entries.flatMap { metric -> listOf("${metric.name}.primary", "${metric.name}.secondary") }.associateWith { true },
    val showDelta: Boolean = false,
    val showAge: Boolean = false,
    val deltaScalePercent: Int = 100,
    val deltaOffsetXDp: Int = 0,
    val deltaOffsetYDp: Int = 0,
    val ageScalePercent: Int = 100,
    val ageOffsetXDp: Int = 0,
    val ageOffsetYDp: Int = 0,
    val unitScalePercent: Int = 100,
    val unitOffsetXDp: Int = 0,
    val unitOffsetYDp: Int = 0,
    val cgmDotRadiusDp: Float = CgmGraphVisualPolicy.DOT_RADIUS_DP,
    val historicalDotOutlineEnabled: Boolean = true,
    val currentDotOutlineEnabled: Boolean = true,
    val historicalDotOutlineWidthDp: Float = CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP,
    val currentDotOutlineWidthDp: Float = CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP,
    val darkTrendStyle: TrendArrowStyleOverride = TrendArrowStyleOverride(),
    val lightTrendStyle: TrendArrowStyleOverride = TrendArrowStyleOverride(),
    val therapyProgressAppearance: TherapyProgressAppearance = TherapyProgressAppearance(),
    val graphOutlineEnabled: Boolean = false,
    val graphOutlineWidthDp: Float = 1f,
    val themeMode: DashboardThemeMode = DashboardThemeMode.SYSTEM,
)

/** Approved 2026-10-18 widget templates captured from the newest active instance of each kind. */
internal object WidgetSystemDefaults {
    private const val ACTIVE_BACKGROUND = 0xFF000000.toInt()

    fun forKind(kind: ConfigurableWidgetKind): WidgetInstanceConfiguration =
        when (kind) {
            ConfigurableWidgetKind.GLUCOSE ->
                WidgetInstanceConfiguration(
                    backgroundArgb = ACTIVE_BACKGROUND,
                    shapeMode = WidgetShapeMode.PILL,
                    trendScalePercent = 110,
                    colorOverrides =
                        mapOf(
                            WidgetColorRole.DELTA_ICON to 0xFFCCCCCC.toInt(),
                            WidgetColorRole.AGE_ICON to 0xFF59FFFF.toInt(),
                            WidgetColorRole.TIR_ICON to 0xFF59FF59.toInt(),
                        ),
                )
            ConfigurableWidgetKind.GRAPH ->
                WidgetInstanceConfiguration(
                    showTimeAxis = true,
                    scaleMode = WidgetScaleMode.DYNAMIC,
                    backgroundArgb = ACTIVE_BACKGROUND,
                    historicalDotOutlineWidthDp = .7f,
                    currentDotOutlineWidthDp = 1.25f,
                )
            ConfigurableWidgetKind.GLUCOSE_GRAPH ->
                WidgetInstanceConfiguration(
                    showTimeAxis = true,
                    backgroundArgb = ACTIVE_BACKGROUND,
                    cornerRadiusDp = 30,
                    trendScalePercent = 110,
                    glucoseGraphValuePercent = 38,
                    historicalDotOutlineWidthDp = .4f,
                    currentDotOutlineWidthDp = 1.3f,
                )
            ConfigurableWidgetKind.METABOLIC ->
                WidgetInstanceConfiguration(
                    backgroundArgb = ACTIVE_BACKGROUND,
                    shapeMode = WidgetShapeMode.PILL,
                    therapyProgressAppearance = TherapyProgressAppearance(gapDp = 20),
                )
        }.copy(lightBackgroundArgb = Color.WHITE)

    fun forNewInstance(
        context: Context,
        kind: ConfigurableWidgetKind,
    ): WidgetInstanceConfiguration = snapshotInheritedSettings(context, forKind(kind))
}

private fun TrendArrowStyle.asCompleteOverride(): TrendArrowStyleOverride =
    TrendArrowStyleOverride(
        fillColor = fillColor,
        outlineEnabled = outlineEnabled,
        outlineColor = outlineColor,
        outlineThicknessDp = outlineThicknessDp,
        sizePercent = sizePercent,
        alpha = alpha,
    )

private fun snapshotInheritedSettings(
    context: Context,
    value: WidgetInstanceConfiguration,
): WidgetInstanceConfiguration {
    val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
    val darkPalette = WidgetColorStore.load(context, AppearanceMode.DARK)
    val lightPalette = WidgetColorStore.load(context, AppearanceMode.LIGHT)
    val darkColors = WidgetColorRole.entries.associateWith(darkPalette::argb) + value.colorOverrides
    val lightColors = WidgetColorRole.entries.associateWith(lightPalette::argb) + value.lightColorOverrides
    val darkTrend = value.trendStyle(AppearanceMode.DARK, MobileTrendArrowAppearance.load(preferences, AppearanceMode.DARK))
    val lightTrend = value.trendStyle(AppearanceMode.LIGHT, MobileTrendArrowAppearance.load(preferences, AppearanceMode.LIGHT))
    return value.copy(
        colorOverrides = darkColors,
        lightColorOverrides = lightColors,
        darkTrendStyle = darkTrend.asCompleteOverride(),
        lightTrendStyle = lightTrend.asCompleteOverride(),
    )
}

internal fun toggleTherapyMetric(
    appearance: TherapyProgressAppearance,
    metric: TherapyProgressMetric,
    enabled: Boolean,
): TherapyProgressAppearance {
    val updated = if (enabled) appearance.enabledMetrics + metric else appearance.enabledMetrics - metric
    return if (updated.isEmpty()) appearance else appearance.copy(enabledMetrics = updated)
}

internal fun WidgetInstanceConfiguration.trendStyle(
    mode: AppearanceMode,
    parent: TrendArrowStyle,
): TrendArrowStyle =
    (if (mode == AppearanceMode.LIGHT) lightTrendStyle else darkTrendStyle)
        .copy(sizePercent = trendScalePercent)
        .resolve(parent)

internal fun WidgetInstanceConfiguration.withTrendStyle(
    mode: AppearanceMode,
    value: TrendArrowStyleOverride,
): WidgetInstanceConfiguration = if (mode == AppearanceMode.LIGHT) copy(lightTrendStyle = value) else copy(darkTrendStyle = value)

internal fun WidgetInstanceConfiguration.resolvedAppearance(mode: AppearanceMode): WidgetInstanceConfiguration =
    if (mode == AppearanceMode.LIGHT) {
        copy(
            backgroundEnabled = lightBackgroundEnabled,
            outlineEnabled = lightOutlineEnabled,
            backgroundArgb = lightBackgroundArgb,
            outlineArgb = lightOutlineArgb,
            colorOverrides = lightColorOverrides,
        )
    } else {
        this
    }

internal fun WidgetInstanceConfiguration.withBackground(
    mode: AppearanceMode,
    argb: Int,
): WidgetInstanceConfiguration = if (mode == AppearanceMode.LIGHT) copy(lightBackgroundArgb = argb) else copy(backgroundArgb = argb)

internal fun WidgetInstanceConfiguration.withOutline(
    mode: AppearanceMode,
    argb: Int,
): WidgetInstanceConfiguration = if (mode == AppearanceMode.LIGHT) copy(lightOutlineArgb = argb) else copy(outlineArgb = argb)

internal fun WidgetInstanceConfiguration.withBackgroundEnabled(
    mode: AppearanceMode,
    enabled: Boolean,
): WidgetInstanceConfiguration = if (mode == AppearanceMode.LIGHT) copy(lightBackgroundEnabled = enabled) else copy(backgroundEnabled = enabled)

internal fun WidgetInstanceConfiguration.withOutlineEnabled(
    mode: AppearanceMode,
    enabled: Boolean,
): WidgetInstanceConfiguration = if (mode == AppearanceMode.LIGHT) copy(lightOutlineEnabled = enabled) else copy(outlineEnabled = enabled)

internal fun WidgetInstanceConfiguration.withColorOverride(
    mode: AppearanceMode,
    role: WidgetColorRole,
    argb: Int?,
): WidgetInstanceConfiguration =
    if (mode == AppearanceMode.LIGHT) {
        copy(
            lightColorOverrides = if (argb == null) lightColorOverrides - role else lightColorOverrides + (role to argb),
        )
    } else {
        copy(colorOverrides = if (argb == null) colorOverrides - role else colorOverrides + (role to argb))
    }

internal object WidgetInstanceConfigurationStore {
    private const val PREFS = "widget_instance_configuration"
    private const val ISOLATED_PROFILE_VERSION = 3

    private fun key(
        id: Int,
        suffix: String,
    ) = "$id.$suffix"

    fun hasStoredConfiguration(
        context: Context,
        appWidgetId: Int,
    ): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .all.keys
            .any { it.startsWith("$appWidgetId.") }

    fun read(
        context: Context,
        appWidgetId: Int,
    ): WidgetInstanceConfiguration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacyBackground = prefs.getInt(key(appWidgetId, "background"), Color.BLACK)
        val legacyOutline = prefs.getInt(key(appWidgetId, "outline"), Color.DKGRAY)
        val legacyOverrides =
            WidgetColorRole.entries
                .mapNotNull { role ->
                    key(appWidgetId, "color.${role.preferenceKey}").takeIf(prefs::contains)?.let { role to prefs.getInt(it, Color.BLACK) }
                }.toMap()

        fun overrides(mode: AppearanceMode) =
            WidgetColorRole.entries
                .mapNotNull { role ->
                    val modeKey = key(appWidgetId, "${mode.storageKey}.color.${role.preferenceKey}")
                    when {
                        prefs.contains(modeKey) -> role to prefs.getInt(modeKey, Color.BLACK)
                        role in legacyOverrides -> role to legacyOverrides.getValue(role)
                        else -> null
                    }
                }.toMap()
        return WidgetInstanceConfiguration(
            themeMode =
                runCatching {
                    DashboardThemeMode.valueOf(context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE).getString("themeMode", "SYSTEM") ?: "SYSTEM")
                }.getOrDefault(DashboardThemeMode.SYSTEM),
            graphHours = prefs.getInt(key(appWidgetId, "hours"), 3).takeIf { it in listOf(1, 2, 3, 6, 12, 24) } ?: 3,
            graphOutlineEnabled = prefs.getBoolean(key(appWidgetId, "graph_outline_enabled"), false),
            graphOutlineWidthDp = prefs.getFloat(key(appWidgetId, "graph_outline_width"), 1f).takeIf { it.isFinite() }?.coerceIn(.5f, 4f) ?: 1f,
            showTimeAxis = prefs.getBoolean(key(appWidgetId, "axis"), true),
            scaleMode =
                runCatching {
                    WidgetScaleMode.valueOf(prefs.getString(key(appWidgetId, "scale"), WidgetScaleMode.STATIC.name)!!)
                }.getOrDefault(WidgetScaleMode.STATIC),
            backgroundArgb = prefs.getInt(key(appWidgetId, "dark.background"), legacyBackground),
            backgroundEnabled = prefs.getBoolean(key(appWidgetId, "background_enabled"), true),
            outlineEnabled = prefs.getBoolean(key(appWidgetId, "outline_enabled"), false),
            outlineArgb = prefs.getInt(key(appWidgetId, "dark.outline"), legacyOutline),
            splitDividerArgb = prefs.getInt(key(appWidgetId, "split_divider_color"), 0xFF606060.toInt()),
            splitDividerHeightPercent = prefs.getInt(key(appWidgetId, "split_divider_height"), 90).coerceIn(10, 100),
            cornerRadiusDp = prefs.getInt(key(appWidgetId, "corner_radius"), 0).coerceIn(0, 32),
            graphCornerRadiusDp =
                prefs
                    .getInt(
                        key(appWidgetId, "graph_corner_radius"),
                        DEFAULT_WIDGET_GRAPH_CORNER_RADIUS_DP,
                    ).coerceIn(MIN_WIDGET_GRAPH_CORNER_RADIUS_DP, MAX_WIDGET_GRAPH_CORNER_RADIUS_DP),
            scaleLaneOpacityPercent = prefs.getInt(key(appWidgetId, "scale_lane_opacity_percent"), 30).coerceIn(0, 100),
            shapeMode =
                runCatching {
                    WidgetShapeMode.valueOf(prefs.getString(key(appWidgetId, "shape"), WidgetShapeMode.STANDARD.name)!!)
                }.getOrDefault(WidgetShapeMode.STANDARD),
            glucoseScalePercent =
                prefs
                    .getInt(
                        key(appWidgetId, "glucose_scale"),
                        100,
                    ).coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            trendScalePercent =
                prefs
                    .getInt(
                        key(appWidgetId, "trend_scale"),
                        100,
                    ).coerceIn(GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT),
            colorOverrides = overrides(AppearanceMode.DARK),
            lightBackgroundArgb = prefs.getInt(key(appWidgetId, "light.background"), legacyBackground),
            lightOutlineArgb = prefs.getInt(key(appWidgetId, "light.outline"), legacyOutline),
            lightBackgroundEnabled =
                prefs.getBoolean(
                    key(appWidgetId, "light.background_enabled"),
                    prefs.getBoolean(key(appWidgetId, "background_enabled"), true),
                ),
            lightOutlineEnabled =
                prefs.getBoolean(
                    key(appWidgetId, "light.outline_enabled"),
                    prefs.getBoolean(key(appWidgetId, "outline_enabled"), false),
                ),
            lightColorOverrides = overrides(AppearanceMode.LIGHT),
            glucoseGraphValuePercent =
                prefs
                    .getInt(
                        key(appWidgetId, "glucose_graph_value_percent"),
                        DEFAULT_COMBINED_WIDGET_VALUE_PERCENT,
                    ).takeIf { it in MIN_COMBINED_WIDGET_VALUE_PERCENT..MAX_COMBINED_WIDGET_VALUE_PERCENT }
                    ?: DEFAULT_COMBINED_WIDGET_VALUE_PERCENT,
            showGlucoseUnit = prefs.getBoolean(key(appWidgetId, "show_glucose_unit"), true),
            glucoseBold = prefs.getBoolean(key(appWidgetId, "glucose_bold"), true),
            deltaUnitBold = prefs.getBoolean(key(appWidgetId, "delta_unit_bold"), true),
            deltaBold = prefs.getBoolean(key(appWidgetId, "delta_bold"), false),
            ageBold = prefs.getBoolean(key(appWidgetId, "age_bold"), false),
            detailDeltaBold = prefs.getBoolean(key(appWidgetId, "detail_delta_bold"), true),
            detailAgeBold = prefs.getBoolean(key(appWidgetId, "detail_age_bold"), true),
            // New detail typography intentionally starts bold even on existing widgets.
            tirBold = prefs.getBoolean(key(appWidgetId, "detail_tir_bold"), true),
            graphLabelBold = prefs.getBoolean(key(appWidgetId, "graph_label_bold"), false),
            graphTimeAxisBold = prefs.getBoolean(key(appWidgetId, "graph_time_axis_bold"), false),
            graphValueAxisBold = prefs.getBoolean(key(appWidgetId, "graph_value_axis_bold"), false),
            therapyTextBold = TherapyProgressMetric.entries.flatMap { metric -> listOf("${metric.name}.primary", "${metric.name}.secondary") }.associateWith { prefs.getBoolean(key(appWidgetId, "therapy_bold.$it"), true) },
            showDelta = prefs.getBoolean(key(appWidgetId, "show_delta"), false),
            showAge = prefs.getBoolean(key(appWidgetId, "show_age"), false),
            deltaScalePercent = prefs.getInt(key(appWidgetId, "delta_scale"), 100),
            deltaOffsetXDp = prefs.getInt(key(appWidgetId, "delta_x"), 0),
            deltaOffsetYDp = prefs.getInt(key(appWidgetId, "delta_y"), 0),
            ageScalePercent = prefs.getInt(key(appWidgetId, "age_scale"), 100),
            ageOffsetXDp = prefs.getInt(key(appWidgetId, "age_x"), 0),
            ageOffsetYDp = prefs.getInt(key(appWidgetId, "age_y"), 0),
            unitScalePercent = prefs.getInt(key(appWidgetId, "unit_scale"), 100),
            unitOffsetXDp = prefs.getInt(key(appWidgetId, "unit_x"), 0),
            unitOffsetYDp = prefs.getInt(key(appWidgetId, "unit_y"), 0),
            cgmDotRadiusDp =
                prefs
                    .getFloat(key(appWidgetId, "cgm_dot_radius_dp"), CgmGraphVisualPolicy.DOT_RADIUS_DP)
                    .coerceIn(1.5f, 6f),
            historicalDotOutlineEnabled = prefs.getBoolean(key(appWidgetId, "historical_dot_outline_enabled"), true),
            // Legacy false values are intentionally ignored: the current real CGM dot is always outlined.
            currentDotOutlineEnabled = true,
            historicalDotOutlineWidthDp =
                prefs
                    .getFloat(
                        key(appWidgetId, "historical_dot_outline_width_dp"),
                        CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP,
                    ).coerceIn(0.25f, 3f),
            currentDotOutlineWidthDp =
                prefs
                    .getFloat(
                        key(appWidgetId, "current_dot_outline_width_dp"),
                        CgmGraphVisualPolicy.DOT_OUTLINE_WIDTH_DP,
                    ).coerceIn(0.25f, 3f),
            darkTrendStyle = readTrendStyle(prefs, appWidgetId, AppearanceMode.DARK),
            lightTrendStyle = readTrendStyle(prefs, appWidgetId, AppearanceMode.LIGHT),
            therapyProgressAppearance = readTherapyAppearance(prefs, appWidgetId),
            // One-time compatible fallback for widgets created before per-instance tap targets existed.
            // The configuration activity persists this resolved value for the individual widget.
            launchPackage =
                prefs.getString(key(appWidgetId, "launch"), null)
                    ?: WidgetLaunchTargetStore.legacySelectedPackage(context),
        )
    }

    fun readIsolated(
        context: Context,
        appWidgetId: Int,
        kind: ConfigurableWidgetKind,
    ): WidgetInstanceConfiguration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versionKey = key(appWidgetId, "isolated_profile_version")
        if (!prefs.contains(key(appWidgetId, "theme_mode")) && hasStoredConfiguration(context, appWidgetId)) {
            val inheritedMode = read(context, appWidgetId).themeMode
            prefs.edit { putString(key(appWidgetId, "theme_mode"), inheritedMode.name) }
        }
        if (prefs.getInt(versionKey, 0) >= ISOLATED_PROFILE_VERSION) return read(context, appWidgetId)

        val source = if (hasStoredConfiguration(context, appWidgetId)) read(context, appWidgetId) else WidgetSystemDefaults.forKind(kind)
        val isolated = snapshotInheritedSettings(context, source)
        save(context, appWidgetId, isolated)
        prefs.edit { putInt(versionKey, ISOLATED_PROFILE_VERSION) }
        return isolated
    }

    fun save(
        context: Context,
        appWidgetId: Int,
        value: WidgetInstanceConfiguration,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(key(appWidgetId, "theme_mode"), value.themeMode.name)
            putInt(key(appWidgetId, "hours"), value.graphHours)
            putBoolean(key(appWidgetId, "graph_outline_enabled"), value.graphOutlineEnabled)
            putFloat(key(appWidgetId, "graph_outline_width"), value.graphOutlineWidthDp.takeIf { it.isFinite() }?.coerceIn(.5f, 4f) ?: 1f)
            putBoolean(key(appWidgetId, "axis"), value.showTimeAxis)
            putString(key(appWidgetId, "scale"), value.scaleMode.name)
            putInt(key(appWidgetId, "dark.background"), value.backgroundArgb)
            putInt(key(appWidgetId, "light.background"), value.lightBackgroundArgb)
            putBoolean(key(appWidgetId, "background_enabled"), value.backgroundEnabled)
            putBoolean(key(appWidgetId, "outline_enabled"), value.outlineEnabled)
            putBoolean(key(appWidgetId, "light.background_enabled"), value.lightBackgroundEnabled)
            putBoolean(key(appWidgetId, "light.outline_enabled"), value.lightOutlineEnabled)
            putInt(key(appWidgetId, "dark.outline"), value.outlineArgb)
            putInt(key(appWidgetId, "split_divider_color"), value.splitDividerArgb)
            putInt(key(appWidgetId, "split_divider_height"), value.splitDividerHeightPercent.coerceIn(10, 100))
            putInt(key(appWidgetId, "light.outline"), value.lightOutlineArgb)
            putInt(key(appWidgetId, "corner_radius"), value.cornerRadiusDp)
            putInt(key(appWidgetId, "graph_corner_radius"), value.graphCornerRadiusDp)
            putInt(key(appWidgetId, "scale_lane_opacity_percent"), value.scaleLaneOpacityPercent.coerceIn(0, 100))
            putString(key(appWidgetId, "shape"), value.shapeMode.name)
            putInt(key(appWidgetId, "glucose_scale"), value.glucoseScalePercent)
            putInt(key(appWidgetId, "trend_scale"), value.trendScalePercent)
            putInt(key(appWidgetId, "glucose_graph_value_percent"), value.glucoseGraphValuePercent)
            putBoolean(key(appWidgetId, "show_glucose_unit"), value.showGlucoseUnit)
            putBoolean(key(appWidgetId, "glucose_bold"), value.glucoseBold)
            putBoolean(key(appWidgetId, "delta_unit_bold"), value.deltaUnitBold)
            putBoolean(key(appWidgetId, "delta_bold"), value.deltaBold)
            putBoolean(key(appWidgetId, "age_bold"), value.ageBold)
            putBoolean(key(appWidgetId, "detail_delta_bold"), value.detailDeltaBold)
            putBoolean(key(appWidgetId, "detail_age_bold"), value.detailAgeBold)
            putBoolean(key(appWidgetId, "detail_tir_bold"), value.tirBold)
            putBoolean(key(appWidgetId, "graph_label_bold"), value.graphLabelBold)
            putBoolean(key(appWidgetId, "graph_time_axis_bold"), value.graphTimeAxisBold)
            putBoolean(key(appWidgetId, "graph_value_axis_bold"), value.graphValueAxisBold)
            TherapyProgressMetric.entries.forEach { metric ->
                listOf("primary", "secondary").forEach { line ->
                    val textKey = "${metric.name}.$line"
                    putBoolean(key(appWidgetId, "therapy_bold.$textKey"), value.therapyTextBold[textKey] ?: true)
                }
            }
            putBoolean(key(appWidgetId, "show_delta"), value.showDelta)
            putBoolean(key(appWidgetId, "show_age"), value.showAge)
            putInt(key(appWidgetId, "delta_scale"), value.deltaScalePercent)
            putInt(key(appWidgetId, "delta_x"), value.deltaOffsetXDp)
            putInt(key(appWidgetId, "delta_y"), value.deltaOffsetYDp)
            putInt(key(appWidgetId, "age_scale"), value.ageScalePercent)
            putInt(key(appWidgetId, "age_x"), value.ageOffsetXDp)
            putInt(key(appWidgetId, "age_y"), value.ageOffsetYDp)
            putInt(key(appWidgetId, "unit_scale"), value.unitScalePercent)
            putInt(key(appWidgetId, "unit_x"), value.unitOffsetXDp)
            putInt(key(appWidgetId, "unit_y"), value.unitOffsetYDp)
            putFloat(key(appWidgetId, "cgm_dot_radius_dp"), value.cgmDotRadiusDp.coerceIn(1.5f, 6f))
            putBoolean(key(appWidgetId, "historical_dot_outline_enabled"), value.historicalDotOutlineEnabled)
            putBoolean(key(appWidgetId, "current_dot_outline_enabled"), true)
            putFloat(key(appWidgetId, "historical_dot_outline_width_dp"), value.historicalDotOutlineWidthDp.coerceIn(0.25f, 3f))
            putFloat(key(appWidgetId, "current_dot_outline_width_dp"), value.currentDotOutlineWidthDp.coerceIn(0.25f, 3f))
            writeTrendStyle(this, appWidgetId, AppearanceMode.DARK, value.darkTrendStyle)
            writeTrendStyle(this, appWidgetId, AppearanceMode.LIGHT, value.lightTrendStyle)
            writeTherapyAppearance(this, appWidgetId, value.therapyProgressAppearance)
            WidgetColorRole.entries.forEach { role ->
                val roleKey = key(appWidgetId, "dark.color.${role.preferenceKey}")
                value.colorOverrides[role]?.let { putInt(roleKey, it) } ?: remove(roleKey)
                val lightRoleKey = key(appWidgetId, "light.color.${role.preferenceKey}")
                value.lightColorOverrides[role]?.let { putInt(lightRoleKey, it) } ?: remove(lightRoleKey)
            }
            if (value.launchPackage == null) {
                remove(key(appWidgetId, "launch"))
            } else {
                putString(key(appWidgetId, "launch"), value.launchPackage)
            }
        }
    }

    fun delete(
        context: Context,
        appWidgetId: Int,
    ) {
        val prefix = "$appWidgetId."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith(prefix) }
                .forEach(::remove)
        }
    }

    private fun readTrendStyle(
        prefs: android.content.SharedPreferences,
        id: Int,
        mode: AppearanceMode,
    ): TrendArrowStyleOverride {
        val p = key(id, "${mode.storageKey}.trend.")
        return TrendArrowStyleOverride(
            fillColor = prefs.getInt(p + "fillColor", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            outlineEnabled = if (prefs.contains(p + "outlineEnabled")) prefs.getBoolean(p + "outlineEnabled", false) else null,
            outlineColor = prefs.getInt(p + "outlineColor", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            outlineThicknessDp = prefs.getFloat(p + "outlineThickness", Float.NaN).takeUnless { it.isNaN() },
            sizePercent = prefs.getInt(p + "sizePercent", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
            alpha = prefs.getFloat(p + "alpha", Float.NaN).takeUnless { it.isNaN() },
        )
    }

    private fun writeTrendStyle(
        editor: android.content.SharedPreferences.Editor,
        id: Int,
        mode: AppearanceMode,
        style: TrendArrowStyleOverride,
    ) {
        val p = key(id, "${mode.storageKey}.trend.")
        style.fillColor?.let { editor.putInt(p + "fillColor", it) } ?: editor.remove(p + "fillColor")
        style.outlineEnabled?.let { editor.putBoolean(p + "outlineEnabled", it) } ?: editor.remove(p + "outlineEnabled")
        style.outlineColor?.let { editor.putInt(p + "outlineColor", it) } ?: editor.remove(p + "outlineColor")
        style.outlineThicknessDp?.let { editor.putFloat(p + "outlineThickness", it) } ?: editor.remove(p + "outlineThickness")
        style.sizePercent?.let { editor.putInt(p + "sizePercent", it) } ?: editor.remove(p + "sizePercent")
        style.alpha?.let { editor.putFloat(p + "alpha", it) } ?: editor.remove(p + "alpha")
    }

    private fun readTherapyAppearance(
        prefs: android.content.SharedPreferences,
        id: Int,
    ): TherapyProgressAppearance {
        val prefix = key(id, "therapy.")
        val enabled =
            prefs
                .getString(prefix + "enabled", null)
                ?.split(',')
                ?.mapNotNull { name -> runCatching { TherapyProgressMetric.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: TherapyProgressMetric.entries.toSet()

        fun metricAppearance(storagePrefix: String): TherapyMetricAppearance =
            TherapyMetricAppearance(
                overallScalePercent = prefs.getInt(storagePrefix + "overall", 100),
                ringThicknessPercent = prefs.getInt(storagePrefix + "ring", 100),
                primaryTextPercent = prefs.getInt(storagePrefix + "primary", 100),
                secondaryTextPercent = prefs.getInt(storagePrefix + "secondary", 100),
                iconPercent = prefs.getInt(storagePrefix + "icon", 100),
            ).validated()

        return TherapyProgressAppearance(
            enabledMetrics = enabled,
            groupScalePercent = prefs.getInt(prefix + "group_scale", 100),
            gapDp = prefs.getInt(prefix + "gap_dp", 6),
            global = metricAppearance(prefix + "global."),
            metricOverrides =
                TherapyProgressMetric.entries
                    .mapNotNull { metric ->
                        val metricPrefix = prefix + metric.name.lowercase() + "."
                        metric
                            .takeIf {
                                listOf("overall", "ring", "primary", "secondary", "icon")
                                    .any { suffix -> prefs.contains(metricPrefix + suffix) }
                            }?.let { it to metricAppearance(metricPrefix) }
                    }.toMap(),
        ).validated()
    }

    private fun writeTherapyAppearance(
        editor: android.content.SharedPreferences.Editor,
        id: Int,
        appearance: TherapyProgressAppearance,
    ) {
        val safe = appearance.validated()
        val prefix = key(id, "therapy.")
        editor.putString(prefix + "enabled", safe.enabledMetrics.joinToString(",") { it.name })
        editor.putInt(prefix + "group_scale", safe.groupScalePercent)
        editor.putInt(prefix + "gap_dp", safe.gapDp)

        fun writeMetric(storagePrefix: String, value: TherapyMetricAppearance) {
            editor.putInt(storagePrefix + "overall", value.overallScalePercent)
            editor.putInt(storagePrefix + "ring", value.ringThicknessPercent)
            editor.putInt(storagePrefix + "primary", value.primaryTextPercent)
            editor.putInt(storagePrefix + "secondary", value.secondaryTextPercent)
            editor.putInt(storagePrefix + "icon", value.iconPercent)
        }
        writeMetric(prefix + "global.", safe.global)
        TherapyProgressMetric.entries.forEach { metric ->
            val metricPrefix = prefix + metric.name.lowercase() + "."
            safe.metricOverrides[metric]?.let { writeMetric(metricPrefix, it) }
                ?: listOf("overall", "ring", "primary", "secondary", "icon").forEach { editor.remove(metricPrefix + it) }
        }
    }
}

@dagger.hilt.android.AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOneUiWidgetSettingsWindow(window)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val providerClassName = manager.getAppWidgetInfo(appWidgetId)?.provider?.className
        val options = manager.getAppWidgetOptions(appWidgetId)
        if (providerClassName?.endsWith("AdditionalWidgetReceiver") == true &&
            !options.containsKey(ADDITIONAL_WIDGET_KIND)
        ) {
            android.app.AlertDialog
                .Builder(this)
                .setTitle("Zusätzlich")
                .setItems(arrayOf("Glukose", "Graph", "Therapie")) { _, index ->
                    val kinds = listOf(ConfigurableWidgetKind.GLUCOSE, ConfigurableWidgetKind.GRAPH, ConfigurableWidgetKind.METABOLIC)
                    intent.putExtra("additional_initial_selection", true)
                    manager.updateAppWidgetOptions(appWidgetId, Bundle().apply { putString(ADDITIONAL_WIDGET_KIND, kinds[index].name) })
                    recreate()
                }.setOnCancelListener { finish() }
                .show()
            return
        }
        val widgetKind = configurableWidgetKind(this, appWidgetId, providerClassName)
        val automaticShape = widgetUsesAutomaticShape(providerClassName)
        val model =
            injectedViewModelProvider(this) { extras ->
                extras.withCreationCallback<WidgetConfigurationViewModel.Factory> { factory ->
                    factory.create(appWidgetId, widgetKind, intent.getBooleanExtra("additional_initial_selection", false))
                }
            }["widget-$appWidgetId", WidgetConfigurationViewModel::class.java]
        val widgetOptions = manager.getAppWidgetOptions(appWidgetId)
        val previewWidthDp = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220).coerceAtLeast(80)
        val previewHeightDp = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(48)
        val familyLayout = widgetFamilyLayout(previewWidthDp.toFloat(), previewHeightDp.toFloat())
        val detailText = automaticShape && familyLayout != WidgetFamilyLayout.COMPACT_2X1
        val graphSettingsVisible =
            widgetKind.hasGraph ||
                (widgetKind == ConfigurableWidgetKind.GLUCOSE && familyLayout == WidgetFamilyLayout.WIDE_4X2)
        setContent {
            OneUiWidgetSettingsTheme {
                val editor by model.state.collectAsStateWithLifecycle()
                val snapshot = editor.snapshot
                if (snapshot == null) {
                    Column {
                        Text(if (editor.error == null) "Einstellungen werden geladen ..." else "Einstellungen konnten nicht geladen werden.")
                        if (editor.error != null) Text("Erneut versuchen", Modifier.clickable { model.reload() })
                    }
                    return@OneUiWidgetSettingsTheme
                }
                androidx.compose.runtime.LaunchedEffect(editor.saved, editor.error) {
                    if (editor.saved) {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    } else if (editor.error == WidgetConfigurationError.SAVE_FAILED) {
                        android.widget.Toast
                            .makeText(this@WidgetConfigurationActivity, "Widget konnte nicht aktualisiert werden. Bitte erneut speichern.", android.widget.Toast.LENGTH_LONG)
                            .show()
                        model.clearError()
                    }
                }
                var value by model.configuration
                var editBackground by model.backgroundEditor
                var editOutline by model.outlineEditor
                var editSplitDivider by model.dividerEditor
                var editTrendOutline by model.trendEditor
                var editRole by model.colorRole
                var selectedMode by model.mode
                val appearance = value.resolvedAppearance(selectedMode)
                val parentTrendStyle = snapshot.trends.getValue(selectedMode)

                fun resetTrendStyle() {
                    value = value.withTrendStyle(selectedMode, parentTrendStyle.copy(sizePercent = value.trendScalePercent).asCompleteOverride())
                }
                val trendAppearance = value.trendStyle(selectedMode, parentTrendStyle)
                val resetPalette = snapshot.palettes.getValue(selectedMode)
                val settingsScroll = rememberScrollState()
                val compactSettingsTitle = measuredSettingsCompactTitle("Widget-Einstellungen", settingsWindowWidthDp() - 20f)
                OneUiWidgetSettingsSurface(settingsScroll, onBack = { finish() }) { reportPreviewBounds ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = (compactSettingsTitle.heightDp + SETTINGS_SUBMENU_CARD_GAP_DP).dp)
                            .padding(horizontal = 10.dp)
                            .verticalScroll(settingsScroll)
                            .padding(top = LocalWidgetHeaderExpansionDp.current.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        WidgetConfigurationPreview(
                            widgetKind,
                            value,
                            selectedMode,
                            previewWidthDp,
                            previewHeightDp,
                            automaticShape,
                            widgetUsesNativeHostBounds(providerClassName, widgetOptions),
                            widgetOptions.getFloat("hsResizeRatio", 1f),
                            Modifier.onGloballyPositioned { reportPreviewBounds(it.boundsInRoot()) },
                            snapshot,
                        )
                        WidgetSettingsSection("Farbvariante") {
                            WidgetAppearanceModeRow(selectedMode) { selectedMode = it }
                        }
                        WidgetSettingsSection("Widget-Hintergrund und Form") {
                            OneUiWidgetSeekBar("Deckkraft", (Color.alpha(appearance.backgroundArgb) * 100f / 255f).roundToInt(), 0..100) {
                                value =
                                    value.withBackground(
                                        selectedMode,
                                        androidx.core.graphics.ColorUtils
                                            .setAlphaComponent(appearance.backgroundArgb, (it * 255f / 100f).roundToInt()),
                                    )
                            }
                            WidgetToggleSetting("Hintergrund anzeigen", appearance.backgroundEnabled) { value = value.withBackgroundEnabled(selectedMode, it) }
                            WidgetColorSetting("Hintergrund", appearance.backgroundArgb, { editBackground = true })
                            WidgetToggleSetting("Kontur anzeigen", appearance.outlineEnabled) { value = value.withOutlineEnabled(selectedMode, it) }
                            if (appearance.outlineEnabled) {
                                WidgetColorSetting("Kontur", appearance.outlineArgb, { editOutline = true })
                            }
                            if (widgetSupportsPill(widgetKind) && !automaticShape) {
                                OneUiWidgetChoiceRow("Form", value.shapeMode, WidgetShapeMode.entries, { if (it == WidgetShapeMode.STANDARD) "Standard" else "Pille" }) { value = value.copy(shapeMode = it) }
                            }
                            if (!automaticShape && value.shapeMode == WidgetShapeMode.STANDARD) {
                                WidgetPercentSlider("Eckenradius", value.cornerRadiusDp, 0..32, " dp") { value = value.copy(cornerRadiusDp = it) }
                            }
                        }
                        if (widgetKind == ConfigurableWidgetKind.GLUCOSE || widgetKind == ConfigurableWidgetKind.GLUCOSE_GRAPH) {
                            WidgetSettingsSection("Glukosewert") {
                                WidgetPercentSlider("Größe", value.glucoseScalePercent) { value = value.copy(glucoseScalePercent = it) }
                                WidgetToggleSetting("Zuckerwert fett", value.glucoseBold) { value = value.copy(glucoseBold = it) }
                                listOf(WidgetColorRole.VERY_HIGH, WidgetColorRole.HIGH, WidgetColorRole.IN_RANGE, WidgetColorRole.LOW, WidgetColorRole.URGENT_LOW).forEach { role ->
                                    WidgetColorSetting(
                                        role.label,
                                        appearance.colorOverrides[role]
                                            ?: snapshot.palettes.getValue(selectedMode).argb(role),
                                        {
                                            editRole =
                                                role
                                        },
                                    )
                                }
                            }
                            if (widgetKind == ConfigurableWidgetKind.GLUCOSE) {
                                WidgetSettingsSection("Delta") {
                                    WidgetToggleSetting("Fett", if (detailText) value.detailDeltaBold else value.deltaBold) {
                                        value = if (detailText) value.copy(detailDeltaBold = it) else value.copy(deltaBold = it)
                                    }
                                    if (!automaticShape) WidgetToggleSetting("Anzeigen", value.showDelta) { value = value.copy(showDelta = it) }
                                    WidgetPercentSlider("Größe", value.deltaScalePercent) { value = value.copy(deltaScalePercent = it) }
                                    WidgetPercentSlider("Position X", value.deltaOffsetXDp, -40..40, "dp") { value = value.copy(deltaOffsetXDp = it) }
                                    WidgetPercentSlider("Position Y", value.deltaOffsetYDp, -40..40, "dp") { value = value.copy(deltaOffsetYDp = it) }
                                    val roles = listOf(WidgetColorRole.DELTA) + if (automaticShape && familyLayout != WidgetFamilyLayout.COMPACT_2X1) listOf(WidgetColorRole.DELTA_ICON, WidgetColorRole.DELTA_SYMBOL) else emptyList()
                                    roles.forEach { role -> WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role } }
                                }
                                WidgetSettingsSection("Alter") {
                                    WidgetToggleSetting("Fett", if (detailText) value.detailAgeBold else value.ageBold) {
                                        value = if (detailText) value.copy(detailAgeBold = it) else value.copy(ageBold = it)
                                    }
                                    if (!automaticShape) WidgetToggleSetting("Anzeigen", value.showAge) { value = value.copy(showAge = it) }
                                    WidgetPercentSlider("Größe", value.ageScalePercent) { value = value.copy(ageScalePercent = it) }
                                    WidgetPercentSlider("Position X", value.ageOffsetXDp, -40..40, "dp") { value = value.copy(ageOffsetXDp = it) }
                                    WidgetPercentSlider("Position Y", value.ageOffsetYDp, -40..40, "dp") { value = value.copy(ageOffsetYDp = it) }
                                    val roles = listOf(WidgetColorRole.AGE) + if (automaticShape && familyLayout != WidgetFamilyLayout.COMPACT_2X1) listOf(WidgetColorRole.AGE_ICON, WidgetColorRole.AGE_SYMBOL) else emptyList()
                                    roles.forEach { role -> WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role } }
                                }
                                if (!automaticShape) {
                                    WidgetSettingsSection("Maßeinheit") {
                                        WidgetPercentSlider("Größe", value.unitScalePercent) { value = value.copy(unitScalePercent = it) }
                                        WidgetPercentSlider("Position X", value.unitOffsetXDp, -40..40, "dp") { value = value.copy(unitOffsetXDp = it) }
                                        WidgetPercentSlider("Position Y", value.unitOffsetYDp, -40..40, "dp") { value = value.copy(unitOffsetYDp = it) }
                                        WidgetColorSetting(WidgetColorRole.UNIT.label, appearance.colorOverrides[WidgetColorRole.UNIT] ?: resetPalette.argb(WidgetColorRole.UNIT)) { editRole = WidgetColorRole.UNIT }
                                    }
                                }
                                if (automaticShape && familyLayout != WidgetFamilyLayout.COMPACT_2X1) {
                                    WidgetSettingsSection("Zeit im Ziel") {
                                        WidgetToggleSetting("Fett", value.tirBold) { value = value.copy(tirBold = it) }
                                        listOf(WidgetColorRole.TIR, WidgetColorRole.TIR_ICON, WidgetColorRole.TIR_SYMBOL).forEach { role ->
                                            WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role }
                                        }
                                    }
                                }
                            }
                            if (automaticShape && familyLayout == WidgetFamilyLayout.WIDE_4X2) {
                                WidgetSettingsSection("Trennlinie") {
                                    WidgetColorSetting("Farbe", value.splitDividerArgb, { editSplitDivider = true })
                                    WidgetPercentSlider("Höhe", value.splitDividerHeightPercent, 10..100, "%") {
                                        value = value.copy(splitDividerHeightPercent = it)
                                    }
                                }
                            }
                            WidgetSettingsSection("Trendpfeil") {
                                WidgetPercentSlider("Größe", value.trendScalePercent) { value = value.copy(trendScalePercent = it) }
                                listOf(WidgetColorRole.TREND_HIGH, WidgetColorRole.TREND_IN_RANGE, WidgetColorRole.TREND_LOW).forEach { role ->
                                    WidgetColorSetting(
                                        role.label,
                                        appearance.colorOverrides[role]
                                            ?: snapshot.palettes.getValue(selectedMode).argb(role),
                                        {
                                            editRole =
                                                role
                                        },
                                    )
                                }
                                val trendOverride = if (selectedMode == AppearanceMode.LIGHT) value.lightTrendStyle else value.darkTrendStyle
                                WidgetToggleSetting("Kontur anzeigen", trendAppearance.outlineEnabled) {
                                    value = value.withTrendStyle(selectedMode, trendOverride.copy(outlineEnabled = it))
                                }
                                if (trendAppearance.outlineEnabled) {
                                    WidgetColorSetting("Trend-Kontur", trendAppearance.outlineColor, { editTrendOutline = true })
                                    WidgetPercentSlider(
                                        "Konturdicke",
                                        (trendAppearance.outlineThicknessDp * 100).roundToInt(),
                                        25..400,
                                        "×0,01 dp",
                                    ) {
                                        value = value.withTrendStyle(selectedMode, trendOverride.copy(outlineThicknessDp = it / 100f))
                                    }
                                }
                                WidgetPercentSlider("Deckkraft", (trendAppearance.alpha * 100).roundToInt(), 0..100, "%") {
                                    value = value.withTrendStyle(selectedMode, trendOverride.copy(alpha = it / 100f))
                                }
                            }
                        }
                        if (widgetKind == ConfigurableWidgetKind.GLUCOSE_GRAPH) {
                            WidgetSettingsSection("Kombination") {
                                WidgetPercentSlider(
                                    "Wertbereich",
                                    value.glucoseGraphValuePercent,
                                    MIN_COMBINED_WIDGET_VALUE_PERCENT..MAX_COMBINED_WIDGET_VALUE_PERCENT,
                                ) { value = value.copy(glucoseGraphValuePercent = it) }
                                WidgetToggleSetting("Maßeinheit anzeigen", value.showGlucoseUnit) { value = value.copy(showGlucoseUnit = it) }
                                WidgetToggleSetting("Delta + Maßeinheit fett", value.deltaUnitBold) { value = value.copy(deltaUnitBold = it) }
                                listOf(WidgetColorRole.DELTA, WidgetColorRole.UNIT).forEach { role ->
                                    WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role), { editRole = role })
                                }
                            }
                        }
                        if (widgetKind == ConfigurableWidgetKind.METABOLIC) {
                            if (familyLayout == WidgetFamilyLayout.WIDE_4X2 && automaticShape) {
                                WidgetSettingsSection("IOB/COB-Graph") {
                                    WidgetToggleSetting("Graphkontur anzeigen", value.graphOutlineEnabled) { value = value.copy(graphOutlineEnabled = it) }
                                    if (value.graphOutlineEnabled) {
                                        WidgetPercentSlider("Graphkonturstärke", (value.graphOutlineWidthDp * 10).roundToInt(), 5..40, " ×0,1 dp", 10) { value = value.copy(graphOutlineWidthDp = it / 10f) }
                                        WidgetColorSetting("Graphkontur", appearance.colorOverrides[WidgetColorRole.GRAPH_OUTLINE] ?: resetPalette.argb(WidgetColorRole.GRAPH_OUTLINE), { editRole = WidgetColorRole.GRAPH_OUTLINE })
                                    }
                                    OneUiWidgetChoiceRow("Zeitraum", value.graphHours, listOf(1, 2, 3, 6, 12, 24), { "${it}h" }) { value = value.copy(graphHours = it) }
                                    WidgetToggleSetting("Zeitskala", value.showTimeAxis) { value = value.copy(showTimeAxis = it) }
                                    WidgetColorSetting("Trennlinie", value.splitDividerArgb, { editSplitDivider = true })
                                    WidgetPercentSlider("Trennlinie · Höhe", value.splitDividerHeightPercent, 10..100) { value = value.copy(splitDividerHeightPercent = it) }
                                }
                            }
                            TherapyProgressSettings(
                                appearance = value.therapyProgressAppearance,
                                capacity = if (automaticShape && familyLayout == WidgetFamilyLayout.COMPACT_2X1) 2 else 3,
                                metricControls = { metric ->
                                    val textKey = "${metric.name}.primary"
                                    WidgetToggleSetting("Wert fett", value.therapyTextBold[textKey] ?: true) {
                                        value = value.copy(therapyTextBold = value.therapyTextBold + (textKey to it))
                                    }
                                    WidgetColorRole.entries.filter { it.sourceRole != null && !it.name.endsWith("_SECONDARY") && it.name.startsWith("${metric.name}_") }.forEach { role ->
                                        WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role }
                                    }
                                },
                                onChange = { value = value.copy(therapyProgressAppearance = it) },
                            )
                        }
                        if (graphSettingsVisible) {
                            WidgetSettingsSection("Graph") {
                                WidgetToggleSetting("Graphkontur anzeigen", value.graphOutlineEnabled) { value = value.copy(graphOutlineEnabled = it) }
                                if (value.graphOutlineEnabled) {
                                    WidgetPercentSlider("Graphkonturstärke", (value.graphOutlineWidthDp * 10).roundToInt(), 5..40, " ×0,1 dp", 10) { value = value.copy(graphOutlineWidthDp = it / 10f) }
                                    WidgetColorSetting("Graphkontur", appearance.colorOverrides[WidgetColorRole.GRAPH_OUTLINE] ?: resetPalette.argb(WidgetColorRole.GRAPH_OUTLINE), { editRole = WidgetColorRole.GRAPH_OUTLINE })
                                }
                                WidgetToggleSetting("Graphbeschriftung fett", value.graphLabelBold) { value = value.copy(graphLabelBold = it) }
                                WidgetToggleSetting("Zeitskala fett", value.graphTimeAxisBold) { value = value.copy(graphTimeAxisBold = it) }
                                WidgetToggleSetting("Hoch-/Tief-Skala fett", value.graphValueAxisBold) { value = value.copy(graphValueAxisBold = it) }
                                WidgetPercentSlider(
                                    "Graph-Eckenradius",
                                    value.graphCornerRadiusDp,
                                    MIN_WIDGET_GRAPH_CORNER_RADIUS_DP..MAX_WIDGET_GRAPH_CORNER_RADIUS_DP,
                                    "dp",
                                    DEFAULT_WIDGET_GRAPH_CORNER_RADIUS_DP,
                                ) { value = value.copy(graphCornerRadiusDp = it) }
                                WidgetPercentSlider(
                                    "Skalenbereich",
                                    value.scaleLaneOpacityPercent,
                                    0..100,
                                    "%",
                                    30,
                                ) { value = value.copy(scaleLaneOpacityPercent = it) }
                                OneUiWidgetChoiceRow("Zeitraum", value.graphHours, listOf(1, 2, 3, 6, 12, 24), { "${it}h" }) { value = value.copy(graphHours = it) }
                                WidgetToggleSetting("Zeitachse anzeigen", value.showTimeAxis) { value = value.copy(showTimeAxis = it) }
                                OneUiWidgetChoiceRow("Skalierung", value.scaleMode, WidgetScaleMode.entries, {
                                    when (it) {
                                        WidgetScaleMode.STATIC -> "Statisch"
                                        WidgetScaleMode.DYNAMIC -> "Dynamisch"
                                        WidgetScaleMode.LOGARITHMIC -> "Logarithmisch"
                                    }
                                }) { value = value.copy(scaleMode = it) }
                                listOf(WidgetColorRole.GRAPH_BACKGROUND, WidgetColorRole.AXIS, WidgetColorRole.HIGH_REGION_LABEL, WidgetColorRole.AXIS_TICK, WidgetColorRole.DIVIDER).forEach { role ->
                                    WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role }
                                }
                            }
                            WidgetSettingsSection("Zielbereiche im Graph") {
                                listOf(WidgetColorRole.RANGE_HIGH, WidgetColorRole.RANGE_IN_RANGE, WidgetColorRole.RANGE_LOW, WidgetColorRole.HIGH_LINE, WidgetColorRole.LOW_LINE).forEach { role ->
                                    WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role }
                                }
                            }
                            WidgetSettingsSection("CGM-Punkte") {
                                WidgetPercentSlider(
                                    "CGM-Dot-Größe",
                                    (value.cgmDotRadiusDp * 10f).roundToInt(),
                                    15..60,
                                    " ×0,1 dp",
                                    (CgmGraphVisualPolicy.DOT_RADIUS_DP * 10f).roundToInt(),
                                ) { value = value.copy(cgmDotRadiusDp = it / 10f) }
                                listOf(WidgetColorRole.DOT_HIGH, WidgetColorRole.DOT_IN_RANGE, WidgetColorRole.DOT_LOW).forEach { role ->
                                    WidgetColorSetting(role.label, appearance.colorOverrides[role] ?: resetPalette.argb(role)) { editRole = role }
                                }
                                WidgetToggleSetting(app.sugarlicious.model.AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE, value.historicalDotOutlineEnabled) { value = value.copy(historicalDotOutlineEnabled = it) }
                                if (value.historicalDotOutlineEnabled) {
                                    WidgetColorSetting(
                                        WidgetColorRole.DOT_OUTLINE.label,
                                        appearance.colorOverrides[WidgetColorRole.DOT_OUTLINE]
                                            ?: WidgetColorStore
                                                .load(
                                                    this@WidgetConfigurationActivity,
                                                    selectedMode,
                                                ).argb(WidgetColorRole.DOT_OUTLINE),
                                        { editRole = WidgetColorRole.DOT_OUTLINE },
                                    )
                                    WidgetPercentSlider(
                                        label = app.sugarlicious.model.AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE_WIDTH,
                                        value = (value.historicalDotOutlineWidthDp * 100f).roundToInt(),
                                        range = 25..300,
                                        suffix = " ×0,01 dp",
                                    ) {
                                        value =
                                            value.copy(
                                                historicalDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                                            )
                                    }
                                }
                                Text(app.sugarlicious.model.AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE, color = OneUiWidgetStyle.primary)
                                run {
                                    WidgetColorSetting(
                                        WidgetColorRole.CURRENT_DOT_OUTLINE.label,
                                        appearance.colorOverrides[WidgetColorRole.CURRENT_DOT_OUTLINE]
                                            ?: WidgetColorStore
                                                .load(
                                                    this@WidgetConfigurationActivity,
                                                    selectedMode,
                                                ).argb(WidgetColorRole.CURRENT_DOT_OUTLINE),
                                        { editRole = WidgetColorRole.CURRENT_DOT_OUTLINE },
                                    )
                                    WidgetPercentSlider(
                                        label = app.sugarlicious.model.AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE_WIDTH,
                                        value = (value.currentDotOutlineWidthDp * 100f).roundToInt(),
                                        range = 25..300,
                                        suffix = " ×0,01 dp",
                                    ) {
                                        value =
                                            value.copy(
                                                currentDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                                            )
                                    }
                                }
                            }
                        }
                        WidgetSettingsSection("Beim Antippen öffnen") {
                            val targets = WidgetLaunchTargetStore.available(this@WidgetConfigurationActivity)
                            val selected = targets.firstOrNull { it.packageName == (value.launchPackage ?: packageName) } ?: targets.first()
                            OneUiWidgetChoiceRow("App", selected, targets, { it.label }) { value = value.copy(launchPackage = it.packageName) }
                        }
                        Spacer(Modifier.height(84.dp))
                    }
                    WidgetSettingsActionPill(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                        onSave = model::save,
                    )
                }
                if (editBackground) {
                    ColorEditorDialog(
                        widgetStyle = LocalOneUiWidgetSettingsColors.current,
                        role = null,
                        label = "Widget-Hintergrund",
                        resetArgb = Color.BLACK,
                        initialArgb = appearance.backgroundArgb,
                        onDismiss = { editBackground = false },
                        onChange = { value = value.withBackground(selectedMode, it) },
                    )
                }
                if (editSplitDivider) {
                    ColorEditorDialog(
                        widgetStyle = LocalOneUiWidgetSettingsColors.current,
                        role = null,
                        label = "Trennlinie",
                        resetArgb = 0xFF606060.toInt(),
                        initialArgb = value.splitDividerArgb,
                        onDismiss = { editSplitDivider = false },
                        onChange = { value = value.copy(splitDividerArgb = it) },
                    )
                }
                if (editOutline) {
                    ColorEditorDialog(
                        widgetStyle = LocalOneUiWidgetSettingsColors.current,
                        role = null,
                        label = "Widget-Kontur",
                        resetArgb = Color.DKGRAY,
                        initialArgb = appearance.outlineArgb,
                        onDismiss = { editOutline = false },
                        onChange = { value = value.withOutline(selectedMode, it) },
                    )
                }
                if (editTrendOutline) {
                    ColorEditorDialog(
                        widgetStyle = LocalOneUiWidgetSettingsColors.current,
                        role = null,
                        label = "Trendpfeil-Kontur",
                        actions =
                            listOf(
                                ColorPickerAction("Trendstil zurücksetzen") {
                                    resetTrendStyle()
                                    parentTrendStyle.outlineColor
                                },
                            ),
                        resetArgb = parentTrendStyle.outlineColor,
                        initialArgb = trendAppearance.outlineColor,
                        onDismiss = { editTrendOutline = false },
                        onChange = { color ->
                            val current = if (selectedMode == AppearanceMode.LIGHT) value.lightTrendStyle else value.darkTrendStyle
                            value = value.withTrendStyle(selectedMode, current.copy(outlineColor = color))
                        },
                    )
                }
                editRole?.let { role ->
                    val palette = snapshot.palettes.getValue(selectedMode)
                    ColorEditorDialog(
                        widgetStyle = LocalOneUiWidgetSettingsColors.current,
                        role = null,
                        label = role.label,
                        actions =
                            if (role in setOf(WidgetColorRole.TREND_HIGH, WidgetColorRole.TREND_IN_RANGE, WidgetColorRole.TREND_LOW)) {
                                listOf(
                                    ColorPickerAction("Trendstil zurücksetzen") {
                                        resetTrendStyle()
                                        appearance.colorOverrides[role] ?: palette.argb(role)
                                    },
                                )
                            } else {
                                emptyList()
                            },
                        resetArgb = palette.argb(role),
                        initialArgb = appearance.colorOverrides[role] ?: palette.argb(role),
                        onDismiss = { editRole = null },
                        onChange = { color -> value = value.withColorOverride(selectedMode, role, color) },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetSettingsActionPill(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = modifier.width(220.dp).height(52.dp),
        color = OneUiWidgetStyle.action,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "Abbrechen",
                color = OneUiWidgetStyle.primary,
                style = oneUiMenuTypography.labelLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onCancel)
                        .wrapContentSize(Alignment.Center),
            )
            Box(Modifier.width(1.dp).height(16.dp).background(ComposeColor(0xFF555555)))
            Text(
                "Speichern",
                color = OneUiWidgetStyle.primary,
                style = oneUiMenuTypography.labelLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onSave)
                        .wrapContentSize(Alignment.Center),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun TherapyProgressSettings(
    appearance: TherapyProgressAppearance,
    capacity: Int,
    metricControls: @androidx.compose.runtime.Composable (TherapyProgressMetric) -> Unit,
    onChange: (TherapyProgressAppearance) -> Unit,
) {
    fun updateGlobal(transform: (TherapyMetricAppearance) -> TherapyMetricAppearance) =
        onChange(appearance.copy(global = transform(appearance.global)).validated())

    fun updateMetric(
        metric: TherapyProgressMetric,
        transform: (TherapyMetricAppearance) -> TherapyMetricAppearance,
    ) {
        val current = appearance.metricOverrides[metric] ?: TherapyMetricAppearance()
        onChange(appearance.copy(metricOverrides = appearance.metricOverrides + (metric to transform(current))).validated())
    }

    WidgetSettingsSection("Therapieanzeigen · Anordnung") {
        Text("Anzeigen · maximal $capacity", color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        val visibleMetrics =
            TherapyProgressMetric.entries
                .filter(appearance.enabledMetrics::contains)
                .take(capacity)
                .toSet()
        TherapyProgressMetric.entries.forEach { metric ->
            WidgetToggleSetting(
                label = metric.displayLabel(),
                checked = metric in visibleMetrics,
                enabled = metric in visibleMetrics || visibleMetrics.size < capacity,
                onCheckedChange = { enabled ->
                    onChange(toggleTherapyMetric(appearance.copy(enabledMetrics = visibleMetrics), metric, enabled))
                },
            )
        }
        WidgetPercentSlider("Gesamtgröße", appearance.groupScalePercent, 50..150) {
            onChange(appearance.copy(groupScalePercent = it))
        }
        WidgetPercentSlider("Abstand", appearance.gapDp, 0..24, "dp") {
            onChange(appearance.copy(gapDp = it))
        }
        Text("Für alle Anzeigen", color = OneUiWidgetStyle.secondary, fontSize = 14.sp)
        WidgetPercentSlider("Ringdicke", appearance.global.ringThicknessPercent, 50..200) {
            updateGlobal { value -> value.copy(ringThicknessPercent = it) }
        }
        WidgetPercentSlider("Wert", appearance.global.primaryTextPercent, 60..180) {
            updateGlobal { value -> value.copy(primaryTextPercent = it) }
        }
        WidgetPercentSlider("Icon", appearance.global.iconPercent, 50..180) {
            updateGlobal { value -> value.copy(iconPercent = it) }
        }
        OneUiResetIcon(onClick = { onChange(TherapyProgressAppearance()) }, description = "Progressanzeigen zurücksetzen")
    }
    TherapyProgressMetric.entries.forEach { metric ->
        val local = appearance.metricOverrides[metric] ?: TherapyMetricAppearance()
        WidgetSettingsSection(metric.displayLabel()) {
            metricControls(metric)
            WidgetPercentSlider("Gesamtgröße", local.overallScalePercent, 60..140) {
                updateMetric(metric) { value -> value.copy(overallScalePercent = it) }
            }
            WidgetPercentSlider("Ringdicke", local.ringThicknessPercent, 50..200) {
                updateMetric(metric) { value -> value.copy(ringThicknessPercent = it) }
            }
            WidgetPercentSlider("Wert", local.primaryTextPercent, 60..180) {
                updateMetric(metric) { value -> value.copy(primaryTextPercent = it) }
            }
            WidgetPercentSlider("Icon", local.iconPercent, 50..180) {
                updateMetric(metric) { value -> value.copy(iconPercent = it) }
            }
        }
    }
}

private fun TherapyProgressMetric.displayLabel(): String =
    when (this) {
        TherapyProgressMetric.IOB -> "IOB"
        TherapyProgressMetric.COB -> "COB"
        TherapyProgressMetric.BASAL -> "Basal"
    }

@androidx.compose.runtime.Composable
private fun WidgetConfigurationPreview(
    kind: ConfigurableWidgetKind,
    configuration: WidgetInstanceConfiguration,
    mode: AppearanceMode,
    widthDp: Int,
    heightDp: Int,
    automaticShape: Boolean,
    nativeHostSized: Boolean,
    hostResizeRatio: Float,
    modifier: Modifier,
    settings: WidgetConfigurationSnapshot,
) {
    val context = LocalContext.current
    val deviceVendorCompact = settings.deviceVendorCompact
    val runtimeKind =
        when (kind) {
            ConfigurableWidgetKind.GLUCOSE -> WidgetKind.GLUCOSE
            ConfigurableWidgetKind.GRAPH -> WidgetKind.GRAPH
            ConfigurableWidgetKind.GLUCOSE_GRAPH -> WidgetKind.GLUCOSE_GRAPH
            ConfigurableWidgetKind.METABOLIC -> WidgetKind.METABOLIC
        }
    val renderSize =
        widgetPreviewRenderSizeDp(
            runtimeKind,
            widthDp.toFloat(),
            heightDp.toFloat(),
            deviceVendorCompact,
            nativeHostSized,
        )
    val themedConfiguration = configuration.resolvedAppearance(mode)
    val now = System.currentTimeMillis()
    val state = remember(now) { widgetPreviewState(now) }
    val palette =
        remember(context, themedConfiguration, mode) {
            settings.palettes
                .getValue(mode)
                .with(themedConfiguration.colorOverrides)
                .with(
                    WidgetColorRole.BACKGROUND,
                    if (themedConfiguration.backgroundEnabled) themedConfiguration.backgroundArgb else Color.TRANSPARENT,
                )
        }
    val previewWidth = (renderSize.widthDp * 2f).roundToInt().coerceIn(160, 900)
    val previewHeight = (renderSize.heightDp * 2f).roundToInt().coerceIn(96, 900)
    val previewLayout = responsiveWidgetLayout(renderSize.widthDp, renderSize.heightDp)
    val previewRadius =
        if (automaticShape) {
            adaptiveGlucoseCornerRadius(
                widgetFamilyLayout(renderSize.widthDp, renderSize.heightDp),
                renderSize.heightDp,
                systemWidgetCornerRadiusDp(context),
            )
        } else {
            resolveWidgetCornerRadiusDp(
                themedConfiguration,
                renderSize.heightDp,
                systemWidgetCornerRadiusDp(context),
                pillAllowed = widgetSupportsPill(kind),
            )
        }
    val renderConfiguration = themedConfiguration.copy(cornerRadiusDp = previewRadius.toInt())
    val previewTrendStyle =
        configuration.trendStyle(
            mode,
            settings.trends.getValue(mode),
        )
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val bitmap =
        remember(kind, renderConfiguration, state, palette, previewTrendStyle, previewWidth, previewHeight, automaticShape, fontScale) {
            when (kind) {
                ConfigurableWidgetKind.GRAPH ->
                    renderWidgetGraph(
                        state,
                        palette,
                        previewWidth,
                        previewHeight,
                        now,
                        app.sugarlicious.model.CgmThresholds.DEFAULT,
                        previewLayout,
                        2f,
                        renderConfiguration,
                        showScaleAndAge = true,
                        scaledDensity = 2f * fontScale,
                    )
                ConfigurableWidgetKind.GLUCOSE_GRAPH ->
                    renderGlucoseGraphWidget(
                        state,
                        palette,
                        previewWidth,
                        previewHeight,
                        now,
                        app.sugarlicious.model.CgmThresholds.DEFAULT,
                        previewLayout,
                        2f,
                        renderConfiguration,
                        previewTrendStyle,
                        fontScale = fontScale,
                    )
                ConfigurableWidgetKind.METABOLIC ->
                    renderTherapyIndicatorWidget(
                        context,
                        state,
                        palette,
                        previewWidth,
                        previewHeight,
                        now,
                        settings.dashboard,
                        renderConfiguration,
                        2f,
                        mode,
                        automaticShape,
                    )
                ConfigurableWidgetKind.GLUCOSE ->
                    if (automaticShape) {
                        renderAdaptiveGlucoseWidget(
                            state,
                            palette,
                            previewWidth,
                            previewHeight,
                            now,
                            app.sugarlicious.model.CgmThresholds.DEFAULT,
                            2f,
                            renderConfiguration,
                            previewTrendStyle,
                            systemWidgetCornerRadiusDp(context),
                            fontScale = fontScale,
                        )
                    } else {
                        renderMinimalGlucoseWidget(
                            state,
                            palette,
                            previewWidth,
                            previewHeight,
                            now,
                            app.sugarlicious.model.CgmThresholds.DEFAULT,
                            previewLayout,
                            2f,
                            GlucoseWidgetRenderOptions(
                                glucoseScale = configuration.glucoseScalePercent / 100f,
                                trendScale = configuration.trendScalePercent / 100f,
                                outlineEnabled = configuration.outlineEnabled,
                                outlineArgb = configuration.outlineArgb,
                                cornerRadiusDp = previewRadius,
                                trendStyle = previewTrendStyle,
                                configuration = renderConfiguration,
                            ),
                            fontScale = fontScale,
                        )
                    }
            }
        }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(242.dp),
        contentAlignment = Alignment.Center,
    ) {
        val placement = oneUiWidgetPreviewPlacement(renderSize.widthDp, renderSize.heightDp, hostResizeRatio, maxWidth.value)
        AndroidView(
            factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageBitmap(bitmap) },
            modifier =
                Modifier.width(placement.widthDp.dp).height(placement.heightDp.dp),
        )
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    Surface(color = OneUiWidgetStyle.card, shape = RoundedCornerShape(30.dp), modifier = modifier.fillMaxWidth().settingsSnapHeading(title)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = OneUiWidgetStyle.primary, fontWeight = FontWeight.Bold, style = oneUiMenuTypography.titleMedium)
            androidx.compose.material3.HorizontalDivider(color = OneUiWidgetStyle.secondary.copy(alpha = .16f))
            content()
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetColorSetting(
    label: String,
    argb: Int,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = OneUiWidgetStyle.primary, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .width(26.dp)
                    .height(26.dp)
                    .background(ComposeColor(argb), RoundedCornerShape(999.dp))
                    .border(1.dp, OneUiWidgetStyle.secondary, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetPercentSlider(
    label: String,
    value: Int,
    range: IntRange = GlucoseTrendSizing.MIN_SCALE_PERCENT..GlucoseTrendSizing.MAX_SCALE_PERCENT,
    suffix: String = "%",
    resetValue: Int? = null,
    onChange: (Int) -> Unit,
) {
    val editorModel = rememberAppearanceEditorModel("widget-percent:$label", AppearanceNumberEditorViewModel::class.java)
    val editor by editorModel.uiState.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = OneUiWidgetStyle.primary,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f).clickable { editorModel.open(value.toFloat()) }.padding(vertical = 6.dp),
        )
        Text("$value$suffix", color = OneUiWidgetStyle.secondary, fontSize = 14.sp, modifier = Modifier.clickable { editorModel.open(value.toFloat()) })
        if (resetValue != null && value != resetValue) {
            OneUiResetIcon(onClick = { onChange(resetValue) }, description = "$label zurücksetzen")
        }
    }
    OneUiWidgetSeekBar(label, value, range, onChange)
    if (editor.open) {
        AlertDialog(
            onDismissRequest = { editorModel.dismiss() },
            title = { Text(label) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editor.text,
                    onValueChange = editorModel::enter,
                    label = { Text("${range.first} bis ${range.last}") },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    editor.text.toIntOrNull()?.takeIf { it in range }?.let {
                        onChange(it)
                        editorModel.dismiss()
                    }
                }) { Text("ÜBERNEHMEN") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editorModel.dismiss() }) { Text("ABBRECHEN") }
            },
        )
    }
}

internal fun widgetPreviewState(now: Long): TherapyDisplayState =
    TherapyDisplayState(
        source = DataSourceId.ANDROID_APS,
        receivedAtEpochMs = now,
        glucose = GlucoseState(125.0, GlucoseUnit.MG_DL, Trend.FLAT, now - 300_000L, deltaMgDl = 2.0),
        insulin = app.sugarlicious.model.InsulinState(totalIob = 1.2),
        carbs = app.sugarlicious.model.CarbState(cobGrams = 15.0),
        basal = app.sugarlicious.model.BasalState(currentUnitsPerHour = .7),
        therapyHistory =
            (0..36).map { index ->
                app.sugarlicious.model.TherapyHistorySample(
                    measuredAtEpochMs = now - (36 - index) * 300_000L,
                    totalIob = 1.2 + (36 - index) * .035,
                    cobGrams = 15.0 + (36 - index) * 1.1,
                    baseBasalUnitsPerHour = .7,
                    tempBasalUnitsPerHour = if (index in 12..20) .35 else .7,
                )
            },
        glucoseHistory =
            (0..36).map { index ->
                val minutesBack = (36 - index) * 5L
                GlucoseSample(
                    valueMgDl = 108.0 + kotlin.math.sin(index / 5.0) * 18.0 + index * 0.45,
                    measuredAtEpochMs = now - minutesBack * 60_000L,
                    source = DataSourceId.ANDROID_APS,
                )
            },
        target = TargetState(lowMgDl = 80.0, highMgDl = 160.0),
    )
