package app.sugarlicious.mobile

import app.sugarlicious.model.TherapyEventKind
import app.sugarlicious.model.TherapyEventSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEndpointTreatmentParserTest {
    @Test fun `normalizes documented treatment fields without CGM entries`() {
        val parsed =
            CloudEndpointTreatmentParser.parse(
                """[
              {"_id":"meal-1","eventType":"Meal Bolus","created_at":"2026-08-26T06:03:00Z","insulin":4.2,"carbs":45,"enteredBy":"AndroidAPS"},
              {"_id":"smb-1","eventType":"SMB","date":1787720220000,"insulin":0.15,"isSMB":true},
              {"_id":"cgm-entry","type":"sgv","date":1787720400000,"sgv":123}
            ]""",
            )
        assertEquals(3, parsed.size)
        assertEquals(setOf(TherapyEventKind.MEAL_BOLUS, TherapyEventKind.MEAL_CARBS, TherapyEventKind.SMB), parsed.map { it.kind }.toSet())
        assertTrue(parsed.all { it.source == TherapyEventSource.NIGHTSCOUT_ONLY })
    }

    @Test fun `duration classifies carbs as extended carbs`() {
        val parsed =
            CloudEndpointTreatmentParser.parse(
                """[{"identifier":"ec-1","eventType":"Carb Correction","timestamp":1787720400,"carbs":2,"duration":60}]""",
            )
        assertEquals(TherapyEventKind.ECARBS, parsed.single().kind)
        assertEquals(60, parsed.single().durationMinutes)
        assertEquals(1787720400000L, parsed.single().timestampEpochMs)
    }

    @Test fun `unknown insulin event is not guessed from amount`() {
        val parsed =
            CloudEndpointTreatmentParser.parse(
                """[{"_id":"unknown","eventType":"Note","date":1787720400000,"insulin":3.0}]""",
            )
        assertTrue(parsed.isEmpty())
    }

    @Test fun `cloudEndpoint correction bolus is normalized as automatic loop bolus`() {
        val parsed =
            CloudEndpointTreatmentParser.parse(
                """[{"_id":"auto-1","eventType":"Correction Bolus","date":1787720400000,"insulin":0.2}]""",
            )
        assertEquals(TherapyEventKind.SMB, parsed.single().kind)
    }
}
