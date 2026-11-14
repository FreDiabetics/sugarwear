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
import app.sugarwear.ui.visual.HomeVisualStyle

/**
 * Editor for typography and font scaling parameters.
 */
@Composable
fun PlaygroundTypographyEditor(
    homeStyle: HomeVisualStyle,
    onHomeStyleChange: (HomeVisualStyle) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicText(
            "Typografie & Skalierung",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(8.dp))

        PlaygroundSliderControl(
            label = "Glukose-Textskalierung (%)",
            value = homeStyle.glucoseScalePercent.toFloat(),
            valueRange = 70f..150f,
            onValueChange = { onHomeStyleChange(homeStyle.copy(glucoseScalePercent = it.toInt())) },
            formatValue = { "${it.toInt()}%" },
        )
    }
}
