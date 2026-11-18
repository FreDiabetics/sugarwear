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
import app.sugarwear.ui.visual.GraphVisualStyle

/**
 * Color picker and swatch editor for graph/UI color tokens.
 */
@Composable
fun PlaygroundColorEditor(
    graphStyle: GraphVisualStyle,
    onGraphStyleChange: (GraphVisualStyle) -> Unit,
) {
    val presets =
        listOf(
            Color(0xFF22EB60),
            Color(0xFFFF5C69),
            Color(0xFFFFD040),
            Color(0xFF4CAF50),
            Color(0xFFD32F2F),
            Color(0xFFF57C00),
            Color(0xFF2196F3),
            Color(0xFF9C27B0),
            Color(0xFFFFFFFF),
            Color(0xFF000000),
        )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicText(
            "Farbanpassung Graphen",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(8.dp))

        PlaygroundColorSwatchControl(
            label = "Zielbereich (InRange)",
            color = graphStyle.dotInRangeColor,
            onClick = {
                onGraphStyleChange(graphStyle.copy(dotInRangeColor = presets[(presets.indexOf(graphStyle.dotInRangeColor) + 1) % presets.size]))
            },
        )

        PlaygroundColorSwatchControl(
            label = "Niedrig (Low)",
            color = graphStyle.dotLowColor,
            onClick = {
                onGraphStyleChange(graphStyle.copy(dotLowColor = presets[(presets.indexOf(graphStyle.dotLowColor) + 1) % presets.size]))
            },
        )

        PlaygroundColorSwatchControl(
            label = "Hoch (High)",
            color = graphStyle.dotHighColor,
            onClick = {
                onGraphStyleChange(graphStyle.copy(dotHighColor = presets[(presets.indexOf(graphStyle.dotHighColor) + 1) % presets.size]))
            },
        )

        PlaygroundColorSwatchControl(
            label = "Hintergrund",
            color = graphStyle.backgroundColor,
            onClick = {
                onGraphStyleChange(graphStyle.copy(backgroundColor = if (graphStyle.backgroundColor == Color.Black) Color(0xFF121212) else Color.Black))
            },
        )
    }
}
