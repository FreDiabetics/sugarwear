package app.sugarwear.debug.playground.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarwear.CgmAppearanceStore
import app.sugarwear.CgmCollectorGraphView
import app.sugarwear.debug.playground.PlaygroundAction
import app.sugarwear.debug.playground.PlaygroundUiState

/**
 * Full-screen graph playground rendering the CgmCollectorGraphView with live style parameter overrides.
 */
@Composable
fun GraphPlaygroundScreen(
    state: PlaygroundUiState,
    readings: List<CgmReading>,
    @Suppress("UNUSED_PARAMETER") onAction: (PlaygroundAction) -> Unit,
) {
    val homeStyle = state.styleOverrides.homeStyle

    Box(
        Modifier
            .fillMaxSize()
            .background(homeStyle.backgroundColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(homeStyle.cardCornerRadiusDp))
                .background(homeStyle.cardBackgroundColor),
        ) {
            AndroidView(
                factory = { ctx ->
                    CgmCollectorGraphView(ctx).apply {
                        bind(readings, CgmAppearanceStore(ctx).load(), 3, System.currentTimeMillis())
                    }
                },
                update = { view ->
                    view.bind(readings, CgmAppearanceStore(view.context).load(), 3, System.currentTimeMillis())
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
