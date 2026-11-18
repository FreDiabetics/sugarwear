package app.sugarwear.debug.playground.editor

import androidx.compose.ui.graphics.toArgb

/**
 * Exporter generating JSON strings and Kotlin snippet representation of active debug visual style overrides.
 * Explicitly documents which production file must be modified for permanent application.
 */
object PlaygroundStyleExporter {
    fun exportToJson(overrides: PlaygroundStyleOverrides): String {
        val graph = overrides.graphStyle
        val home = overrides.homeStyle
        val alarm = overrides.alarmStyle

        return """
            {
              "target_production_files": [
                "app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/GraphVisualStyle.kt",
                "app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/HomeVisualStyle.kt",
                "app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/AlarmVisualStyle.kt"
              ],
              "graphStyle": {
                "lineWidthDp": ${graph.lineWidthDp.value},
                "pointRadiusDp": ${graph.pointRadiusDp.value},
                "fillEnabled": ${graph.fillEnabled},
                "dotInRangeColor": "0x${Integer.toHexString(graph.dotInRangeColor.toArgb())}",
                "dotLowColor": "0x${Integer.toHexString(graph.dotLowColor.toArgb())}",
                "dotHighColor": "0x${Integer.toHexString(graph.dotHighColor.toArgb())}"
              },
              "homeStyle": {
                "glucoseScalePercent": ${home.glucoseScalePercent},
                "cardCornerRadiusDp": ${home.cardCornerRadiusDp.value},
                "graphTileHeightDp": ${home.graphTileHeightDp.value}
              },
              "alarmStyle": {
                "iconSizeDp": ${alarm.iconSizeDp.value},
                "buttonCornerRadiusDp": ${alarm.buttonCornerRadiusDp.value}
              }
            }
            """.trimIndent()
    }

    fun exportToKotlin(overrides: PlaygroundStyleOverrides): String {
        val graph = overrides.graphStyle
        val home = overrides.homeStyle

        return """
            // Copy to app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/GraphVisualStyle.kt
            val customGraphStyle = GraphVisualStyle(
                lineWidthDp = ${graph.lineWidthDp.value}.dp,
                pointRadiusDp = ${graph.pointRadiusDp.value}.dp,
                fillEnabled = ${graph.fillEnabled},
                animationEnabled = ${graph.animationEnabled}
            )

            // Copy to app-sugarwear/src/main/kotlin/app/sugarwear/ui/visual/HomeVisualStyle.kt
            val customHomeStyle = HomeVisualStyle(
                glucoseScalePercent = ${home.glucoseScalePercent},
                cardCornerRadiusDp = ${home.cardCornerRadiusDp.value}.dp,
                graphTileHeightDp = ${home.graphTileHeightDp.value}.dp
            )
            """.trimIndent()
    }
}
