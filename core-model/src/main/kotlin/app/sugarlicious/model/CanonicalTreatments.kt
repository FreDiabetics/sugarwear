package app.sugarlicious.model

import kotlin.math.abs

/** Source-independent treatment merge. It never manufactures events from IOB/COB curves. */
object CanonicalTreatments {
    private const val TIME_TOLERANCE_MS = 120_000L
    private const val INSULIN_TOLERANCE = 0.01
    private const val CARB_TOLERANCE = 0.1

    fun merge(
        therapyBridge: List<TherapyEvent>,
        enrichment: List<TherapyEvent>,
    ): List<TherapyEvent> {
        val result = therapyBridge.filter { it.isUsable() }.map { it.copy(source = TherapyEventSource.AAPS_ONLY) }.toMutableList()
        enrichment.filter { it.isUsable() }.sortedBy(TherapyEvent::timestampEpochMs).forEach { incoming ->
            val index = result.indexOfFirst { existing -> sameIdentity(existing, incoming) }
            if (index < 0) {
                result += incoming.copy(source = TherapyEventSource.NIGHTSCOUT_ONLY)
            } else {
                val primary = result[index]
                result[index] =
                    primary.copy(
                        source = TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT,
                        originalSourceId = primary.originalSourceId ?: incoming.originalSourceId,
                        insulinUnits = primary.insulinUnits ?: incoming.insulinUnits,
                        carbsGrams = primary.carbsGrams ?: incoming.carbsGrams,
                        durationMinutes = primary.durationMinutes ?: incoming.durationMinutes,
                        enteredBy = primary.enteredBy ?: incoming.enteredBy,
                        eventType = primary.eventType ?: incoming.eventType,
                        validated = primary.validated && incoming.validated,
                    )
            }
        }
        return result.sortedBy(TherapyEvent::timestampEpochMs)
    }

    fun sameIdentity(
        first: TherapyEvent,
        second: TherapyEvent,
    ): Boolean {
        val firstIds = setOfNotNull(first.id.takeIf(String::isNotBlank), first.originalSourceId?.takeIf(String::isNotBlank))
        val secondIds = setOfNotNull(second.id.takeIf(String::isNotBlank), second.originalSourceId?.takeIf(String::isNotBlank))
        if (firstIds.intersect(secondIds).isNotEmpty()) return true
        if (!sameTreatmentFamily(first.kind, second.kind) ||
            abs(first.timestampEpochMs - second.timestampEpochMs) > TIME_TOLERANCE_MS
        ) {
            return false
        }
        return amountsMatch(first, second)
    }

    private fun sameTreatmentFamily(
        first: TherapyEventKind,
        second: TherapyEventKind,
    ): Boolean = (first in insulinKinds && second in insulinKinds) || (first == second && first in carbKinds)

    private fun amountsMatch(
        first: TherapyEvent,
        second: TherapyEvent,
    ): Boolean =
        when {
            first.kind in insulinKinds ->
                abs((first.insulinUnits ?: first.amount) - (second.insulinUnits ?: second.amount)) <= INSULIN_TOLERANCE
            else ->
                abs((first.carbsGrams ?: first.amount) - (second.carbsGrams ?: second.amount)) <= CARB_TOLERANCE &&
                    (first.durationMinutes == null || second.durationMinutes == null || first.durationMinutes == second.durationMinutes)
        }

    private val insulinKinds = setOf(TherapyEventKind.SMB, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.MEAL_BOLUS)
    private val carbKinds = setOf(TherapyEventKind.MEAL_CARBS, TherapyEventKind.ECARBS)

    private fun TherapyEvent.isUsable() = timestampEpochMs > 0L && amount.isFinite() && amount > 0.0
}
