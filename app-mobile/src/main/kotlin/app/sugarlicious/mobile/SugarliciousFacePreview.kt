package app.sugarlicious.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sugarlicious.model.ComplicationPresentationFormatter
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.SugarliciousComplicationIds
import app.sugarlicious.model.TherapyDisplayState
import kotlin.math.cos
import kotlin.math.sin

/** Product-fixed preview hands at 10:07:32. */
internal val fixedWatchPreviewHandAngles =
    WatchPreviewHandAngles(
        hour = 303.5f,
        minute = 42f,
        second = 192f,
    )

private const val PROFILE_PREVIEW_ID = -1
private const val PHONE_BATTERY_PREVIEW_ID = -2

/** Mirrors the DefaultProviderPolicy order in each packaged Sugarlicious WFF. */
internal fun defaultSugarliciousPreviewIds(faceIndex: Int): List<Int> =
    when (faceIndex.coerceIn(sugarliciousWatchFaceCards.indices)) {
        0 ->
            listOf(
                SugarliciousComplicationIds.GLUCOSE_TREND_RANGED,
                SugarliciousComplicationIds.GLUCOSE_AGE,
                PROFILE_PREVIEW_ID,
                SugarliciousComplicationIds.IOB,
                SugarliciousComplicationIds.COB,
                SugarliciousComplicationIds.BASAL,
                SugarliciousComplicationIds.LOOP,
                SugarliciousComplicationIds.GRAPH,
            )
        1, 2 ->
            listOf(
                SugarliciousComplicationIds.GLUCOSE_TREND_RANGED,
                SugarliciousComplicationIds.IOB,
                SugarliciousComplicationIds.COB,
                SugarliciousComplicationIds.GRAPH,
            )
        3 ->
            listOf(
                SugarliciousComplicationIds.GLUCOSE_TREND_RANGED,
                SugarliciousComplicationIds.GRAPH,
                SugarliciousComplicationIds.IOB,
                SugarliciousComplicationIds.COB,
            )
        4 ->
            listOf(
                SugarliciousComplicationIds.GLUCOSE_TREND_DELTA,
                SugarliciousComplicationIds.GLUCOSE_AGE,
                SugarliciousComplicationIds.GRAPH,
                SugarliciousComplicationIds.IOB,
                SugarliciousComplicationIds.COB,
                SugarliciousComplicationIds.BASAL,
                SugarliciousComplicationIds.LOOP,
                PHONE_BATTERY_PREVIEW_ID,
            )
        else -> emptyList()
    }

private fun orderedSugarliciousPreviewIds(
    faceIndex: Int,
    complicationIds: List<Int>,
): List<Int> {
    val defaults = defaultSugarliciousPreviewIds(faceIndex)
    if (complicationIds.isEmpty()) return defaults

    val selected = complicationIds.toSet()
    val regularDefaults = defaults.filter { it >= 0 }
    val includeBuiltInSlots = selected.containsAll(regularDefaults)
    val orderedDefaults = defaults.filter { id -> id in selected || (id < 0 && includeBuiltInSlots) }
    return (orderedDefaults + complicationIds.filterNot { it in orderedDefaults }).distinct()
}

@Composable
internal fun SugarliciousFacePreview(
    index: Int,
    state: TherapyDisplayState?,
    complicationIds: List<Int>,
    modifier: Modifier = Modifier,
) {
    val faceIndex = index.coerceIn(sugarliciousWatchFaceCards.indices)
    BoxWithConstraints(
        modifier = modifier.clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            when (faceIndex) {
                0 -> {
                    drawCircle(Color(0xFF050B10), r)
                    repeat(12) { tick ->
                        val a = Math.toRadians(tick * 30.0 - 90.0)
                        val inner = r - 13.dp.toPx()
                        val outer = r - 4.dp.toPx()
                        drawLine(
                            color = if (tick % 3 == 0) Color(0xFF19D7E8) else Color(0xFF68757C),
                            start = Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner),
                            end = Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer),
                            strokeWidth = if (tick % 3 == 0) 2.2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    // Mirrors the activeDetails card geometry from sugarlicious-analog/watchface.xml.
                    drawPreviewCard(115f / 450f, 42f / 450f, 220f / 450f, 86f / 450f)
                    drawPreviewCard(39f / 450f, 163f / 450f, 105f / 450f, 70f / 450f)
                    drawPreviewCard(306f / 450f, 163f / 450f, 105f / 450f, 70f / 450f)
                    drawPreviewCard(48f / 450f, 281f / 450f, 113f / 450f, 66f / 450f)
                    drawPreviewCard(289f / 450f, 281f / 450f, 113f / 450f, 66f / 450f)
                    drawPreviewCard(169f / 450f, 365f / 450f, 112f / 450f, 50f / 450f)
                }
                1 -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF303030), r - 6.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawArc(
                        color = Color(0xFF19D7E8),
                        startAngle = 220f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(Color(0xFF343434), r * 0.453f, style = Stroke(7.dp.toPx()))
                    drawArc(
                        Color(0xFF54DF30),
                        130f,
                        205f,
                        false,
                        Offset(center.x - r * 0.453f, center.y - r * 0.453f),
                        Size(r * 0.906f, r * 0.906f),
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                2 -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF222222), r - 6.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawArc(
                        Color(0xFFFF8B60),
                        120f,
                        120f,
                        false,
                        Offset(10.dp.toPx(), 10.dp.toPx()),
                        Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        Color(0xFF19D7E8),
                        300f,
                        120f,
                        false,
                        Offset(16.dp.toPx(), 16.dp.toPx()),
                        Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx()),
                        style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                3 -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF19D7E8), r - 5.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    drawRoundRect(
                        Color(0xFF151515),
                        topLeft = Offset(size.width * (47f / 450f), size.height * (53f / 450f)),
                        size = Size(size.width * (356f / 450f), size.height * (106f / 450f)),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(size.minDimension * (28f / 450f)),
                    )
                }
                else -> {
                    drawCircle(Color(0xFF050B10), r)
                    drawRoundRect(
                        Color(0xFF091117),
                        topLeft = Offset(size.width * (62f / 450f), size.height * (108f / 450f)),
                        size = Size(size.width * (326f / 450f), size.height * (116f / 450f)),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(size.minDimension * (30f / 450f)),
                    )
                    drawRoundRect(
                        Color(0xFF091117),
                        topLeft = Offset(size.width * (45f / 450f), size.height * (238f / 450f)),
                        size = Size(size.width * (360f / 450f), size.height * (91f / 450f)),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(size.minDimension * (22f / 450f)),
                    )
                }
            }
        }

        if (faceIndex == 4) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = maxHeight * (32f / 450f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "10:30",
                    color = Color.White,
                    fontSize = 25.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "SUGARLICIOUS",
                    color = Color(0xFF19D7E8),
                    fontSize = 5.5.sp,
                    lineHeight = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        val slots = previewSlots(faceIndex, maxWidth, maxHeight)
        orderedSugarliciousPreviewIds(faceIndex, complicationIds).take(slots.size).forEachIndexed { slotIndex, id ->
            val slot = slots[slotIndex]
            PreviewComplication(
                id = id,
                state = state,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = slot.x, y = slot.y)
                        .size(width = slot.width, height = slot.height),
            )
        }

        if (faceIndex < 4) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.minDimension / 512f

                fun sx(value: Float): Float = (center.x - 256f * scale) + value * scale

                fun sy(value: Float): Float = (center.y - 256f * scale) + value * scale
                val handAngles = fixedWatchPreviewHandAngles

                withTransform({ rotate(handAngles.hour, center) }) {
                    drawRoundRect(
                        Color.White,
                        Offset(sx(243f), sy(113.57f)),
                        Size(26f * scale, 114f * scale),
                        androidx.compose.ui.geometry
                            .CornerRadius(13f * scale),
                    )
                    drawRect(
                        Color.White,
                        Offset(sx(252.75f), sy(224.44f)),
                        Size(6.5f * scale, 29.56f * scale),
                    )
                }
                withTransform({ rotate(handAngles.minute, center) }) {
                    drawRoundRect(
                        Color.White,
                        Offset(sx(243f), sy(34.47f)),
                        Size(26f * scale, 193.1f * scale),
                        androidx.compose.ui.geometry
                            .CornerRadius(13f * scale),
                    )
                    drawRect(
                        Color.White,
                        Offset(sx(252.75f), sy(224.44f)),
                        Size(6.5f * scale, 29.56f * scale),
                    )
                    drawCircle(Color(0xFFBCBCBC), 12f * scale, center)
                }
                withTransform({ rotate(handAngles.second, center) }) {
                    drawRect(
                        Color.Red,
                        Offset(sx(254f), sy(6f)),
                        Size(4f * scale, 290f * scale),
                    )
                    drawCircle(Color.Red, 8.5f * scale, center)
                }
                drawCircle(Color.Black, 4f * scale, center)
            }
        }
    }
}

private data class PreviewSlot(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

private fun previewSlots(
    index: Int,
    width: Dp,
    height: Dp,
): List<PreviewSlot> {
    fun slot(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) = PreviewSlot(
        x = width * (x / 450f),
        y = height * (y / 450f),
        width = width * (w / 450f),
        height = height * (h / 450f),
    )

    return when (index) {
        // Exact ComplicationSlot bounds from the five WFF watchface.xml files.
        0 ->
            listOf(
                slot(127f, 49f, 196f, 73f),
                slot(330f, 54f, 58f, 38f),
                slot(58f, 60f, 78f, 46f),
                slot(46f, 170f, 91f, 56f),
                slot(313f, 170f, 91f, 56f),
                slot(55f, 288f, 99f, 52f),
                slot(296f, 288f, 99f, 52f),
                slot(155f, 365f, 140f, 50f),
            )
        1 ->
            listOf(
                slot(112f, 110f, 226f, 226f),
                slot(32f, 165f, 90f, 58f),
                slot(328f, 165f, 90f, 58f),
                slot(75f, 322f, 300f, 92f),
            )
        2 ->
            listOf(
                slot(145f, 260f, 160f, 160f),
                slot(43f, 105f, 125f, 72f),
                slot(282f, 105f, 125f, 72f),
                slot(70f, 188f, 310f, 74f),
            )
        3 ->
            listOf(
                slot(62f, 62f, 326f, 88f),
                slot(30f, 238f, 390f, 150f),
                slot(44f, 170f, 112f, 60f),
                slot(294f, 170f, 112f, 60f),
            )
        4 ->
            listOf(
                slot(84f, 121f, 282f, 96f),
                slot(333f, 117f, 65f, 45f),
                slot(55f, 247f, 340f, 72f),
                slot(48f, 348f, 72f, 54f),
                slot(142f, 348f, 72f, 54f),
                slot(236f, 348f, 72f, 54f),
                slot(330f, 348f, 72f, 54f),
                slot(304f, 52f, 82f, 38f),
            )
        else -> emptyList()
    }
}

@Composable
private fun PreviewComplication(
    id: Int,
    state: TherapyDisplayState?,
    modifier: Modifier,
) {
    if (id == PROFILE_PREVIEW_ID || id == PHONE_BATTERY_PREVIEW_ID) {
        val text =
            if (id == PROFILE_PREVIEW_ID) {
                state?.profile?.name?.takeIf(String::isNotBlank) ?: "Profil"
            } else {
                state?.device?.phoneBatteryPercent?.let { "$it %" } ?: "Akku"
            }
        Text(
            text = text,
            modifier = modifier,
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        return
    }

    if (id == SugarliciousComplicationIds.GRAPH) {
        MiniPreviewGraph(state, modifier)
        return
    }

    val now = System.currentTimeMillis()
    val presentation = ComplicationPresentationFormatter.format(id, state ?: previewFaceState(now), now)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = presentation.text,
            color = Color.White,
            fontSize = 8.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        presentation.title?.takeIf(String::isNotBlank)?.let { title ->
            Text(
                text = title,
                modifier = Modifier.offset(y = (-1).dp),
                color = Color(0xFFB8C0C4),
                fontSize = 5.5.sp,
                lineHeight = 6.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniPreviewGraph(
    state: TherapyDisplayState?,
    modifier: Modifier,
) {
    val now = System.currentTimeMillis()
    val timeWindow = GraphTimeWindow.live(now, 90L * 60_000L)
    val cutoff = timeWindow.startEpochMs
    val effectiveState = state ?: previewFaceState(now)
    val points =
        (
            effectiveState.glucoseHistory +
                listOfNotNull(effectiveState.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs) })
        ).filter { it.measuredAtEpochMs in cutoff..now + 5 * 60_000L }
            .sortedBy { it.measuredAtEpochMs }

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val low = 70.0
        val high = 180.0

        fun x(ts: Long): Float = timeWindow.plotX(ts, 0f, size.width)

        fun y(value: Double): Float = size.height - (GlucoseGraphScale.ratio(value).toFloat() * size.height)
        drawRoundRect(
            Color(0x2219D7E8),
            size = size,
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(3.dp.toPx()),
        )
        points.forEach { point ->
            val color =
                when {
                    point.valueMgDl < low -> Color(0xFFFF6464)
                    point.valueMgDl > high -> Color(0xFFFFA24B)
                    else -> Color(0xFF54DF30)
                }
            drawCircle(
                color,
                radius = 1.2.dp.toPx(),
                center = Offset(x(point.measuredAtEpochMs), y(point.valueMgDl)),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewCard(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRoundRect(
        color = Color(0xFF091117),
        topLeft = Offset(size.width * x, size.height * y),
        size = Size(size.width * width, size.height * height),
        cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(size.minDimension * 0.035f),
    )
}

private fun previewFaceState(now: Long): TherapyDisplayState {
    val history =
        (0..18).map { index ->
            GlucoseSample(
                valueMgDl = 108.0 + ((index % 8) * 3.0),
                measuredAtEpochMs = now - (18 - index) * 5L * 60_000L,
            )
        }
    return TherapyDisplayState(
        receivedAtEpochMs = now,
        sourceVersion = "AndroidAPS",
        glucose =
            app.sugarlicious.model.GlucoseState(
                valueMgDl = 123.0,
                displayUnit = app.sugarlicious.model.GlucoseUnit.MG_DL,
                trend = app.sugarlicious.model.Trend.FORTY_FIVE_UP,
                measuredAtEpochMs = now - 2 * 60_000L,
                deltaMgDl = 5.0,
            ),
        glucoseHistory = history,
        insulin = app.sugarlicious.model.InsulinState(totalIob = 1.2, bolusIob = 0.8, basalIob = 0.4),
        carbs = app.sugarlicious.model.CarbState(cobGrams = 15.0),
        basal = app.sugarlicious.model.BasalState(currentUnitsPerHour = 0.70),
    )
}
