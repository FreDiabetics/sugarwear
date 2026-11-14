package app.sugarlicious.mobile

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.model.AppearanceMode

internal fun deviceUsesDarkMode(context: Context): Boolean =
    context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal fun resolveSettingsAppearance(mode: DashboardThemeMode, deviceDark: Boolean): AppearanceMode =
    when (mode) {
        DashboardThemeMode.LIGHT -> AppearanceMode.LIGHT
        DashboardThemeMode.DARK -> AppearanceMode.DARK
        DashboardThemeMode.SYSTEM -> if (deviceDark) AppearanceMode.DARK else AppearanceMode.LIGHT
    }

internal fun settingsSystemMode(enabled: Boolean, deviceDark: Boolean): DashboardThemeMode =
    if (enabled) {
        DashboardThemeMode.SYSTEM
    } else if (deviceDark) {
        DashboardThemeMode.DARK
    } else {
        DashboardThemeMode.LIGHT
    }

@Composable
internal fun OneUiResetIcon(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Zurücksetzen", enabled: Boolean = true) {
    IconButton(onClick, modifier.size(40.dp), enabled = enabled) {
        Icon(painterResource(R.drawable.ic_oneui_reset), description, tint = if (enabled) OneUiWidgetStyle.accent else OneUiWidgetStyle.secondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun OneUiSettingsModeSelector(mode: DashboardThemeMode, showSystemChoice: Boolean = true, showIllustrations: Boolean = true, onSelected: (DashboardThemeMode) -> Unit) {
    LocalConfiguration.current // Observe configuration; read actual device, not the forced menu ContextThemeWrapper.
    val deviceDark = deviceUsesDarkMode(LocalContext.current)
    val resolved = resolveSettingsAppearance(mode, deviceDark)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(DashboardThemeMode.LIGHT, DashboardThemeMode.DARK).forEach { option ->
                val selected = resolved == if (option == DashboardThemeMode.LIGHT) AppearanceMode.LIGHT else AppearanceMode.DARK
                Column(
                    Modifier.weight(1f).selectable(selected, role = Role.RadioButton) { onSelected(option) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showIllustrations) OneUiThemeIllustration(option == DashboardThemeMode.DARK)
                    Text(if (option == DashboardThemeMode.LIGHT) "Hell" else "Dunkel", color = OneUiWidgetStyle.primary, fontSize = 16.sp)
                    RadioButton(selected, null)
                }
            }
        }
        if (showSystemChoice) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = OneUiWidgetStyle.secondary.copy(alpha = .2f))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Geräteabhängig", color = OneUiWidgetStyle.primary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                WidgetSettingsToggle(mode == DashboardThemeMode.SYSTEM, { onSelected(settingsSystemMode(it, deviceDark)) }, label = "Geräteabhängig")
            }
        }
    }
}

@Composable
private fun OneUiThemeIllustration(dark: Boolean) {
    val frame = if (dark) Color(0xFF30353D) else Color(0xFFE7E8EC)
    val card = if (dark) Color(0xFF1C1C1C) else Color.White
    val ink = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    Canvas(Modifier.width(86.dp).height(166.dp)) {
        val sx = size.width / 86f
        val sy = size.height / 166f

        fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Color) =
            drawRoundRect(color, Offset(x * sx, y * sy), Size(w * sx, h * sy), CornerRadius(radius * sx))
        rect(0f, 0f, 86f, 166f, 8f, frame)
        rect(5f, 8f, 76f, 56f, 6f, card)
        rect(5f, 69f, 76f, 44f, 6f, card)
        rect(5f, 118f, 76f, 40f, 6f, card)
        drawIntoCanvas { native ->
            val paint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    app.sugarlicious.uishared.SystemTypography
                        .apply(this, emphasized = true)
                }

            fun label(value: String, x: Float, y: Float, font: Float) {
                paint.textSize = font * sx
                native.nativeCanvas.drawText(value, x * sx, y * sy, paint)
            }
            label("98 →", 11f, 27f, 12f)
            label("−2", 61f, 26f, 7f)
            label("IOB", 10f, 59f, 5f)
            label("COB", 36f, 59f, 5f)
            label("Basal", 60f, 59f, 5f)
        }
        drawLine(Color(0xFFDBB44D), Offset(9f * sx, 77f * sy), Offset(77f * sx, 77f * sy), sx)
        drawLine(Color(0xFFC95F68), Offset(9f * sx, 105f * sy), Offset(77f * sx, 105f * sy), sx)
        listOf(86f, 82f, 79f, 82f, 87f, 84f, 78f, 74f, 77f, 81f, 79f, 76f).forEachIndexed { index, y ->
            drawCircle(if (dark) Color.White else Color.DarkGray, 1.6f * sx, Offset((11f + index * 5.8f) * sx, (y + 10f) * sy))
        }
        listOf(Color(0xFF60C4F7), Color(0xFFFFA516), Color(0xFF28B6BD)).forEachIndexed { index, color ->
            val stroke = 2.5f * sx
            val arcs = app.sugarlicious.model.progressArcSegments(listOf(.6f, .3f, .8f)[index], 17f * sx, stroke, 270f)
            val topLeft = Offset((10f + index * 25f) * sx, 35f * sy)
            val arcSize = Size(17f * sx, 17f * sy)
            val style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (arcs.trackSweep > 0f) drawArc(color.copy(alpha = .3f), 130f + arcs.trackStartOffset, arcs.trackSweep, false, topLeft, arcSize, style = style)
            drawArc(color, 130f, arcs.fillSweep, false, topLeft, arcSize, style = style)
        }
        listOf(Color(0xFF60C4F7), Color(0xFFFFA516)).forEachIndexed { row, color ->
            val values = if (row == 0) listOf(8f, 4f, 2f, 5f, 9f, 11f, 9f, 6f) else listOf(10f, 8f, 4f, 2f, 5f, 9f, 10f, 8f)
            values.zipWithNext().forEachIndexed { index, (from, to) ->
                drawLine(
                    color,
                    Offset((10f + index * 9f) * sx, (121f + row * 15f + from) * sy),
                    Offset((19f + index * 9f) * sx, (121f + row * 15f + to) * sy),
                    1.3f * sx,
                )
            }
        }
    }
}

@Composable
internal fun OneUiGlucoseUnitSelector(selected: DisplayUnitPreference, onSelected: (DisplayUnitPreference) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        val options = listOf(DisplayUnitPreference.AAPS to "Wie Datenquelle", DisplayUnitPreference.MG_DL to "mg/dL", DisplayUnitPreference.MMOL_L to "mmol/L")
        options.forEachIndexed { index, (option, label) ->
            if (index > 0) VerticalDivider(Modifier.height(45.dp).padding(top = 6.dp), color = OneUiWidgetStyle.secondary.copy(alpha = .25f))
            Column(
                Modifier.weight(1f).selectable(selected == option, role = Role.RadioButton) { onSelected(option) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.settings_ic_glucose_units), null, Modifier.size(28.dp), tint = if (selected == option) OneUiWidgetStyle.accent else OneUiWidgetStyle.secondary)
                Text(label, color = OneUiWidgetStyle.primary, fontSize = 14.sp)
                RadioButton(selected == option, null)
            }
        }
    }
}
