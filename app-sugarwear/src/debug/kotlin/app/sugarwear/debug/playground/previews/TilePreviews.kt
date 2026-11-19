package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.screens.TilePlaygroundScreen

@Preview(name = "Collector Tile Preview", device = "id:wearos_small_round")
@Composable
fun PreviewCollectorTile() {
    val state = PlaygroundUiState()
    TilePlaygroundScreen(state = state, onAction = {})
}

@Preview(name = "Graph Tile Preview", device = "id:wearos_large_round")
@Composable
fun PreviewGraphTile() {
    val state = PlaygroundUiState()
    TilePlaygroundScreen(state = state, onAction = {})
}
