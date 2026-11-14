package app.sugarlicious.mobile

import app.sugarlicious.model.LoopState
import app.sugarlicious.model.PumpState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewStatePersistenceTest {
    @Test
    fun `missing loop state is unknown and never rendered as loop off`() {
        val state = TherapyDisplayState(receivedAtEpochMs = 1_000L)

        assertEquals("Loop unbekannt", overviewLoopTileState(state).label)
    }

    @Test
    fun `only explicit loop off is rendered as loop off`() {
        val on = TherapyDisplayState(receivedAtEpochMs = 1_000L, loop = LoopState(status = "enacted"))
        val off = TherapyDisplayState(receivedAtEpochMs = 2_000L, loop = LoopState(status = "off"))

        assertEquals("Closed Loop", overviewLoopTileState(on).label)
        assertEquals("Loop aus", overviewLoopTileState(off).label)
    }

    @Test
    fun `explicit pump suspension still takes precedence`() {
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = 1_000L,
                loop = LoopState(status = "enacted"),
                pump = PumpState(status = "suspended"),
            )

        assertEquals("Pumpe pausiert", overviewLoopTileState(state).label)
    }
}
