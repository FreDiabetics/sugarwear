package app.sugarwear.debug.playground.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarwear.CgmConnectionDotsView
import app.sugarwear.debug.playground.PlaygroundAction
import app.sugarwear.debug.playground.PlaygroundUiState

/**
 * Isolated preview playground for individual UI components (e.g. CgmConnectionDotsView).
 */
@Composable
fun ComponentPlaygroundScreen(
    @Suppress("UNUSED_PARAMETER") state: PlaygroundUiState,
    @Suppress("UNUSED_PARAMETER") readings: List<CgmReading>,
    @Suppress("UNUSED_PARAMETER") onAction: (PlaygroundAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            "Verbindungs-Dots Component",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1C))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    CgmConnectionDotsView(ctx).apply {
                        color = android.graphics.Color.GREEN
                        isAnimating = true
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
            )
        }
    }
}
