package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.demo.DemoGlucoseFactory
import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.debug.playground.screens.HomePlaygroundScreen

@Preview(name = "Home Screen - Stable", device = "id:wearos_small_round")
@Composable
fun PreviewHomeScreenStable() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE)
    HomePlaygroundScreen(state = state, readings = readings, onAction = {})
}

@Preview(name = "Home Screen - Fast Rise", device = "id:wearos_large_round")
@Composable
fun PreviewHomeScreenFastRise() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.FAST_RISE)
    HomePlaygroundScreen(state = state, readings = readings, onAction = {})
}

@Preview(name = "Home Screen - Hypo", device = "id:wearos_small_round")
@Composable
fun PreviewHomeScreenHypo() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.HYPOGLYCEMIA)
    HomePlaygroundScreen(state = state, readings = readings, onAction = {})
}
