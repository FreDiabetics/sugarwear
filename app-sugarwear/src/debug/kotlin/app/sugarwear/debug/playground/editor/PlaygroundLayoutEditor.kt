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
import app.sugarwear.ui.visual.HomeVisualStyle

/**
 * Editor for layout parameters (margins, paddings, stroke widths, corner radii).
 */
@Composable
fun PlaygroundLayoutEditor(
    graphStyle: GraphVisualStyle,
    homeStyle: HomeVisualStyle,
    onGraphStyleChange: (GraphVisualStyle) -> Unit,
    onHomeStyleChange: (HomeVisualStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicText(
            "Layout & Dimensionen",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(8.dp))

        PlaygroundSliderControl(
            label = "Linienbreite Graph (dp)",
            value = graphStyle.lineWidthDp.value,
            valueRange = 1f..6f,
            onValueChange = { onGraphStyleChange(graphStyle.copy(lineWidthDp = it.dp)) },
            formatValue = { "%.1f dp".format(it) },
        )

        PlaygroundSliderControl(
            label = "Punktradius Graph (dp)",
            value = graphStyle.pointRadiusDp.value,
            valueRange = 2f..10f,
            onValueChange = { onGraphStyleChange(graphStyle.copy(pointRadiusDp = it.dp)) },
            formatValue = { "%.1f dp".format(it) },
        )

        PlaygroundSliderControl(
            label = "Graph Kachelhöhe (dp)",
            value = homeStyle.graphTileHeightDp.value,
            valueRange = 100f..220f,
            onValueChange = { onHomeStyleChange(homeStyle.copy(graphTileHeightDp = it.dp)) },
            formatValue = { "${it.toInt()} dp" },
        )

        PlaygroundSliderControl(
            label = "Karten Eckenradius (dp)",
            value = homeStyle.cardCornerRadiusDp.value,
            valueRange = 0f..30f,
            onValueChange = { onHomeStyleChange(homeStyle.copy(cardCornerRadiusDp = it.dp)) },
            formatValue = { "${it.toInt()} dp" },
        )
    }
}
