package app.sugarlicious.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class BasalDetailPresentation(
    val base: String,
    val temporary: String,
)

fun basalDetailPresentation(basal: BasalState?, now: Long, locale: Locale = Locale.getDefault()): BasalDetailPresentation {
    if (basal == null) return BasalDetailPresentation("—", "—")
    val format = DecimalFormat("0.##", DecimalFormatSymbols(locale))

    fun rate(value: Double?) = value?.takeIf { it.isFinite() && it >= 0.0 }?.let { "${format.format(it)}U/h" } ?: "—"
    val duration = basal.tempDurationMinutes?.takeIf { it > 0 }
    val start = basal.tempStartedAtEpochMs
    val end = basal.tempEndsAtEpochMs ?: start?.let { s -> duration?.let { runCatching { Math.addExact(s, Math.multiplyExact(it, 60_000L)) }.getOrNull() } }
    val active = (basal.tempAbsoluteUnitsPerHour != null || basal.tempPercent != null) && (end == null || end > now) && (start == null || start <= now)
    val declaredPercent = basal.tempPercent?.takeIf { it in 0..500 }
    val actualRate =
        basal.tempAbsoluteUnitsPerHour?.takeIf { it.isFinite() && it >= 0.0 }
            ?: declaredPercent?.let { percent -> basal.currentUnitsPerHour?.takeIf { it.isFinite() && it >= 0.0 }?.times(percent / 100.0) }
    val percent = if (!active) 100 else declaredPercent ?: basal.currentUnitsPerHour?.takeIf { it.isFinite() && it > 0.0 }?.let { base -> actualRate?.let { (it / base * 100).toInt() } }
    val base = rate(basal.currentUnitsPerHour) + (percent?.let { " @$it%" } ?: "")
    if (!active) return BasalDetailPresentation(base, "Keine TBR")
    val elapsed = if (start != null && duration != null) ((now - start).coerceAtLeast(0) / 60_000).coerceAtMost(duration).toString() else "—"
    return BasalDetailPresentation(base, "${rate(actualRate)} $elapsed/${duration ?: "—"} m")
}
