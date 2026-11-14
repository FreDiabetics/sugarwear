package app.sugarlicious.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.withScale
import app.sugarlicious.model.TherapyDisplayState
import kotlin.math.roundToInt

internal data class AnalogRectGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

internal data class AnalogPointGeometry(
    val x: Float,
    val y: Float,
)

internal data class AnalogArcGeometry(
    val startAngle: Float,
    val endAngle: Float,
    val clockwise: Boolean,
)

/**
 * Geometry copied directly from sugarlicious-analog/src/main/res/raw/watchface.xml.
 * Keep the contract test green whenever the WFF layout is changed.
 */
internal object SugarliciousAnalogGeometry {
    const val WFS_REFERENCE_CANVAS = 450f
    const val CANVAS = 512f
    const val WFS_TO_WFF_SCALE = CANVAS / WFS_REFERENCE_CANVAS
    val center = AnalogPointGeometry(256f, 256f)
    const val watchRadius = 256f
    const val safeRadius = 236f
    const val centerSafetyRadius = 24f
    val handPivot = center
    val graph = fromWfsRect(51.8748f, 54.9999f, 346.2504f, 121.3336f)

    // Canonical Orbit graph fills the upper inner semicircle, centered on the hand pivot.
    val graphContent = AnalogRectGeometry(91f, 91f, 330f, 97f)
    val middleLeft = fromWfsRect(73f, 171f, 108.3334f, 108.3334f)
    val middleRight = fromWfsRect(269f, 171f, 108.3334f, 108.3334f)
    val bottomCenter = fromWfsRect(158.9996f, 247f, 132.0008f, 130.9996f)
    val middleLeftText = within(middleLeft, 8f, 60f, 107f, 38f)
    val middleLeftTitle = within(middleLeft, 8f, 20f, 107f, 40f)
    val middleRightText = within(middleRight, 8f, 60f, 107f, 38f)
    val middleRightTitle = within(middleRight, 8f, 20f, 107f, 40f)
    val bottomText = AnalogRectGeometry(188f, 330f, 136f, 54f)

    const val outerCenter = 256f
    val outerTextDiameter = fromWfsValue(376f - 33f)
    val outerProgressDiameter = fromWfsValue(359f - 15f)
    val outerStroke = fromWfsValue(15f)
    val outerUpperLeft = AnalogArcGeometry(285f, 333f, true)
    val outerUpperRight = AnalogArcGeometry(15f, 63f, true)
    val outerLowerRight = AnalogArcGeometry(103f, 151f, true)
    val outerLowerLeft = AnalogArcGeometry(195f, 243f, true)

    val bottomArcDiameter = fromWfsValue(120f - 10f)
    const val bottomArcStart = 220f
    const val bottomArcSweep = 280f
    val bottomArcStroke = fromWfsValue(10f)

    private fun fromWfsValue(value: Float): Float = (value * WFS_TO_WFF_SCALE).roundToInt().toFloat()

    private fun fromWfsRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) = AnalogRectGeometry(
        x = fromWfsValue(x),
        y = fromWfsValue(y),
        width = fromWfsValue(width),
        height = fromWfsValue(height),
    )

    private fun within(
        parent: AnalogRectGeometry,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) = AnalogRectGeometry(
        x = parent.x + fromWfsValue(x),
        y = parent.y + fromWfsValue(y),
        width = fromWfsValue(width),
        height = fromWfsValue(height),
    )

    fun mirrorHorizontally(rect: AnalogRectGeometry): AnalogRectGeometry = rect.copy(x = CANVAS - rect.x - rect.width)

    fun centerOf(rect: AnalogRectGeometry): AnalogPointGeometry = AnalogPointGeometry(rect.x + rect.width / 2f, rect.y + rect.height / 2f)
}

/** Native-rendered template: illustrative provider preview data, never live glucose. */
@Composable
internal fun SugarliciousAnalogFacePreview(
    @Suppress("UNUSED_PARAMETER") state: TherapyDisplayState?,
    modifier: Modifier = Modifier,
) {
    val native = ImageBitmap.imageResource(R.drawable.apex_slots_native)
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.apex_template_preview),
            contentDescription = "ApeX – gespeicherte Darstellungsvorlage, keine Live-Messwerte",
            modifier = Modifier.matchParentSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas { target ->
                val canvas = target.nativeCanvas
                canvas.withScale(size.width / 512f, size.height / 512f) {
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                    canvas.drawRect(128f, 102f, 384f, 194f, paint)
                    val arc =
                        android.graphics.Path().apply {
                            addArc(android.graphics.RectF(91f, 91f, 421f, 421f), 180f, 180f)
                            lineTo(421f, 256f)
                            close()
                        }
                    canvas.clipPath(arc)
                    canvas.clipRect(91f, 91f, 421f, 188f)
                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.translate(91f, 91f)
                    val demoNow = 1_700_000_000_000L
                    app.sugarlicious.uishared.WatchfaceCgmGraphRenderer.render(
                        canvas,
                        330,
                        97,
                        previewTherapyState(demoNow).glucoseHistory,
                        app.sugarlicious.model.GraphTimeWindow
                            .live(demoNow, 5_400_000L),
                        app.sugarlicious.model.CgmThresholds(lowMgDl = 80.0, highMgDl = 160.0),
                        color = 0xFFFFCCB1.toInt(),
                        semicircle = true,
                        showThresholdLabels = false,
                    )
                }
            }
            withTransform({ scale(size.width / 512f, size.height / 512f, Offset.Zero) }) {
                listOf(SugarliciousAnalogGeometry.middleLeft, SugarliciousAnalogGeometry.middleRight, SugarliciousAnalogGeometry.bottomCenter).forEach { bounds ->
                    val path = nativeWatchSlotPath(NativeSlotBounds("BoundingOval", bounds.x, bounds.y, bounds.width, bounds.height))
                    drawPath(path, Color.Black)
                    clipPath(path) { drawImage(native, dstSize = IntSize(512, 512)) }
                }
            }
        }
        NativePreviewHands(0, Modifier.matchParentSize())
    }
}
