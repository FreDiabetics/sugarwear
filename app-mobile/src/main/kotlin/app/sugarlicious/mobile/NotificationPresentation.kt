package app.sugarlicious.mobile

import app.sugarlicious.model.CanonicalTrendPolicy
import app.sugarlicious.model.CgmPresentationPolicy
import app.sugarlicious.model.CgmPresentationStatus
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyBridgeDisplayField
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TrendRateProfile
import app.sugarlicious.model.effectiveBasalPresentation
import java.util.Locale
import kotlin.math.roundToInt

internal enum class NotificationMode { STANDARD_CUSTOM, ANDROID_LIVE, SAMSUNG_LIVE }

internal data class NotificationPresentation(
    val glucoseText: String?,
    val glucoseNumeric: Double?,
    val unit: GlucoseUnit?,
    val unitText: String?,
    val trendSymbol: String?,
    val nowBarTrendSymbol: String?,
    val deltaText: String?,
    val ageText: String?,
    val status: CgmPresentationStatus,
    val statusText: String?,
    val therapyText: String?,
    val basalText: String?,
    val sourceText: String,
    val profileText: String?,
    val androidCapsuleText: String?,
    val compactPrimaryText: String?,
    val drawerPrimaryText: String,
    val revision: String,
)

internal fun notificationMode(
    liveRequested: Boolean,
    sdkInt: Int,
    manufacturer: String,
): NotificationMode =
    when {
        !liveRequested -> NotificationMode.STANDARD_CUSTOM
        manufacturer.equals("samsung", ignoreCase = true) -> NotificationMode.SAMSUNG_LIVE
        sdkInt >= 36 -> NotificationMode.ANDROID_LIVE
        else -> NotificationMode.STANDARD_CUSTOM
    }

internal fun notificationPresentation(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
    selectedUnit: GlucoseUnit? = state?.glucose?.displayUnit,
): NotificationPresentation {
    val status = TherapyDisplayFormatter.presentationStatus(state, nowEpochMs)
    val glucose = state?.glucose
    val displayable =
        glucose != null &&
            TherapyDisplayFormatter.isGlucoseKnown(state) &&
            status in
            setOf(
                CgmPresentationStatus.CURRENT,
                CgmPresentationStatus.AGING,
                CgmPresentationStatus.STALE,
                CgmPresentationStatus.SIGNAL_LOSS,
            )
    val unit = selectedUnit ?: glucose?.displayUnit
    val glucoseText = if (displayable && unit != null) formatGlucose(glucose.valueMgDl, unit) else null
    val glucoseNumeric =
        if (displayable && unit != null) {
            if (unit == GlucoseUnit.MMOL_L) glucose.valueMgDl / 18.0 else glucose.valueMgDl
        } else {
            null
        }
    val trendSymbol =
        if (displayable) {
            TherapyDisplayFormatter.trendArrow(glucose.trend).ifBlank { null }
        } else {
            null
        }
    val deltaText =
        if (displayable && unit != null) {
            TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, unit).replace('-', '−').ifBlank { null }
        } else {
            null
        }
    val nowBarTrendSymbol =
        if (displayable) {
            glucose.deltaMgDl
                ?.takeIf(Double::isFinite)
                ?.div(NOW_BAR_DELTA_MINUTES)
                ?.let { CanonicalTrendPolicy.fromRate(it, TrendRateProfile.ANDROID_APS) }
                ?.let(TherapyDisplayFormatter::trendArrow)
                ?.ifBlank { null }
                ?: trendSymbol
        } else {
            null
        }
    val compactPrimary = joinVisible(glucoseText, trendSymbol, deltaText)
    val androidCapsule = joinVisible(glucoseText, trendSymbol)
    val therapyText =
        joinSeparated(
            state
                ?.insulin
                ?.totalIob
                ?.takeIf(Double::isFinite)
                ?.let { "IOB ${TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.IOB, it, state)}" },
            state
                ?.carbs
                ?.cobGrams
                ?.takeIf(Double::isFinite)
                ?.let { "COB ${TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.COB, it, state)}" },
        )
    val basalText =
        effectiveBasalPresentation(state, nowEpochMs)?.unitsPerHour?.let {
            "Basal ${TherapyDisplayFormatter.therapyBridge(TherapyBridgeDisplayField.BASAL, it, state)}"
        }
    val statusText =
        when (status) {
            CgmPresentationStatus.CURRENT -> null
            CgmPresentationStatus.AGING -> "Verzögert"
            CgmPresentationStatus.STALE -> "Veraltet"
            CgmPresentationStatus.SIGNAL_LOSS -> "Signalverlust"
            CgmPresentationStatus.SENSOR_ERROR -> "Sensorfehler"
            CgmPresentationStatus.NO_SOURCE -> "Keine Quelle"
        }
    val drawerPrimary =
        if (compactPrimary != null) {
            joinSeparated(compactPrimary, basalText).orEmpty()
        } else {
            statusText ?: "Keine Quelle"
        }
    val sourceText = TherapyDisplayFormatter.sourceName(state?.source)
    val profileText = state?.profile?.name?.takeIf(String::isNotBlank)
    val measuredAt = glucose?.measuredAtEpochMs
    val ageText = measuredAt?.let { TherapyDisplayFormatter.ageMinutes(it, nowEpochMs) }
    val revision =
        listOf(
            measuredAt,
            sourceText,
            glucoseText,
            glucoseNumeric,
            unit,
            trendSymbol,
            nowBarTrendSymbol,
            deltaText,
            ageText,
            status,
            glucose?.quality,
            therapyText,
            basalText,
            profileText,
            drawerPrimary,
        ).joinToString("\u001f") { it?.toString().orEmpty() }

    return NotificationPresentation(
        glucoseText = glucoseText,
        glucoseNumeric = glucoseNumeric,
        unit = unit,
        unitText = unit?.let { if (it == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL" },
        trendSymbol = trendSymbol,
        nowBarTrendSymbol = nowBarTrendSymbol,
        deltaText = deltaText,
        ageText = ageText,
        status = status,
        statusText = statusText,
        therapyText = therapyText,
        basalText = basalText,
        sourceText = sourceText,
        profileText = profileText,
        androidCapsuleText = androidCapsule,
        compactPrimaryText = compactPrimary,
        drawerPrimaryText = drawerPrimary,
        revision = revision,
    )
}

private const val NOW_BAR_DELTA_MINUTES = 5.0

internal fun nextNotificationFreshnessBoundary(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): Long? {
    val measuredAt = state?.glucose?.measuredAtEpochMs ?: return null
    if (!TherapyDisplayFormatter.isGlucoseKnown(state)) return null
    val freshnessBoundaries =
        listOf(
            measuredAt + FreshnessPolicy.CURRENT_MAX_MS + 1L,
            measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L,
            measuredAt + CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS,
        )
    val basal = state.basal
    val tempBasalEnd =
        basal?.tempEndsAtEpochMs
            ?: basal?.tempStartedAtEpochMs?.let { start -> basal.tempDurationMinutes?.let { start + it * 60_000L } }
    return (freshnessBoundaries + listOfNotNull(tempBasalEnd))
        .filter { it > nowEpochMs }
        .minOrNull()
}

internal fun nextNotificationSafetyBoundary(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): Long? {
    val measuredAt = state?.glucose?.measuredAtEpochMs ?: return null
    if (!TherapyDisplayFormatter.isGlucoseKnown(state)) return null
    return (measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L).takeIf { it > nowEpochMs }
}

internal fun nextNotificationAgeBoundary(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
): Long? {
    val measuredAt = state?.glucose?.measuredAtEpochMs ?: return null
    if (!TherapyDisplayFormatter.isGlucoseKnown(state)) return null
    val elapsedMinutes = ((nowEpochMs - measuredAt).coerceAtLeast(0L) / 60_000L)
    return measuredAt + (elapsedMinutes + 1L) * 60_000L
}

private fun formatGlucose(
    valueMgDl: Double,
    unit: GlucoseUnit,
): String =
    if (unit == GlucoseUnit.MMOL_L) {
        String.format(Locale.US, "%.1f", valueMgDl / 18.0)
    } else {
        valueMgDl.roundToInt().toString()
    }

private fun joinVisible(vararg values: String?): String? =
    values.mapNotNull { it?.takeIf(String::isNotBlank) }.takeIf(List<String>::isNotEmpty)?.joinToString(" ")

private fun joinSeparated(vararg values: String?): String? =
    values.mapNotNull { it?.takeIf(String::isNotBlank) }.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
