package app.sugarwear.debug.playground

import androidx.compose.ui.unit.dp
import app.sugarwear.debug.playground.editor.PlaygroundStyleOverrides
import app.sugarwear.debug.playground.editor.PlaygroundStyleValidator
import app.sugarwear.ui.visual.GraphVisualStyle
import app.sugarwear.ui.visual.HomeVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaygroundStyleValidatorTest {

    @Test
    fun `validator clamps invalid negative dimensions to safe bounds`() {
        val invalidGraph = GraphVisualStyle(lineWidthDp = (-5).dp, fillAlpha = 2.5f)
        val invalidHome = HomeVisualStyle(glucoseScalePercent = 10, graphTileHeightDp = 10.dp)

        val overrides = PlaygroundStyleOverrides(graphStyle = invalidGraph, homeStyle = invalidHome)
        val validated = PlaygroundStyleValidator.validate(overrides)

        assertEquals(0.5f, validated.graphStyle.lineWidthDp.value, 0.01f)
        assertEquals(1.0f, validated.graphStyle.fillAlpha, 0.01f)
        assertEquals(50, validated.homeStyle.glucoseScalePercent)
        assertEquals(80f, validated.homeStyle.graphTileHeightDp.value, 0.01f)
    }
}
