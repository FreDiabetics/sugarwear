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
import app.sugarwear.debug.playground.demo.DemoNotificationFactory
import app.sugarwear.debug.playground.testing.DebugNotificationPublisher

/**
 * Playground screen for previewing and triggering debug test notifications.
 */
@Composable
fun NotificationPlaygroundScreen(
    @Suppress("UNUSED_PARAMETER") state: PlaygroundUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (PlaygroundAction) -> Unit,
) {
    val context = LocalContext.current
    val sampleErrors = DemoNotificationFactory.sampleErrors

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            "System-Notification Test",
            style = TextStyle(color = Color(0xFF22EB60), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(8.dp))

        BasicText(
            "Verwendet einen isolierten Test-Kanal ohne Lautstärke/Vibration.",
            style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(16.dp))

        sampleErrors.forEach { error ->
            BasicText(
                "Test-Notif: ${error.code}",
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF242424))
                    .clickable {
                        DebugNotificationPublisher.postTestErrorNotification(context, error)
                    }.padding(vertical = 10.dp),
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        BasicText(
            "Alle Test-Notifications entfernen",
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFF5C69))
                .clickable {
                    DebugNotificationPublisher.cancelAllTestNotifications(context)
                }.padding(vertical = 10.dp),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
    }
}
