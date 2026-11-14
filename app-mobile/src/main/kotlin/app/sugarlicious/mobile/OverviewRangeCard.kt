package app.sugarlicious.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private data class RangeArchiveWindow(
    val samples: List<GlucoseSample>,
)

/** One shared period and transition drives both TIR columns and all their bars. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OverviewRangeCard(state: TherapyDisplayState?, now: Long, showTir: Boolean, showTitr: Boolean, choosingPeriod: Boolean, onChoosingPeriod: (Boolean) -> Unit, onPillBounds: (Rect) -> Unit) {
    val context = LocalContext.current
    var days by remember(context) { mutableIntStateOf(OverviewRangePeriodStore.read(context)) }
    val store = remember(context) { TherapyStateStore(context.applicationContext) }
    val revision by store.archiveRevision.collectAsState(initial = 0L)
    val zone = ZoneId.systemDefault()
    val start =
        Instant
            .ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .minusDays(89)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    val archive by produceState<RangeArchiveWindow?>(null, store, revision, start) {
        value = withContext(Dispatchers.IO) { RangeArchiveWindow(store.rangeHistory(start, now)) }
    }
    // Publish the label and statistics together after that period's archive loads.
    // Never briefly relabel a one-day calculation as ninety-day data.
    val result by produceState(days to dailyGlucoseRanges(null, now), state, archive, now / 60_000L, zone, days) {
        val window = archive ?: return@produceState
        value = days to withContext(Dispatchers.Default) { dailyGlucoseRanges(state, now, zone, days, window.samples) }
    }
    val availability by produceState<List<Int>?>(null, state, archive, now / 60_000L, zone) {
        val window = archive ?: return@produceState
        value = withContext(Dispatchers.Default) { availableRangePeriods(state, window.samples, now, zone) }
    }
    val available = availability.orEmpty()
    LaunchedEffect(availability) {
        if (availability == null) return@LaunchedEffect
        val fallback = available.lastOrNull() ?: 1
        if (days !in available && days != fallback) days = OverviewRangePeriodStore.select(context, fallback)
    }
    val displayedDays = result.first
    val ranges = result.second
    val transition = updateTransition(ranges, label = "measured-range-period")
    Column(Modifier.fillMaxWidth().background(SugarliciousColors.Surface, RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.Top) {
            Text(
                if (displayedDays == 1) "Heute" else "$displayedDays Tage",
                color = SugarliciousColors.TextSecondary,
                style = oneUiMenuTypography.titleLarge,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClickLabel = "Zeitraum wechseln",
                            onLongClickLabel = "Zeitraum ausw�hlen",
                            onLongClick = { onChoosingPeriod(true) },
                            onClick = { days = OverviewRangePeriodStore.advance(context, days, available) },
                        ).padding(vertical = 2.dp),
            )
            AnimatedVisibility(
                visible = choosingPeriod,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                enter = expandHorizontally(animationSpec = spring(dampingRatio = .78f, stiffness = Spring.StiffnessLow), expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(animationSpec = spring(dampingRatio = .86f, stiffness = Spring.StiffnessLow), shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(
                    Modifier
                        .onGloballyPositioned { onPillBounds(it.boundsInWindow()) }
                        .clip(RoundedCornerShape(50))
                        .background(SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.OVERVIEW_TIR_PILL))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverviewRangePeriodStore.periods.forEach { period ->
                        Text(
                            if (period == 1) "Heute" else "$period T",
                            color = (if (period == days) SugarliciousColors.BrandGreen else SugarliciousColors.TextPrimary).let { if (period in available) it else it.copy(alpha = .35f) },
                            style = oneUiMenuTypography.bodyLarge,
                            fontWeight = if (period == days) FontWeight.Bold else FontWeight.Normal,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = period in available) {
                                        days = OverviewRangePeriodStore.select(context, period)
                                        onChoosingPeriod(false)
                                    }.padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            @Composable fun RangeColumn(tight: Boolean, title: String, bounds: String, modifier: Modifier) {
                val stats = if (tight) ranges.titr else ranges.tir
                val animated =
                    listOf<(MeasuredRangeStats) -> Int?>({ it.above }, { it.inRange }, { it.below }).mapIndexed { index, selector ->
                        transition
                            .animateFloat(
                                transitionSpec = { spring(dampingRatio = .78f, stiffness = 180f) },
                                label = "${if (tight) "titr" else "tir"}-$index",
                            ) { target -> (selector(if (tight) target.titr else target.tir) ?: 0).toFloat() }
                            .value
                    }
                Column(modifier) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(title, color = SugarliciousColors.TextPrimary, style = oneUiMenuTypography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(bounds, color = SugarliciousColors.TextSecondary, style = oneUiMenuTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp))
                    }
                    Text(stats.inRange?.let { "$it%" } ?: "—", color = SugarliciousColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    TirProgressColumn(stats, Modifier.fillMaxWidth(), animated)
                }
            }
            if (showTir && showTitr && maxWidth < (320f * LocalDensity.current.fontScale).dp) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    RangeColumn(true, "Time in Tight Range", "70–140 mg/dL", Modifier.fillMaxWidth())
                    RangeColumn(false, "Time in Range", "70–180 mg/dL", Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (showTitr) RangeColumn(true, "Time in Tight Range", "70–140 mg/dL", Modifier.weight(1f))
                    if (showTir) RangeColumn(false, "Time in Range", "70–180 mg/dL", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TirProgressColumn(
    stats: MeasuredRangeStats,
    modifier: Modifier = Modifier,
    animated: List<Float>,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.above,
            animatedPercent = animated[0],
            rangeLabel = "�ber Ziel",
            accent = SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.PROGRESS_ABOVE),
        )

        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.inRange,
            animatedPercent = animated[1],
            rangeLabel = "Im Ziel",
            accent = SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.PROGRESS_IN_RANGE),
        )

        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.below,
            animatedPercent = animated[2],
            rangeLabel = "Unter Ziel",
            accent = SugarliciousColors.color(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.PROGRESS_BELOW),
        )
    }
}

@Composable
private fun TirProgress(
    modifier: Modifier,
    percent: Int?,
    animatedPercent: Float,
    rangeLabel: String,
    accent: Color,
) {
    val safePercent = (percent ?: 0).coerceIn(0, 100)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = oneUiMenuTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    val labelWidth =
        with(density) {
            measurer
                .measure("100%", labelStyle, softWrap = false)
                .size.width
                .toDp()
        } + 4.dp
    val track = SugarliciousColors.SurfaceRaised
    Row(
        modifier =
            modifier.heightIn(min = 24.dp).semantics(mergeDescendants = true) {
                contentDescription = rangeLabel
                stateDescription = percent?.let { "$safePercent%" } ?: "Keine Daten"
                if (percent != null) progressBarRangeInfo = ProgressBarRangeInfo(safePercent.toFloat(), 0f..100f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Health-style data bars need their own full-height pill geometry: the native
        // horizontal widget keeps a thin drawable even inside a taller view.
        Canvas(Modifier.weight(1f).height(14.dp)) {
            val radius =
                androidx.compose.ui.geometry
                    .CornerRadius(size.height / 2f)
            val remainingStart = if (animatedPercent <= 0f) 0f else (size.width * animatedPercent.coerceIn(0f, 100f) / 100f + size.height * .25f).coerceAtMost(size.width)
            if (remainingStart < size.width) {
                drawRoundRect(
                    color = track,
                    topLeft =
                        androidx.compose.ui.geometry
                            .Offset(remainingStart, 0f),
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width - remainingStart, size.height),
                    cornerRadius = radius,
                )
            }
            if (animatedPercent > 0f) {
                drawRoundRect(
                    color = accent,
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width * animatedPercent.coerceIn(0f, 100f) / 100f, size.height),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = percent?.let { "$safePercent%" } ?: "—",
            modifier = Modifier.width(labelWidth).clearAndSetSemantics { },
            color = SugarliciousColors.TextPrimary,
            style = labelStyle,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}
