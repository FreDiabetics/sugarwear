package app.sugarlicious.mobile

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.AppearanceControlContract
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.ArgbColor
import app.sugarlicious.model.TrendArrowStyle
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/** A named surface always describes the displayed object, never the type of control. */
@Composable
internal fun MobileSettingsObjectCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .settingsSnapHeading(title)
            .background(OneUiWidgetStyle.card, RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = OneUiWidgetStyle.primary, style = oneUiMenuTypography.titleMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(1.dp).background(OneUiWidgetStyle.secondary.copy(alpha = 0.16f)))
        content()
    }
}

internal fun mobileAppearanceObjectTitle(role: SugarliciousColorRole): String =
    when {
        role in setOf(SugarliciousColorRole.OVERVIEW_GLUCOSE_PILL, SugarliciousColorRole.OVERVIEW_TIR_PILL) -> "Übersicht · Pillen"
        role.name.startsWith("OVERVIEW_WAVE_") -> "Glukosebalken"
        role.name.startsWith("GLUCOSE_") -> "Glukosewert"
        role == SugarliciousColorRole.DELTA_UNIT -> "Delta"
        role.name.startsWith("CGM_DOT_") || role == SugarliciousColorRole.GRAPH_CURRENT_OUTLINE -> "CGM-Punkte"
        role.name.startsWith("RANGE_") || role == SugarliciousColorRole.TARGET_BAND -> "Zielbereiche im Graph"
        role == SugarliciousColorRole.TARGET_VALUE -> "Zielwert"
        role.name.startsWith("PREDICTION_") -> "Vorhersagen"
        role.name.startsWith("PROGRESS_") -> "Glukose-Progressbar"
        role.name.startsWith("THERAPY_IOB") || role == SugarliciousColorRole.GRAPH_IOB -> "IOB"
        role.name.startsWith("THERAPY_COB") || role == SugarliciousColorRole.GRAPH_COB -> "COB"
        role.name.startsWith("THERAPY_BASAL") -> "Basalrate"
        role.name.startsWith("GRAPH_") -> "Graph"
        else -> role.group.label
    }

@Composable
internal fun SugarliciousColorSettingsPanel(
    showCgmGraph: Boolean = true,
    showMetabolicGraph: Boolean = false,
    themeMode: DashboardThemeMode,
    onThemeModeSelected: (DashboardThemeMode) -> Unit,
) {
    val model = rememberAppearanceSettingsModel("mobile-appearance", AppearanceColorTarget.MOBILE)
    val uiMode = androidx.compose.ui.platform.LocalConfiguration.current.uiMode
    LaunchedEffect(themeMode, uiMode) { model.refreshActiveMode() }
    val state by model.uiState.collectAsStateWithLifecycle()
    val snapshot =
        state.snapshot ?: run {
            AppearanceLoadingOrError(state, model::retry)
            return
        }
    AppearanceLoadingOrError(state, model::retry)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val selectedMode = snapshot.mode
    val palette = snapshot.palette
    var editingRole by AppearanceBinding({ model.uiState.value.editingRole }, model::editColor)
    var editingTrendColor by AppearanceBinding({ model.uiState.value.editingTrendColor }, model::editTrendColor)
    var trendStyle by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).trend }, model::setTrend)
    val watchSyncStatus = state.watchSyncStatus
    var cgmDotRadiusDp by model.floatBinding(AppearanceFloat.CGM_RADIUS)
    var cgmHistoricalDotOutlineEnabled by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).historicalOutlineEnabled }, model::setHistoricalOutline)
    var cgmHistoricalDotOutlineWidthDp by model.floatBinding(AppearanceFloat.HISTORICAL_WIDTH)
    var cgmCurrentDotOutlineWidthDp by model.floatBinding(AppearanceFloat.CURRENT_WIDTH)
    var predictionDotRadiusDp by model.floatBinding(AppearanceFloat.PREDICTION_RADIUS)
    var predictionDotOutlineWidthDp by model.floatBinding(AppearanceFloat.PREDICTION_WIDTH)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MobileSettingsObjectCard("Darstellung") {
            OneUiSettingsModeSelector(themeMode, onSelected = onThemeModeSelected)
        }
        MobileSettingsObjectCard("Trendpfeil") {
            MobileGlucoseElementSize(model, trend = true)
            ColorSettingRow(null, "Füllfarbe", trendStyle.fillColor, { editingTrendColor = "fill" })
            GraphSettingSwitch("Kontur", "Separate Trendpfeil-Kontur", trendStyle.outlineEnabled) {
                trendStyle = trendStyle.copy(outlineEnabled = it)
                model.saveTrend()
            }
            if (trendStyle.outlineEnabled) {
                ColorSettingRow(null, "Konturfarbe", trendStyle.outlineColor, { editingTrendColor = "outline" })
                SugarliciousSettingSlider(
                    "Konturdicke",
                    "Trendpfeil-Kontur",
                    trendStyle.outlineThicknessDp,
                    0.25f..4f,
                    "${String.format(
                        locale,
                        "%.2f",
                        trendStyle.outlineThicknessDp,
                    )} dp",
                    {
                        trendStyle = trendStyle.copy(outlineThicknessDp = it)
                    },
                    { model.saveTrend() },
                )
            }
            SugarliciousSettingSlider("Deckkraft", "Trendpfeil", trendStyle.alpha * 100f, 0f..100f, "${(trendStyle.alpha * 100).roundToInt()} %", {
                trendStyle = trendStyle.copy(alpha = it / 100f)
            }, { model.saveTrend() })
        }
        MobileSettingsObjectCard("Watch-Farben") {
            Button(
                onClick = model::sendWatchColors,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = OneUiWidgetStyle.action,
                        contentColor = OneUiWidgetStyle.primary,
                    ),
            ) {
                Text("AN WATCH SENDEN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            watchSyncStatus?.let { status ->
                Text(status, color = OneUiWidgetStyle.secondary, style = oneUiMenuTypography.bodyMedium)
            }
        }
        if (showCgmGraph) {
            MobileSettingsObjectCard("CGM-Punkte") {
                Text(
                    text = "CGM-PUNKTE",
                    color = OneUiWidgetStyle.secondary,
                    style = oneUiMenuTypography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )

                SugarliciousSettingSlider(
                    title = "Punktgröße",
                    description = "Größe der CGM-Dots im Glukosegraph",
                    value = cgmDotRadiusDp,
                    valueRange = 1.5f..6.0f,
                    valueText = "${String.format(locale, "%.1f", cgmDotRadiusDp)} dp",
                    onValueChange = { cgmDotRadiusDp = it },
                    onValueChangeFinished = {
                        model.saveFloat(AppearanceFloat.CGM_RADIUS)
                    },
                )

                GraphSettingSwitch(
                    title = "Historische Kontur",
                    description = "Kontur um historische CGM-Dots anzeigen",
                    checked = cgmHistoricalDotOutlineEnabled,
                    onCheckedChange = { enabled ->
                        cgmHistoricalDotOutlineEnabled = enabled
                    },
                )

                if (cgmHistoricalDotOutlineEnabled) {
                    SugarliciousSettingSlider(
                        title = "Historische Konturdicke",
                        description = "Dicke der Kontur historischer Dots",
                        value = cgmHistoricalDotOutlineWidthDp,
                        valueRange = 0.25f..3.0f,
                        valueText = "${String.format(locale, "%.2f", cgmHistoricalDotOutlineWidthDp)} dp",
                        onValueChange = { cgmHistoricalDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it) },
                        onValueChangeFinished = {
                            model.saveFloat(AppearanceFloat.HISTORICAL_WIDTH)
                        },
                    )
                }

                SugarliciousSettingSlider(
                    title = "Aktuelle Konturdicke",
                    description = "Der aktuelle echte CGM-Dot besitzt immer eine Kontur",
                    value = cgmCurrentDotOutlineWidthDp,
                    valueRange = 0.25f..3.0f,
                    valueText = "${String.format(locale, "%.2f", cgmCurrentDotOutlineWidthDp)} dp",
                    onValueChange = { cgmCurrentDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it) },
                    onValueChangeFinished = {
                        model.saveFloat(AppearanceFloat.CURRENT_WIDTH)
                    },
                )

                AppearanceObjectColorRows("CGM-Punkte", palette, showCgmGraph, showMetabolicGraph) { editingRole = it }
            }
            MobileSettingsObjectCard("Vorhersagen") {
                SugarliciousSettingSlider(
                    title = "Prediction-Punktgröße",
                    description = "Größe der Vorhersagepunkte im CGM-Graph",
                    value = predictionDotRadiusDp,
                    valueRange = 1.0f..6.0f,
                    valueText = "${String.format(locale, "%.1f", predictionDotRadiusDp)} dp",
                    onValueChange = { predictionDotRadiusDp = it },
                    onValueChangeFinished = {
                        model.saveFloat(AppearanceFloat.PREDICTION_RADIUS)
                    },
                )

                SugarliciousSettingSlider(
                    title = "Prediction-Konturdicke",
                    description = "0,00 dp blendet die Kontur aus",
                    value = predictionDotOutlineWidthDp,
                    valueRange = 0.0f..3.0f,
                    valueText = "${String.format(locale, "%.2f", predictionDotOutlineWidthDp)} dp",
                    onValueChange = { predictionDotOutlineWidthDp = it },
                    onValueChangeFinished = {
                        model.saveFloat(AppearanceFloat.PREDICTION_WIDTH)
                    },
                )
                AppearanceObjectColorRows("Vorhersagen", palette, showCgmGraph, showMetabolicGraph) { editingRole = it }
            }
        }
        SugarliciousColorRole.entries
            .filter { colorRoleVisible(it, showCgmGraph, showMetabolicGraph, palette.isLight) }
            .groupBy(::mobileAppearanceObjectTitle)
            .forEach { (objectTitle, roles) ->
                if (showCgmGraph && objectTitle in setOf("CGM-Punkte", "Vorhersagen")) return@forEach
                MobileSettingsObjectCard(objectTitle) {
                    if (objectTitle == "Glukosewert") MobileGlucoseElementSize(model, trend = false)
                    roles.forEach { role -> ColorSettingRow(role = role, argb = palette.argb(role), onEdit = { editingRole = role }) }
                }
            }
    }

    editingRole?.let { role ->
        ColorEditorDialog(
            role = role,
            label = role.label,
            initialArgb = palette.argb(role),
            onReset = {
                model.reset(AppearanceReset.COLOR, role = role)
            },
            actions =
                listOf(
                    ColorPickerAction("Alle Farben zurücksetzen") {
                        model.reset(AppearanceReset.ALL_COLORS, role = role)
                    },
                ),
            onDismiss = { editingRole = null },
            onChange = { argb ->
                model.saveColor(role, argb)
            },
        )
    }
    editingTrendColor?.let { field ->
        ColorEditorDialog(
            role = null,
            label = if (field == "fill") "Trendpfeil · Füllfarbe" else "Trendpfeil · Konturfarbe",
            initialArgb = if (field == "fill") trendStyle.fillColor else trendStyle.outlineColor,
            resetArgb = if (field == "fill") palette.argb(SugarliciousColorRole.GLUCOSE_IN_RANGE) else TrendArrowStyle.defaults(selectedMode, trendStyle.fillColor).outlineColor,
            onDismiss = { editingTrendColor = null },
            onChange = { color ->
                trendStyle = if (field == "fill") trendStyle.copy(fillColor = color) else trendStyle.copy(outlineColor = color)
                model.saveTrend()
            },
        )
    }
}

@Composable
private fun AppearanceObjectColorRows(
    title: String,
    palette: app.sugarlicious.mobile.ui.theme.SugarliciousPalette,
    showCgmGraph: Boolean,
    showMetabolicGraph: Boolean,
    onEdit: (SugarliciousColorRole) -> Unit,
) {
    SugarliciousColorRole.entries
        .filter {
            mobileAppearanceObjectTitle(it) == title && colorRoleVisible(it, showCgmGraph, showMetabolicGraph, palette.isLight)
        }.forEach { role -> ColorSettingRow(role = role, argb = palette.argb(role), onEdit = { onEdit(role) }) }
}

@Composable
private fun MobileGlucoseElementSize(model: AppearanceSettingsViewModel, trend: Boolean) {
    val field = if (trend) AppearanceFloat.TREND_SIZE else AppearanceFloat.GLUCOSE_SIZE
    var size by model.floatBinding(field)
    val minimum =
        app.sugarlicious.model.GlucoseTrendSizing.MIN_SCALE_PERCENT
            .toFloat()
    val maximum =
        app.sugarlicious.model.GlucoseTrendSizing.MAX_SCALE_PERCENT
            .toFloat()
    SugarliciousSettingSlider(
        "Größe",
        if (trend) "Trendpfeil in der Übersicht" else "Glukosewert in der Übersicht",
        size,
        minimum..maximum,
        "${size.toInt()} %",
        { size = it.toInt().toFloat() },
        { model.saveFloat(field) },
    )
}

@Composable
internal fun WidgetColorSettingsPanel() {
    val model = rememberAppearanceSettingsModel("widget-appearance", AppearanceColorTarget.WIDGET)
    val state by model.uiState.collectAsStateWithLifecycle()
    val snapshot =
        state.snapshot ?: run {
            AppearanceLoadingOrError(state, model::retry)
            return
        }
    AppearanceLoadingOrError(state, model::retry)
    var editingRole by AppearanceBinding({ model.uiState.value.editingWidgetRole }, model::editWidgetColor)
    var selectedMode by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).mode }, model::selectMode)
    var deviceVendorCompact by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).deviceVendorCompact }, model::setCompact)
    val palette = snapshot.widgetPalette

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MobileSettingsObjectCard("Widget-Grunddarstellung") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetSettingsToggle(
                    checked = deviceVendorCompact,
                    onCheckedChange = { enabled ->
                        deviceVendorCompact = enabled
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Samsung-kompakte Widgetgröße", color = OneUiWidgetStyle.primary, style = oneUiMenuTypography.bodyLarge)
                    Text(
                        "Gleicher Innenabstand wie bei Samsung-Widgets; gilt für alle Mobile-Widgets.",
                        color = OneUiWidgetStyle.secondary,
                        style = oneUiMenuTypography.bodyMedium,
                    )
                }
            }
            AppearanceModeSelector(selectedMode) { selectedMode = it }
            Text(
                "Diese App-Defaults gelten nur für Widgets ohne eigenen Override. Zeitraum, Form und Tap-Ziel stellst du direkt pro Widget ein.",
                color = OneUiWidgetStyle.secondary,
                style = oneUiMenuTypography.bodyMedium,
            )
        }
        WidgetColorRole.entries
            .filterNot { it in setOf(WidgetColorRole.DELTA_SYMBOL, WidgetColorRole.AGE_SYMBOL, WidgetColorRole.TIR_SYMBOL) }
            .groupBy(::widgetAppearanceObjectTitle)
            .forEach { (title, roles) ->
                MobileSettingsObjectCard(title) {
                    roles.forEach { role -> WidgetColorSettingRow(role, palette.argb(role)) { editingRole = role } }
                }
            }
    }

    editingRole?.let { role ->
        ColorEditorDialog(
            role = null,
            label = "Widget: ${role.label}",
            initialArgb = palette.argb(role),
            onReset = {
                model.reset(AppearanceReset.COLOR, widgetRole = role)
            },
            actions =
                listOf(
                    ColorPickerAction("Aus Mobile-Graph übernehmen") {
                        model.reset(AppearanceReset.COPY_MOBILE, widgetRole = role)
                    },
                    ColorPickerAction("Alle Widget-Farben zurücksetzen") {
                        model.reset(AppearanceReset.ALL_COLORS, widgetRole = role)
                    },
                ),
            onDismiss = { editingRole = null },
            onChange = { argb ->
                model.saveWidgetColor(role, argb)
            },
        )
    }
}

internal fun widgetAppearanceObjectTitle(role: WidgetColorRole): String =
    when {
        role.name.startsWith("IOB_") -> "IOB"
        role.name.startsWith("COB_") -> "COB"
        role.name.startsWith("BASAL_") -> "Basalrate"
        role.name.startsWith("TREND") -> "Trendpfeil"
        role.name.startsWith("DELTA") -> "Delta"
        role.name.startsWith("AGE") -> "Alter"
        role.name.startsWith("TIR") -> "Zeit im Ziel"
        role in setOf(WidgetColorRole.HIGH, WidgetColorRole.IN_RANGE, WidgetColorRole.LOW, WidgetColorRole.URGENT_LOW, WidgetColorRole.VERY_HIGH, WidgetColorRole.UNIT) -> "Glukosewert"
        role in setOf(WidgetColorRole.TEXT, WidgetColorRole.BACKGROUND) -> "Widget-Hintergrund und Text"
        role.name.startsWith("DOT_") || role == WidgetColorRole.CURRENT_DOT_OUTLINE -> "CGM-Punkte"
        role.name.startsWith("RANGE_") || role in setOf(WidgetColorRole.HIGH_LINE, WidgetColorRole.LOW_LINE) -> "Zielbereiche im Graph"
        else -> "Graph"
    }

@Composable
private fun WidgetColorSettingRow(
    role: WidgetColorRole,
    argb: Int,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(OneUiWidgetStyle.card, RoundedCornerShape(28.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .background(Color(argb), CircleShape)
                    .border(1.dp, SugarliciousColors.Border, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(role.label, color = OneUiWidgetStyle.primary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
internal fun AppearanceModeSelector(
    selected: AppearanceMode,
    onSelected: (AppearanceMode) -> Unit,
) {
    OneUiSettingsModeSelector(if (selected == AppearanceMode.LIGHT) DashboardThemeMode.LIGHT else DashboardThemeMode.DARK, showSystemChoice = false) {
        onSelected(if (it == DashboardThemeMode.LIGHT) AppearanceMode.LIGHT else AppearanceMode.DARK)
    }
}

@Composable
private fun GraphSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(OneUiWidgetStyle.card, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = OneUiWidgetStyle.primary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
            Text(description, color = OneUiWidgetStyle.secondary, style = oneUiMenuTypography.bodyMedium)
        }
        WidgetSettingsToggle(checked, onCheckedChange, label = title)
    }
}

@Composable
internal fun SugarliciousSettingSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val editor = rememberAppearanceEditorModel("appearance-number:$title:$description", AppearanceNumberEditorViewModel::class.java)
    val editorState by editor.uiState.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(OneUiWidgetStyle.card, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OneUiWidgetStyle.primary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
                Text(description, color = OneUiWidgetStyle.secondary, style = oneUiMenuTypography.bodyMedium)
            }
            Text(
                valueText,
                color = OneUiWidgetStyle.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .background(OneUiWidgetStyle.action, RoundedCornerShape(10.dp))
                        .clickable { editor.open(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        MobileSettingsSeekBar(title, value, valueRange, onValueChange, onValueChangeFinished)
    }
    if (editorState.open) {
        AlertDialog(
            onDismissRequest = editor::dismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = editorState.text,
                    onValueChange = editor::enter,
                    label = { Text("Wert ${valueRange.start} bis ${valueRange.endInclusive}") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editor.confirm(valueRange)?.let {
                        onValueChange(it)
                        onValueChangeFinished()
                    }
                }) { Text("ÜBERNEHMEN") }
            },
            dismissButton = { TextButton(onClick = editor::dismiss) { Text("ABBRECHEN") } },
        )
    }
}

@Composable
private fun ColorSettingRow(
    role: SugarliciousColorRole?,
    label: String = role?.label ?: "Farbe",
    argb: Int,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(OneUiWidgetStyle.card, RoundedCornerShape(28.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(Color(argb), CircleShape)
                    .border(1.dp, SugarliciousColors.Border, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(text = label, color = OneUiWidgetStyle.primary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
        Spacer(Modifier.width(6.dp))
        if (role != null) {
            ColorRoleExample(role = role, argb = argb, modifier = Modifier.width(88.dp).height(42.dp))
        }
    }
}

@Composable
private fun ColorRoleExample(
    role: SugarliciousColorRole,
    argb: Int,
    modifier: Modifier = Modifier,
) {
    val selectedColor = Color(argb)
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier =
            modifier
                .background(SugarliciousColors.Surface, shape)
                .border(1.dp, SugarliciousColors.Border, shape)
                .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (role) {
            SugarliciousColorRole.BACKGROUND -> {
                Box(
                    Modifier.fillMaxSize().background(selectedColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(20.dp)
                            .background(SugarliciousColors.Surface, RoundedCornerShape(6.dp)),
                    )
                }
            }

            SugarliciousColorRole.SURFACE,
            SugarliciousColorRole.OVERVIEW_GLUCOSE_PILL,
            SugarliciousColorRole.OVERVIEW_TIR_PILL,
            SugarliciousColorRole.SURFACE_HIGH,
            SugarliciousColorRole.SURFACE_RAISED,
            SugarliciousColorRole.SURFACE_SELECTED,
            -> {
                Box(
                    Modifier
                        .width(60.dp)
                        .height(27.dp)
                        .background(selectedColor, RoundedCornerShape(8.dp))
                        .border(1.dp, SugarliciousColors.Border, RoundedCornerShape(8.dp)),
                )
            }

            SugarliciousColorRole.BORDER -> {
                Box(Modifier.width(60.dp).height(27.dp).border(2.dp, selectedColor, RoundedCornerShape(8.dp)))
            }

            SugarliciousColorRole.TEXT_PRIMARY,
            SugarliciousColorRole.TEXT_SECONDARY,
            -> {
                Text("Aa", color = selectedColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            SugarliciousColorRole.ON_PRIMARY -> {
                Box(
                    Modifier.fillMaxSize().background(SugarliciousColors.Primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = selectedColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            SugarliciousColorRole.ON_SECONDARY -> {
                Box(
                    Modifier.fillMaxSize().background(SugarliciousColors.Secondary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = selectedColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            SugarliciousColorRole.GLUCOSE_LOW,
            SugarliciousColorRole.GLUCOSE_IN_RANGE,
            SugarliciousColorRole.GLUCOSE_HIGH,
            SugarliciousColorRole.RANGE_LOW,
            SugarliciousColorRole.RANGE_VERY_LOW,
            SugarliciousColorRole.RANGE_IN_RANGE,
            SugarliciousColorRole.RANGE_HIGH,
            SugarliciousColorRole.RANGE_VERY_HIGH,
            SugarliciousColorRole.CGM_DOT_LOW,
            SugarliciousColorRole.CGM_DOT_IN_RANGE,
            SugarliciousColorRole.CGM_DOT_HIGH,
            -> {
                Text("123", color = selectedColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }

            SugarliciousColorRole.PROGRESS_BELOW,
            SugarliciousColorRole.PROGRESS_IN_RANGE,
            SugarliciousColorRole.PROGRESS_ABOVE,
            -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(SugarliciousColors.SurfaceRaised, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.68f)
                            .height(12.dp)
                            .background(selectedColor, RoundedCornerShape(999.dp)),
                    )
                }
            }

            SugarliciousColorRole.TARGET_BAND -> {
                Box(Modifier.fillMaxWidth().height(18.dp).background(selectedColor, RoundedCornerShape(5.dp)))
            }

            SugarliciousColorRole.PREDICTION_IOB,
            SugarliciousColorRole.PREDICTION_COB,
            SugarliciousColorRole.PREDICTION_UAM,
            SugarliciousColorRole.PREDICTION_ZERO_TEMP,
            -> {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(5) { index ->
                        drawCircle(
                            color = selectedColor,
                            radius = 3.3.dp.toPx(),
                            center =
                                Offset(
                                    x = 9.dp.toPx() + index * 14.dp.toPx(),
                                    y = size.height - 8.dp.toPx() - index * 4.dp.toPx(),
                                ),
                        )
                    }
                }
            }

            SugarliciousColorRole.GRAPH_IOB,
            SugarliciousColorRole.GRAPH_COB,
            SugarliciousColorRole.GRAPH_INSULIN_ACTIVITY,
            -> {
                Canvas(Modifier.fillMaxSize()) {
                    val points =
                        listOf(
                            Offset(4.dp.toPx(), size.height - 5.dp.toPx()),
                            Offset(22.dp.toPx(), size.height - 15.dp.toPx()),
                            Offset(40.dp.toPx(), size.height - 11.dp.toPx()),
                            Offset(58.dp.toPx(), 7.dp.toPx()),
                        )
                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(color = selectedColor, start = start, end = end, strokeWidth = 3.dp.toPx())
                    }
                }
            }

            SugarliciousColorRole.GRAPH_GRID -> {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(3) { index ->
                        val y = size.height * index / 2f
                        drawLine(selectedColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    repeat(3) { index ->
                        val x = size.width * index / 2f
                        drawLine(selectedColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
            }

            SugarliciousColorRole.GRAPH_LABEL -> {
                Text("120  18", color = selectedColor, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            SugarliciousColorRole.GRAPH_MUTED,
            SugarliciousColorRole.GRAPH_DIVIDER,
            SugarliciousColorRole.GRAPH_BORDER,
            SugarliciousColorRole.GRAPH_SIGNAL_LOSS,
            -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { Box(Modifier.width(11.dp).height(2.dp).background(selectedColor)) }
                }
            }

            SugarliciousColorRole.GRAPH_CURRENT_OUTLINE -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(selectedColor, 12.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                    drawCircle(SugarliciousColors.GlucoseInRange, 7.dp.toPx(), center)
                }
            }

            SugarliciousColorRole.GRAPH_BACKGROUND -> {
                Box(Modifier.fillMaxSize().background(selectedColor, RoundedCornerShape(10.dp)))
            }

            else -> {
                val label =
                    when (role) {
                        SugarliciousColorRole.BLUE -> "IOB"
                        SugarliciousColorRole.ORANGE -> "COB"
                        SugarliciousColorRole.YELLOW -> "!"
                        SugarliciousColorRole.RED -> "!"
                        SugarliciousColorRole.PURPLE -> "●"
                        SugarliciousColorRole.GREEN -> "●"
                        SugarliciousColorRole.BRAND_GREEN -> "S"
                        SugarliciousColorRole.PRIMARY -> "AKTIV"
                        SugarliciousColorRole.SECONDARY -> "INFO"
                        else -> "●"
                    }
                Box(
                    modifier =
                        Modifier
                            .background(selectedColor.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                            .border(1.dp, selectedColor, RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = selectedColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ColorEditorDialog(
    role: SugarliciousColorRole?,
    label: String = role?.label ?: "Farbe",
    initialArgb: Int,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    widgetStyle: OneUiWidgetSettingsColors? = null,
    resetArgb: Int? = null,
    onReset: (() -> Int)? = null,
    actions: List<ColorPickerAction> = emptyList(),
) {
    OneUiWidgetColorPicker(label, initialArgb, widgetStyle ?: LocalOneUiWidgetSettingsColors.current, resetArgb, onDismiss, onChange, onReset, actions)
}

@Composable
internal fun SaturationBrightnessPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = "Farbspektrum"
                    stateDescription = "Sättigung ${(saturation * 100).roundToInt()} %, Helligkeit ${(brightness * 100).roundToInt()} %"
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Sättigung erhöhen") {
                                currentOnChange((saturation + .05f).coerceAtMost(1f), brightness)
                                true
                            },
                            CustomAccessibilityAction("Sättigung verringern") {
                                currentOnChange((saturation - .05f).coerceAtLeast(0f), brightness)
                                true
                            },
                            CustomAccessibilityAction("Helligkeit erhöhen") {
                                currentOnChange(saturation, (brightness + .05f).coerceAtMost(1f))
                                true
                            },
                            CustomAccessibilityAction("Helligkeit verringern") {
                                currentOnChange(saturation, (brightness - .05f).coerceAtLeast(0f))
                                true
                            },
                        )
                }.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentOnChange((offset.x / size.width).coerceIn(0f, 1f), (1f - offset.y / size.height).coerceIn(0f, 1f))
                    }
                }.pointerInput(Unit) {
                    fun update(offset: Offset) {
                        currentOnChange(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            (1f - offset.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    detectDragGestures(onDragStart = ::update) { change, _ -> update(change.position) }
                },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val inset = 9.dp.toPx()
        val marker = Offset((saturation * size.width).coerceIn(inset, size.width - inset), ((1f - brightness) * size.height).coerceIn(inset, size.height - inset))
        drawCircle(Color.White, 7.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.65f), 9.dp.toPx(), marker, style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun HuePicker(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    contentDescription = "Farbton"
                    progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f)
                    setProgress { value ->
                        if (value.isFinite()) currentOnChange(value.coerceIn(0f, 360f))
                        value.isFinite()
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnChange((offset.x / size.width).coerceIn(0f, 1f) * 360f) }
                }.pointerInput(Unit) {
                    fun update(offset: Offset) = currentOnChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                    detectDragGestures(onDragStart = ::update) { change, _ -> update(change.position) }
                },
    ) {
        drawRoundRect(
            brush =
                Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                ),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(size.height / 2f),
        )
        val x = (hue / 360f * size.width).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx())
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.55f), 9.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun AlphaPicker(
    color: Color,
    alpha: Float,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    contentDescription = "Deckkraft"
                    progressBarRangeInfo = ProgressBarRangeInfo(alpha, 0f..1f)
                    stateDescription = "Transparenz ${(100 - alpha * 100).roundToInt()} %"
                    setProgress { value ->
                        if (value.isFinite()) currentOnChange(value.coerceIn(0f, 1f))
                        value.isFinite()
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnChange((offset.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(Unit) {
                    fun update(offset: Offset) = currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                    detectDragGestures(onDragStart = ::update) { change, _ -> update(change.position) }
                },
    ) {
        val cell = 7.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(
                    color = if ((row + column) % 2 == 0) Color(0xFFBEBEBE) else Color(0xFF707070),
                    topLeft = Offset(x, y),
                    size =
                        androidx.compose.ui.geometry
                            .Size(cell, cell),
                )
                x += cell
                column++
            }
            y += cell
            row++
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(size.height / 2f),
        )
        val x = (alpha * size.width).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx())
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.55f), 9.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun NotificationGraphSettingsPanel() {
    val model = rememberAppearanceSettingsModel("notification-appearance", AppearanceColorTarget.NOTIFICATION)
    val state by model.uiState.collectAsStateWithLifecycle()
    val snapshot =
        state.snapshot ?: run {
            AppearanceLoadingOrError(state, model::retry)
            return
        }
    AppearanceLoadingOrError(state, model::retry)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var editingRole by AppearanceBinding({ model.uiState.value.editingRole }, model::editColor)
    var selectedMode by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).mode }, model::selectMode)

    fun resolved(role: SugarliciousColorRole) = requireNotNull(model.uiState.value.snapshot).notificationColors.getValue(role)
    var scaleLaneOpacity by model.floatBinding(AppearanceFloat.NOTIFICATION_SCALE)
    var collapsedLayout by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationLayouts.getValue(NotificationGraphProfile.COLLAPSED) }, { model.setLayout(NotificationGraphProfile.COLLAPSED, it) })
    var expandedLayout by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationLayouts.getValue(NotificationGraphProfile.EXPANDED) }, { model.setLayout(NotificationGraphProfile.EXPANDED, it) })
    var collapsedRadius by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.COLLAPSED).cgmRadiusDp }, { value -> model.updateDots(NotificationGraphProfile.COLLAPSED) { it.copy(cgmRadiusDp = value) } })
    var collapsedOutlineEnabled by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.COLLAPSED).historicalOutlineEnabled }, { value -> model.updateDots(NotificationGraphProfile.COLLAPSED) { it.copy(historicalOutlineEnabled = value) } })
    var collapsedOutlineWidth by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.COLLAPSED).historicalOutlineWidthDp }, { value -> model.updateDots(NotificationGraphProfile.COLLAPSED) { it.copy(historicalOutlineWidthDp = value) } })
    var collapsedCurrentOutlineWidth by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.COLLAPSED).currentOutlineWidthDp }, { value -> model.updateDots(NotificationGraphProfile.COLLAPSED) { it.copy(currentOutlineWidthDp = value) } })
    var expandedRadius by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.EXPANDED).cgmRadiusDp }, { value -> model.updateDots(NotificationGraphProfile.EXPANDED) { it.copy(cgmRadiusDp = value) } })
    var expandedOutlineEnabled by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.EXPANDED).historicalOutlineEnabled }, { value -> model.updateDots(NotificationGraphProfile.EXPANDED) { it.copy(historicalOutlineEnabled = value) } })
    var expandedOutlineWidth by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.EXPANDED).historicalOutlineWidthDp }, { value -> model.updateDots(NotificationGraphProfile.EXPANDED) { it.copy(historicalOutlineWidthDp = value) } })
    var expandedCurrentOutlineWidth by AppearanceBinding({ requireNotNull(model.uiState.value.snapshot).notificationDots.getValue(NotificationGraphProfile.EXPANDED).currentOutlineWidthDp }, { value -> model.updateDots(NotificationGraphProfile.EXPANDED) { it.copy(currentOutlineWidthDp = value) } })

    fun saveCollapsed() = model.saveDots(NotificationGraphProfile.COLLAPSED)

    fun saveExpanded() = model.saveDots(NotificationGraphProfile.EXPANDED)
    val roles = notificationAppearanceRoles

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MobileSettingsObjectCard("Benachrichtigung · Darstellung") {
            AppearanceModeSelector(selectedMode) {
                selectedMode = it
            }

            Text(
                "Farben gelten gemeinsam für eingeklappt und ausgeklappt. Punktgeometrie wird getrennt gespeichert.",
                color = OneUiWidgetStyle.secondary,
                style = oneUiMenuTypography.bodyMedium,
            )
        }
        MobileSettingsObjectCard("Graph · Skalenbereich") {
            SugarliciousSettingSlider(
                "Skalenbereich",
                "Nur Graphfläche und Zielbereichslinien; Beschriftungen bleiben deckend",
                scaleLaneOpacity,
                0f..100f,
                "${scaleLaneOpacity.roundToInt()} %",
                { value ->
                    scaleLaneOpacity = value
                    model.saveFloat(AppearanceFloat.NOTIFICATION_SCALE)
                },
                {},
            )
        }
        Text(
            "EINGEKLAPPT",
            color = OneUiWidgetStyle.secondary,
            style = oneUiMenuTypography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp),
        )
        NotificationLayoutControls(
            profileLabel = "Eingeklappt",
            value = collapsedLayout,
            onChange = {
                collapsedLayout = it
            },
        )
        MobileSettingsObjectCard("Eingeklappt · CGM-Punkte") {
            SugarliciousSettingSlider(
                title = "Punktgröße",
                description = "CGM-Dots im eingeklappten Notification-Graph",
                value = collapsedRadius,
                valueRange = 1.5f..6f,
                valueText = "${String.format(locale, "%.1f", collapsedRadius)} dp",
                onValueChange = { collapsedRadius = it },
                onValueChangeFinished = ::saveCollapsed,
            )
            GraphSettingSwitch(
                title = "Historische Kontur",
                description = "Kontur historischer CGM-Dots",
                checked = collapsedOutlineEnabled,
                onCheckedChange = {
                    collapsedOutlineEnabled = it
                    saveCollapsed()
                },
            )
            if (collapsedOutlineEnabled) {
                SugarliciousSettingSlider(
                    title = "Konturdicke",
                    description = "Konturdicke im eingeklappten Notification-Graph",
                    value = collapsedOutlineWidth,
                    valueRange = 0.25f..3f,
                    valueText = "${String.format(locale, "%.2f", collapsedOutlineWidth)} dp",
                    onValueChange = { collapsedOutlineWidth = it },
                    onValueChangeFinished = ::saveCollapsed,
                )
            }
            SugarliciousSettingSlider(
                title = "Aktuelle Konturdicke",
                description = "Aktueller CGM-Dot (immer aktiv)",
                value = collapsedCurrentOutlineWidth,
                valueRange = 0.25f..3f,
                valueText = "${String.format(locale, "%.2f", collapsedCurrentOutlineWidth)} dp",
                onValueChange = { collapsedCurrentOutlineWidth = it },
                onValueChangeFinished = ::saveCollapsed,
            )
        }
        Text(
            "AUSGEKLAPPT",
            color = OneUiWidgetStyle.secondary,
            style = oneUiMenuTypography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        NotificationLayoutControls(
            profileLabel = "Ausgeklappt",
            value = expandedLayout,
            onChange = {
                expandedLayout = it
            },
        )
        MobileSettingsObjectCard("Ausgeklappt · CGM-Punkte") {
            SugarliciousSettingSlider(
                title = "Punktgröße",
                description = "CGM-Dots im ausgeklappten Notification-Graph",
                value = expandedRadius,
                valueRange = 1.5f..6f,
                valueText = "${String.format(locale, "%.1f", expandedRadius)} dp",
                onValueChange = { expandedRadius = it },
                onValueChangeFinished = ::saveExpanded,
            )
            GraphSettingSwitch(
                title = "Historische Kontur",
                description = "Kontur historischer CGM-Dots",
                checked = expandedOutlineEnabled,
                onCheckedChange = {
                    expandedOutlineEnabled = it
                    saveExpanded()
                },
            )
            if (expandedOutlineEnabled) {
                SugarliciousSettingSlider(
                    title = "Konturdicke",
                    description = "Konturdicke im ausgeklappten Notification-Graph",
                    value = expandedOutlineWidth,
                    valueRange = 0.25f..3f,
                    valueText = "${String.format(locale, "%.2f", expandedOutlineWidth)} dp",
                    onValueChange = { expandedOutlineWidth = it },
                    onValueChangeFinished = ::saveExpanded,
                )
            }
            SugarliciousSettingSlider(
                title = "Aktuelle Konturdicke",
                description = "Aktueller CGM-Dot (immer aktiv)",
                value = expandedCurrentOutlineWidth,
                valueRange = 0.25f..3f,
                valueText = "${String.format(locale, "%.2f", expandedCurrentOutlineWidth)} dp",
                onValueChange = { expandedCurrentOutlineWidth = it },
                onValueChangeFinished = ::saveExpanded,
            )
        }
        TextButton(
            onClick = {
                model.copyCollapsedDots()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("VON EINGEKLAPPT ÜBERNEHMEN", color = OneUiWidgetStyle.primary, style = oneUiMenuTypography.bodyMedium, fontWeight = FontWeight.Bold)
        }

        roles.groupBy(::mobileAppearanceObjectTitle).forEach { (title, objectRoles) ->
            MobileSettingsObjectCard("Benachrichtigung · $title") {
                objectRoles.forEach { role -> ColorSettingRow(role = role, argb = resolved(role), onEdit = { editingRole = role }) }
            }
        }
    }

    editingRole?.let { role ->
        ColorEditorDialog(
            role = role,
            label = role.label,
            initialArgb = resolved(role),
            onReset = {
                model.reset(AppearanceReset.COLOR, role = role)
            },
            actions =
                listOf(
                    ColorPickerAction("Farben aus Mobile übernehmen") {
                        model.reset(AppearanceReset.COPY_MOBILE, role = role)
                    },
                    ColorPickerAction("Notification-Graph zurücksetzen") {
                        model.reset(AppearanceReset.NOTIFICATION_GRAPH, role = role)
                    },
                ),
            onDismiss = { editingRole = null },
            onChange = { argb ->
                model.saveColor(role, argb)
            },
        )
    }
}

@Composable
private fun NotificationLayoutControls(
    profileLabel: String,
    value: NotificationLayoutSettings,
    onChange: (NotificationLayoutSettings) -> Unit,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    MobileSettingsObjectCard("$profileLabel · Glukosewert") {
        SugarliciousSettingSlider("CGM-Größe", "Nur der Glukosewert", value.glucoseScalePercent.toFloat(), 70f..200f, "${value.glucoseScalePercent} %", {
            onChange(value.copy(glucoseScalePercent = it.toInt()))
        }, {})
        SugarliciousSettingSlider("CGM X", "Relative horizontale Position", value.glucoseXPercent.toFloat(), -40f..40f, "${value.glucoseXPercent} %", {
            onChange(value.copy(glucoseXPercent = it.toInt()))
        }, {})
        SugarliciousSettingSlider("CGM Y", "Relative vertikale Position", value.glucoseYPercent.toFloat(), -40f..40f, "${value.glucoseYPercent} %", {
            onChange(value.copy(glucoseYPercent = it.toInt()))
        }, {})
    }
    MobileSettingsObjectCard("$profileLabel · Trendpfeil") {
        SugarliciousSettingSlider(
            "Trendpfeilgröße",
            if (value.trendScalePercent == null) "Systemstandard" else "Notification-Override",
            (value.trendScalePercent ?: 100).toFloat(),
            70f..200f,
            value.trendScalePercent?.let { "$it %" } ?: "System",
            { onChange(value.copy(trendScalePercent = it.toInt())) },
            {},
        )
        OneUiResetIcon(onClick = { onChange(value.copy(trendScalePercent = null)) }, description = "Trendpfeilgröße auf Systemstandard zurücksetzen")
        SugarliciousSettingSlider("Trend X", "Relative horizontale Position", value.trendXPercent.toFloat(), -40f..40f, "${value.trendXPercent} %", {
            onChange(value.copy(trendXPercent = it.toInt()))
        }, {})
        SugarliciousSettingSlider("Trend Y", "Relative vertikale Position", value.trendYPercent.toFloat(), -40f..40f, "${value.trendYPercent} %", {
            onChange(value.copy(trendYPercent = it.toInt()))
        }, {})
    }
    MobileSettingsObjectCard("$profileLabel · Delta und Alter") {
        SugarliciousSettingSlider(
            "Delta/Age-Größe",
            "Sekundärzeile",
            value.metaScalePercent.toFloat(),
            70f..160f,
            String.format(
                locale,
                "%d %%",
                value.metaScalePercent,
            ),
            {
                onChange(value.copy(metaScalePercent = it.toInt()))
            },
            {},
        )
    }
}

internal fun toHex(argb: Int): String = ArgbColor.format(argb)

internal fun copyWidgetColorHex(context: android.content.Context, argb: Int) {
    context
        .getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText("HEX", toHex(argb)))
}

@Composable
private fun AppearanceLoadingOrError(state: AppearanceSettingsUiState, retry: () -> Unit) {
    if (state.error == AppearanceSettingsError.STORAGE) {
        TextButton(onClick = retry) { Text("Einstellungen konnten nicht geladen oder gespeichert werden. Erneut versuchen", color = OneUiWidgetStyle.primary) }
    } else if (state.snapshot == null) {
        Text("Einstellungen werden geladen ...", color = OneUiWidgetStyle.secondary)
    }
}
