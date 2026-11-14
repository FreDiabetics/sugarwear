package app.sugarlicious.datasource.therapybridge

import app.sugarlicious.model.TherapyEventKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TherapyBridgeTherapyEventParserTest {
    @Test fun `parses classified treatments and deduplicates stable ids`() {
        val events =
            TherapyBridgeTherapyEventParser.parse(
                """[
              {"id":"b1","type":"MEAL_BOLUS","timestamp":1000,"amount":7.6},
              {"id":"b1","type":"MEAL_BOLUS","timestamp":1000,"amount":7.6},
              {"id":"s1","type":"SMB","timestamp":2000,"amount":0.2},
              {"id":"c1","type":"MANUAL_CORRECTION","timestamp":3000,"amount":1.0},
              {"id":"m1","type":"MEAL_CARBS","timestamp":4000,"amount":60},
              {"id":"e1","type":"ECARBS","timestamp":5000,"amount":2}
            ]""",
            )
        assertEquals(5, events.size)
        assertEquals(
            listOf(
                TherapyEventKind.MEAL_BOLUS,
                TherapyEventKind.SMB,
                TherapyEventKind.MANUAL_CORRECTION,
                TherapyEventKind.MEAL_CARBS,
                TherapyEventKind.ECARBS,
            ),
            events.map {
                it.kind
            },
        )
        assertEquals(1_000_000L, events.first().timestampEpochMs)
    }

    @Test fun `rejects zero invalid and unknown events`() {
        assertEquals(
            emptyList(),
            TherapyBridgeTherapyEventParser.parse("""[{"type":"SMB","timestamp":1,"amount":0},{"type":"UNKNOWN","timestamp":2,"amount":1}]"""),
        )
    }
}
