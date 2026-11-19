package app.sugarwear.debug.playground.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarwear.CgmAppearanceStore
import app.sugarwear.CgmCollectorGraphView
import app.sugarwear.debug.playground.PlaygroundAction
import app.sugarwear.debug.playground.PlaygroundUiState

/**
 * Renders the home screen overview representation with live/demo state and visual overrides.
 */
@Composable
fun HomePlaygroundScreen(
    state: PlaygroundUiState,
    readings: List<CgmReading>,
    onAction: (PlaygroundAction) -> Unit,
) {
    val context = LocalContext.current
    val latestReading = readings.lastOrNull()
    val homeStyle = state.styleOverrides.homeStyle

    Column(
        Modifier
            .fillMaxSize()
            .background(homeStyle.backgroundColor)
            .padding(horizontal = homeStyle.contentPaddingDp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))

        // Glucose Card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(homeStyle.cardCornerRadiusDp))
                .background(homeStyle.cardBackgroundColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val valueText = latestReading?.let { "%.0f".format(it.glucoseMgDl) } ?: "---"
                val scaledFontSize = (36 * (homeStyle.glucoseScalePercent / 100f)).sp

                BasicText(
                    valueText,
                    style =
                        TextStyle(
                            color = homeStyle.primaryTextColor,
                            fontSize = scaledFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                )
                BasicText(
                    "mg/dL",
                    style = TextStyle(color = homeStyle.secondaryTextColor, fontSize = 14.sp),
                )
            }
        }

        Spacer(Modifier.height(homeStyle.cardSpacingDp))

        // Embedded Graph Canvas
        Box(
            Modifier
                .fillMaxWidth()
                .height(homeStyle.graphTileHeightDp)
                .clip(RoundedCornerShape(homeStyle.cardCornerRadiusDp))
                .background(homeStyle.cardBackgroundColor),
        ) {
            AndroidView(
                factory = { ctx ->
                    CgmCollectorGraphView(ctx).apply {
                        bind(readings, CgmAppearanceStore(ctx).load(), 3, System.currentTimeMillis())
                    }
                },
                update = { view ->
                    view.bind(readings, CgmAppearanceStore(view.context).load(), 3, System.currentTimeMillis())
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}
