package app.sugarlicious.mobile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.launch

internal const val DIRECT_TO_WATCH_FACE_INDEX = 1

internal enum class WatchFaceSelectionEvent {
    USER_SELECTION,
    CGM_REFRESH,
    COLLECTOR_REFRESH,
    DATA_LAYER_REFRESH,
}

internal fun reduceWatchFaceSelection(
    currentFaceIndex: Int,
    requestedFaceIndex: Int?,
    event: WatchFaceSelectionEvent,
): Int =
    if (event == WatchFaceSelectionEvent.USER_SELECTION && requestedFaceIndex != null) {
        requestedFaceIndex.coerceIn(sugarliciousWatchFaceCards.indices)
    } else {
        currentFaceIndex.coerceIn(sugarliciousWatchFaceCards.indices)
    }

internal data class SugarliciousWatchFaceCard(
    val name: String,
    val style: String,
    val slots: Int,
    val features: List<String>,
)

internal val sugarliciousWatchFaceCards =
    listOf(
        SugarliciousWatchFaceCard(
            name = "ApeX",
            style = "Analog",
            slots = 8,
            features = listOf("Graph", "AOD"),
        ),
        SugarliciousWatchFaceCard(
            name = "Digital",
            style = "Digital",
            slots = 8,
            features = listOf("Graph", "AOD"),
        ),
        SugarliciousWatchFaceCard(
            name = "Orbit",
            style = "Analog",
            slots = 7,
            features = listOf("Arc / Graph", "Themes", "Anzeiger", "AOD"),
        ),
    )

internal val watchFaceMenuSections = listOf("Sugarlicious Ziffernblätter" to listOf(0, 2))

@Composable
internal fun SugarliciousWatchScreen(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    onSelectedFace: (Int) -> Unit,
    diagnostics: DiagnosticsSnapshot,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val savedFaceIndex = SugarliciousWatchFaceSelectionStore.read(appContext, preferences.watchFaceIndex).let { if (it == DIRECT_TO_WATCH_FACE_INDEX) 0 else it }
    var activeFaceIndex by remember(savedFaceIndex) { mutableIntStateOf(savedFaceIndex) }
    var editingFaceIndex by remember(savedFaceIndex) { mutableIntStateOf(savedFaceIndex) }
    var facePresets by remember { mutableStateOf(WatchFacePresetStore.readAll(appContext)) }
    val nativeSlotResources = androidx.compose.ui.platform.LocalResources.current
    val nativeSlots =
        remember(editingFaceIndex, nativeSlotResources) {
            when (editingFaceIndex) {
                0 -> nativeSlotResources.openRawResource(R.raw.native_apex_slots).use(::readNativeWatchSlots)
                2 -> nativeSlotResources.openRawResource(R.raw.native_orbit_slots).use(::readNativeWatchSlots)
                else -> emptyList()
            }
        }
    var selectedSlotId by remember(editingFaceIndex) { mutableStateOf(nativeSlotPreviewEntries(nativeSlots).firstOrNull(::isNativeSlotEditable)?.id) }
    var slotAssignments by remember(editingFaceIndex) { mutableStateOf(WatchFacePresetStore.readSlots(appContext, editingFaceIndex)) }
    val selectedSlot = nativeSlots.firstOrNull { it.id == selectedSlotId && isNativeSlotEditable(it) }
    val listState = rememberLazyListState()
    val snapConnection = rememberWatchSectionSnap(listState, setOf(0, 2, 4))

    fun selectFace(index: Int) {
        editingFaceIndex = index
        activeFaceIndex = reduceWatchFaceSelection(activeFaceIndex, index, WatchFaceSelectionEvent.USER_SELECTION)
        SugarliciousWatchFaceSelectionStore.write(appContext, index)
        val activated = WatchFacePresetStore.activate(appContext, index)
        facePresets = WatchFacePresetStore.readAll(appContext).toMutableList().also { it[index] = activated }
        onSelectedFace(index)
    }

    LaunchedEffect(appContext) {
        runCatching { requestWatchRuntimeStatus(appContext) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val constrained = maxWidth < 360.dp || maxHeight < 700.dp || LocalDensity.current.fontScale > 1.2f
        val headerMaximum = (maxHeight * .62f).coerceAtLeast(1.dp)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(max = headerMaximum), color = OneUiWidgetStyle.card, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OverviewWatchFaceTile(
                        state,
                        diagnostics,
                        activeFaceIndex,
                        onSelectedFace = ::selectFace,
                        onEdit = { editingFaceIndex = activeFaceIndex },
                        compactLayout = true,
                        highlightedSlot = nativeSlots.firstOrNull { it.id == selectedSlotId && isNativeSlotEditable(it) },
                        showConnection = false,
                        slotAssignments = slotAssignments,
                        editableSlots = nativeSlots,
                        constrainedLayout = constrained,
                    )
                    if (nativeSlots.isNotEmpty()) NativeWatchSlotPanel(editingFaceIndex, nativeSlots, selectedSlotId, slotAssignments, constrained) { selectedSlotId = it }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(snapConnection), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                watchFaceMenuSections.forEach { (sectionTitle, faceIndices) ->
                    item {
                        Text(
                            text = sectionTitle,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = OneUiWidgetStyle.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                        )
                    }
                    items(faceIndices.chunked(2)) { indices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            indices.forEach { index ->
                                val enabled = true
                                WatchFaceTile(
                                    modifier = Modifier.weight(1f),
                                    face = sugarliciousWatchFaceCards[index],
                                    index = index,
                                    state = state,
                                    activeComplicationIds = facePresets.getOrElse(index) { emptyList() },
                                    slotAssignments = if (index == editingFaceIndex) slotAssignments else WatchFacePresetStore.readSlots(appContext, index),
                                    selected = activeFaceIndex == index,
                                    enabled = enabled,
                                    onSelected = { selectFace(index) },
                                )
                            }
                            if (indices.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (editingFaceIndex != DIRECT_TO_WATCH_FACE_INDEX) {
                    item {
                        Text(
                            text = "Complications",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = OneUiWidgetStyle.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                        )
                    }
                }

                if (editingFaceIndex != DIRECT_TO_WATCH_FACE_INDEX) {
                    item {
                        key(editingFaceIndex) {
                            CompositionLocalProvider(LocalSugarliciousTrendArrowMaxSize provides 32.dp) {
                                ComplicationStudio(
                                    state = state,
                                    selectedSlot = selectedSlot,
                                    selectedVariantId = selectedSlot?.let { slotAssignments[it.id] ?: nativeDefaultVariant(it) },
                                    onSlotVariantSelected = { variant ->
                                        selectedSlot?.let { slot ->
                                            val updated = slotAssignments + (slot.id to variant)
                                            WatchFacePresetStore.saveSlots(appContext, editingFaceIndex, updated)
                                            slotAssignments = updated
                                        }
                                    },
                                    showSendButton = false,
                                    onPresetChanged = { updated ->
                                        WatchFacePresetStore.save(appContext, editingFaceIndex, updated)
                                        facePresets =
                                            facePresets.toMutableList().also { presets ->
                                                presets[editingFaceIndex] = updated
                                            }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchFaceTile(
    modifier: Modifier,
    face: SugarliciousWatchFaceCard,
    index: Int,
    state: TherapyDisplayState?,
    activeComplicationIds: List<Int>,
    slotAssignments: Map<Int, Int>,
    selected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val nativeSlots =
        remember(index, resources) {
            when (index) {
                0 -> resources.openRawResource(R.raw.native_apex_slots).use(::readNativeWatchSlots)
                2 -> resources.openRawResource(R.raw.native_orbit_slots).use(::readNativeWatchSlots)
                else -> emptyList()
            }
        }
    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { this.selected = selected }
                .clickable(enabled = enabled) {
                    onSelected()
                    scope.launch {
                        val appContext = context.applicationContext
                        val preset = WatchFacePresetStore.activate(appContext, index)
                        runCatching { syncComplicationPreset(appContext, preset) }
                        val nodes = runCatching { requestWatchFaceApply(appContext, index) }.getOrDefault(0)
                        if (nodes == 0) {
                            Toast.makeText(context, "Watch nicht erreichbar", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
        shape = shape,
        color = OneUiWidgetStyle.card,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                FaceDial(
                    index = index,
                    state = state,
                    activeComplicationIds = activeComplicationIds,
                    modifier = Modifier.fillMaxSize(),
                )
                if (slotAssignments.isNotEmpty()) {
                    NativeWatchComplicationPreview(index, nativeSlots, slotAssignments, Modifier.fillMaxSize())
                    NativePreviewHands(index, Modifier.fillMaxSize())
                }
            }

            Text(
                text = face.name,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                color = if (enabled) OneUiWidgetStyle.primary else OneUiWidgetStyle.secondary,
                style = oneUiMenuTypography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
