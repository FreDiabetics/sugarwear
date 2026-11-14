package app.sugarwear.debug.playground.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarwear.debug.playground.PlaygroundAction
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.testing.DebugTileController

/**
 * Playground screen explaining Wear OS Tile debugging and offering debug update triggers.
 */
@Composable
fun TilePlaygroundScreen(
    @Suppress("UNUSED_PARAMETER") state: PlaygroundUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (PlaygroundAction) -> Unit,
) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            "Wear OS Tile Vorschau",
            style = TextStyle(color = Color(0xFF22EB60), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(10.dp))

        BasicText(
            "ProtoLayout Tiles nutzen die native Android Studio Tile-Preview (@Preview). Öffne TilePreviews.kt im Studio Editor für die visuelle Vorschau.",
            style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(16.dp))

        BasicText(
            "Tile Updates jetzt anfordern",
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF242424))
                .clickable {
                    DebugTileController.requestTileUpdate(context)
                }
                .padding(vertical = 12.dp),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
    }
}
