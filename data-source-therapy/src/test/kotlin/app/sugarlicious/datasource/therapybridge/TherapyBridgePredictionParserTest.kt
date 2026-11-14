package app.sugarlicious.datasource.therapybridge

import app.sugarlicious.model.PredictionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TherapyBridgePredictionParserTest {
    @Test fun parsesKnownPredictionSeriesAtFiveMinuteIntervals() {
        val parsed =
            TherapyBridgePredictionParser.parse(
                """{"predBGs":{"IOB":[129,125,120],"UAM":[129,135,142],"bad":[1,2]}}""",
                1_000_000L,
            )
        assertEquals(listOf(PredictionKind.IOB, PredictionKind.UAM), parsed.map { it.kind })
        assertEquals(1_600_000L, parsed.first().samples[2].measuredAtEpochMs)
        assertEquals(120.0, parsed.first().samples[2].valueMgDl)
    }

    @Test fun rejectsMalformedAndUnsafeValuesWithoutFailingPayload() {
        assertTrue(TherapyBridgePredictionParser.parse("not-json", 1_000L).isEmpty())
        val parsed = TherapyBridgePredictionParser.parse("""{"predBGs":{"IOB":[129,9999,"x"]}}""", 1_000L)
        assertTrue(parsed.isEmpty())
    }
}
