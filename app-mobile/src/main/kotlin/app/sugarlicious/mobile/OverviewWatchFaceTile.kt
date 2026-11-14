package app.sugarlicious.mobile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

internal val sugarliciousWatchFaceNames = sugarliciousWatchFaceCards.map { it.name }

private const val carouselPages = 400

internal object OverviewWatchHeroLayout {
    const val FRAME_HEIGHT_DP = 224
    const val GAP_DP = 3
    const val MODEL_HEIGHT_DP = 24
    const val PILL_ICON_SIZE_DP = 14
    const val PILL_VERTICAL_PADDING_DP = 6
}

private val carouselHeight = OverviewWatchHeroLayout.FRAME_HEIGHT_DP.dp
private val carouselFaceSize = 135.dp
private val carouselPageSpacing = 8.dp
private val carouselFaceVerticalOffset = (-7).dp

private object GalaxyWatchUltraFrameLoader {
    private val mutex = Mutex()

    @Volatile
    private var cached: androidx.compose.ui.graphics.ImageBitmap? = null

    fun cachedOrNull(): androidx.compose.ui.graphics.ImageBitmap? = cached

    suspend fun load(context: Context): androidx.compose.ui.graphics.ImageBitmap? {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                val svg =
                    context.resources
                        .openRawResource(R.raw.galaxy_watch_ultra_mockup_exact)
                        .bufferedReader()
                        .use { reader -> reader.readText() }
                val marker = "data:image/png;base64,"
                val start = svg.indexOf(marker)
                if (start < 0) return@withContext null
                val dataStart = start + marker.length
                val dataEnd = svg.indexOf('"', dataStart)
                if (dataEnd <= dataStart) return@withContext null
                val bytes = Base64.decode(svg.substring(dataStart, dataEnd), Base64.DEFAULT)
                if (
                    bytes.size < 8 ||
                    bytes[0] != 0x89.toByte() ||
                    bytes[1] != 0x50.toByte() ||
                    bytes[2] != 0x4e.toByte() ||
                    bytes[3] != 0x47.toByte()
                ) {
                    return@withContext null
                }
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                BitmapFactory
                    .decodeByteArray(bytes, 0, bytes.size, options)
                    ?.also { bitmap -> bitmap.prepareToDraw() }
                    ?.asImageBitmap()
            }.also { decoded -> cached = decoded }
        }
    }
}

internal fun carouselTargetPage(
    currentPage: Int,
    dragDistance: Float,
    pageCount: Int = carouselPages,
): Int {
    if (abs(dragDistance) < 24f) return currentPage.coerceIn(0, pageCount - 1)
    val direction = if (dragDistance < 0f) 1 else -1
    return (currentPage + direction).coerceIn(0, pageCount - 1)
}

internal fun carouselPageVisibility(distanceFromCenter: Float): Float = if (distanceFromCenter <= 0.50f) 1f else 0f

internal data class WatchPreviewHandAngles(
    val hour: Float,
    val minute: Float,
    val second: Float,
)

internal fun watchPreviewHandAngles(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): WatchPreviewHandAngles {
    val calendar =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = epochMs
        }
    val seconds = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1_000f
    val minutes = calendar.get(Calendar.MINUTE) + seconds / 60f
    val hours = calendar.get(Calendar.HOUR) + minutes / 60f
    return WatchPreviewHandAngles(
        hour = hours * 30f,
        minute = minutes * 6f,
        second = seconds * 6f,
    )
}

@Composable
internal fun OverviewWatchFaceTile(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    selectedFaceIndex: Int,
    onSelectedFace: (Int) -> Unit,
    onEdit: () -> Unit,
    interactive: Boolean = true,
    compactLayout: Boolean = false,
    highlightedSlot: NativeWatchSlot? = null,
    slotAssignments: Map<Int, Int> = emptyMap(),
    editableSlots: List<NativeWatchSlot> = emptyList(),
    constrainedLayout: Boolean = false,
    showConnection: Boolean = true,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var runtime by remember { mutableStateOf(WatchRuntimeStatusStore.read(appContext)) }

    DisposableEffect(appContext) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                runtime = WatchRuntimeStatusStore.read(appContext)
            }
        WatchRuntimeStatusStore.registerListener(appContext, listener)
        onDispose { WatchRuntimeStatusStore.unregisterListener(appContext, listener) }
    }

    LaunchedEffect(appContext) {
        runCatching { requestWatchRuntimeStatus(appContext) }
    }

    // SugarWear has its own installer; reserved slot 1 is not a Sugarlicious carousel choice.
    val directToWatchRelevant = false
    val savedFaceIndex = SugarliciousWatchFaceSelectionStore.read(appContext, selectedFaceIndex)
    val selectableSavedFaceIndex =
        SugarliciousWatchFaceSelectionStore.resolveSelectableFallback(
            savedFaceIndex = savedFaceIndex,
            legacyFallback = selectedFaceIndex,
            directToWatchRelevant = directToWatchRelevant,
        )
    // Runtime status may refresh complications and connectivity, but only local user input owns
    // carousel selection. This prevents G7/CGM Data-Layer traffic from jumping the visible face.
    val selected = selectableSavedFaceIndex.coerceIn(0, sugarliciousWatchFaceNames.lastIndex)
    val activeComplicationIds =
        runtime.activeComplicationIds
            .takeIf { runtime.activeSugarliciousFaceIndex == selected }
            .orEmpty()
            .ifEmpty {
                WatchFacePresetStore.read(appContext, selected).ifEmpty {
                    loadComplicationPreset(appContext)
                }
            }
    val faceSize =
        if (constrainedLayout) {
            101.dp
        } else if (compactLayout) {
            135.dp
        } else {
            carouselFaceSize
        }
    val frameHeight =
        if (constrainedLayout) {
            150.dp
        } else if (compactLayout) {
            200.dp
        } else {
            carouselHeight
        }
    val midpoint = carouselPages / 2
    val aligned = midpoint - midpoint % sugarliciousWatchFaceNames.size
    val pager =
        rememberPagerState(
            initialPage = aligned + selected,
            pageCount = { carouselPages },
        )
    val carouselScope = rememberCoroutineScope()

    LaunchedEffect(selected) {
        val currentIndex = pager.settledPage % sugarliciousWatchFaceNames.size
        if (currentIndex != selected) pager.scrollToPage(aligned + selected)
    }

    LaunchedEffect(pager.settledPage, directToWatchRelevant) {
        val index = pager.settledPage % sugarliciousWatchFaceNames.size
        if (index != selected) {
            if (SugarliciousWatchFaceSelectionStore.isSelectable(index, directToWatchRelevant)) {
                SugarliciousWatchFaceSelectionStore.write(appContext, index)
                onSelectedFace(index)
            } else {
                pager.scrollToPage(aligned + selected)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OverviewWatchHeroLayout.GAP_DP.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(frameHeight).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val oneStepSwipe =
                Modifier.pointerInput(pager.settledPage, directToWatchRelevant) {
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onHorizontalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        },
                        onDragEnd = {
                            val target = carouselTargetPage(pager.settledPage, dragDistance)
                            val targetIndex = target % sugarliciousWatchFaceNames.size
                            if (
                                target != pager.settledPage &&
                                SugarliciousWatchFaceSelectionStore.isSelectable(targetIndex, directToWatchRelevant)
                            ) {
                                carouselScope.launch { pager.animateScrollToPage(target) }
                            }
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                }
            val centeredPadding = ((maxWidth - faceSize) / 2).coerceAtLeast(0.dp)

            GalaxyWatchUltraFrame()

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = centeredPadding),
                pageSpacing = carouselPageSpacing,
                pageSize = PageSize.Fixed(faceSize),
                userScrollEnabled = false,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val index = page % sugarliciousWatchFaceNames.size
                Box(
                    modifier =
                        Modifier
                            .offset(y = carouselFaceVerticalOffset)
                            .graphicsLayer {
                                val rawDistance = abs((pager.currentPage - page) + pager.currentPageOffsetFraction)
                                val distance = rawDistance.coerceIn(0f, 1f)
                                val scale = lerp(1f, 0.73f, distance)
                                scaleX = scale
                                scaleY = scale
                                alpha = carouselPageVisibility(rawDistance)
                            }.clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    val pageComplications =
                        if (index == selected) {
                            activeComplicationIds
                        } else {
                            WatchFacePresetStore.read(appContext, index)
                        }
                    FaceDial(
                        index = index,
                        state = state,
                        activeComplicationIds = pageComplications,
                        modifier = Modifier.size(faceSize),
                    )
                    if (index == selected && slotAssignments.isNotEmpty()) {
                        NativeWatchComplicationPreview(index, editableSlots, slotAssignments, Modifier.size(faceSize))
                        NativePreviewHands(index, Modifier.size(faceSize))
                    }
                    if (index == selected && highlightedSlot != null) {
                        NativeWatchSlotHighlight(highlightedSlot, Modifier.size(faceSize))
                    }
                }
            }

            if (interactive) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .then(oneStepSwipe)
                            .clickable(onClick = onEdit),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().offset(y = (-18).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OverviewWatchHeroLayout.GAP_DP.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = OverviewWatchHeroLayout.MODEL_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Galaxy Watch Ultra",
                    color = OneUiWidgetStyle.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (showConnection) WatchConnectionPill(diagnostics)
        }
    }
}

@Composable
private fun GalaxyWatchUltraFrame() {
    val context = LocalContext.current.applicationContext
    val frame by produceState(
        initialValue = GalaxyWatchUltraFrameLoader.cachedOrNull(),
        key1 = context,
    ) {
        value = GalaxyWatchUltraFrameLoader.load(context)
    }
    frame?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Galaxy Watch Ultra",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun FaceDial(
    index: Int,
    state: TherapyDisplayState?,
    modifier: Modifier = Modifier,
    activeComplicationIds: List<Int> = emptyList(),
) {
    val faceModifier = modifier.clip(CircleShape)
    when (index) {
        0 -> SugarliciousAnalogFacePreview(state = state, modifier = faceModifier)
        // Slot 1 stays hidden until its Digital preview/editor is exposed.
        DIRECT_TO_WATCH_FACE_INDEX -> SugarliciousAnalogFacePreview(state = state, modifier = faceModifier)
        2 -> OrbitFacePreview(modifier = faceModifier)
        else ->
            SugarliciousFacePreview(
                index = index,
                state = state,
                complicationIds = activeComplicationIds,
                modifier = faceModifier,
            )
    }
}

@Composable
internal fun WatchConnectionPill(diagnostics: DiagnosticsSnapshot, compact: Boolean = false) {
    val syncStatus = diagnostics.syncStatus
    val connected = isWatchConnected(diagnostics.reachableWatches)
    val pending = !connected && syncStatus == "pending"
    val error = syncStatus !in listOf(null, "ok", "pending")
    val statusColor =
        when {
            connected -> SugarliciousColors.Primary
            pending -> SugarliciousColors.Yellow
            error -> SugarliciousColors.Red
            else -> SugarliciousColors.Red
        }
    val statusText =
        when {
            connected -> "Verbunden"
            pending -> "Verbindung wird geprüft"
            else -> "Nicht verbunden"
        }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = statusColor.copy(alpha = 0.14f),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.72f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else OverviewWatchHeroLayout.PILL_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SugarliciousIcon(
                drawableRes = R.drawable.ic_watch_status,
                contentDescription = null,
                modifier = Modifier.size(OverviewWatchHeroLayout.PILL_ICON_SIZE_DP.dp).offset(y = 1.dp).graphicsLayer { alpha = 1f },
                tint = statusColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
