package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.screens.NotificationPlaygroundScreen

@Preview(name = "Notification Test Screen", device = "id:wearos_small_round")
@Composable
fun PreviewNotificationContent() {
    val state = PlaygroundUiState()
    NotificationPlaygroundScreen(state = state, onAction = {})
}
