package app.sugarlicious.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun OverviewInlineHeader(
    onSettings: () -> Unit,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
    pinActionsToBottom: Boolean = false,
    glucoseContent: @Composable () -> Unit,
) {
    Box(modifier.then(if (pinActionsToBottom) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
        Box(
            Modifier.fillMaxWidth().then(if (pinActionsToBottom) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier),
            contentAlignment = if (pinActionsToBottom) Alignment.Center else Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxWidth()) {
                glucoseContent()
                Box(Modifier.align(Alignment.TopEnd).padding(top = 22.dp)) {
                    OverviewActionMenu(onWatch, onSettings)
                }
            }
        }
    }
}
