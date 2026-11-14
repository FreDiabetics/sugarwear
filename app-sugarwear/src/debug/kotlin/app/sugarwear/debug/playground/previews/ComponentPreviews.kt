package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.demo.DemoGlucoseFactory
import app.sugarwear.debug.playground.demo.DemoScenario
import app.sugarwear.debug.playground.screens.ComponentPlaygroundScreen

@Preview(name = "Connection Dots Component", device = "id:wearos_small_round")
@Composable
fun PreviewComponentDots() {
    val state = PlaygroundUiState()
    val readings = DemoGlucoseFactory.createReadingsForScenario(DemoScenario.STABLE)
    ComponentPlaygroundScreen(state = state, readings = readings, onAction = {})
}
