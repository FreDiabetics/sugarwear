package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalTreatmentsTest {
    private fun event(
        id: String,
        kind: TherapyEventKind,
        time: Long,
        amount: Double,
        source: TherapyEventSource = TherapyEventSource.AAPS_ONLY,
    ) = TherapyEvent(
        id,
        kind,
        time,
        amount,
        source,
        originalSourceId = id,
        insulinUnits =
            amount.takeIf {
                kind in
                    setOf(TherapyEventKind.SMB, TherapyEventKind.MANUAL_CORRECTION, TherapyEventKind.MEAL_BOLUS)
            },
        carbsGrams =
            amount.takeIf {
                kind in
                    setOf(TherapyEventKind.MEAL_CARBS, TherapyEventKind.ECARBS)
            },
    )

    @Test fun `same meal bolus from both sources becomes one enriched event`() {
        val merged =
            CanonicalTreatments.merge(
                listOf(event("aaps-1", TherapyEventKind.MEAL_BOLUS, 1_000_000L, 7.6)),
                listOf(event("ns-1", TherapyEventKind.MEAL_BOLUS, 1_000_000L, 7.6, TherapyEventSource.NIGHTSCOUT_ONLY)),
            )
        assertEquals(1, merged.size)
        assertEquals(TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT, merged.single().source)
    }

    @Test fun `nearby distinct SMB values are retained`() {
        val merged =
            CanonicalTreatments.merge(
                listOf(event("a", TherapyEventKind.SMB, 1_000_000L, 0.15)),
                listOf(event("b", TherapyEventKind.SMB, 1_010_000L, 0.20, TherapyEventSource.NIGHTSCOUT_ONLY)),
            )
        assertEquals(2, merged.size)
    }

    @Test fun `stable cross-source id wins despite timestamp drift`() {
        val merged =
            CanonicalTreatments.merge(
                listOf(event("shared", TherapyEventKind.MEAL_CARBS, 1_000_000L, 45.0)),
                listOf(event("shared", TherapyEventKind.MEAL_CARBS, 1_120_000L, 45.0, TherapyEventSource.NIGHTSCOUT_ONLY)),
            )
        assertEquals(1, merged.size)
        assertTrue(merged.single().source == TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT)
    }

    @Test fun `explicit AAPS correction wins over matching CloudEndpoint automatic classification`() {
        val merged =
            CanonicalTreatments.merge(
                listOf(event("aaps-manual", TherapyEventKind.MANUAL_CORRECTION, 1_000_000L, 0.3)),
                listOf(event("ns-auto", TherapyEventKind.SMB, 1_045_000L, 0.3, TherapyEventSource.NIGHTSCOUT_ONLY)),
            )
        assertEquals(1, merged.size)
        assertEquals(TherapyEventKind.MANUAL_CORRECTION, merged.single().kind)
    }

    @Test fun `same carbs from AAPS and CloudEndpoint merge despite timestamp drift`() {
        val merged =
            CanonicalTreatments.merge(
                listOf(event("aaps-carbs", TherapyEventKind.MEAL_CARBS, 1_000_000L, 60.0)),
                listOf(event("ns-carbs", TherapyEventKind.MEAL_CARBS, 1_090_000L, 60.0, TherapyEventSource.NIGHTSCOUT_ONLY)),
            )
        assertEquals(1, merged.size)
    }
}
