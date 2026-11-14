package app.sugarlicious.mobile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.withSave
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.ComplicationPresentationFormatter
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.SugarliciousComplicationIds
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.protocol.WearProtocol
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.launch
import java.util.Locale

internal data class ComplicationCatalogEntry(
    val name: String,
    val category: ComplicationCategory,
    val variants: List<ComplicationVariant>,
) {
    val id: Int get() = variants.first().id
    val types: String get() = variants.joinToString(" · ") { it.label }

    fun selectedVariant(selectedIds: List<Int>): ComplicationVariant = variants.firstOrNull { it.id in selectedIds } ?: variants.first()
}

internal data class ComplicationVariant(
    val id: Int,
    val type: ComplicationVariantType,
) {
    val label: String get() =
        when (id) {
            SugarliciousComplicationIds.GRAPH -> "ApeX"
            SugarliciousComplicationIds.GRAPH_ORBIT -> "Orbit"
            else -> type.shortLabel
        }
}

internal enum class ComplicationVariantType(
    val shortLabel: String,
    val displayLabel: String,
) {
    SHORT_TEXT("Kompakt", "Kompakt"),
    LONG_TEXT("Details", "Ausführlich"),
    RANGED_VALUE("Fortschritt", "Fortschritt"),
    MONOCHROMATIC_IMAGE("Symbol", "Symbol"),
    GOAL_PROGRESS("Ziel", "Ziel"),
    WEIGHTED_ELEMENTS("Anteile", "Anteile"),
    SMALL_IMAGE("Verlauf", "Verlauf"),
    PHOTO_IMAGE("Groß", "Großer Verlauf"),
}

private fun variant(
    id: Int,
    type: ComplicationVariantType,
) = ComplicationVariant(id, type)

private fun entry(
    name: String,
    category: ComplicationCategory,
    vararg variants: ComplicationVariant,
) = ComplicationCatalogEntry(name, category, variants.toList())

internal enum class ComplicationCategory(
    val label: String,
    val range: String,
) {
    GLUCOSE("Glukose", "01–12"),
    THERAPY("Therapie", "13–19"),
    GENERAL("Allgemein", "20"),
}

internal val SugarliciousComplicationCatalog =
    listOf(
        entry(
            "Glukose",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_LONG, ComplicationVariantType.LONG_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "Glukose + Trend",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_TREND, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_LONG, ComplicationVariantType.LONG_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "Glukose + Delta",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA_LONG, ComplicationVariantType.LONG_TEXT),
        ),
        entry(
            "Glukose + Trend + Zeit",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_AGE, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_AGE_LONG, ComplicationVariantType.LONG_TEXT),
        ),
        entry(
            "Glukose + Trend + Delta",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_DELTA, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "Glukose + Trend + Delta + Zeit",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE_LONG, ComplicationVariantType.LONG_TEXT),
        ),
        entry(
            "CGM Graph",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GRAPH, ComplicationVariantType.SMALL_IMAGE),
            variant(SugarliciousComplicationIds.GRAPH_ORBIT, ComplicationVariantType.SMALL_IMAGE),
            variant(SugarliciousComplicationIds.GRAPH_LARGE, ComplicationVariantType.PHOTO_IMAGE),
        ),
        entry(
            "Trend",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.TREND_ONLY, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "Delta",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.DELTA_ONLY, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "Zeit seit letztem Wert",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.GLUCOSE_AGE, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "Zeit + Delta",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.TIME_DELTA, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "Sensoralter",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.SENSOR_AGE, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.SENSOR_AGE_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "Basal",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.BASAL, ComplicationVariantType.SHORT_TEXT),
        ),
        entry(
            "IOB",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.IOB, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.IOB_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "COB",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.COB, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.COB_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "IOB + COB",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.IOB_COB, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.IOB_COB_LONG, ComplicationVariantType.LONG_TEXT),
        ),
        entry(
            "Basal + IOB + COB",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.IOB_COB_BASAL, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.IOB_COB_BASAL_LONG, ComplicationVariantType.LONG_TEXT),
        ),
        entry(
            "Loop Status",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.LOOP, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.LOOP_ICON, ComplicationVariantType.MONOCHROMATIC_IMAGE),
        ),
        entry(
            "Pumpe / Reservoir",
            ComplicationCategory.THERAPY,
            variant(SugarliciousComplicationIds.RESERVOIR, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.RESERVOIR_RANGED, ComplicationVariantType.RANGED_VALUE),
        ),
        entry(
            "TIR",
            ComplicationCategory.GLUCOSE,
            variant(SugarliciousComplicationIds.TIR, ComplicationVariantType.SHORT_TEXT),
            variant(SugarliciousComplicationIds.TIR_RANGED, ComplicationVariantType.RANGED_VALUE),
            variant(SugarliciousComplicationIds.TIR_GOAL, ComplicationVariantType.GOAL_PROGRESS),
            variant(SugarliciousComplicationIds.TIR_WEIGHTED, ComplicationVariantType.WEIGHTED_ELEMENTS),
        ),
        entry(
            "Datum",
            ComplicationCategory.GENERAL,
            variant(SugarliciousComplicationIds.DATE, ComplicationVariantType.SHORT_TEXT),
        ),
    )

private fun catalogNumber(entry: ComplicationCatalogEntry): Int = SugarliciousComplicationCatalog.indexOf(entry).coerceAtLeast(0) + 1

internal fun complicationDisplayName(name: String): String = name.replace(Regex("\\s+\\+\\s+"), " · ")

internal val SugarliciousComplicationVariantIds: Set<Int> =
    SugarliciousComplicationCatalog.flatMap { it.variants }.map { it.id }.toSet()

private fun catalogEntryForVariant(id: Int): ComplicationCatalogEntry? = SugarliciousComplicationCatalog.firstOrNull { entry -> entry.variants.any { it.id == id } }

internal fun complicationCatalogColumns(availableWidthDp: Float): Int = ((availableWidthDp + 7f) / 155f).toInt().coerceIn(1, 3)

@Composable
private fun SugarliciousSettingSliderControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
) {
    MobileSettingsSeekBar(label, value, valueRange, onValueChange, {})
}

@Composable
internal fun ComplicationStudio(
    state: TherapyDisplayState?,
    onPresetChanged: (List<Int>) -> Unit = {},
    selectedSlot: NativeWatchSlot? = null,
    selectedVariantId: Int? = null,
    onSlotVariantSelected: ((Int) -> Unit)? = null,
    showSendButton: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(selectedSlot?.id, selectedVariantId) { mutableStateOf(if (selectedSlot != null) listOfNotNull(selectedVariantId) else loadComplicationPreset(context)) }
    val graphHours = remember { loadComplicationGraphHours(context) }
    var variantDialogEntry by remember { mutableStateOf<ComplicationCatalogEntry?>(null) }

    fun applySelection(updated: List<Int>) {
        if (updated == selected) return
        selected = updated
        if (selectedSlot != null) {
            updated.lastOrNull()?.let { onSlotVariantSelected?.invoke(it) }
            return
        }
        onPresetChanged(updated)
        scope.launch {
            runCatching { syncComplicationPreset(context, updated, graphHours) }
                .onFailure { Toast.makeText(context, "Lokal gespeichert · Watch nicht erreichbar", Toast.LENGTH_SHORT).show() }
        }
    }

    val previewState = state ?: previewTherapyState(System.currentTimeMillis())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showSendButton) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            syncComplicationPreset(context, selected, graphHours)
                        }.onSuccess {
                            Toast.makeText(context, "Zur Watch-Übertragung gespeichert", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Watch nicht erreichbar", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).width(190.dp).height(40.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = OneUiWidgetStyle.action,
                        contentColor = OneUiWidgetStyle.primary,
                    ),
            ) {
                Text("An Watch senden", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = complicationCatalogColumns(maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val eligibleIds = selectedSlot?.let { eligibleNativeVariants(it).map { variant -> variant.id }.toSet() }
                val catalog =
                    SugarliciousComplicationCatalog.mapNotNull { entry ->
                        val variants = entry.variants.filter { eligibleIds == null || it.id in eligibleIds }
                        if (variants.isEmpty()) null else entry.copy(variants = variants)
                    }
                catalog.chunked(columns).forEach { entries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        entries.forEach { entry ->
                            val selectedVariant = entry.selectedVariant(selected)
                            ComplicationCatalogTile(
                                modifier = Modifier.weight(1f),
                                entry = entry,
                                variant = selectedVariant,
                                state = previewState,
                                graphHours = graphHours,
                                selected = entry.variants.any { it.id in selected },
                                onToggle = {
                                    val updated = if (selectedSlot != null) listOf(selectedVariant.id) else togglePresetEntry(context, selected, selectedVariant.id)
                                    if (selectedSlot == null && updated == selected && selectedVariant.id !in selected && selected.size >= 4) {
                                        Toast.makeText(context, "Das Watch-Preset hat maximal 4 Plätze.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        applySelection(updated)
                                    }
                                },
                                onLongPress = {
                                    variantDialogEntry = entry
                                },
                            )
                        }
                        repeat(columns - entries.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    variantDialogEntry?.let { entry ->
        val currentVariant = entry.selectedVariant(selected)
        ComplicationVariantDialog(
            entry = entry,
            currentVariant = currentVariant,
            state = previewState,
            graphHours = graphHours,
            onDismiss = { variantDialogEntry = null },
            onSave = { variant, appearance ->
                val updated =
                    if (selectedSlot != null) {
                        listOf(variant.id)
                    } else if (variant.id == currentVariant.id) {
                        selected
                    } else {
                        selectPresetVariant(context, selected, entry, variant.id)
                    }
                if (selectedSlot == null && variant.id != currentVariant.id && updated == selected && entry.variants.none { it.id in selected } && selected.size >= 4) {
                    Toast.makeText(context, "Das Watch-Preset hat maximal 4 Plätze.", Toast.LENGTH_SHORT).show()
                } else {
                    val catalogId = SugarliciousComplicationIds.baseId(variant.id)
                    if (appearance != ComplicationAppearanceSettingsStore.load(context, catalogId)) {
                        ComplicationAppearanceSettingsStore.save(context, catalogId, appearance)
                    }
                    applySelection(updated)
                    variantDialogEntry = null
                }
            },
        )
    }
}

@Composable
private fun ComplicationCatalogTile(
    modifier: Modifier,
    entry: ComplicationCatalogEntry,
    variant: ComplicationVariant,
    state: TherapyDisplayState?,
    graphHours: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            modifier
                .semantics { this.selected = selected }
                .background(OneUiWidgetStyle.card, shape)
                .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
                .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                complicationDisplayName(entry.name),
                modifier = Modifier.fillMaxWidth(),
                color = OneUiWidgetStyle.primary,
                style = oneUiMenuTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
                minLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height((72f * LocalDensity.current.fontScale).dp), contentAlignment = Alignment.Center) {
                CompactComplicationPreview(entry, variant, state, graphHours)
            }
            Spacer(Modifier.height(5.dp))
            Text(variant.label, color = OneUiWidgetStyle.secondary, style = oneUiMenuTypography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ComplicationAppearanceControls(
    entry: ComplicationCatalogEntry,
    appearance: ComplicationAppearanceSettings,
    onChange: (ComplicationAppearanceSettings) -> Unit,
) {
    val hasTrend = entry.name.contains("Trend", ignoreCase = true)

    fun update(value: ComplicationAppearanceSettings) = onChange(value)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Diese Einstellungen ändern nur die Vorschauen im Watch-Menü. Die Darstellung auf der Uhr bestimmt das Ziffernblatt.", color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
        if (hasTrend) {
            MobileSettingsObjectCard("Trendpfeil") {
                Text(
                    "TRENDPFEIL · ${appearance.trendScalePercent?.let { "$it %" } ?: "Systemstandard"}",
                    color = OneUiWidgetStyle.secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                SugarliciousSettingSliderControl(
                    label = "Trendpfeil-Größe",
                    value = (appearance.trendScalePercent ?: GlucoseTrendSizing.DEFAULT_SCALE_PERCENT).toFloat(),
                    onValueChange = {
                        val size = it.toInt().coerceIn(70, 200)
                        update(
                            appearance.copy(
                                trendScalePercent = size,
                                trendStyleOverride = appearance.trendStyleOverride.copy(sizePercent = size),
                            ),
                        )
                    },
                    valueRange = 70f..200f,
                )
                Text("POSITION X · ${appearance.trendOffsetXPercent}", color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
                SugarliciousSettingSliderControl(
                    label = "Trendpfeil Position X",
                    value = appearance.trendOffsetXPercent.toFloat(),
                    onValueChange = { update(appearance.copy(trendOffsetXPercent = it.toInt())) },
                    valueRange =
                        -50f..50f,
                )
                Text("POSITION Y · ${appearance.trendOffsetYPercent}", color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
                SugarliciousSettingSliderControl(
                    label = "Trendpfeil Position Y",
                    value = appearance.trendOffsetYPercent.toFloat(),
                    onValueChange = { update(appearance.copy(trendOffsetYPercent = it.toInt())) },
                    valueRange =
                        -50f..50f,
                )
                val resolvedTrend = appearance.trendStyleOverride.resolve(MobileTrendArrowAppearance.style)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KONTUR", color = OneUiWidgetStyle.secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WidgetSettingsToggle(
                        checked = resolvedTrend.outlineEnabled,
                        onCheckedChange = {
                            update(
                                appearance.copy(trendStyleOverride = appearance.trendStyleOverride.copy(outlineEnabled = it)),
                            )
                        },
                    )
                }
                if (resolvedTrend.outlineEnabled) {
                    Text(
                        "KONTURDICKE · ${String.format(Locale.GERMANY, "%.2f", resolvedTrend.outlineThicknessDp)} dp",
                        color = OneUiWidgetStyle.secondary,
                        fontSize = 13.sp,
                    )
                    SugarliciousSettingSliderControl(
                        label = "Trendpfeil-Konturdicke",
                        value = resolvedTrend.outlineThicknessDp,
                        onValueChange = {
                            update(appearance.copy(trendStyleOverride = appearance.trendStyleOverride.copy(outlineThicknessDp = it)))
                        },
                        valueRange =
                            0.25f..4f,
                    )
                }
                Text("DECKKRAFT · ${(resolvedTrend.alpha * 100).toInt()} %", color = OneUiWidgetStyle.secondary, fontSize = 13.sp)
                SugarliciousSettingSliderControl(label = "Trendpfeil-Deckkraft", value = resolvedTrend.alpha * 100f, onValueChange = {
                    update(
                        appearance.copy(
                            trendStyleOverride =
                                appearance.trendStyleOverride.copy(
                                    alpha =
                                        it / 100f,
                                ),
                        ),
                    )
                }, valueRange = 0f..100f)
                OneUiResetIcon(
                    onClick = { update(ComplicationAppearanceSettings()) },
                    modifier = Modifier.align(Alignment.End),
                    description = "Complication-Darstellung auf Systemstandard zurücksetzen",
                )
            }
        } else {
            Text(
                "Textgröße und Position dieser Vorlage werden vom jeweiligen Watchface-Slot bestimmt.",
                color = OneUiWidgetStyle.secondary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun CompactComplicationPreview(
    entry: ComplicationCatalogEntry,
    variant: ComplicationVariant,
    state: TherapyDisplayState?,
    graphHours: Int,
    appearance: ComplicationAppearanceSettings? = null,
) {
    if (SugarliciousComplicationIds.baseId(variant.id) == SugarliciousComplicationIds.GRAPH) {
        MiniGlucosePreview(
            samples = state?.glucoseHistory.orEmpty(),
            current = state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, it.source, it.sensorId, it.sessionId, it.sequenceNumber, it.receivedAtEpochMs, it.quality) },
            windowMinutes = if (variant.type == ComplicationVariantType.PHOTO_IMAGE) 1440 else 90,
            semicircle = variant.id == SugarliciousComplicationIds.GRAPH_ORBIT,
            allowExampleData = state == null,
        )
        return
    }

    val preview = previewFor(variant.id, state)
    val resolvedAppearance = appearance ?: ComplicationAppearanceSettingsStore.load(LocalContext.current, SugarliciousComplicationIds.baseId(variant.id))
    val previewTrendStyle = resolvedAppearance.trendStyleOverride.resolve(MobileTrendArrowAppearance.style)

    if (variant.type == ComplicationVariantType.SHORT_TEXT) {
        ApexCatalogTemplate(preview, ranged = false, previewTrendStyle, resolvedAppearance, iconOnly = SugarliciousComplicationIds.baseId(variant.id) == SugarliciousComplicationIds.TREND_ONLY, iconRes = catalogPreviewIcon(variant.id))
        return
    }

    when (variant.type) {
        ComplicationVariantType.RANGED_VALUE,
        ComplicationVariantType.GOAL_PROGRESS,
        -> {
            ApexCatalogTemplate(preview, ranged = true, previewTrendStyle, resolvedAppearance, iconRes = catalogPreviewIcon(variant.id))
            return
        }

        ComplicationVariantType.MONOCHROMATIC_IMAGE -> {
            Box(Modifier.size(64.dp).background(Color(0xFF262626), CircleShape), contentAlignment = Alignment.Center) {
                SugarliciousIcon(overviewLoopTileState(state).iconRes, entry.name, Modifier.size(32.dp), tint = Color.White, colored = false)
            }
            return
        }

        ComplicationVariantType.WEIGHTED_ELEMENTS -> {
            Row(Modifier.background(Color(0xFF262626), RoundedCornerShape(18.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(Color.White.copy(alpha = .35f), Color.White, Color.White.copy(alpha = .65f))
                    .forEach { color ->
                        Box(Modifier.size(width = 12.dp, height = 28.dp).background(color, RoundedCornerShape(5.dp)))
                    }
            }
            return
        }

        else -> Unit
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(Color(0xFF262626), RoundedCornerShape(18.dp))
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (preview.trend != null && SugarliciousComplicationIds.baseId(variant.id) == SugarliciousComplicationIds.TREND_ONLY) {
            SugarliciousTrendIndicator(
                preview.trend,
                modifier =
                    Modifier.offset(
                        x = (appearance?.trendOffsetXPercent ?: 0).dp / 5,
                        y = (appearance?.trendOffsetYPercent ?: 0).dp / 5,
                    ),
                arrowSize = (25f * GlucoseTrendSizing.scaleFactor(appearance?.trendScalePercent ?: 100)).dp,
                color = Color.White,
                style = previewTrendStyle,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    preview.primary,
                    color = if (preview.secondary.isNotBlank()) Color.LightGray else Color.White,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (variant.type == ComplicationVariantType.LONG_TEXT) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                preview.trend?.let {
                    SugarliciousTrendIndicator(
                        it,
                        modifier =
                            Modifier.offset(
                                x = (appearance?.trendOffsetXPercent ?: 0).dp / 5,
                                y = (appearance?.trendOffsetYPercent ?: 0).dp / 5,
                            ),
                        arrowSize =
                            GlucoseTrendSizing
                                .arrowHeightForGlucoseHeight(
                                    14f,
                                    GlucoseTrendSizing.scaleFactor(appearance?.trendScalePercent ?: 100),
                                ).dp,
                        color = Color.White,
                        style = previewTrendStyle,
                    )
                }
            }
        }
        if (preview.secondary.isNotBlank()) {
            Text(
                preview.secondary,
                modifier = Modifier.offset(y = (-2).dp),
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApexCatalogTemplate(
    preview: PhonePreview,
    ranged: Boolean,
    trendStyle: app.sugarlicious.model.TrendArrowStyle,
    appearance: ComplicationAppearanceSettings?,
    iconOnly: Boolean = false,
    iconRes: Int? = null,
) {
    if (!ranged) {
        ShortTextCatalogTemplate(preview, trendStyle, appearance, iconOnly, iconRes)
        return
    }
    val systemZoom = rangedCatalogSystemZoom(LocalDensity.current.fontScale)
    Box(
        Modifier.sizeIn(maxWidth = (72f * systemZoom).dp, maxHeight = (72f * systemZoom).dp).aspectRatio(1f).background(Color(0xFF262626), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (ranged) {
            Canvas(Modifier.fillMaxSize().padding((8f * systemZoom).dp)) {
                // The template shows the open track, not an invented live progress value.
                val ring = circularComplicationPreviewBounds(size.width, size.height)
                drawArc(
                    Color.LightGray,
                    130f,
                    280f,
                    false,
                    topLeft = ring.topLeft,
                    size = ring.size,
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke((5f * systemZoom).dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = (9f * systemZoom).dp, vertical = ((if (ranged) 15f else 10f) * systemZoom).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!iconOnly) {
                if (!ranged && iconRes != null) {
                    SugarliciousIcon(iconRes, null, Modifier.size((12f * systemZoom).dp), tint = Color.LightGray, colored = false)
                }
                val primarySize = if (ranged) 17.sp else 14.sp
                Text(preview.primary, color = if (preview.secondary.isNotBlank()) Color.LightGray else Color.White, fontSize = primarySize, lineHeight = primarySize, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preview.secondary.isNotBlank()) {
                    val secondarySize = if (ranged) 11.sp else 16.sp
                    Text(preview.secondary, color = Color.White, fontSize = secondarySize, lineHeight = secondarySize, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            preview.trend?.takeUnless { ranged }?.let {
                SugarliciousTrendIndicator(
                    it,
                    modifier = Modifier.offset(x = ((appearance?.trendOffsetXPercent ?: 0) * systemZoom).dp / 5, y = ((appearance?.trendOffsetYPercent ?: 0) * systemZoom).dp / 5),
                    arrowSize =
                        (
                            (
                                if (iconOnly) {
                                    28f
                                } else {
                                    GlucoseTrendSizing.arrowHeightForGlucoseHeight(14f, 1f)
                                }
                            ) * GlucoseTrendSizing.scaleFactor(appearance?.trendScalePercent ?: 100) * systemZoom
                        ).dp,
                    color = Color.White,
                    style = trendStyle,
                )
            }
        }
        if (ranged && preview.trend != null) {
            SugarliciousTrendIndicator(
                preview.trend,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (3f * systemZoom).dp).offset(x = ((appearance?.trendOffsetXPercent ?: 0) * systemZoom).dp / 5, y = ((appearance?.trendOffsetYPercent ?: 0) * systemZoom).dp / 5),
                arrowSize = (GlucoseTrendSizing.arrowHeightForGlucoseHeight(17f, 1f) * GlucoseTrendSizing.scaleFactor(appearance?.trendScalePercent ?: 100) * systemZoom).dp,
                color = Color.White,
                style = trendStyle,
            )
        }
        if (ranged && preview.trend == null && iconRes != null) {
            SugarliciousIcon(iconRes, null, Modifier.align(Alignment.BottomCenter).padding(bottom = (4f * systemZoom).dp).size((13f * systemZoom).dp), tint = Color.LightGray, colored = false)
        }
    }
}

internal data class CatalogPreviewSizing(
    val primary: Float,
    val secondary: Float,
    val icon: Float,
)

internal fun rangedCatalogSystemZoom(fontScale: Float): Float = fontScale.coerceAtLeast(.1f)

internal fun catalogPreviewSizing(diameter: Float, ranged: Boolean, twoValues: Boolean): CatalogPreviewSizing =
    if (ranged) CatalogPreviewSizing(17f, 11f, 13f) else CatalogPreviewSizing(diameter * if (twoValues) 13f / 72f else 18f / 72f, diameter * 18f / 72f, diameter * 16f / 72f)

@Composable
private fun ShortTextCatalogTemplate(
    preview: PhonePreview,
    trendStyle: app.sugarlicious.model.TrendArrowStyle,
    appearance: ComplicationAppearanceSettings?,
    iconOnly: Boolean,
    iconRes: Int?,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.sizeIn(maxWidth = (72f * density.fontScale).dp, maxHeight = (72f * density.fontScale).dp).aspectRatio(1f).background(Color(0xFF262626), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = maxWidth.value
        val sizing = catalogPreviewSizing(diameter, ranged = false, twoValues = preview.secondary.isNotBlank())
        val contentWidth = maxWidth * .79f
        val showIcon = iconRes != null && !iconOnly
        val showTrend = preview.trend != null
        val allowTwoLines = preview.secondary.isBlank() && !showIcon && !showTrend

        @Composable
        fun FittedText(value: String, initialSize: Float, color: Color, maxLines: Int = 1) {
            val widthPx = with(density) { contentWidth.roundToPx() }
            val baseStyle =
                androidx.compose.material3.LocalTextStyle.current
                    .copy(fontWeight = FontWeight.Bold, platformStyle = PlatformTextStyle(includeFontPadding = false))
            var fontSize = initialSize
            while (fontSize > 10f) {
                val measured = measurer.measure(value, style = baseStyle.copy(fontSize = fontSize.sp, lineHeight = fontSize.sp), maxLines = maxLines, constraints = Constraints(maxWidth = widthPx))
                if (!measured.hasVisualOverflow) break
                fontSize -= .5f
            }
            Text(value, modifier = Modifier.width(contentWidth), color = color, fontSize = fontSize.sp, lineHeight = fontSize.sp, style = baseStyle, maxLines = maxLines, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((diameter * .025f).dp, Alignment.CenterVertically)) {
            if (!iconOnly) {
                if (showIcon) SugarliciousIcon(requireNotNull(iconRes), null, Modifier.size(sizing.icon.dp), tint = Color.LightGray, colored = false)
                FittedText(preview.primary, sizing.primary / density.fontScale, if (preview.secondary.isBlank()) Color.White else Color.LightGray, if (allowTwoLines) 2 else 1)
                if (preview.secondary.isNotBlank()) FittedText(preview.secondary, sizing.secondary / density.fontScale, Color.White)
            }
            preview.trend?.let { trend ->
                val baseHeight = if (iconOnly) diameter * .56f else sizing.icon
                val aspect =
                    app.sugarlicious.model.TrendVisuals
                        .spec(trend)
                        ?.aspectRatio ?: 1f
                val requestedHeight = baseHeight * GlucoseTrendSizing.scaleFactor(appearance?.trendScalePercent ?: 100)
                val fittedHeight = minOf(requestedHeight, diameter * .94f / aspect, if (iconOnly) diameter * .65f else diameter * .27f)
                SugarliciousTrendIndicator(trend, modifier = Modifier.offset(x = (appearance?.trendOffsetXPercent ?: 0).dp / 5, y = (appearance?.trendOffsetYPercent ?: 0).dp / 5), arrowSize = fittedHeight.dp, color = Color.White, style = trendStyle)
            }
        }
    }
}

internal fun circularComplicationPreviewBounds(width: Float, height: Float): Rect {
    val diameter = minOf(width, height)
    val left = (width - diameter) / 2f
    val top = (height - diameter) / 2f
    return Rect(left, top, left + diameter, top + diameter)
}

internal fun catalogPreviewIcon(id: Int): Int? =
    when (SugarliciousComplicationIds.baseId(id)) {
        SugarliciousComplicationIds.IOB -> app.sugarlicious.uishared.R.drawable.ic_iob
        SugarliciousComplicationIds.COB -> app.sugarlicious.uishared.R.drawable.ic_cob
        SugarliciousComplicationIds.BASAL -> app.sugarlicious.uishared.R.drawable.ic_basal
        SugarliciousComplicationIds.TIR -> app.sugarlicious.uishared.R.drawable.ic_tir
        SugarliciousComplicationIds.RESERVOIR -> app.sugarlicious.uishared.R.drawable.ic_reservoir
        else -> null
    }

@Composable
private fun ComplicationVariantDialog(
    entry: ComplicationCatalogEntry,
    currentVariant: ComplicationVariant,
    state: TherapyDisplayState?,
    graphHours: Int,
    onDismiss: () -> Unit,
    onSave: (ComplicationVariant, ComplicationAppearanceSettings) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(entry.id, currentVariant.id) {
        mutableStateOf(ComplicationEditorDraft(currentVariant.id, ComplicationAppearanceSettingsStore.load(context, SugarliciousComplicationIds.baseId(currentVariant.id))))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(complicationDisplayName(entry.name), color = OneUiWidgetStyle.primary, style = oneUiMenuTypography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    "Aktiv: ${currentVariant.type.displayLabel}",
                    color = OneUiWidgetStyle.secondary,
                    fontSize = 14.sp,
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.variants.forEach { variant ->
                    val marked = draft.variantId == variant.id
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = OneUiWidgetStyle.action,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { draft = draft.copy(variantId = variant.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(width = 78.dp, height = 78.dp), contentAlignment = Alignment.Center) {
                                    CompactComplicationPreview(entry, variant, state, graphHours, draft.appearance)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    variant.type.displayLabel,
                                    modifier = Modifier.weight(1f),
                                    color = OneUiWidgetStyle.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                RadioButton(selected = marked, onClick = { draft = draft.copy(variantId = variant.id) })
                            }
                            if (marked) {
                                Text(complicationVariantInfo(variant.type), color = OneUiWidgetStyle.secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                                ComplicationAppearanceControls(entry, draft.appearance) { draft = draft.copy(appearance = it) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (draft.hasChanges) {
                WidgetSettingsActionPill(
                    onCancel = onDismiss,
                    onSave = { onSave(entry.variants.first { it.id == draft.variantId }, draft.appearance) },
                )
            }
        },
        containerColor = OneUiWidgetStyle.card,
    )
}

private fun complicationVariantInfo(type: ComplicationVariantType): String =
    when (type) {
        ComplicationVariantType.SHORT_TEXT -> "Kompakte Anzeige mit Text, Titel und gegebenenfalls einem Symbol."
        ComplicationVariantType.LONG_TEXT -> "Ausführliche Textanzeige für größere Complication-Flächen."
        ComplicationVariantType.RANGED_VALUE -> "Wert mit einer Progressbar und dem zugehörigen Symbol."
        ComplicationVariantType.GOAL_PROGRESS -> "Fortschritt zum eingestellten Ziel."
        ComplicationVariantType.WEIGHTED_ELEMENTS -> "Anteile der einzelnen Glukosebereiche."
        ComplicationVariantType.MONOCHROMATIC_IMAGE -> "Anzeige als einfarbiges Symbol."
        ComplicationVariantType.SMALL_IMAGE -> "Kompakter Glukoseverlauf für den Watchface-Slot."
        ComplicationVariantType.PHOTO_IMAGE -> "Großer Glukoseverlauf für eine Bild-Complication."
    }

@Composable
private fun MiniGlucosePreview(
    samples: List<GlucoseSample>,
    current: GlucoseSample?,
    windowMinutes: Int,
    semicircle: Boolean,
    allowExampleData: Boolean,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE) }
    val now = System.currentTimeMillis()
    val windowMs = windowMinutes * 60_000L
    val timeWindow = GraphTimeWindow.live(now, windowMs)
    val cutoff = timeWindow.startEpochMs
    val demoHours = maxOf(1, (windowMinutes + 59) / 60)
    val merged =
        app.sugarlicious.model.CanonicalCgmHistory
            .merge(
                samples + listOfNotNull(current),
                now,
                windowMs = windowMs,
                futureToleranceMs = 0,
                maxPoints = 9_000,
            ).ifEmpty { if (allowExampleData) demoHistory(now, demoHours).filter { it.measuredAtEpochMs >= cutoff } else emptyList() }
    val thresholds = CgmThresholdPreferences.read(preferences)
    val dotRadiusDp = 1.6f
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF262626), RoundedCornerShape(16.dp)),
    ) {
        if (windowMinutes == 90) {
            drawIntoCanvas { native ->
                val canvas = native.nativeCanvas
                canvas.withSave {
                    if (semicircle) {
                        canvas.clipPath(
                            android.graphics.Path().apply {
                                addOval(android.graphics.RectF(0f, 0f, size.width, size.height * 2), android.graphics.Path.Direction.CW)
                            },
                        )
                    }
                    app.sugarlicious.uishared.WatchfaceCgmGraphRenderer
                        .render(canvas, size.width.toInt(), size.height.toInt(), merged, timeWindow, thresholds, semicircle = semicircle)
                }
            }
            return@Canvas
        }
        val left = 3.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val top = 3.dp.toPx()
        val bottom = size.height - 3.dp.toPx()

        fun x(timestamp: Long) = timeWindow.plotX(timestamp, left, right - left)

        fun y(value: Double) = bottom - (GlucoseGraphScale.ratio(value) * (bottom - top)).toFloat()
        val low = thresholds.lowMgDl
        val high = thresholds.highMgDl
        drawLine(
            Color.White.copy(alpha = .3f),
            Offset(left, y(high)),
            Offset(right, y(high)),
            0.7.dp.toPx(),
        )
        drawLine(Color.White.copy(alpha = .3f), Offset(left, y(low)), Offset(right, y(low)), 0.7.dp.toPx())
        merged.forEachIndexed { index, sample ->
            val radius = dotRadiusDp.dp.toPx() * if (index == merged.lastIndex) 1.25f else 1f
            val center = Offset(x(sample.measuredAtEpochMs), y(sample.valueMgDl))
            val currentPoint = index == merged.lastIndex
            if (currentPoint) {
                val outlineWidthDp = 0.8f
                drawCircle(Color.Black, radius + outlineWidthDp.dp.toPx(), center)
            }
            drawCircle(Color.White, radius, center)
        }
    }
}

internal data class PhonePreview(
    val primary: String,
    val secondary: String,
    val color: Color = SugarliciousColors.TextPrimary,
    val trend: Trend? = null,
)

internal fun previewFor(
    id: Int,
    state: TherapyDisplayState?,
): PhonePreview {
    val now = System.currentTimeMillis()
    val effectiveState = state ?: previewTherapyState(now)
    val baseId = SugarliciousComplicationIds.baseId(id)
    val presentation = ComplicationPresentationFormatter.format(baseId, effectiveState, now)
    val g = effectiveState.glucose
    val glucoseColor =
        when {
            g == null -> SugarliciousColors.TextPrimary
            g.valueMgDl < 80.0 -> SugarliciousColors.GlucoseLow
            g.valueMgDl > 160.0 -> SugarliciousColors.GlucoseHigh
            else -> SugarliciousColors.GlucoseInRange
        }
    return PhonePreview(
        primary = presentation.title ?: presentation.text,
        secondary = if (presentation.title != null) presentation.text else "",
        color =
            if (baseId in
                setOf(
                    SugarliciousComplicationIds.GLUCOSE,
                    SugarliciousComplicationIds.GLUCOSE_TREND,
                    SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA,
                    SugarliciousComplicationIds.GLUCOSE_TREND_DELTA,
                    SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE,
                    SugarliciousComplicationIds.GLUCOSE_TREND_AGE,
                )
            ) {
                glucoseColor
            } else {
                SugarliciousColors.TextPrimary
            },
        trend = presentation.trend,
    )
}

internal fun previewTherapyState(now: Long): TherapyDisplayState =
    TherapyDisplayState(
        receivedAtEpochMs = now,
        sourceVersion = "AndroidAPS",
        glucose =
            app.sugarlicious.model.GlucoseState(
                valueMgDl = 123.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FORTY_FIVE_UP,
                measuredAtEpochMs = now - 2 * 60_000L,
                deltaMgDl = 5.0,
            ),
        glucoseHistory = demoHistory(now, 24),
        insulin = app.sugarlicious.model.InsulinState(totalIob = 1.2, bolusIob = 0.8, basalIob = 0.4),
        carbs = app.sugarlicious.model.CarbState(cobGrams = 15.0),
        basal = app.sugarlicious.model.BasalState(currentUnitsPerHour = 0.70),
        loop = app.sugarlicious.model.LoopState(status = "enacted", lastRunAtEpochMs = now - 2 * 60_000L),
        pump = app.sugarlicious.model.PumpState(status = "OK", reservoirUnits = 120.0, batteryPercent = 80),
    )

internal fun complicationPreviewLabel(
    id: Int,
    state: TherapyDisplayState?,
): String {
    val entry = catalogEntryForVariant(id)
    val preview = previewFor(id, state)
    return "${entry?.name ?: "Comp"} ${preview.primary}".take(20)
}

private fun demoHistory(
    now: Long,
    hours: Int,
): List<GlucoseSample> {
    val count = hours * 12
    return (0..count).map { index ->
        val minutesAgo = (count - index) * 5L
        val phase = index % 24
        val value =
            when {
                phase < 8 -> 105.0 + phase * 4.0
                phase < 16 -> 137.0 - (phase - 8) * 3.0
                else -> 113.0 + (phase - 16) * 2.0
            }
        GlucoseSample(value, now - minutesAgo * 60_000L)
    }
}

private const val PRESET_PREFS = "complication_setup"
private const val PRESET_KEY = "selected_ids"
private const val COMPLICATION_GRAPH_HOURS_KEY = "graph_hours"

internal fun loadComplicationPreset(context: Context): List<Int> =
    context
        .getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getString(PRESET_KEY, null)
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { id -> id in SugarliciousComplicationVariantIds }
        ?.distinct()
        .orEmpty()

internal fun loadComplicationGraphHours(context: Context): Int =
    context
        .getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getInt(COMPLICATION_GRAPH_HOURS_KEY, 3)
        .takeIf { it in listOf(1, 2, 3, 6, 12, 24) } ?: 3

private fun togglePresetEntry(
    context: Context,
    current: List<Int>,
    entryId: Int,
): List<Int> {
    val updated =
        when {
            entryId in current -> current.filterNot { it == entryId }
            current.size >= 4 -> current
            else -> current + entryId
        }

    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE).edit {
        putString(PRESET_KEY, updated.joinToString(","))
    }

    return updated
}

private fun selectPresetVariant(
    context: Context,
    current: List<Int>,
    entry: ComplicationCatalogEntry,
    variantId: Int,
): List<Int> {
    if (entry.variants.none { it.id == variantId }) return current

    val familyIds = entry.variants.map { it.id }.toSet()
    val existingIndex = current.indexOfFirst { it in familyIds }
    val updated =
        when {
            existingIndex >= 0 -> current.toMutableList().also { it[existingIndex] = variantId }
            current.size < 4 -> current + variantId
            else -> current
        }.distinct()

    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE).edit {
        putString(PRESET_KEY, updated.joinToString(","))
    }
    return updated
}

internal suspend fun syncComplicationPreset(
    context: Context,
    ids: List<Int>,
    graphHours: Int = loadComplicationGraphHours(context),
) {
    val request =
        PutDataMapRequest
            .create(WearProtocol.COMPLICATION_PRESET_PATH)
            .apply {
                dataMap.putIntegerArrayList("ids", ArrayList(ids))
                dataMap.putInt("graphHours", graphHours.takeIf { it in OVERVIEW_GRAPH_HOUR_OPTIONS } ?: 3)
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest()
            .setUrgent()

    context.putLoggedWearData(request, "Complication-Auswahl", mapOf("complications" to ids.size, "keys" to 3))
}
