package app.sugarlicious.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.CgmSourceState
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.cgmBoundaryDisplay
import app.sugarlicious.protocol.DirectToWatchGraphColorDefaults
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.storage.TrendArrowStylePreferences
import app.sugarlicious.uishared.DirectToWatchGraphDefaults
import app.sugarlicious.uishared.SharedWearCgmGraphInput
import app.sugarlicious.uishared.SharedWearCgmGraphPalette
import app.sugarlicious.uishared.SharedWearCgmGraphRenderer
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

internal fun formatVigilSystemTime(
    context: Context,
    nowEpochMs: Long,
): String =
    android.text.format.DateFormat
        .getTimeFormat(context)
        .format(Date(nowEpochMs))

internal data class DirectToWatchHeaderPresentation(
    val glucose: String,
    val secondary: String,
    val trend: Trend? = null,
    val sensorError: Boolean = false,
    val trendUnavailable: Boolean = false,
    val sensorDisconnected: Boolean = false,
    val collectorPaused: Boolean = false,
    val signalLoss: Boolean = false,
)

internal data class DirectToWatchGraphStatusPresentation(
    val text: String,
)

internal fun vigilStatusContrastColor(state: TherapyDisplayState?, nowEpochMs: Long, context: Context): Int {
    val colors = DirectToWatchPreferences.graphColors(context)
    val history = DirectToWatchPresentationFormatter.samples(state, nowEpochMs, DirectToWatchPreferences.graphHours(context))
    val high =
        app.sugarlicious.model.CgmGraphPolicy
            .rangeExcursion(history, DirectToWatchPreferences.thresholds(context)) == app.sugarlicious.model.RangeExcursion.HIGH

    val style = DirectToWatchPreferences.graphStyle(context)
    val metrics = SharedWearCgmGraphRenderer.metrics(450, 205, 2f, DirectToWatchPreferences.thresholds(context), style)
    val labelY = 23f
    val area =
        if (labelY in metrics.highY..metrics.lowY) {
            colors.rangeInRange
        } else if (high && style.rangeBackgroundEnabled) {
            colors.rangeHigh
        } else {
            Color.TRANSPARENT
        }
    return app.sugarlicious.uishared.graphTextContrast(colors.graphBackground, area)
}

internal fun isVigilSensorDisconnected(state: TherapyDisplayState?): Boolean = state == null || DirectSensorReadingResolver.directSensorState(state) in setOf("UNKNOWN", "ENDED", "NOT_ACTIVE")

internal fun directToWatchGraphWindow(
    nowEpochMs: Long,
    graphHours: Int,
): GraphTimeWindow = GraphTimeWindow.live(nowEpochMs, graphHours * DirectToWatchPresentationFormatter.HOUR_MS)

internal object DirectToWatchPresentationFormatter {
    fun header(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
        displayUnit: GlucoseUnit? = null,
    ): DirectToWatchHeaderPresentation {
        if (DirectSensorReadingResolver.directSessionState(state) == "PAUSED" && !isVigilSensorDisconnected(state)) {
            return DirectToWatchHeaderPresentation("-", "Datenempfang pausiert", collectorPaused = true)
        }
        if (isVigilSensorDisconnected(state)) {
            return DirectToWatchHeaderPresentation(
                glucose = "-",
                secondary = "Kein Sensor verbunden",
                trendUnavailable = true,
                sensorDisconnected = true,
            )
        }
        when (DirectSensorReadingResolver.directSensorState(state)) {
            "ERROR" -> return DirectToWatchHeaderPresentation("-", "Sensorfehler", sensorError = true)
        }
        val freshness = TherapyDisplayFormatter.freshness(state, nowEpochMs)
        val retained = freshness in setOf(Freshness.CURRENT, Freshness.DELAYED, Freshness.STALE, Freshness.SIGNAL_LOSS)
        if (!isDirect(state) || state?.glucose == null || !retained) {
            if (activeSessionWithoutData(state)) {
                return DirectToWatchHeaderPresentation(
                    glucose = "-",
                    secondary = "Keine aktuellen Daten\nBitte warten oder Verbindung\nzum Sensor prüfen",
                )
            }
            return DirectToWatchHeaderPresentation(
                glucose = "-",
                secondary =
                    if (state?.glucose != null || freshness == Freshness.STALE) {
                        "Keine aktuellen\nGlukosewerte oder Alarme\nverfügbar"
                    } else {
                        "Bitte Sensor\nstarten oder\nverbinden"
                    },
            )
        }
        val glucose = requireNotNull(state.glucose)
        cgmBoundaryDisplay(glucose.valueMgDl)?.let { boundary ->
            return DirectToWatchHeaderPresentation(
                glucose = boundary.label,
                secondary = "",
                trend = null,
                signalLoss = freshness == Freshness.SIGNAL_LOSS,
            )
        }
        val resolvedUnit = displayUnit ?: glucose.displayUnit
        val delta = TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, resolvedUnit).ifBlank { "-" }
        val unit = if (resolvedUnit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
        return DirectToWatchHeaderPresentation(
            glucose =
                if (resolvedUnit == GlucoseUnit.MMOL_L) {
                    String.format(java.util.Locale.getDefault(), "%.1f", glucose.valueMgDl / 18.0)
                } else {
                    glucose.valueMgDl.toInt().toString()
                },
            secondary = listOf(delta, unit).filter(String::isNotBlank).joinToString(" "),
            trend = glucose.trend.takeIf { TherapyDisplayFormatter.trendArrow(it).isNotBlank() },
            trendUnavailable = TherapyDisplayFormatter.trendArrow(glucose.trend).isBlank(),
            signalLoss = freshness == Freshness.SIGNAL_LOSS,
        )
    }

    fun graphStatus(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
        graphHours: Int,
    ): DirectToWatchGraphStatusPresentation {
        if (isVigilSensorDisconnected(state)) {
            return DirectToWatchGraphStatusPresentation("${graphHours}h")
        }
        if (!isDirect(state) || state?.glucose == null) {
            return DirectToWatchGraphStatusPresentation("")
        }
        val glucose = requireNotNull(state.glucose)
        val age = TherapyDisplayFormatter.ageMinutesValue(glucose.measuredAtEpochMs, nowEpochMs)?.let { "${it}m" } ?: "\u2014"
        return DirectToWatchGraphStatusPresentation("${graphHours}h \u2022 $age")
    }

    fun samples(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
        graphHours: Int,
    ): List<GlucoseSample> {
        if (!isDirect(state)) return emptyList()
        val cutoff = nowEpochMs - graphHours * HOUR_MS
        return buildList {
            addAll(state?.glucoseHistory.orEmpty())
            state?.glucose?.let { glucose ->
                add(
                    GlucoseSample(
                        valueMgDl = glucose.valueMgDl,
                        measuredAtEpochMs = glucose.measuredAtEpochMs,
                        source = glucose.source,
                        sensorId = glucose.sensorId,
                        sessionId = glucose.sessionId,
                        sequenceNumber = glucose.sequenceNumber,
                        receivedAtEpochMs = glucose.receivedAtEpochMs,
                        quality = glucose.quality,
                    ),
                )
            }
        }.asSequence()
            .filter {
                it.source == DataSourceId.DEXCOM_G7_WATCH &&
                    it.quality == CgmQuality.VALID &&
                    it.valueMgDl.isFinite() &&
                    it.valueMgDl in 20.0..1_000.0 &&
                    it.measuredAtEpochMs in cutoff..(nowEpochMs + FUTURE_TOLERANCE_MS)
            }.distinctBy { listOf(it.sensorId, it.sessionId, it.sequenceNumber, it.measuredAtEpochMs) }
            .sortedBy(GlucoseSample::measuredAtEpochMs)
            .toList()
    }

    fun validTimeRange(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): TimeRange {
        // The provider owns STALE/NO_SOURCE rendering. Expiring the complication makes Wear OS
        // replace the information with EMPTY, especially in ambient mode.
        return TimeRange.ALWAYS
    }

    fun isDirect(state: TherapyDisplayState?): Boolean =
        state?.source == DataSourceId.DEXCOM_G7_WATCH &&
            DirectSensorReadingResolver.sourceState(state) in setOf(CgmSourceState.WATCH_DIRECT, CgmSourceState.NO_SOURCE)

    fun activeSessionWithoutData(state: TherapyDisplayState?): Boolean =
        state?.glucose == null &&
            DirectSensorReadingResolver.directSessionState(state) in
            setOf(
                "AUTHENTICATED",
                "READY_FOR_RECONNECT",
                "ACTIVE",
                "WAITING_FOR_NEXT_READING",
                "RECOVERING",
            )

    const val HOUR_MS = 60L * 60_000L
    private const val FUTURE_TOLERANCE_MS = 5L * 60_000L
}

object DirectToWatchPreferences {
    const val NAME = "direct_to_watch"
    private const val KEY_GRAPH_HOURS = "graph.hours"
    private const val KEY_GRAPH_DOT_RADIUS = "graph_style_dot_radius"
    private const val KEY_GRAPH_DOT_OUTLINE_ENABLED = "graph_style_dot_outline_enabled"
    private const val KEY_GRAPH_HISTORICAL_DOT_OUTLINE_ENABLED = "graph_style_historical_dot_outline_enabled"
    private const val KEY_GRAPH_CURRENT_DOT_OUTLINE_ENABLED = "graph_style_current_dot_outline_enabled"
    private const val KEY_GRAPH_DOT_OUTLINE_WIDTH = "graph_style_dot_outline_width"
    private const val KEY_GRAPH_HISTORICAL_DOT_OUTLINE_WIDTH = "graph_style_historical_dot_outline_width"
    private const val KEY_GRAPH_CURRENT_DOT_OUTLINE_WIDTH = "graph_style_current_dot_outline_width"
    private const val KEY_GRAPH_SCALE_LANE_OPACITY = "graph_style_scale_lane_opacity_percent"
    private const val KEY_GLUCOSE_UNIT = "display.glucose_unit"
    private const val KEY_GLUCOSE_BOLD = "display.glucose_bold"
    private const val KEY_DELTA_UNIT_COLOR = "watchface.delta_unit_color"
    private const val KEY_STATUS_SIZE_PERCENT = "watchface.status_size_percent"
    private const val KEY_STATUS_COLOR = "watchface.status_color"
    private const val KEY_STATUS_BOLD = "watchface.status_bold"
    private const val KEY_CLOCK_SIZE_PERCENT = "watchface.clock_size_percent"
    private const val KEY_CLOCK_COLOR = "watchface.clock_color"
    private const val KEY_CLOCK_BOLD = "watchface.clock_bold"
    private const val KEY_TARGET_LOW = "target.low_mg_dl"
    private const val KEY_TARGET_HIGH = "target.high_mg_dl"
    val graphHourOptions = listOf(1, 2, 3, 6, 12, 24)

    fun graphHours(context: Context): Int =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt(KEY_GRAPH_HOURS, 2)
            .takeIf { it in graphHourOptions } ?: 2

    fun glucoseUnit(context: Context): GlucoseUnit =
        runCatching {
            GlucoseUnit.valueOf(
                app.sugarlicious.uishared.SugarWearDisplayDefaults
                    .preferences(context, NAME)
                    .getString(KEY_GLUCOSE_UNIT, GlucoseUnit.MG_DL.name)!!,
            )
        }.getOrDefault(GlucoseUnit.MG_DL)

    fun glucoseBold(context: Context): Boolean =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getBoolean(KEY_GLUCOSE_BOLD, true)

    fun deltaUnitColor(
        context: Context,
        mode: AppearanceMode,
    ): Int =
        app.sugarlicious.uishared.SugarWearDisplayDefaults.preferences(context, NAME).getInt(
            "$KEY_DELTA_UNIT_COLOR.${mode.storageKey}",
            if (mode == AppearanceMode.LIGHT) 0xFF666666.toInt() else 0xFFA8A8BA.toInt(),
        )

    fun statusSizePercent(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt(KEY_STATUS_SIZE_PERCENT, 100)
            .coerceIn(75, 150)

    fun statusColor(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt(KEY_STATUS_COLOR, Color.WHITE)

    fun scaleSizeEnabled(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getBoolean("watchface.scale_size_enabled", false)

    fun scaleSizePercent(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt("watchface.scale_size_percent", 100)
            .coerceIn(50, 120)

    fun statusBold(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getBoolean(KEY_STATUS_BOLD, true)

    fun clockSizePercent(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt(KEY_CLOCK_SIZE_PERCENT, 280)
            .coerceIn(75, 450)

    fun clockColor(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getInt(KEY_CLOCK_COLOR, Color.WHITE)

    fun clockBold(context: Context) =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getBoolean(KEY_CLOCK_BOLD, true)

    fun thresholds(context: Context): CgmThresholds {
        val preferences =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, NAME)
        return CgmThresholds(
            veryHighMgDl = CgmThresholds.DEFAULT_VERY_HIGH_MG_DL,
            highMgDl = preferences.getFloat(KEY_TARGET_HIGH, CgmThresholds.DEFAULT_HIGH_MG_DL.toFloat()).toDouble(),
            lowMgDl = preferences.getFloat(KEY_TARGET_LOW, CgmThresholds.DEFAULT_LOW_MG_DL.toFloat()).toDouble(),
            veryLowMgDl = CgmThresholds.DEFAULT_VERY_LOW_MG_DL,
        ).takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT
    }

    fun cycleGraphHours(context: Context): Int {
        val current = graphHours(context)
        val next = graphHourOptions[(graphHourOptions.indexOf(current) + 1) % graphHourOptions.size]
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .edit()
            .putInt(KEY_GRAPH_HOURS, next)
            .apply()
        return next
    }

    fun saveGraphHours(
        context: Context,
        hours: Int,
    ) {
        require(hours in graphHourOptions)
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .edit()
            .putInt(KEY_GRAPH_HOURS, hours)
            .apply()
        requestUpdates(context)
    }

    fun graphColors(context: Context): WatchGraphColors {
        val defaults = DirectToWatchGraphColorDefaults.create()
        val p =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, "g7_graph_colors")
        val prefix = "dark."
        return WatchGraphColors(
            graphBackground = p.getInt(prefix + "background", p.getInt("graph_color_background", defaults.graphBackground)),
            rangeLow = p.getInt(prefix + "range_low", p.getInt("graph_color_range_low", defaults.rangeLow)),
            rangeInRange = p.getInt(prefix + "range_in", p.getInt("graph_color_range_in", defaults.rangeInRange)),
            rangeHigh = p.getInt(prefix + "range_high", p.getInt("graph_color_range_high", defaults.rangeHigh)),
            cgmLow = p.getInt(prefix + "cgm_low", p.getInt("graph_color_cgm_low", defaults.cgmLow)),
            cgmInRange = p.getInt(prefix + "cgm_in", p.getInt("graph_color_cgm_in", defaults.cgmInRange)),
            cgmHigh = p.getInt(prefix + "cgm_high", p.getInt("graph_color_cgm_high", defaults.cgmHigh)),
            cgmVeryLow = p.getInt(prefix + "cgm_very_low", p.getInt("graph_color_cgm_very_low", defaults.cgmVeryLow)),
            cgmVeryHigh = p.getInt(prefix + "cgm_very_high", p.getInt("graph_color_cgm_very_high", defaults.cgmVeryHigh)),
            divider = p.getInt(prefix + "divider", p.getInt("graph_color_divider", defaults.divider)),
            border = p.getInt(prefix + "border", p.getInt("graph_color_border", defaults.border)),
            highLine = p.getInt(prefix + "high_line", p.getInt("graph_color_high_line", defaults.highLine)),
            lowLine = p.getInt(prefix + "low_line", p.getInt("graph_color_low_line", defaults.lowLine)),
            axisLabel = p.getInt(prefix + "axis_label", p.getInt("graph_color_axis_label", defaults.axisLabel)),
            axisTick = p.getInt(prefix + "axis_tick", p.getInt("graph_color_axis_tick", defaults.axisTick)),
            nowLine = p.getInt(prefix + "now_line", p.getInt("graph_color_now_line", defaults.nowLine)),
            outline = p.getInt(prefix + "outline", p.getInt("graph_color_outline", defaults.outline)),
            targetValue = p.getInt(prefix + "target_value", p.getInt("graph_color_target_value", defaults.targetValue)),
            signalLoss = p.getInt(prefix + "signal_loss", p.getInt("graph_color_signal_loss", defaults.signalLoss)),
            predictionIob = p.getInt(prefix + "prediction_iob", p.getInt("graph_color_prediction_iob", defaults.predictionIob)),
            predictionCob = p.getInt(prefix + "prediction_cob", p.getInt("graph_color_prediction_cob", defaults.predictionCob)),
            predictionUam = p.getInt(prefix + "prediction_uam", p.getInt("graph_color_prediction_uam", defaults.predictionUam)),
            predictionZeroTemp = p.getInt(prefix + "prediction_zero_temp", p.getInt("graph_color_prediction_zero_temp", defaults.predictionZeroTemp)),
        )
    }

    fun saveGraphColors(
        context: Context,
        colors: WatchGraphColors,
    ) {
        val p =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, "g7_graph_colors")
        val prefix = "dark."
        p.edit()
            .putInt(prefix + "background", colors.graphBackground)
            .putInt(prefix + "range_low", colors.rangeLow)
            .putInt(prefix + "range_in", colors.rangeInRange)
            .putInt(prefix + "range_high", colors.rangeHigh)
            .putInt(prefix + "cgm_low", colors.cgmLow)
            .putInt(prefix + "cgm_in", colors.cgmInRange)
            .putInt(prefix + "cgm_high", colors.cgmHigh)
            .putInt(prefix + "cgm_very_low", colors.cgmVeryLow)
            .putInt(prefix + "cgm_very_high", colors.cgmVeryHigh)
            .putInt(prefix + "divider", colors.divider)
            .putInt(prefix + "border", colors.border)
            .putInt(prefix + "high_line", colors.highLine)
            .putInt(prefix + "low_line", colors.lowLine)
            .putInt(prefix + "axis_label", colors.axisLabel)
            .putInt(prefix + "axis_tick", colors.axisTick)
            .putInt(prefix + "now_line", colors.nowLine)
            .putInt(prefix + "outline", colors.outline)
            .putInt(prefix + "target_value", colors.targetValue)
            .putInt(prefix + "signal_loss", colors.signalLoss)
            .putInt(prefix + "prediction_iob", colors.predictionIob)
            .putInt(prefix + "prediction_cob", colors.predictionCob)
            .putInt(prefix + "prediction_uam", colors.predictionUam)
            .putInt(prefix + "prediction_zero_temp", colors.predictionZeroTemp)
            .apply()
        requestUpdates(context)
    }

    fun graphStyle(context: Context): SharedWearCgmGraphStyle {
        val defaults = DirectToWatchGraphDefaults.vigilStyle()
        val p =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, "g7_direct_to_watch_settings")
        return defaults.copy(
            dotRadiusDp = p.getFloat("graph_style_dot_radius", defaults.dotRadiusDp).coerceIn(1.5f, 6f),
            dotOutlineEnabled = true,
            historicalDotOutlineEnabled = p.getBoolean("graph_style_historical_dot_outline_enabled", p.getBoolean("graph_style_dot_outline_enabled", defaults.historicalDotOutlineEnabled)),
            currentDotOutlineEnabled = true,
            dotOutlineWidthDp = p.getFloat("graph_style_dot_outline_width", defaults.dotOutlineWidthDp).coerceIn(0.25f, 3f),
            historicalDotOutlineWidthDp = p.getFloat("graph_style_historical_dot_outline_width", p.getFloat("graph_style_dot_outline_width", defaults.historicalDotOutlineWidthDp)).coerceIn(0.25f, 3f),
            currentDotOutlineWidthDp = p.getFloat("graph_style_current_dot_outline_width", p.getFloat("graph_style_dot_outline_width", defaults.currentDotOutlineWidthDp)).coerceIn(0.25f, 3f),
            cornerRadiusDp = p.getFloat("graph_style_corner_radius", defaults.cornerRadiusDp).coerceIn(0f, 40f),
            borderEnabled = p.getBoolean("graph_style_border_enabled", defaults.borderEnabled),
            timeAxisEnabled = p.getBoolean("graph_style_time_axis_enabled", defaults.timeAxisEnabled),
            targetTicksEnabled = false,
            targetLabelsOutsideRange = true,
            targetLabelsInsidePlot = true,
            // Vigil follows the canonical graph policy unconditionally; this is not a face-local toggle.
            rangeBackgroundEnabled = true,
            scaleLaneOpacityPercent = p.getInt("graph_style_scale_lane_opacity_percent", defaults.scaleLaneOpacityPercent).coerceIn(0, 100),
            maximumScaleEnabled = p.getBoolean("graph_style_maximum_scale_enabled", defaults.maximumScaleEnabled),
        )
    }

    fun saveGraphStyle(
        context: Context,
        style: SharedWearCgmGraphStyle,
    ) {
        val p =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, "g7_direct_to_watch_settings")
        p.edit()
            .putFloat("graph_style_dot_radius", style.dotRadiusDp.coerceIn(1.5f, 6f))
            .putBoolean("graph_style_dot_outline_enabled", true)
            .putBoolean("graph_style_historical_dot_outline_enabled", style.historicalDotOutlineEnabled)
            .putBoolean("graph_style_current_dot_outline_enabled", true)
            .putFloat("graph_style_dot_outline_width", style.dotOutlineWidthDp.coerceIn(0.25f, 3f))
            .putFloat("graph_style_historical_dot_outline_width", style.historicalDotOutlineWidthDp.coerceIn(0.25f, 3f))
            .putFloat("graph_style_current_dot_outline_width", style.currentDotOutlineWidthDp.coerceIn(0.25f, 3f))
            .putFloat("graph_style_corner_radius", style.cornerRadiusDp.coerceIn(0f, 40f))
            .putBoolean("graph_style_border_enabled", style.borderEnabled)
            .putBoolean("graph_style_time_axis_enabled", style.timeAxisEnabled)
            .putInt("graph_style_scale_lane_opacity_percent", style.scaleLaneOpacityPercent.coerceIn(0, 100))
            .putBoolean("graph_style_maximum_scale_enabled", style.maximumScaleEnabled)
            .remove("graph_style_target_ticks_enabled")
            .remove("graph_style_range_background_enabled")
            .apply()
        requestUpdates(context)
    }

    fun resetGraphAppearance(context: Context) {
        val p =
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, "g7_direct_to_watch_settings")
        p
            .edit()
            .apply {
                p.all.keys
                    .filter { it.startsWith("graph_color_") || it.startsWith("graph_style_") }
                    .forEach(::remove)
            }.apply()
        requestUpdates(context)
    }

    fun trendStyle(
        context: Context,
        mode: AppearanceMode,
    ) = TrendArrowStylePreferences.read(
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME),
        mode,
        Color.WHITE,
    )

    fun activeAppearanceMode(context: Context): AppearanceMode =
        app.sugarlicious.uishared.SugarWearDisplayDefaults
            .preferences(context, NAME)
            .getString("appearance.active_mode", null)
            ?.let { stored -> AppearanceMode.entries.firstOrNull { it.storageKey == stored } }
            ?: AppearanceMode.DARK

    fun saveTrendStyle(
        context: Context,
        mode: AppearanceMode,
        style: app.sugarlicious.model.TrendArrowStyle,
    ) {
        TrendArrowStylePreferences.write(
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, NAME),
            mode,
            style,
        )
        requestUpdates(context)
    }

    fun resetTrendStyle(
        context: Context,
        mode: AppearanceMode,
    ) {
        TrendArrowStylePreferences.reset(
            app.sugarlicious.uishared.SugarWearDisplayDefaults
                .preferences(context, NAME),
            mode,
        )
        requestUpdates(context)
    }

    fun requestClockUpdate(context: Context, provider: Class<*>) {
        require(provider == DirectToWatchClockComplication::class.java || provider == DirectToWatchAmbientClockComplication::class.java)
        ActiveProviderUpdates.request(context, provider)
    }

    fun requestUpdates(context: Context) {
        SugarWearComplicationUpdates.request(context)
        listOf(
            DirectToWatchHeaderComplication::class.java,
            DirectToWatchAnalogHeaderComplication::class.java,
            DirectToWatchGraphComplication::class.java,
            DirectToWatchStatusComplication::class.java,
            DirectToWatchClockComplication::class.java,
            DirectToWatchAmbientClockComplication::class.java,
            DirectToWatchAmbientHeaderComplication::class.java,
            DirectToWatchAmbientGraphComplication::class.java,
        ).forEach { service ->
            ActiveProviderUpdates.request(context, service)
        }
    }
}

abstract class DirectToWatchComplicationService : TrackedComplicationService() {
    final override fun getPreviewData(type: ComplicationType): ComplicationData {
        val preview = build(previewState(), System.currentTimeMillis()) as SmallImageComplicationData
        // Native editors cache demo images. A live minute/reading validity window
        // makes that cached artwork turn into the system's warning placeholder.
        return SmallImageComplicationData
            .Builder(preview.smallImage, requireNotNull(preview.contentDescription))
            .setTapAction(preview.tapAction)
            .setValidTimeRange(TimeRange.ALWAYS)
            .build()
    }

    final override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        withContext(Dispatchers.IO) {
            ActiveProviderUpdates.activate(this@DirectToWatchComplicationService, this@DirectToWatchComplicationService.javaClass, request.complicationInstanceId)
            build(DirectSensorReadingResolver.resolveWatchDirect(this@DirectToWatchComplicationService), System.currentTimeMillis())
        }

    protected abstract fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData

    protected fun collectorTapAction(): PendingIntent = DirectToWatchActions.collector(this)

    protected fun graphScaleTapAction(): PendingIntent =
        DirectToWatchActions.graphScale(this)

    protected fun readThresholds(): CgmThresholds = DirectToWatchPreferences.thresholds(this)

    private fun previewState(): TherapyDisplayState {
        val now = System.currentTimeMillis()
        val history =
            (0..36).map { index ->
                GlucoseSample(
                    valueMgDl = 105.0 + index * 1.3,
                    measuredAtEpochMs = now - (36 - index) * 5L * 60_000L,
                    source = DataSourceId.DEXCOM_G7_WATCH,
                )
            }
        return TherapyDisplayState(
            source = DataSourceId.DEXCOM_G7_WATCH,
            sourceContract = "CANONICAL_CGM_V2:WATCH_DIRECT:preview",
            sourceVersion = "SugarWear",
            receivedAtEpochMs = now,
            glucose =
                GlucoseState(
                    valueMgDl = 152.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FLAT,
                    measuredAtEpochMs = now - 2 * 60_000L,
                    deltaMgDl = 1.0,
                    source = DataSourceId.DEXCOM_G7_WATCH,
                ),
            glucoseHistory = history,
            target = TargetState(lowMgDl = 70.0, highMgDl = 180.0),
        )
    }
}

open class DirectToWatchHeaderComplication : DirectToWatchComplicationService() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val presentation = DirectToWatchPresentationFormatter.header(state, nowEpochMs, DirectToWatchPreferences.glucoseUnit(this))
        val bitmap = renderHeader(presentation, DirectToWatchPreferences.glucoseBold(this))
        return SmallImageComplicationData
            .Builder(
                SmallImage
                    .Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO)
                    .setAmbientImage(Icon.createWithBitmap(renderHeader(presentation, DirectToWatchPreferences.glucoseBold(this), ambient = true)))
                    .build(),
                PlainComplicationText.Builder("SugarWear ${presentation.glucose}, ${presentation.secondary}").build(),
            ).setValidTimeRange(DirectToWatchPresentationFormatter.validTimeRange(state, nowEpochMs))
            .build()
    }

    internal fun renderHeader(
        presentation: DirectToWatchHeaderPresentation,
        glucoseBold: Boolean = true,
        ambient: Boolean = false,
    ): Bitmap {
        if (isVigilPairingRequired(presentation) || presentation.collectorPaused || presentation.glucose == "-") {
            val message =
                when {
                    presentation.collectorPaused -> "Datenempfang pausiert"
                    isVigilPairingRequired(presentation) -> "Kein Sensor verbunden"
                    presentation.sensorError -> "Sensorfehler"
                    else -> "Keine Daten"
                }
            return renderVigilInactiveHeader(this, ambient, presentation.collectorPaused, message)
        }
        val width = 340
        val height = 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val valuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    if (presentation.signalLoss) {
                        0xFF9E9E9E.toInt()
                    } else if (ambient) {
                        0xFFD0D0D0.toInt()
                    } else {
                        Color.WHITE
                    }
                textSize = 61f
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, glucoseBold)
                textAlign = Paint.Align.LEFT
            }
        val mode = DirectToWatchPreferences.activeAppearanceMode(this)
        val secondaryPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    if (ambient) AMBIENT_SECONDARY else DirectToWatchPreferences.deltaUnitColor(this@DirectToWatchHeaderComplication, mode)
                textSize = 23f
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
                textAlign = Paint.Align.LEFT
            }
        val configuredStyle = DirectToWatchPreferences.trendStyle(this, mode)
        val style =
            if (ambient) {
                configuredStyle.copy(
                    fillColor = AMBIENT_PRIMARY,
                    outlineColor = AMBIENT_SECONDARY,
                )
            } else {
                configuredStyle
            }
        val arrow =
            presentation.trend
                .takeUnless { presentation.signalLoss }
                ?.let {
                    TrendComplicationIcon.renderScaled(this, it, 34, style.sizePercent, style = style)
                }?.let(TrendComplicationIcon::cropTransparentPadding)
        val gap = if (arrow == null) 0f else 6f
        val valueWidth = valuePaint.measureText(presentation.glucose)
        val valueBaseline = 58f
        val valueBounds =
            android.graphics.Rect().also {
                valuePaint.getTextBounds(presentation.glucose, 0, presentation.glucose.length, it)
            }
        val valueCenterY = valueBaseline + (valueBounds.top + valueBounds.bottom) / 2f
        // Local center compensates the WFF slot's x=90 offset on the 450px face.
        val centerX = 135f
        val unavailableWidth = if (arrow == null && presentation.trendUnavailable && !presentation.signalLoss) 8f + secondaryPaint.measureText("-") else 0f
        val groupWidth = valueWidth + (arrow?.let { gap + it.width } ?: unavailableWidth)
        val contentLeft = centerX - groupWidth / 2f
        canvas.drawText(presentation.glucose, contentLeft, valueBaseline, valuePaint)
        arrow?.let {
            val arrowTop = valueCenterY - it.height / 2f
            canvas.drawBitmap(it, contentLeft + valueWidth + gap, arrowTop, null)
        }
        if (arrow == null && presentation.trendUnavailable && !presentation.signalLoss) {
            canvas.drawText("-", contentLeft + valueWidth + 8f, valueBaseline, secondaryPaint)
        }
        if (presentation.sensorDisconnected) {
            secondaryPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(presentation.secondary, centerX, 96f, secondaryPaint)
            return bitmap
        }
        val delta = presentation.secondary.substringBeforeLast(' ', presentation.secondary)
        val unit = presentation.secondary.substringAfterLast(' ', "")
        val deltaPaint =
            Paint(secondaryPaint).apply {
                color = if (ambient) AMBIENT_PRIMARY else Color.WHITE
                textSize = 29f
            }
        if (presentation.signalLoss) return bitmap
        val secondaryWidth = deltaPaint.measureText(delta) + if (unit.isBlank()) 0f else 6f + secondaryPaint.measureText(unit)
        val secondaryLeft = centerX - secondaryWidth / 2f
        canvas.drawText(delta, secondaryLeft, 96f, deltaPaint)
        if (unit.isNotBlank()) canvas.drawText(unit, secondaryLeft + deltaPaint.measureText(delta) + 6f, 96f, secondaryPaint)
        return bitmap
    }

    private companion object {
        const val AMBIENT_PRIMARY = 0xFFD0D0D0.toInt()
        const val AMBIENT_SECONDARY = 0xFF707070.toInt()
    }
}

/** Full-face transparent text layer selected natively when Vigil hands are visible. */
class DirectToWatchAnalogHeaderComplication : DirectToWatchHeaderComplication() {
    override fun build(state: TherapyDisplayState?, nowEpochMs: Long): ComplicationData {
        val presentation = DirectToWatchPresentationFormatter.header(state, nowEpochMs, DirectToWatchPreferences.glucoseUnit(this))
        return SmallImageComplicationData
            .Builder(
                SmallImage
                    .Builder(Icon.createWithBitmap(renderAnalogHeader(presentation)), SmallImageType.PHOTO)
                    .setAmbientImage(Icon.createWithBitmap(renderAnalogHeader(presentation, ambient = true)))
                    .build(),
                PlainComplicationText.Builder("SugarWear ${presentation.glucose}, ${presentation.secondary}").build(),
            ).setValidTimeRange(DirectToWatchPresentationFormatter.validTimeRange(state, nowEpochMs))
            .build()
    }

    internal fun renderAnalogHeader(presentation: DirectToWatchHeaderPresentation, ambient: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val header = renderHeader(presentation, DirectToWatchPreferences.glucoseBold(this), ambient)
        if (isVigilPairingRequired(presentation) || presentation.collectorPaused || presentation.glucose == "-" && !presentation.sensorDisconnected) {
            canvas.drawBitmap(header, 90f, if (isVigilPairingRequired(presentation)) 34f else 22f, null)
            return bitmap
        }
        canvas.drawBitmap(header, android.graphics.Rect(0, 0, 340, 70), RectF(90f, 55f, 430f, 125f), null)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    if (presentation.signalLoss) {
                        Color.rgb(255, 77, 94)
                    } else if (ambient) {
                        0xFFD0D0D0.toInt()
                    } else {
                        Color.WHITE
                    }
                textSize = 29f
                textAlign = Paint.Align.CENTER
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        if (presentation.sensorDisconnected) {
            paint.textSize = 23f
            canvas.drawText(presentation.secondary, 225f, 385f - (paint.ascent() + paint.descent()) / 2f, paint)
        } else if (!presentation.signalLoss && presentation.glucose != "-") {
            val delta = presentation.secondary.substringBeforeLast(' ', presentation.secondary)
            val unit = presentation.secondary.substringAfterLast(' ', "")
            paint.textSize = 36f
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = .5f
            canvas.drawText(delta, 225f, 387f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 23f
            paint.color = if (ambient) 0xFF707070.toInt() else DirectToWatchPreferences.deltaUnitColor(this, DirectToWatchPreferences.activeAppearanceMode(this))
            canvas.drawText(unit, 225f, 418f, paint)
        }
        return bitmap
    }
}

class DirectToWatchAmbientHeaderComplication : DirectToWatchHeaderComplication() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val presentation = DirectToWatchPresentationFormatter.header(state, nowEpochMs, DirectToWatchPreferences.glucoseUnit(this))
        val bitmap = renderHeader(presentation, DirectToWatchPreferences.glucoseBold(this), ambient = true)
        return SmallImageComplicationData
            .Builder(
                SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO).build(),
                PlainComplicationText.Builder("Vigil AOD ${presentation.glucose}").build(),
            ).setValidTimeRange(TimeRange.ALWAYS)
            .build()
    }
}

class DirectToWatchStatusComplication : DirectToWatchComplicationService() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val text = DirectToWatchPresentationFormatter.graphStatus(state, nowEpochMs, DirectToWatchPreferences.graphHours(this)).text
        val bitmap =
            renderWatchfaceText(
                text,
                180,
                34,
                DirectToWatchPreferences.statusSizePercent(this),
                vigilStatusContrastColor(state, nowEpochMs, this),
                DirectToWatchPreferences.statusBold(this),
                Paint.Align.LEFT,
            )
        return SmallImageComplicationData
            .Builder(
                SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO).build(),
                PlainComplicationText.Builder(text).build(),
            ).setTapAction(graphScaleTapAction())
            .setValidTimeRange(DirectToWatchPresentationFormatter.validTimeRange(state, nowEpochMs))
            .build()
    }
}

fun renderVigilClock(context: Context, text: String, ambient: Boolean = false): Bitmap {
    val source = DirectToWatchPreferences.clockColor(context)
    val color = if (ambient) Color.rgb(Color.red(source) * 3 / 5, Color.green(source) * 3 / 5, Color.blue(source) * 3 / 5) else source
    return renderWatchfaceText(text, 270, 78, DirectToWatchPreferences.clockSizePercent(context), color, DirectToWatchPreferences.clockBold(context), Paint.Align.CENTER)
}

open class DirectToWatchClockComplication : DirectToWatchComplicationService() {
    protected fun renderVigilClockState(state: TherapyDisplayState?, nowEpochMs: Long, text: String, ambient: Boolean = false): Bitmap =
        if (vigilNeedsPairing(state, nowEpochMs) || hasVigilSignalLoss(state, nowEpochMs)) Bitmap.createBitmap(270, 78, Bitmap.Config.ARGB_8888) else renderVigilClock(this, text, ambient)

    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val text = formatVigilSystemTime(this, nowEpochMs)
        val bitmap = renderVigilClockState(state, nowEpochMs, text)
        val nextMinute = ((nowEpochMs / 60_000L) + 1L) * 60_000L
        return SmallImageComplicationData
            .Builder(
                SmallImage
                    .Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO)
                    .setAmbientImage(Icon.createWithBitmap(renderVigilClockState(state, nowEpochMs, text, ambient = true)))
                    .build(),
                PlainComplicationText.Builder(text).build(),
            ).setValidTimeRange(
                TimeRange.between(java.time.Instant.ofEpochMilli(nowEpochMs), java.time.Instant.ofEpochMilli(nextMinute)),
            ).build()
    }
}

class DirectToWatchAmbientClockComplication : DirectToWatchClockComplication() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val text = formatVigilSystemTime(this, nowEpochMs)
        val bitmap = renderVigilClockState(state, nowEpochMs, text, ambient = true)
        val nextMinute = ((nowEpochMs / 60_000L) + 1L) * 60_000L
        return SmallImageComplicationData
            .Builder(
                SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO).build(),
                PlainComplicationText.Builder(text).build(),
            ).setValidTimeRange(
                TimeRange.between(java.time.Instant.ofEpochMilli(nowEpochMs), java.time.Instant.ofEpochMilli(nextMinute)),
            ).build()
    }
}

internal fun renderWatchfaceText(
    text: String,
    width: Int,
    height: Int,
    sizePercent: Int,
    color: Int,
    bold: Boolean,
    align: Paint.Align,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = (if (height > 34) 28f else 18f) * sizePercent.coerceIn(75, 450) / 100f
            app.sugarlicious.uishared.SystemTypography
                .apply(this, bold)
            if (height > 34 && bold) {
                typeface = android.graphics.Typeface.create(typeface, 900, false)
                setFontVariationSettings("'wght' 900")
                // Device font aliases can cap their supported weight; retain a strong clock there.
                isFakeBoldText = true
            }
            textAlign = align
        }
    val maxWidth = width - 8f
    val maxHeight = height - 6f
    val measuredWidth = paint.measureText(text)
    val measuredHeight = paint.descent() - paint.ascent()
    val fit = minOf(1f, maxWidth / measuredWidth.coerceAtLeast(1f), maxHeight / measuredHeight.coerceAtLeast(1f))
    paint.textSize *= fit
    val x = if (align == Paint.Align.CENTER) width / 2f else 0f
    val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
    Canvas(bitmap).drawText(text, x, baseline, paint)
    return bitmap
}

open class DirectToWatchGraphComplication : DirectToWatchComplicationService() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val hours = DirectToWatchPreferences.graphHours(this)
        val bitmap = renderGraphAndFooter(state, nowEpochMs, hours)
        return SmallImageComplicationData
            .Builder(
                SmallImage
                    .Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO)
                    .setAmbientImage(Icon.createWithBitmap(renderGraphAndFooter(state, nowEpochMs, hours, ambient = true)))
                    .build(),
                PlainComplicationText.Builder("$hours Stunden SugarWear-Glukoseverlauf").build(),
            ).setTapAction(graphScaleTapAction())
            .setValidTimeRange(DirectToWatchPresentationFormatter.validTimeRange(state, nowEpochMs))
            .build()
    }

    protected fun renderGraphAndFooter(state: TherapyDisplayState?, nowEpochMs: Long, hours: Int, ambient: Boolean = false): Bitmap {
        val image = Bitmap.createBitmap(450, 285, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawBitmap(renderGraph(state, nowEpochMs, hours, ambient), 0f, 0f, null)
            val footer =
                if (hasVigilSignalLoss(state, nowEpochMs)) {
                    renderVigilSignalLossFooter(this@DirectToWatchGraphComplication)
                } else {
                    Bitmap.createBitmap(270, 78, Bitmap.Config.ARGB_8888)
                }
            drawBitmap(footer, 90f, 207f, null)
        }
        return image
    }

    internal fun renderGraph(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
        hours: Int,
        ambient: Boolean = false,
    ): Bitmap {
        val width = 450
        val height = 205
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (DirectSensorReadingResolver.directSensorState(state) == "ERROR") return bitmap
        // This bitmap is WFF artwork in a fixed 450-unit face, not a device-sized View.
        // WFF scales the complete image to the physical display. Applying device density
        // here as well makes only graph text/strokes change relative to the other slots.
        // Reference face: 450 design units across a 225dp Wear display. Retain the
        // readable 24-unit default axis labels and apply accessibility font scale.
        val density = 450f / 225f
        val baseStyle = DirectToWatchPreferences.graphStyle(this)
        val graphStyle =
            baseStyle.copy(
                axisTextSizeSp = baseStyle.axisTextSizeSp * if (DirectToWatchPreferences.scaleSizeEnabled(this)) DirectToWatchPreferences.scaleSizePercent(this) / 100f else 1f,
                timeAxisVisible = !ambient,
                timeHoursBold = false,
                timeAxisBottomInsetDp = 1f,
            )
        // Full bleed at the display midpoint, clipped to the same round face on every size.
        val horizontalInset = 0f
        val graphWidth = width - (horizontalInset * 2).toInt()
        val radius = graphStyle.cornerRadiusDp * density
        val colors = if (ambient) DirectToWatchPreferences.graphColors(this).ambient() else DirectToWatchPreferences.graphColors(this)
        val thresholds = readThresholds()
        canvas.save()
        canvas.translate(horizontalInset, 0f)
        canvas.clipPath(Path().apply { addCircle(225f, 85f, 225f, Path.Direction.CW) })
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, graphWidth.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW) })
        SharedWearCgmGraphRenderer.render(
            canvas = canvas,
            widthPx = graphWidth,
            heightPx = height,
            density = density,
            scaledDensity = density * resources.configuration.fontScale,
            input =
                SharedWearCgmGraphInput(
                    history = DirectToWatchPresentationFormatter.samples(state, nowEpochMs, hours),
                    timeWindow = directToWatchGraphWindow(nowEpochMs, hours),
                    nowEpochMs = nowEpochMs,
                    thresholds = thresholds,
                    palette = colors.toSharedPalette().let { if (ambient) it else it.copy(axisText = Color.WHITE, targetText = Color.WHITE) },
                    style = graphStyle,
                    emptyLabel = "",
                ),
        )
        canvas.restore()
        return bitmap
    }

    private fun WatchGraphColors.toSharedPalette() =
        SharedWearCgmGraphPalette(
            background = graphBackground,
            targetArea = rangeInRange,
            highArea = rangeHigh,
            lowArea = rangeLow,
            highLine = highLine,
            lowLine = lowLine,
            dotHigh = cgmHigh,
            dotVeryHigh = cgmVeryHigh,
            dotInRange = cgmInRange,
            dotLow = cgmLow,
            dotVeryLow = cgmVeryLow,
            dotOutline = outline,
            axisText = axisLabel,
            axisTick = axisTick,
            nowLine = nowLine,
            border = border,
            predictionIob = predictionIob,
            predictionCob = predictionCob,
            predictionUam = predictionUam,
            predictionZeroTemp = predictionZeroTemp,
            targetText = targetValue,
            emptyText = signalLoss,
        )

    internal fun WatchGraphColors.ambient() =
        copy(
            graphBackground = Color.TRANSPARENT,
            rangeLow = Color.TRANSPARENT,
            rangeInRange = ambientGray(rangeInRange, 0x004C4C4C),
            rangeHigh = Color.TRANSPARENT,
            cgmLow = ambientGray(cgmLow, 0x00B0B0B0),
            cgmInRange = ambientGray(cgmInRange, 0x00D0D0D0),
            cgmHigh = ambientGray(cgmHigh, 0x00B0B0B0),
            cgmVeryLow = ambientGray(cgmVeryLow, 0x00909090),
            cgmVeryHigh = ambientGray(cgmVeryHigh, 0x00909090),
            divider = Color.TRANSPARENT,
            highLine = ambientGray(highLine, 0x00888888),
            lowLine = ambientGray(lowLine, 0x00888888),
            axisLabel = ambientGray(axisLabel, 0x00B0B0B0),
            axisTick = ambientGray(axisTick, 0x00888888),
            nowLine = ambientGray(nowLine, 0x00888888),
            outline = ambientGray(outline, 0x00303030),
            predictionIob = ambientGray(predictionIob, 0x00999999),
            predictionCob = ambientGray(predictionCob, 0x00999999),
            predictionUam = ambientGray(predictionUam, 0x00999999),
            predictionZeroTemp = ambientGray(predictionZeroTemp, 0x00999999),
            targetValue = ambientGray(targetValue, 0x00B0B0B0),
            signalLoss = ambientGray(signalLoss, 0x00888888),
        )

    private fun ambientGray(
        source: Int,
        grayRgb: Int,
    ): Int = (source and 0xFF000000.toInt()) or (grayRgb and 0x00FFFFFF)
}

class DirectToWatchAmbientGraphComplication : DirectToWatchGraphComplication() {
    override fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData {
        val hours = DirectToWatchPreferences.graphHours(this)
        val bitmap = renderGraphAndFooter(state, nowEpochMs, hours, ambient = true)
        return SmallImageComplicationData
            .Builder(
                SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO).build(),
                PlainComplicationText.Builder("Vigil AOD Graph").build(),
            ).setTapAction(graphScaleTapAction())
            .setValidTimeRange(TimeRange.ALWAYS)
            .build()
    }
}

class DirectToWatchGraphScaleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != ACTION_CYCLE) return
        DirectToWatchPreferences.cycleGraphHours(context)
        DirectToWatchPreferences.requestUpdates(context)
    }

    companion object {
        const val ACTION_CYCLE = "app.aapswear.complications.DIRECT_TO_WATCH_CYCLE_GRAPH"
    }
}
