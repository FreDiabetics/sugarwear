package app.sugarlicious.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.sugarlicious.mobile.ui.theme.SugarliciousColors

@Composable
internal fun OverviewActionMenu(onWatch: () -> Unit, onSettings: () -> Unit) {
    val expanded = remember { MutableTransitionState(false) }
    val offset = with(LocalDensity.current) { 52.dp.roundToPx() }
    Box {
        Box(
            Modifier.size(48.dp).clickable(onClickLabel = "Menü öffnen oder schließen") { expanded.targetState = !expanded.targetState },
            contentAlignment = Alignment.Center,
        ) {
            SugarliciousIcon(R.drawable.ic_foreground, "Menü", Modifier.requiredSize(58.dp), SugarliciousColors.BrandGreen)
        }
        if (expanded.currentState || expanded.targetState) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offset), onDismissRequest = { expanded.targetState = false }, properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
                AnimatedVisibility(
                    visibleState = expanded,
                    enter = expandVertically(spring(dampingRatio = .78f, stiffness = 180f), expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = .86f, stiffness = 180f), shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column(Modifier.background(SugarliciousColors.SurfaceRaised, RoundedCornerShape(32.dp)).padding(vertical = 4.dp)) {
                        MenuAction("Watch", R.drawable.ic_galaxywatch_filled) {
                            expanded.targetState = false
                            onWatch()
                        }
                        MenuAction("Einstellungen", R.drawable.ic_settings) {
                            expanded.targetState = false
                            onSettings()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuAction(label: String, icon: Int, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        Box(Modifier.size(48.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            SugarliciousIcon(icon, label, Modifier.size(24.dp), SugarliciousColors.TextPrimary, colored = false)
        }
    }
}
