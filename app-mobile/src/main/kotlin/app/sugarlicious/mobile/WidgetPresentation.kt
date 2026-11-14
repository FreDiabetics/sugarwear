package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.CgmGraphPolicy
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.RangeExcursion
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal enum class WidgetWidthClass { VERY_NARROW, NARROW, REGULAR, WIDE, VERY_WIDE }

internal enum class WidgetHeightClass { LOW, REGULAR, HIGH }

internal enum class WidgetFamilyLayout { COMPACT_2X1, DETAIL_2X2, WIDE_4X2 }

internal fun nextWidgetGraphHours(hours: Int): Int {
    val choices = listOf(1, 2, 3, 6, 12, 24)
    return choices[(choices.indexOf(hours) + 1) % choices.size]
}

internal fun widgetFamilyLayout(
    widthDp: Float,
    heightDp: Float,
): WidgetFamilyLayout =
    when {
        widthDp >= 300f -> WidgetFamilyLayout.WIDE_4X2
        heightDp >= 135f -> WidgetFamilyLayout.DETAIL_2X2
        else -> WidgetFamilyLayout.COMPACT_2X1
    }

internal data class WidgetGlucoseFamilyText(
    val age: String? = null,
    val delta: String? = null,
    val tir: String? = null,
)

internal data class WidgetDetailText(
    val prefix: String,
    val value: String,
    val suffix: String,
)

internal fun widgetDetailText(index: Int, label: String): WidgetDetailText =
    when (index) {
        0 -> WidgetDetailText("", label.substringBefore(' '), label.substringAfter(' ', "").let { if (it.isEmpty()) "" else " $it" })
        1 ->
            label.removeSuffix("m").toLongOrNull()?.let { WidgetDetailText("vor ", it.toString(), " Min") }
                ?: WidgetDetailText("", label, "")
        else -> WidgetDetailText("", label, if (label.contains('%')) " im Ziel" else "")
    }

internal fun widgetGlucoseFamilyText(
    layout: WidgetFamilyLayout,
    age: String,
    delta: String,
    unit: String,
    tirPercent: Int?,
): WidgetGlucoseFamilyText =
    when (layout) {
        WidgetFamilyLayout.COMPACT_2X1 -> WidgetGlucoseFamilyText(age = age, delta = delta)
        WidgetFamilyLayout.DETAIL_2X2,
        WidgetFamilyLayout.WIDE_4X2,
        ->
            WidgetGlucoseFamilyText(
                delta = "$delta $unit",
                age = age,
                tir = tirPercent?.let { "$it%" } ?: "--",
            )
    }

internal fun currentDayTirPercent(
    samples: List<GlucoseSample>,
    targetLowMgDl: Double,
    targetHighMgDl: Double,
    nowEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int? {
    val startOfDay =
        Instant
            .ofEpochMilli(nowEpochMs)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    val valid =
        samples
            .asSequence()
            .filter { it.measuredAtEpochMs in startOfDay..(nowEpochMs + 5 * 60_000L) }
            .filter { it.valueMgDl in 20.0..1000.0 }
            .associateBy { it.measuredAtEpochMs }
            .values
    if (valid.isEmpty()) return null
    return (valid.count { it.valueMgDl in targetLowMgDl..targetHighMgDl } * 100.0 / valid.size).roundToInt()
}

internal const val DEVICE_VENDOR_COMPACT_WIDGET_HORIZONTAL_INSET_DP = 8f
internal const val DEVICE_VENDOR_COMPACT_WIDGET_VERTICAL_INSET_DP = 9f

internal object WidgetLayoutSizeStore {
    private const val PREFERENCES = "dashboard_ui"
    private const val KEY_DEVICE_VENDOR_COMPACT = "widget.layout.samsung_compact"

    fun isDeviceVendorCompact(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEVICE_VENDOR_COMPACT, true)

    fun setDeviceVendorCompact(
        context: Context,
        enabled: Boolean,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_DEVICE_VENDOR_COMPACT, enabled) }
    }
}

internal data class ResponsiveWidgetLayout(
    val widthClass: WidgetWidthClass,
    val heightClass: WidgetHeightClass,
    val cornerRadiusDp: Float,
    val paddingDp: Float,
    val glucoseTextSp: Float,
    val graphDotRadiusDp: Float,
    val graphOutlineDp: Float,
    val graphAxisTextSp: Float,
    val graphLineDp: Float,
)

internal fun responsiveWidgetLayout(
    widthDp: Float,
    heightDp: Float,
): ResponsiveWidgetLayout {
    val widthClass =
        when {
            widthDp < 120f -> WidgetWidthClass.VERY_NARROW
            widthDp < 180f -> WidgetWidthClass.NARROW
            widthDp < 300f -> WidgetWidthClass.REGULAR
            widthDp < 480f -> WidgetWidthClass.WIDE
            else -> WidgetWidthClass.VERY_WIDE
        }
    val heightClass =
        when {
            heightDp < 90f -> WidgetHeightClass.LOW
            heightDp < 190f -> WidgetHeightClass.REGULAR
            else -> WidgetHeightClass.HIGH
        }
    val limiting = minOf(widthDp / 3.25f, heightDp * 0.62f)
    return ResponsiveWidgetLayout(
        widthClass = widthClass,
        heightClass = heightClass,
        cornerRadiusDp = (minOf(widthDp, heightDp) * 0.12f).coerceIn(12f, 28f),
        paddingDp = (minOf(widthDp, heightDp) * 0.08f).coerceIn(4f, 18f),
        glucoseTextSp = limiting.coerceIn(27f, 72f),
        // Match the Mobile graph's default visual weight. A larger widget gains plot area; it
        // must not turn the CGM samples, outlines or boundaries into oversized artwork.
        graphDotRadiusDp = 2.4f,
        graphOutlineDp = 0.95f,
        graphAxisTextSp = (heightDp * 0.055f).coerceIn(8f, 11f),
        graphLineDp = 1f,
    )
}

internal data class WidgetRangePresentation(
    val excursion: RangeExcursion?,
    val visibleRole: WidgetColorRole,
)

internal fun nextWidgetScaleMode(mode: WidgetScaleMode): WidgetScaleMode =
    when (mode) {
        WidgetScaleMode.STATIC -> WidgetScaleMode.DYNAMIC
        WidgetScaleMode.DYNAMIC -> WidgetScaleMode.LOGARITHMIC
        WidgetScaleMode.LOGARITHMIC -> WidgetScaleMode.STATIC
    }

internal fun graphScaleAgeColorRole(excursion: RangeExcursion?): WidgetColorRole =
    if (excursion == RangeExcursion.HIGH) WidgetColorRole.HIGH_REGION_LABEL else WidgetColorRole.AXIS

internal fun graphScaleAgeText(
    graphHours: Int,
    ageMinutes: Long?,
): String =
    buildString {
        append(graphHours)
        append("h")
        ageMinutes?.let {
            append(" · ")
            append(it)
            append("m")
        }
    }

internal fun widgetRangePresentation(
    state: TherapyDisplayState?,
    samples: List<GlucoseSample>,
    thresholds: CgmThresholds,
    now: Long,
): WidgetRangePresentation {
    if (!TherapyDisplayFormatter.isGlucoseKnown(state)) {
        return WidgetRangePresentation(null, WidgetColorRole.TEXT)
    }
    val excursion = CgmGraphPolicy.rangeExcursion(samples, thresholds)
    // Both graph and CGM text react immediately; the text uses the canonical current value.
    val currentValue = state?.glucose?.valueMgDl ?: return WidgetRangePresentation(excursion, WidgetColorRole.TEXT)
    val role = widgetGlucoseColorRole(currentValue, thresholds)
    return WidgetRangePresentation(excursion, role)
}
