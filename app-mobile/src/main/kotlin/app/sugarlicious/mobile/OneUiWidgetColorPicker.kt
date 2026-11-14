package app.sugarlicious.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sugarlicious.model.ArgbColor
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/** Original implementation inspired by One UI; no DeviceVendor/private components. */
@ConsistentCopyVisibility
internal data class WidgetPickerColor private constructor(
    val argb: Int,
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
) {
    val opacity: Float get() = AndroidColor.alpha(argb) / 255f

    fun spectrum(h: Float = hue, s: Float = saturation, v: Float = brightness): WidgetPickerColor {
        val hsv = floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        return WidgetPickerColor(AndroidColor.HSVToColor(AndroidColor.alpha(argb), hsv), hsv[0], hsv[1], hsv[2])
    }

    fun opacity(value: Float): WidgetPickerColor = copy(argb = (argb and 0x00FFFFFF) or ((value.coerceIn(0f, 1f) * 255).roundToInt() shl 24))

    fun swatch(rgb: Int): WidgetPickerColor = fromArgb((rgb and 0x00FFFFFF) or (argb and 0xFF000000.toInt()))

    companion object {
        fun fromArgb(argb: Int): WidgetPickerColor {
            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(argb, it) }
            return WidgetPickerColor(argb, hsv[0], hsv[1], hsv[2])
        }
    }
}

internal fun widgetPickerSwatches(): List<Int> =
    (0 until 42).map { index ->
        val row = index / 7
        val column = index % 7
        val saturation =
            if (column == 0) {
                0f
            } else if (row < 3) {
                .3f + row * .35f
            } else {
                1f
            }
        val brightness =
            if (column == 0) {
                1f - row * .2f
            } else if (row < 3) {
                1f
            } else {
                1f - (row - 2) * .2f
            }
        AndroidColor.HSVToColor(floatArrayOf((column - 1).coerceAtLeast(0) * 60f, saturation, brightness))
    }

internal data class ColorPickerAction(
    val label: String,
    val run: () -> Int,
)

@Composable
internal fun OneUiWidgetColorPicker(
    label: String,
    initialArgb: Int,
    colors: OneUiWidgetSettingsColors,
    resetArgb: Int?,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    onReset: (() -> Int)? = null,
    actions: List<ColorPickerAction> = emptyList(),
) {
    val context = LocalContext.current
    val hostOwner = appearanceViewModelStoreOwner(LocalView.current, context)
    val model = rememberAppearanceEditorModel("appearance-color:$label", AppearanceColorEditorViewModel::class.java) { extras -> extras.withCreationCallback<AppearanceColorEditorViewModel.Factory> { it.create(initialArgb) } }
    val state by model.uiState.collectAsStateWithLifecycle()
    val openingArgb = state.openingArgb
    val selected = state.selected
    val hex = state.hex
    val spectrum = state.spectrum
    LaunchedEffect(model) { model.open(initialArgb) }
    LaunchedEffect(initialArgb) { model.syncExternal(initialArgb) }
    DisposableEffect(model) {
        onDispose {
            if ((hostOwner as? androidx.activity.ComponentActivity)?.isChangingConfigurations != true) model.close()
        }
    }

    fun update(value: WidgetPickerColor) {
        model.update(value)
        onChange(value.argb)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.card)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, color = colors.primary, fontFamily = app.sugarlicious.mobile.ui.theme.SystemFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PickerPreview("Aktuell", openingArgb, colors, Modifier.weight(1f))
                        PickerPreview("Neu", selected.argb, colors, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.secondary.copy(alpha = .12f))) {
                        listOf("Farbfelder", "Spektrum").forEachIndexed { index, title ->
                            val active = spectrum == (index == 1)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (active) colors.accent.copy(alpha = .2f) else Color.Transparent)
                                    .selectable(active, role = Role.Tab) { model.setSpectrum(index == 1) },
                                contentAlignment = Alignment.Center,
                            ) { Text(title, color = if (active) colors.accent else colors.secondary, fontSize = 14.sp) }
                        }
                    }
                    if (spectrum) {
                        SaturationBrightnessPicker(selected.hue, selected.saturation, selected.brightness) { s, v -> update(selected.spectrum(s = s, v = v)) }
                        HuePicker(selected.hue) { update(selected.spectrum(h = it)) }
                    } else {
                        PickerSwatches(selected.argb, colors) { update(selected.swatch(it)) }
                    }
                    Text("Transparenz ${(100 - selected.opacity * 100).roundToInt()} %", color = colors.secondary, fontSize = 14.sp)
                    AlphaPicker(Color(selected.argb).copy(alpha = 1f), selected.opacity) { update(selected.opacity(it)) }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = {
                            model.enterHex(it)
                            model.uiState.value.parsedHex
                                ?.let(onChange)
                        },
                        label = { Text("HEX · AARRGGBB") },
                        singleLine = true,
                        isError = ArgbColor.parse(hex) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { copyWidgetColorHex(context, selected.argb) }, modifier = Modifier.fillMaxWidth()) {
                        Text("HEX kopieren", color = colors.accent)
                    }
                    actions.forEach { action ->
                        OneUiResetIcon(onClick = {
                            model.update(WidgetPickerColor.fromArgb(action.run()))
                        }, description = action.label)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OneUiResetIcon(onClick = {
                        if (onReset != null) {
                            model.update(WidgetPickerColor.fromArgb(onReset()))
                        } else {
                            resetArgb?.let { update(WidgetPickerColor.fromArgb(it)) }
                        }
                    }, enabled = resetArgb != null || onReset != null, modifier = Modifier.weight(1f))
                    Box(Modifier.height(20.dp).width(1.dp).background(colors.secondary.copy(alpha = .3f)))
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Fertig", color = colors.accent, fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun PickerPreview(label: String, argb: Int, colors: OneUiWidgetSettingsColors, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = colors.secondary, style = oneUiMenuTypography.bodyMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            // Checkerboard makes alpha visible even for black/white selections.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val cell = 7.dp.toPx()
                for (y in 0..(size.height / cell).toInt()) {
                    for (x in 0..(size.width / cell).toInt()) {
                        if ((x + y) % 2 == 0) {
                            drawRect(
                                Color.LightGray,
                                androidx.compose.ui.geometry
                                    .Offset(x * cell, y * cell),
                                androidx.compose.ui.geometry
                                    .Size(cell, cell),
                            )
                        }
                    }
                }
                drawRect(Color(argb))
            }
        }
    }
}

@Composable
private fun PickerSwatches(selected: Int, colors: OneUiWidgetSettingsColors, onSelect: (Int) -> Unit) {
    val swatches = remember { widgetPickerSwatches() }
    Column {
        swatches.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { argb ->
                    val active = (selected and 0x00FFFFFF) == (argb and 0x00FFFFFF)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .semantics { contentDescription = "Farbe ${ArgbColor.format(argb)}" }
                            .selectable(active, role = Role.RadioButton) { onSelect(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .border(if (active) 2.dp else 1.dp, if (active) colors.primary else colors.secondary.copy(alpha = .35f), CircleShape)
                                .padding(3.dp)
                                .background(Color(argb), CircleShape),
                        )
                    }
                }
            }
        }
    }
}
