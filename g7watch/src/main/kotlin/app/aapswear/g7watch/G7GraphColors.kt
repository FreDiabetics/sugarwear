package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.aapswear.model.AppearanceMode
import app.aapswear.model.CgmThresholds
import app.aapswear.protocol.WatchColorSync
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WearProtocol

internal class G7GraphColorStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        app.aapswear.uishared.SugarWearDisplayDefaults
            .preferences(context.applicationContext, PREFERENCES)

    fun read(): WatchGraphColors = read(G7AppearanceStore(appContext).activeMode())

    fun read(mode: AppearanceMode): WatchGraphColors {
        migrateLegacy()
        val prefix = "${mode.storageKey}."
        val defaults = WatchGraphColors()
        return WatchGraphColors(
            graphBackground = preferences.getInt(prefix + "background", defaults.graphBackground),
            rangeLow = preferences.getInt(prefix + "range_low", defaults.rangeLow),
            rangeInRange = preferences.getInt(prefix + "range_in", defaults.rangeInRange),
            rangeHigh = preferences.getInt(prefix + "range_high", defaults.rangeHigh),
            cgmLow = preferences.getInt(prefix + "cgm_low", defaults.cgmLow),
            cgmInRange = preferences.getInt(prefix + "cgm_in", defaults.cgmInRange),
            cgmHigh = preferences.getInt(prefix + "cgm_high", defaults.cgmHigh),
            cgmVeryLow = preferences.getInt(prefix + "cgm_very_low", defaults.cgmVeryLow),
            cgmVeryHigh = preferences.getInt(prefix + "cgm_very_high", defaults.cgmVeryHigh),
            divider = preferences.getInt(prefix + "divider", defaults.divider),
            border = preferences.getInt(prefix + "border", defaults.border),
            highLine = preferences.getInt(prefix + "high_line", defaults.highLine),
            lowLine = preferences.getInt(prefix + "low_line", defaults.lowLine),
            axisLabel = preferences.getInt(prefix + "axis_label", defaults.axisLabel),
            axisTick = preferences.getInt(prefix + "axis_tick", defaults.axisTick),
            nowLine = preferences.getInt(prefix + "now_line", defaults.nowLine),
            outline = preferences.getInt(prefix + "outline", defaults.outline),
            predictionIob = preferences.getInt(prefix + "prediction_iob", defaults.predictionIob),
            predictionCob = preferences.getInt(prefix + "prediction_cob", defaults.predictionCob),
            predictionUam = preferences.getInt(prefix + "prediction_uam", defaults.predictionUam),
            predictionZeroTemp = preferences.getInt(prefix + "prediction_zero_temp", defaults.predictionZeroTemp),
            targetValue = preferences.getInt(prefix + "target_value", defaults.targetValue),
            signalLoss = preferences.getInt(prefix + "signal_loss", defaults.signalLoss),
        )
    }

    fun readThresholds(): CgmThresholds =
        CgmThresholds(
            veryHighMgDl = preferences.getFloat("threshold_very_high", 250f).toDouble(),
            highMgDl = preferences.getFloat("threshold_high", 180f).toDouble(),
            lowMgDl = preferences.getFloat("threshold_low", 70f).toDouble(),
            veryLowMgDl = preferences.getFloat("threshold_very_low", 50f).toDouble(),
        ).takeIf(CgmThresholds::isValid) ?: CgmThresholds.DEFAULT

    fun saveThresholds(value: CgmThresholds) {
        require(value.isValid)
        preferences.edit {
            putFloat("threshold_very_high", value.veryHighMgDl.toFloat())
            putFloat("threshold_high", value.highMgDl.toFloat())
            putFloat("threshold_low", value.lowMgDl.toFloat())
            putFloat("threshold_very_low", value.veryLowMgDl.toFloat())
        }
    }

    fun save(sync: WatchColorSync) {
        val fallback = sync.graphColors
        preferences.edit {
            putColors(AppearanceMode.LIGHT, sync.lightProfile?.graphColors ?: fallback)
            putColors(AppearanceMode.DARK, sync.darkProfile?.graphColors ?: fallback)
            putFloat("threshold_very_high", sync.cgmThresholds.veryHighMgDl.toFloat())
            putFloat("threshold_high", sync.cgmThresholds.highMgDl.toFloat())
            putFloat("threshold_low", sync.cgmThresholds.lowMgDl.toFloat())
            putFloat("threshold_very_low", sync.cgmThresholds.veryLowMgDl.toFloat())
            putLong("synced_at", sync.sentAtEpochMs)
        }
    }

    private fun android.content.SharedPreferences.Editor.putColors(
        mode: AppearanceMode,
        colors: WatchGraphColors,
    ) {
        val prefix = "${mode.storageKey}."
        putInt(prefix + "background", colors.graphBackground)
        putInt(prefix + "range_low", colors.rangeLow)
        putInt(prefix + "range_in", colors.rangeInRange)
        putInt(prefix + "range_high", colors.rangeHigh)
        putInt(prefix + "cgm_low", colors.cgmLow)
        putInt(prefix + "cgm_in", colors.cgmInRange)
        putInt(prefix + "cgm_high", colors.cgmHigh)
        putInt(prefix + "cgm_very_low", colors.cgmVeryLow)
        putInt(prefix + "cgm_very_high", colors.cgmVeryHigh)
        putInt(prefix + "divider", colors.divider)
        putInt(prefix + "border", colors.border)
        putInt(prefix + "high_line", colors.highLine)
        putInt(prefix + "low_line", colors.lowLine)
        putInt(prefix + "axis_label", colors.axisLabel)
        putInt(prefix + "axis_tick", colors.axisTick)
        putInt(prefix + "now_line", colors.nowLine)
        putInt(prefix + "outline", colors.outline)
        putInt(prefix + "prediction_iob", colors.predictionIob)
        putInt(prefix + "prediction_cob", colors.predictionCob)
        putInt(prefix + "prediction_uam", colors.predictionUam)
        putInt(prefix + "prediction_zero_temp", colors.predictionZeroTemp)
        putInt(prefix + "target_value", colors.targetValue)
        putInt(prefix + "signal_loss", colors.signalLoss)
    }

    private fun migrateLegacy() {
        if (preferences.getBoolean("appearance_profiles_v1", false)) return
        preferences.edit {
            preferences.all.forEach { (key, raw) ->
                if (raw !is Int || key.startsWith("light.") || key.startsWith("dark.")) return@forEach
                AppearanceMode.entries.forEach { mode ->
                    val target = "${mode.storageKey}.$key"
                    if (!preferences.contains(target)) putInt(target, raw)
                }
            }
            putBoolean("appearance_profiles_v1", true)
        }
    }

    private companion object {
        const val PREFERENCES = "g7_graph_colors"
    }
}

class G7ColorSyncReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_APPLY) return
        val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD) ?: return
        val sync = runCatching { WearProtocol.decodeWatchColorSync(payload) }.getOrNull() ?: return
        G7GraphColorStore(context).save(sync)
        G7CollectorTileService.requestUpdate(context)
    }

    companion object {
        const val ACTION_APPLY = "app.aapswear.g7watch.APPLY_GRAPH_COLORS"
        const val EXTRA_PAYLOAD = "color_payload"
    }
}
