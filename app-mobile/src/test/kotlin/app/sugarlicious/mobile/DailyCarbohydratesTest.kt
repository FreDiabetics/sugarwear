package app.sugarlicious.mobile

import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.TherapyEvent
import app.sugarlicious.model.TherapyEventKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyCarbohydratesTest {
    @Test fun `sum only valid today carbohydrate entries and count source duplicates once`() {
        val zone = ZoneId.of("Europe/Berlin")
        val start =
            LocalDate
                .of(2026, 10, 30)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val meal = TherapyEvent("meal", TherapyEventKind.MEAL_CARBS, start + 1, 25.0)
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = start,
                therapyEvents =
                    listOf(
                        meal,
                        meal.copy(id = "mirror", originalSourceId = "meal"),
                        TherapyEvent("extended", TherapyEventKind.ECARBS, start + 2, 10.0, carbsGrams = 40.0),
                        TherapyEvent("old", TherapyEventKind.MEAL_CARBS, start - 1, 100.0),
                        TherapyEvent("future", TherapyEventKind.MEAL_CARBS, start + 10_000, 100.0),
                        TherapyEvent("insulin", TherapyEventKind.SMB, start + 3, 1.0),
                        TherapyEvent("invalid", TherapyEventKind.MEAL_CARBS, start + 4, 100.0, validated = false),
                        TherapyEvent("nan", TherapyEventKind.MEAL_CARBS, start + 4, Double.NaN),
                    ),
            )
        assertEquals(65.0, dailyCarbohydratesGrams(state, start + 5000, zone)!!, .001)
    }

    @Test fun `separate real entries are retained even at the same time and amount`() {
        val now = System.currentTimeMillis()
        val entries = listOf(TherapyEvent("one", TherapyEventKind.MEAL_CARBS, now, 10.0), TherapyEvent("two", TherapyEventKind.MEAL_CARBS, now, 10.0))
        assertEquals(20.0, dailyCarbohydratesGrams(TherapyDisplayState(receivedAtEpochMs = now, therapyEvents = entries), now)!!, .001)
        assertNull(dailyCarbohydratesGrams(null, now))
        assertNull(dailyCarbohydratesGrams(TherapyDisplayState(receivedAtEpochMs = now), now))
    }
}
