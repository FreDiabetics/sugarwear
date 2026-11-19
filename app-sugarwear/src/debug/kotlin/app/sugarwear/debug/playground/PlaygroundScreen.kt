package app.sugarwear.debug.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarwear.debug.playground.editor.PlaygroundEditor
import app.sugarwear.debug.playground.screens.AlarmPlaygroundScreen
import app.sugarwear.debug.playground.screens.ComponentPlaygroundScreen
import app.sugarwear.debug.playground.screens.GraphPlaygroundScreen
import app.sugarwear.debug.playground.screens.HomePlaygroundScreen
import app.sugarwear.debug.playground.screens.NotificationPlaygroundScreen
import app.sugarwear.debug.playground.screens.TilePlaygroundScreen

/**
 * Top-level Wear OS Composable container for SugarWear UI Playground.
 * Assembles toolbar, preview screen, category tabs, and editor drawer.
 */
@Composable
fun PlaygroundScreen(
    viewModel: PlaygroundViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val demoReadings by viewModel.demoDataSource.readings.collectAsState()
    val liveReadings by viewModel.liveDataSource.readings.collectAsState()

    val currentReadings = if (uiState.dataMode == DataMode.LIVE) liveReadings else demoReadings

    if (uiState.isEditorOpen) {
        PlaygroundEditor(
            overrides = uiState.styleOverrides,
            onAction = viewModel::onAction,
        )
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Category Tabs Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaygroundNavigation.categories.forEach { category ->
                    val isSelected = category == uiState.activeTab
                    BasicText(
                        category.label.take(4),
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF22EB60) else Color.Transparent)
                            .clickable { viewModel.onAction(PlaygroundAction.SelectCategory(category)) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        style =
                            TextStyle(
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }

            // Mode & Toolbar Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1C))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mode Toggle (DEMO vs LIVE)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2B2B2B))
                        .padding(2.dp),
                ) {
                    listOf(DataMode.DEMO to "DEMO", DataMode.LIVE to "LIVE").forEach { (mode, label) ->
                        val active = uiState.dataMode == mode
                        BasicText(
                            label,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) Color(0xFF22EB60) else Color.Transparent)
                                .clickable { viewModel.onAction(PlaygroundAction.SetDataMode(mode)) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style =
                                TextStyle(
                                    color = if (active) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }

                // Editor Button
                BasicText(
                    "Editor",
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (uiState.styleOverrides.isModified) Color(0xFFFFD040) else Color(0xFF333333))
                        .clickable { viewModel.onAction(PlaygroundAction.ToggleEditor(open = true)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style =
                        TextStyle(
                            color = if (uiState.styleOverrides.isModified) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }

            // Active Screen Preview
            Box(Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    PlaygroundCategory.HOME -> HomePlaygroundScreen(uiState, currentReadings, viewModel::onAction)
                    PlaygroundCategory.GRAPH -> GraphPlaygroundScreen(uiState, currentReadings, viewModel::onAction)
                    PlaygroundCategory.ALARM -> AlarmPlaygroundScreen(uiState, viewModel::onAction)
                    PlaygroundCategory.NOTIFICATION -> NotificationPlaygroundScreen(uiState, viewModel::onAction)
                    PlaygroundCategory.TILE -> TilePlaygroundScreen(uiState, viewModel::onAction)
                    PlaygroundCategory.COMPONENT -> ComponentPlaygroundScreen(uiState, currentReadings, viewModel::onAction)
                }
            }
        }
    }
}
