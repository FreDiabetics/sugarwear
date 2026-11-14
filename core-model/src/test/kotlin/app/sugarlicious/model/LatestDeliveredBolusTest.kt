package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LatestDeliveredBolusTest {
    private val now = 90_000_000L

    @Test fun `legacy SMB markers without delivery provenance cannot replace a real treatment`() {
        val state = TherapyDisplayState(receivedAtEpochMs = now, therapyEvents = listOf(TherapyEvent("meal", TherapyEventKind.MEAL_BOLUS, now - 60_000, 2.0)), therapyHistory = listOf(TherapyHistorySample(now - 1, smbUnits = 0.4)))
        assertEquals(DeliveredBolus(2.0, now - 60_000), latestDeliveredBolus(state, now))
    }

    @Test fun `newer delivered loop SMB wins over old meal and survives in history`() {
        val state = TherapyDisplayState(receivedAtEpochMs = now, therapyEvents = listOf(TherapyEvent("meal", TherapyEventKind.MEAL_BOLUS, now - 19 * 3_600_000L, 4.0)), loop = LoopState(smbUnits = 0.3, smbAtEpochMs = now - 60_000))
        assertEquals(DeliveredBolus(0.3, now - 60_000), latestDeliveredBolus(state, now))
        assertEquals(DeliveredBolus(0.4, now - 30_000), latestDeliveredBolus(state.copy(loop = null, therapyHistory = listOf(TherapyHistorySample(now - 30_000, smbUnits = 0.4, smbDeliveredAtEpochMs = now - 30_000))), now))
    }

    @Test fun `all delivered insulin kinds participate but invalid and future data do not`() {
        val events = listOf(TherapyEvent("meal", TherapyEventKind.MEAL_BOLUS, now - 300_000, 2.0), TherapyEvent("manual", TherapyEventKind.MANUAL_CORRECTION, now - 60_000, 1.0), TherapyEvent("future", TherapyEventKind.SMB, now + 1, 0.5), TherapyEvent("invalid", TherapyEventKind.SMB, now - 1, 0.7, validated = false), TherapyEvent("carbs", TherapyEventKind.MEAL_CARBS, now - 1, 30.0))
        assertEquals(DeliveredBolus(1.0, now - 60_000), latestDeliveredBolus(TherapyDisplayState(receivedAtEpochMs = now, therapyEvents = events, loop = LoopState(smbUnits = 0.9)), now))
    }
}
