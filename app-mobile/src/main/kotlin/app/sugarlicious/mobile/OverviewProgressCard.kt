package app.sugarlicious.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.TherapyDisplayState

@Composable
internal fun OverviewProgressCard(state: TherapyDisplayState?, preferences: DashboardUiPreferences, now: Long) {
    val indicators = therapyIndicatorPresentations(state, preferences.iobProgressMaximumUnits, now, preferences.cobProgressMaximumGrams)
    BoxWithConstraints(Modifier.fillMaxWidth().background(SugarliciousColors.Surface, RoundedCornerShape(28.dp)).padding(18.dp)) {
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val columnWidth =
            with(density) {
                overviewProgressColumnWidthPx(constraints.maxWidth, 12.dp.roundToPx(), (104.dp * fontScale).roundToPx()).toDp().value
            }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            indicators.forEach { indicator ->
                Column(Modifier.width(columnWidth.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TherapyCircularIndicator(indicator.copy(secondary = null), Modifier, minOf(82f * fontScale, columnWidth))
                }
            }
        }
    }
}
