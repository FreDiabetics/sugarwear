package app.sugarwear.debug.playground.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarwear.debug.playground.PlaygroundAction

/**
 * Main editor composable providing visual tuning controls for graph, home, alarm, and animation styles.
 */
@Composable
fun PlaygroundEditor(
    overrides: PlaygroundStyleOverrides,
    onAction: (PlaygroundAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "Visual Style Editor",
            style = TextStyle(color = Color(0xFF22EB60), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(10.dp))

        PlaygroundLayoutEditor(
            graphStyle = overrides.graphStyle,
            homeStyle = overrides.homeStyle,
            onGraphStyleChange = { onAction(PlaygroundAction.UpdateGraphStyle(it)) },
            onHomeStyleChange = { onAction(PlaygroundAction.UpdateHomeStyle(it)) },
        )

        Spacer(Modifier.height(12.dp))

        PlaygroundTypographyEditor(
            homeStyle = overrides.homeStyle,
            onHomeStyleChange = { onAction(PlaygroundAction.UpdateHomeStyle(it)) },
        )

        Spacer(Modifier.height(12.dp))

        PlaygroundColorEditor(
            graphStyle = overrides.graphStyle,
            onGraphStyleChange = { onAction(PlaygroundAction.UpdateGraphStyle(it)) },
        )

        Spacer(Modifier.height(12.dp))

        PlaygroundAnimationEditor(
            graphStyle = overrides.graphStyle,
            homeStyle = overrides.homeStyle,
            alarmStyle = overrides.alarmStyle,
            onGraphStyleChange = { onAction(PlaygroundAction.UpdateGraphStyle(it)) },
            onHomeStyleChange = { onAction(PlaygroundAction.UpdateHomeStyle(it)) },
            onAlarmStyleChange = { onAction(PlaygroundAction.UpdateAlarmStyle(it)) },
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                "Zurücksetzen",
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF242424))
                    .clickable { onAction(PlaygroundAction.ResetStyles) }
                    .padding(vertical = 10.dp),
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )

            BasicText(
                "Schließen",
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF22EB60))
                    .clickable { onAction(PlaygroundAction.ToggleEditor(open = false)) }
                    .padding(vertical = 10.dp),
                style = TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }
}
