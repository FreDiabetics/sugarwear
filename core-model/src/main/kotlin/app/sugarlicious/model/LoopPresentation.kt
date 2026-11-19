package app.sugarlicious.model

import java.util.Locale

enum class LoopVisualState {
    CLOSED,
    SUSPENDED,
    DEACTIVATED,
    UNKNOWN,
    PUMP_SUSPENDED,
}

data class LoopPresentation(
    val visualState: LoopVisualState,
    val label: String,
    val shortText: String,
)

/** Shared loop-state semantics for the Mobile overview and Wear complications. */
fun loopPresentation(state: TherapyDisplayState?): LoopPresentation {
    val pump =
        state
            ?.pump
            ?.status
            .orEmpty()
            .lowercase(Locale.ROOT)
    val loop =
        state
            ?.loop
            ?.status
            .orEmpty()
            .lowercase(Locale.ROOT)
    return when {
        listOf("suspend", "paused", "disconnect", "stopped").any(pump::contains) ->
            LoopPresentation(LoopVisualState.PUMP_SUSPENDED, "Pumpe pausiert", "Pumpe")
        listOf("suspend", "paused").any(loop::contains) ->
            LoopPresentation(LoopVisualState.SUSPENDED, "Loop pausiert", "Pausiert")
        loop.isBlank() ->
            LoopPresentation(LoopVisualState.UNKNOWN, "Loop unbekannt", "Unbekannt")
        listOf("disabled", "off", "open", "deactivated").any(loop::contains) ->
            LoopPresentation(LoopVisualState.DEACTIVATED, "Loop aus", "Aus")
        else -> LoopPresentation(LoopVisualState.CLOSED, "Closed Loop", "Closed")
    }
}
