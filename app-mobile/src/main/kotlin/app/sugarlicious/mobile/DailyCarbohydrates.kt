package app.sugarlicious.mobile

import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEventKind
import java.time.ZoneId

/** Recorded carbohydrates today; never infer intake from remaining COB or insulin. */
internal fun dailyCarbohydratesGrams(state: TherapyDisplayState?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Double? {
    if (state == null) return null
    val start = localDayStartEpochMs(now, zone)
    val seenIds = mutableSetOf<String>()
    val amounts =
        state.therapyEvents
            .asSequence()
            .filter { it.validated && it.kind in setOf(TherapyEventKind.MEAL_CARBS, TherapyEventKind.ECARBS) && it.timestampEpochMs in start..now }
            .filter { (it.carbsGrams ?: it.amount).let { grams -> grams.isFinite() && grams > 0.0 } }
            .filter { event ->
                val ids = setOfNotNull(event.id.takeIf(String::isNotBlank), event.originalSourceId?.takeIf(String::isNotBlank))
                val duplicate = ids.any { it in seenIds }
                seenIds.addAll(ids)
                !duplicate
            }.map { it.carbsGrams ?: it.amount }
            .toList()
    return amounts.takeIf { it.isNotEmpty() }?.sum()?.takeIf { it.isFinite() }
}
