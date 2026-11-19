package app.sugarwear.debug.playground.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarwear.ui.visual.AlarmVisualStyle
import app.sugarwear.ui.visual.GraphVisualStyle
import app.sugarwear.ui.visual.HomeVisualStyle

/**
 * Editor for animation parameters and duration settings.
 */
@Composable
fun PlaygroundAnimationEditor(
    graphStyle: GraphVisualStyle,
    homeStyle: HomeVisualStyle,
    alarmStyle: AlarmVisualStyle,
    onGraphStyleChange: (GraphVisualStyle) -> Unit,
    onHomeStyleChange: (HomeVisualStyle) -> Unit,
    onAlarmStyleChange: (AlarmVisualStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicText(
            "Animationen & Effekte",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(8.dp))

        PlaygroundSwitchControl(
            label = "Graph-Animationen aktivieren",
            checked = graphStyle.animationEnabled,
            onCheckedChange = { onGraphStyleChange(graphStyle.copy(animationEnabled = it)) },
        )

        Spacer(Modifier.height(4.dp))

        PlaygroundSwitchControl(
            label = "Status-Indikator Animationen",
            checked = homeStyle.animateStatusIndicators,
            onCheckedChange = { onHomeStyleChange(homeStyle.copy(animateStatusIndicators = it)) },
        )

        Spacer(Modifier.height(4.dp))

        PlaygroundSwitchControl(
            label = "Alarm-Animationen aktivieren",
            checked = alarmStyle.animationEnabled,
            onCheckedChange = { onAlarmStyleChange(alarmStyle.copy(animationEnabled = it)) },
        )
    }
}
