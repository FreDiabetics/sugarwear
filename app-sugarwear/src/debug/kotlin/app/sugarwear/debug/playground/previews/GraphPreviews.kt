package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.demo.DemoGlucoseFactory
import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.debug.playground.screens.GraphPlaygroundScreen

@Preview(name = "Graph - Stable", device = "id:wearos_small_round")
@Composable
fun PreviewGraphStable() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE)
    GraphPlaygroundScreen(state = state, readings = readings, onAction = {})
}

@Preview(name = "Graph - Fluctuating", device = "id:wearos_large_round")
@Composable
fun PreviewGraphFluctuating() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.FLUCTUATING)
    GraphPlaygroundScreen(state = state, readings = readings, onAction = {})
}

@Preview(name = "Graph - Missing Gaps", device = "id:wearos_small_round")
@Composable
fun PreviewGraphMissingGaps() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.MISSING_VALUES)
    GraphPlaygroundScreen(state = state, readings = readings, onAction = {})
}
