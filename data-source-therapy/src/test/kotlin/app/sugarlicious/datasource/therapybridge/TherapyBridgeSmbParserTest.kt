package app.sugarlicious.datasource.therapybridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TherapyBridgeSmbParserTest {
    @Test
    fun parsesUnitsAndIsoDeliveryTime() {
        val parsed =
            TherapyBridgeSmbParser.parse(
                """{"units":0.35,"deliverAt":"2026-08-09T14:15:00Z"}""",
                1L,
            )
        assertEquals(0.35, parsed?.units)
        assertEquals(1_786_284_900_000L, parsed?.deliveredAtEpochMs)
    }

    @Test
    fun acceptsEpochSecondsAndRejectsUnsafeUnits() {
        assertEquals(
            1_786_284_900_000L,
            TherapyBridgeSmbParser.parse("""{"units":0.2,"deliverAt":1786284900}""", null)?.deliveredAtEpochMs,
        )
        assertNull(TherapyBridgeSmbParser.parse("""{"units":0}""", 1_000L))
        assertNull(TherapyBridgeSmbParser.parse("""{"units":50}""", 1_000L))
        assertNull(TherapyBridgeSmbParser.parse("not-json", 1_000L))
    }
}
