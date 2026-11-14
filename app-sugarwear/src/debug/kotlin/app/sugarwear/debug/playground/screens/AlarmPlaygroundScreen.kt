package app.sugarwear.debug.playground.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarwear.CgmAlarmScreenContent
import app.sugarwear.SugarWearAlarmType
import app.sugarwear.debug.playground.PlaygroundAction
import app.sugarwear.debug.playground.PlaygroundUiState
import app.sugarwear.debug.playground.demo.DemoAlarmFactory

/**
 * Playground screen for previewing and safely interacting with alarm overlays.
 */
@Composable
fun AlarmPlaygroundScreen(
    state: PlaygroundUiState,
    @Suppress("UNUSED_PARAMETER") onAction: (PlaygroundAction) -> Unit,
) {
    var activeAlarmType by remember { mutableStateOf(SugarWearAlarmType.LOW) }
    var alarmState by remember(activeAlarmType) {
        mutableStateOf(DemoAlarmFactory.createTestAlarmState(activeAlarmType))
    }

    Column(Modifier.fillMaxSize()) {
        // Alarm type selector header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(SugarWearAlarmType.LOW, SugarWearAlarmType.HIGH, SugarWearAlarmType.URGENT_LOW, SugarWearAlarmType.TECHNICAL_WARNING).forEach { type ->
                val isSelected = type == activeAlarmType
                BasicText(
                    type.name.take(4),
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF22EB60) else Color(0xFF333333))
                        .clickable { activeAlarmType = type }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        // Render actual alarm content using extracted CgmAlarmScreenContent
        Box(Modifier.weight(1f)) {
            CgmAlarmScreenContent(
                state = alarmState,
                onAcknowledge = {
                    // Safe test feedback without real alarm side effects
                    alarmState = alarmState.copy(visible = false)
                },
                onSnooze = {
                    alarmState = alarmState.copy(visible = false)
                },
                onAdjustSnooze = { delta ->
                    alarmState = alarmState.copy(snoozeMinutes = (alarmState.snoozeMinutes + delta).coerceIn(5, 120))
                },
                onMute = {},
                onVibrate = {},
                visualStyle = state.styleOverrides.alarmStyle,
            )
        }
    }
}
