package app.sugarlicious.model

import java.util.Locale
import kotlin.math.roundToInt

/** Pure, deterministic display formatting shared by complications, Tiles, widgets and tests. */
object TherapyDisplayFormatter {
    fun therapyBridge(
        field: TherapyBridgeDisplayField,
        value: Double?,
        state: TherapyDisplayState?,
        includeUnit: Boolean = true,
    ): String = TherapyBridgeDisplayFormatter.format(field, value, state?.therapyBridgeDisplaySemantics ?: TherapyBridgeDisplaySemantics(), includeUnit)

    /**
     * Whether a real, validated glucose value is known, independent from its age.
     * Freshness is presentation metadata and must never destroy the last clinical state.
     */
    fun isGlucoseKnown(state: TherapyDisplayState?): Boolean {
        val glucose = state?.glucose ?: return false
        return glucose.quality == CgmQuality.VALID &&
            glucose.valueMgDl.isFinite() &&
            glucose.valueMgDl in 20.0..1_000.0
    }

    fun glucose(glucose: GlucoseState): String = glucose(glucose.valueMgDl, glucose.displayUnit)

    fun glucose(valueMgDl: Double, unit: GlucoseUnit): String =
        if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f", valueMgDl / 18.0)
        } else {
            valueMgDl.roundToInt().toString()
        }

    fun signedDelta(
        valueMgDl: Double?,
        unit: GlucoseUnit,
    ): String =
        valueMgDl?.let {
            val displayValue = if (unit == GlucoseUnit.MMOL_L) it / 18.0 else it
            val prefix = if (displayValue >= 0.0) "+" else ""
            prefix +
                if (unit == GlucoseUnit.MMOL_L) {
                    String.format(Locale.US, "%.1f", displayValue)
                } else {
                    displayValue.roundToInt().toString()
                }
        } ?: ""

    fun trendArrow(trend: Trend): String =
        when (trend) {
            Trend.DOUBLE_DOWN -> "⇊"
            Trend.SINGLE_DOWN -> "↓"
            Trend.FORTY_FIVE_DOWN -> "↘"
            Trend.FLAT -> "→"
            Trend.FORTY_FIVE_UP -> "↗"
            Trend.SINGLE_UP -> "↑"
            Trend.DOUBLE_UP -> "⇈"
            Trend.UNKNOWN -> ""
        }

    fun units(
        value: Double?,
        suffix: String,
        digits: Int,
    ): String = value?.let { String.format(Locale.US, "%.${digits}f%s", it, suffix) } ?: "—"

    /** Preserves every finite AndroidAPS IOB value, including zero and negative values. */
    fun iob(
        value: Double?,
        suffix: String = "U",
        digits: Int = 2,
    ): String = value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.${digits}f%s", it, suffix) } ?: "—"

    fun percent(value: Int?): String = value?.let { "$it%" } ?: "—"

    fun ageMinutes(
        timestampEpochMs: Long?,
        nowEpochMs: Long,
    ): String = ageMinutesValue(timestampEpochMs, nowEpochMs)?.let { "${it}m" } ?: "—"

    fun ageMinutesValue(
        timestampEpochMs: Long?,
        nowEpochMs: Long,
    ): Long? = timestampEpochMs?.let { ((nowEpochMs - it).coerceAtLeast(0L) / 60_000L) }

    fun freshness(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): Freshness {
        val glucose = state?.glucose ?: return Freshness.NO_DATA
        if (glucose.quality == CgmQuality.SENSOR_ERROR) return Freshness.ERROR
        if (
            glucose.quality != CgmQuality.VALID ||
            !glucose.valueMgDl.isFinite() ||
            glucose.valueMgDl !in 20.0..1_000.0
        ) {
            return Freshness.NO_DATA
        }
        return FreshnessPolicy.classify(glucose.measuredAtEpochMs, nowEpochMs)
    }

    fun freshnessLabel(freshness: Freshness): String =
        when (freshness) {
            Freshness.CURRENT -> "AKTUELL"
            Freshness.DELAYED -> "VERZÖGERT"
            Freshness.STALE -> "VERALTET"
            Freshness.SIGNAL_LOSS -> "SIGNALVERLUST"
            Freshness.ERROR -> "SENSORFEHLER"
            Freshness.NO_DATA -> "KEINE DATEN"
        }

    fun presentationStatus(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): CgmPresentationStatus = CgmPresentationPolicy.classify(state, nowEpochMs)

    fun presentationStatusLabel(status: CgmPresentationStatus): String =
        when (status) {
            CgmPresentationStatus.CURRENT -> "AKTUELL"
            CgmPresentationStatus.AGING -> "VERZÖGERT"
            CgmPresentationStatus.STALE -> "VERALTET"
            CgmPresentationStatus.SIGNAL_LOSS -> "SIGNALVERLUST"
            CgmPresentationStatus.SENSOR_ERROR -> "SENSORFEHLER"
            CgmPresentationStatus.NO_SOURCE -> "KEINE QUELLE"
        }

    /**
     * Whether the last validated CGM state should remain visible. Age changes its presentation,
     * but only invalid/no data removes value, delta and trend from a display surface.
     */
    fun isGlucoseDisplayable(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): Boolean {
        if (!isGlucoseKnown(state)) return false
        return when (freshness(state, nowEpochMs)) {
            Freshness.CURRENT, Freshness.DELAYED, Freshness.STALE, Freshness.SIGNAL_LOSS -> true
            Freshness.ERROR, Freshness.NO_DATA -> false
        }
    }

    fun sourceName(source: DataSourceId?): String =
        when (source) {
            DataSourceId.DEXCOM_G7_WATCH -> "Watch Direct"
            DataSourceId.ANDROID_APS -> "AndroidAPS"
            DataSourceId.DEXCOM_G7_APP -> "Dexcom G7 App"
            DataSourceId.DEXCOM_SHARE -> "Dexcom Share"
            DataSourceId.NIGHTSCOUT -> "Nightscout"
            DataSourceId.XDRIP_PLUS -> "xDrip+"
            DataSourceId.LIBRE_PATCHED -> "Libre patched app"
            DataSourceId.OTHER -> "Andere Quelle"
            null -> "Keine Quelle"
        }

    fun target(
        target: TargetState?,
        unit: GlucoseUnit,
    ): String {
        if (target?.lowMgDl == null && target?.highMgDl == null) return "—"
        return listOfNotNull(target.lowMgDl, target.highMgDl).joinToString("–") {
            if (unit == GlucoseUnit.MMOL_L) {
                String.format(Locale.US, "%.1f", it / 18.0)
            } else {
                it.roundToInt().toString()
            }
        }
    }
}
