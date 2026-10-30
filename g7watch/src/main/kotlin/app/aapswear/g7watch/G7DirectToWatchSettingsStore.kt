package app.aapswear.g7watch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import app.aapswear.model.AppearanceMode
import app.aapswear.model.CgmThresholds
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TrendArrowStyle
import app.aapswear.protocol.DirectToWatchGraphColorDefaults
import app.aapswear.protocol.DirectToWatchSettingsContract
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.storage.TrendArrowStylePreferences
import app.aapswear.uishared.DirectToWatchGraphDefaults
import app.aapswear.uishared.SharedWearCgmGraphStyle

class G7DirectToWatchSettingsStore(
    private val context: Context,
) {
    private val preferences =
        app.aapswear.uishared.SugarWearDisplayDefaults
            .preferences(context, DirectToWatchSettingsContract.PREFERENCES)

    init {
        // Range confirmation is a system-wide graph policy, not a per-watchface preference.
        preferences.edit {
            remove(LEGACY_KEY_RANGE_BACKGROUND_ENABLED)
            remove(KEY_TARGET_TICKS_ENABLED)
        }
    }

    fun graphHours(): Int = preferences.getInt(KEY_HOURS, 2).takeIf { it in HOUR_OPTIONS } ?: 2

    fun saveGraphHours(value: Int) = update { putInt(KEY_HOURS, value.takeIf { it in HOUR_OPTIONS } ?: 3) }

    fun glucoseUnit(): GlucoseUnit =
        runCatching {
            GlucoseUnit.valueOf(preferences.getString(KEY_GLUCOSE_UNIT, GlucoseUnit.MG_DL.name)!!)
        }.getOrDefault(GlucoseUnit.MG_DL)

    fun saveGlucoseUnit(value: GlucoseUnit) = update { putString(KEY_GLUCOSE_UNIT, value.name) }

    fun glucoseBold(): Boolean = preferences.getBoolean(KEY_GLUCOSE_BOLD, true)

    fun saveGlucoseBold(value: Boolean) = update { putBoolean(KEY_GLUCOSE_BOLD, value) }

    fun deltaUnitColor(mode: AppearanceMode): Int =
        preferences.getInt(
            "$KEY_DELTA_UNIT_COLOR.${mode.storageKey}",
            if (mode == AppearanceMode.LIGHT) 0xFF666666.toInt() else 0xFFA8A8BA.toInt(),
        )

    fun saveDeltaUnitColor(
        mode: AppearanceMode,
        value: Int,
    ) = update {
        putInt("$KEY_DELTA_UNIT_COLOR.${mode.storageKey}", value)
    }

    fun scaleSizeEnabled(): Boolean = preferences.getBoolean("watchface.scale_size_enabled", false)

    fun saveScaleSizeEnabled(value: Boolean) = update { putBoolean("watchface.scale_size_enabled", value) }

    fun scaleSizePercent(): Int = preferences.getInt("watchface.scale_size_percent", 100).coerceIn(50, 120)

    fun saveScaleSizePercent(value: Int) = update { putInt("watchface.scale_size_percent", value.coerceIn(50, 120)) }

    fun statusSizePercent(): Int = preferences.getInt(KEY_STATUS_SIZE_PERCENT, 100).coerceIn(75, 150)

    fun saveStatusSizePercent(value: Int) = update { putInt(KEY_STATUS_SIZE_PERCENT, value.coerceIn(75, 150)) }

    fun statusColor(): Int = preferences.getInt(KEY_STATUS_COLOR, 0xFFA8A8BA.toInt())

    fun saveStatusColor(value: Int) = update { putInt(KEY_STATUS_COLOR, value) }

    fun statusBold(): Boolean = preferences.getBoolean(KEY_STATUS_BOLD, true)

    fun saveStatusBold(value: Boolean) = update { putBoolean(KEY_STATUS_BOLD, value) }

    fun clockSizePercent(): Int = preferences.getInt(KEY_CLOCK_SIZE_PERCENT, 280).coerceIn(75, 450)

    fun saveClockSizePercent(value: Int) = update { putInt(KEY_CLOCK_SIZE_PERCENT, value.coerceIn(75, 450)) }

    fun clockColor(): Int = preferences.getInt(KEY_CLOCK_COLOR, 0xFFFFFFFF.toInt())

    fun saveClockColor(value: Int) = update { putInt(KEY_CLOCK_COLOR, value) }

    fun clockBold(): Boolean = preferences.getBoolean(KEY_CLOCK_BOLD, true)

    fun saveClockBold(value: Boolean) = update { putBoolean(KEY_CLOCK_BOLD, value) }

    fun activeAppearanceMode(default: AppearanceMode): AppearanceMode =
        preferences
            .getString(KEY_ACTIVE_APPEARANCE_MODE, null)
            ?.let { stored -> AppearanceMode.entries.firstOrNull { it.storageKey == stored } }
            ?: default

    fun saveActiveAppearanceMode(value: AppearanceMode) = update { putString(KEY_ACTIVE_APPEARANCE_MODE, value.storageKey) }

    fun thresholds(): CgmThresholds =
        CgmThresholds(
            veryHighMgDl = CgmThresholds.DEFAULT_VERY_HIGH_MG_DL,
            highMgDl = preferences.getFloat(KEY_TARGET_HIGH, CgmThresholds.DEFAULT_HIGH_MG_DL.toFloat()).toDouble(),
            lowMgDl = preferences.getFloat(KEY_TARGET_LOW, CgmThresholds.DEFAULT_LOW_MG_DL.toFloat()).toDouble(),
            veryLowMgDl = CgmThresholds.DEFAULT_VERY_LOW_MG_DL,
        ).takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT

    fun saveThresholds(value: CgmThresholds): Boolean {
        if (!value.isValid) return false
        update {
            putFloat(KEY_TARGET_LOW, value.lowMgDl.toFloat())
            putFloat(KEY_TARGET_HIGH, value.highMgDl.toFloat())
        }
        return true
    }

    fun graphStyle(): SharedWearCgmGraphStyle {
        val defaults = DirectToWatchGraphDefaults.vigilStyle()
        return defaults.copy(
            dotRadiusDp = preferences.getFloat(KEY_DOT_RADIUS, defaults.dotRadiusDp).coerceIn(1.5f, 6f),
            dotOutlineEnabled = true,
            historicalDotOutlineEnabled =
                preferences.getBoolean(
                    KEY_HISTORICAL_DOT_OUTLINE,
                    preferences.getBoolean(KEY_DOT_OUTLINE, defaults.historicalDotOutlineEnabled),
                ),
            currentDotOutlineEnabled = true,
            dotOutlineWidthDp = preferences.getFloat(KEY_DOT_OUTLINE_WIDTH, defaults.dotOutlineWidthDp).coerceIn(.25f, 3f),
            historicalDotOutlineWidthDp = preferences.getFloat(KEY_HISTORICAL_DOT_OUTLINE_WIDTH, preferences.getFloat(KEY_DOT_OUTLINE_WIDTH, defaults.dotOutlineWidthDp)).coerceIn(.25f, 3f),
            currentDotOutlineWidthDp = preferences.getFloat(KEY_CURRENT_DOT_OUTLINE_WIDTH, preferences.getFloat(KEY_DOT_OUTLINE_WIDTH, defaults.currentDotOutlineWidthDp)).coerceIn(.25f, 3f),
            cornerRadiusDp = preferences.getFloat(KEY_CORNER_RADIUS, defaults.cornerRadiusDp).coerceIn(0f, 40f),
            borderEnabled = preferences.getBoolean(KEY_BORDER_ENABLED, defaults.borderEnabled),
            timeAxisEnabled = preferences.getBoolean(KEY_TIME_AXIS_ENABLED, defaults.timeAxisEnabled),
            targetTicksEnabled = false,
            targetLabelsOutsideRange = true,
            targetLabelsInsidePlot = true,
            rangeBackgroundEnabled = true,
            scaleLaneOpacityPercent = preferences.getInt(KEY_SCALE_LANE_OPACITY, defaults.scaleLaneOpacityPercent).coerceIn(0, 100),
            maximumScaleEnabled = preferences.getBoolean("graph_style_maximum_scale_enabled", defaults.maximumScaleEnabled),
        )
    }

    fun saveGraphStyle(value: SharedWearCgmGraphStyle) =
        update {
            putFloat(KEY_DOT_RADIUS, value.dotRadiusDp.coerceIn(1.5f, 6f))
            putBoolean(KEY_DOT_OUTLINE, true)
            putBoolean(KEY_HISTORICAL_DOT_OUTLINE, value.historicalDotOutlineEnabled)
            putBoolean(KEY_CURRENT_DOT_OUTLINE, true)
            putFloat(KEY_DOT_OUTLINE_WIDTH, value.dotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_HISTORICAL_DOT_OUTLINE_WIDTH, value.historicalDotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_CURRENT_DOT_OUTLINE_WIDTH, value.currentDotOutlineWidthDp.coerceIn(.25f, 3f))
            putFloat(KEY_CORNER_RADIUS, value.cornerRadiusDp.coerceIn(0f, 40f))
            putBoolean(KEY_BORDER_ENABLED, value.borderEnabled)
            putBoolean(KEY_TIME_AXIS_ENABLED, value.timeAxisEnabled)
            putInt(KEY_SCALE_LANE_OPACITY, value.scaleLaneOpacityPercent.coerceIn(0, 100))
            putBoolean("graph_style_maximum_scale_enabled", value.maximumScaleEnabled)
            remove(KEY_TARGET_TICKS_ENABLED)
        }

    fun graphColors(): WatchGraphColors {
        val d = DirectToWatchGraphColorDefaults.create()
        return d.copy(
            graphBackground = color("graph_color_background", d.graphBackground),
            rangeLow = color("graph_color_range_low", d.rangeLow),
            rangeInRange = color("graph_color_range_in", d.rangeInRange),
            rangeHigh = color("graph_color_range_high", d.rangeHigh),
            cgmLow = color("graph_color_cgm_low", d.cgmLow),
            cgmInRange = color("graph_color_cgm_in", d.cgmInRange),
            cgmHigh = color("graph_color_cgm_high", d.cgmHigh),
            cgmVeryLow = color("graph_color_cgm_very_low", d.cgmVeryLow),
            cgmVeryHigh = color("graph_color_cgm_very_high", d.cgmVeryHigh),
            divider = color("graph_color_divider", d.divider),
            border = color("graph_color_border", d.border),
            highLine = color("graph_color_high_line", d.highLine),
            lowLine = color("graph_color_low_line", d.lowLine),
            axisLabel = color("graph_color_axis_label", d.axisLabel),
            axisTick = color("graph_color_axis_tick", d.axisTick),
            nowLine = color("graph_color_now_line", d.nowLine),
            outline = color("graph_color_outline", d.outline),
            predictionIob = color("graph_color_prediction_iob", d.predictionIob),
            predictionCob = color("graph_color_prediction_cob", d.predictionCob),
            predictionUam = color("graph_color_prediction_uam", d.predictionUam),
            predictionZeroTemp = color("graph_color_prediction_zero_temp", d.predictionZeroTemp),
            targetValue = color("graph_color_target_value", d.targetValue),
            signalLoss = color("graph_color_signal_loss", d.signalLoss),
        )
    }

    fun saveGraphColors(value: WatchGraphColors) =
        update {
            putInt("graph_color_background", value.graphBackground)
            putInt("graph_color_range_low", value.rangeLow)
            putInt("graph_color_range_in", value.rangeInRange)
            putInt("graph_color_range_high", value.rangeHigh)
            putInt("graph_color_cgm_low", value.cgmLow)
            putInt("graph_color_cgm_in", value.cgmInRange)
            putInt("graph_color_cgm_high", value.cgmHigh)
            putInt("graph_color_cgm_very_low", value.cgmVeryLow)
            putInt("graph_color_cgm_very_high", value.cgmVeryHigh)
            putInt("graph_color_divider", value.divider)
            putInt("graph_color_border", value.border)
            putInt("graph_color_high_line", value.highLine)
            putInt("graph_color_low_line", value.lowLine)
            putInt("graph_color_axis_label", value.axisLabel)
            putInt("graph_color_axis_tick", value.axisTick)
            putInt("graph_color_now_line", value.nowLine)
            putInt("graph_color_outline", value.outline)
            putInt("graph_color_prediction_iob", value.predictionIob)
            putInt("graph_color_prediction_cob", value.predictionCob)
            putInt("graph_color_prediction_uam", value.predictionUam)
            putInt("graph_color_prediction_zero_temp", value.predictionZeroTemp)
            putInt("graph_color_target_value", value.targetValue)
            putInt("graph_color_signal_loss", value.signalLoss)
        }

    fun trendStyle(mode: AppearanceMode): TrendArrowStyle = TrendArrowStylePreferences.read(preferences, mode, 0xFFFFFFFF.toInt())

    fun saveTrendStyle(
        mode: AppearanceMode,
        value: TrendArrowStyle,
    ) {
        TrendArrowStylePreferences.write(preferences, mode, value)
        sync()
    }

    fun resetTrendStyle(mode: AppearanceMode) {
        TrendArrowStylePreferences.reset(preferences, mode)
        sync()
    }

    fun resetGraph() =
        update {
            preferences.all.keys
                .filter { it.startsWith("graph_") }
                .forEach(::remove)
        }

    fun sync() {
        val values =
            Bundle().apply {
                preferences.all.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }
        context.sendBroadcast(
            Intent(DirectToWatchSettingsContract.ACTION_APPLY)
                .setComponent(ComponentName(DirectToWatchSettingsContract.TARGET_PACKAGE, DirectToWatchSettingsContract.TARGET_RECEIVER))
                .putExtra(DirectToWatchSettingsContract.EXTRA_VALUES, values),
        )
    }

    private fun color(
        key: String,
        default: Int,
    ) = preferences.getInt(key, default)

    private inline fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        preferences.edit(action = block)
        sync()
    }

    companion object {
        val HOUR_OPTIONS = listOf(1, 2, 3, 6, 12, 24)
        private const val KEY_HOURS = "graph.hours"
        private const val KEY_GLUCOSE_UNIT = "display.glucose_unit"
        private const val KEY_GLUCOSE_BOLD = "display.glucose_bold"
        private const val KEY_DELTA_UNIT_COLOR = "watchface.delta_unit_color"
        private const val KEY_STATUS_SIZE_PERCENT = "watchface.status_size_percent"
        private const val KEY_STATUS_COLOR = "watchface.status_color"
        private const val KEY_STATUS_BOLD = "watchface.status_bold"
        private const val KEY_CLOCK_SIZE_PERCENT = "watchface.clock_size_percent"
        private const val KEY_CLOCK_COLOR = "watchface.clock_color"
        private const val KEY_CLOCK_BOLD = "watchface.clock_bold"
        private const val KEY_ACTIVE_APPEARANCE_MODE = "appearance.active_mode"
        private const val KEY_TARGET_LOW = "target.low_mg_dl"
        private const val KEY_TARGET_HIGH = "target.high_mg_dl"
        private const val KEY_DOT_RADIUS = "graph_style_dot_radius"
        private const val KEY_DOT_OUTLINE = "graph_style_dot_outline_enabled"
        private const val KEY_HISTORICAL_DOT_OUTLINE = "graph_style_historical_dot_outline_enabled"
        private const val KEY_CURRENT_DOT_OUTLINE = "graph_style_current_dot_outline_enabled"
        private const val KEY_DOT_OUTLINE_WIDTH = "graph_style_dot_outline_width"
        private const val KEY_HISTORICAL_DOT_OUTLINE_WIDTH = "graph_style_historical_dot_outline_width"
        private const val KEY_CURRENT_DOT_OUTLINE_WIDTH = "graph_style_current_dot_outline_width"
        private const val KEY_CORNER_RADIUS = "graph_style_corner_radius"
        private const val KEY_BORDER_ENABLED = "graph_style_border_enabled"
        private const val KEY_TIME_AXIS_ENABLED = "graph_style_time_axis_enabled"
        private const val KEY_SCALE_LANE_OPACITY = "graph_style_scale_lane_opacity_percent"
        private const val KEY_TARGET_TICKS_ENABLED = "graph_style_target_ticks_enabled"
        private const val LEGACY_KEY_RANGE_BACKGROUND_ENABLED = "graph_style_range_background_enabled"
    }
}
