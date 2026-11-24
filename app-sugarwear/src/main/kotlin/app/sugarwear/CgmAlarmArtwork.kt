package app.sugarwear

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import app.sugarlicious.model.TrendArrowRenderSpec
import app.sugarlicious.sensor.protocol.CgmReading
import kotlin.math.roundToInt

internal object CgmAlarmDesignTokens {
    const val SIZE = 450f
    const val ACTION_WIDTH = 280f
    const val ACTION_HEIGHT = 85f
    const val ACTION_TEXT_SIZE = 34f
    val HIGH = 0xFFFCC93B.toInt()
    val LOW = 0xFFF73D45.toInt()
    val SECONDARY = 0xFF848484.toInt()
    val BUTTON = 0xFF383838.toInt()
}

internal enum class CgmAlarmArtwork { HIGH, LOW, LOW_SOON, RAPID_RISE, RAPID_FALL, TECHNICAL_WARNING, SENSOR_ERROR, SIGNAL_LOSS, BLUETOOTH, PAUSED }

internal enum class CgmAlarmContentMode { FULL_BLEED, IN_GLUCOSE_TILE }

internal fun applyCgmAlarmFullBleedChrome(window: android.view.Window) {
    androidx.core.view.WindowCompat
        .setDecorFitsSystemWindows(window, false)
    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(
            androidx.core.view.WindowInsetsCompat.Type
                .systemBars(),
        )
    }
}

/** All alarm surfaces share this native composition and the same trend assets. */
internal fun cgmAlarmArtwork(type: SugarWearAlarmType): CgmAlarmArtwork? =
    when (type) {
        SugarWearAlarmType.HIGH -> CgmAlarmArtwork.HIGH
        SugarWearAlarmType.RAPID_RISE -> CgmAlarmArtwork.RAPID_RISE
        SugarWearAlarmType.RAPID_FALL -> CgmAlarmArtwork.RAPID_FALL
        SugarWearAlarmType.LOW, SugarWearAlarmType.URGENT_LOW -> CgmAlarmArtwork.LOW
        SugarWearAlarmType.URGENT_LOW_SOON -> CgmAlarmArtwork.LOW_SOON
        SugarWearAlarmType.SIGNAL_LOSS -> CgmAlarmArtwork.SIGNAL_LOSS
        SugarWearAlarmType.TEMPORARY_SENSOR_PROBLEM -> CgmAlarmArtwork.SENSOR_ERROR
        SugarWearAlarmType.TECHNICAL_WARNING -> CgmAlarmArtwork.TECHNICAL_WARNING
        // Alarm types without dedicated full-bleed artwork (for example sensor-lifetime
        // notices) deliberately stay out of the high/low/error artwork paths.
        else -> null
    }

/** Native children laid out on the original 450-unit Illustrator artboard. No text paths. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required alarm artwork.
internal class CgmAlarmFullBleedView(
    context: Context,
    artwork: CgmAlarmArtwork,
    reading: CgmReading? = null,
    glucoseColor: Int = Color.WHITE,
    trendStyle: TrendArrowRenderSpec? = null,
    onStop: (() -> Unit)? = null,
    private val mode: CgmAlarmContentMode = CgmAlarmContentMode.FULL_BLEED,
    onAcknowledge: (() -> Unit)? = null,
    titleOverride: String? = null,
    frozenGlucoseMgDl: Double? = null,
    frozenTrend: app.sugarlicious.model.Trend? = null,
) : ViewGroup(context) {
    private data class Placement(
        val child: View,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val textSize: Float = 0f,
        val baseline: Boolean = false,
    )

    private val placements = mutableListOf<Placement>()
    private val longTitle = artwork in setOf(CgmAlarmArtwork.RAPID_RISE, CgmAlarmArtwork.RAPID_FALL)
    private val tileArtboardHeight = 520f

    init {
        setBackgroundColor(if (mode == CgmAlarmContentMode.FULL_BLEED) Color.BLACK else Color.TRANSPARENT)
        val icon =
            when (artwork) {
                CgmAlarmArtwork.HIGH, CgmAlarmArtwork.RAPID_RISE -> R.drawable.ic_artwork_high
                CgmAlarmArtwork.LOW, CgmAlarmArtwork.RAPID_FALL -> R.drawable.ic_artwork_low
                CgmAlarmArtwork.LOW_SOON -> R.drawable.ic_artwork_low_soon
                CgmAlarmArtwork.SENSOR_ERROR -> R.drawable.ic_artwork_sensor_error
                CgmAlarmArtwork.TECHNICAL_WARNING -> R.drawable.ic_artwork_low
                CgmAlarmArtwork.SIGNAL_LOSS -> R.drawable.ic_artwork_signal_loss
                CgmAlarmArtwork.BLUETOOTH -> R.drawable.ic_artwork_bluetooth
                CgmAlarmArtwork.PAUSED -> R.drawable.ic_artwork_paused
            }
        val box =
            when (artwork) {
                CgmAlarmArtwork.SENSOR_ERROR -> floatArrayOf(193.14f, 55.25f, 78.31f, 99.49f)
                CgmAlarmArtwork.SIGNAL_LOSS -> floatArrayOf(193.14f, 54.5f, 86.76f, 100.98f)
                CgmAlarmArtwork.BLUETOOTH -> floatArrayOf(203.64f, 69f, 42.73f, 72f)
                CgmAlarmArtwork.PAUSED -> floatArrayOf(193.14f, 53.51f, 83.37f, 101.23f)
                else -> floatArrayOf(185.01f, 68.59f, 80f, 72.83f)
            }
        place(
            ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                tag = "original-alarm-artwork"
            },
            box[0],
            box[1],
            box[2],
            box[3],
        )
        when (artwork) {
            CgmAlarmArtwork.HIGH, CgmAlarmArtwork.LOW, CgmAlarmArtwork.LOW_SOON, CgmAlarmArtwork.RAPID_RISE, CgmAlarmArtwork.RAPID_FALL -> {
                val rapid = artwork == CgmAlarmArtwork.RAPID_RISE || artwork == CgmAlarmArtwork.RAPID_FALL
                val valueText =
                    if (frozenGlucoseMgDl != null && frozenGlucoseMgDl.isFinite()) {
                        kotlin.math
                            .round(frozenGlucoseMgDl)
                            .toInt()
                            .toString()
                    } else {
                        cgmGlucoseOnlyNumberText(reading)
                    }
                val arrowTrend =
                    if (frozenGlucoseMgDl != null) {
                        frozenTrend
                    } else if (reading == null) {
                        null
                    } else if (rapid) {
                        when {
                            artwork == CgmAlarmArtwork.RAPID_RISE && reading.trend in setOf(app.sugarlicious.model.Trend.SINGLE_UP, app.sugarlicious.model.Trend.DOUBLE_UP) -> app.sugarlicious.model.Trend.SINGLE_UP
                            artwork == CgmAlarmArtwork.RAPID_FALL && reading.trend in setOf(app.sugarlicious.model.Trend.SINGLE_DOWN, app.sugarlicious.model.Trend.DOUBLE_DOWN) -> app.sugarlicious.model.Trend.SINGLE_DOWN
                            else -> null
                        }
                    } else {
                        reading.trend
                    }
                val hasTrend = trendDrawableRes(arrowTrend) != null
                val high = artwork == CgmAlarmArtwork.HIGH || artwork == CgmAlarmArtwork.RAPID_RISE
                val arrowAspect =
                    arrowTrend?.let {
                        app.sugarlicious.model.TrendVisuals
                            .spec(it)
                            ?.aspectRatio
                    } ?: 1f
                val dynamicRow = rapid || arrowAspect > 1.01f || (trendStyle?.scale ?: 1f) != 1f
                val rapidArrowSize = 45f * (trendStyle?.scale ?: 1f)
                val rapidGap = 16f
                val rapidNumberWidth =
                    android.graphics
                        .Paint()
                        .apply {
                            textSize = 80f * resources.configuration.fontScale
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }.measureText(valueText)
                        .coerceAtMost(220f)
                val rowArrowWidth = if (rapid) rapidArrowSize * 2f + rapidGap else rapidArrowSize * arrowAspect
                val rapidRowStart = (CgmAlarmDesignTokens.SIZE - rapidNumberWidth - rapidGap - rowArrowWidth) / 2f
                text(
                    valueText,
                    if (dynamicRow && hasTrend) {
                        rapidRowStart
                    } else if (hasTrend) {
                        if (high) 115f else 139f
                    } else {
                        65f
                    },
                    245f,
                    if (dynamicRow && hasTrend) {
                        rapidNumberWidth
                    } else if (hasTrend) {
                        if (high) 164f else 112f
                    } else {
                        320f
                    },
                    80f,
                    glucoseColor,
                    true,
                )
                trendDrawableRes(arrowTrend)?.let { drawable ->
                    repeat(if (rapid) 2 else 1) { arrowIndex ->
                        val scale = trendStyle?.scale ?: 1f
                        val size = 45f * scale

                        fun arrow(color: Int, dx: Float = 0f, dy: Float = 0f) =
                            place(
                                ImageView(context).apply {
                                    setImageResource(drawable)
                                    tag = "alarm-trend-$arrowIndex"
                                    setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                                    translationX = dx * resources.displayMetrics.density
                                    translationY = dy * resources.displayMetrics.density
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                },
                                if (dynamicRow) rapidRowStart + rapidNumberWidth + rapidGap + arrowIndex * (size + rapidGap) else (if (high) 271.5f else 251.4f) - (size - 45f) / 2f,
                                194.3f - (size - 45f) / 2f,
                                size * arrowAspect,
                                size,
                            )
                        val outline = trendStyle?.outlineThicknessDp ?: 0f
                        if (outline > 0f) listOf(-outline to 0f, outline to 0f, 0f to -outline, 0f to outline).forEach { (x, y) -> arrow(trendStyle!!.outlineColor, x, y) }
                        arrow(trendStyle?.fillColor ?: Color.WHITE)
                    }
                }
                text("mg/dL", 100f, 284.8f, 250f, 36f, CgmAlarmDesignTokens.SECONDARY, false)
                val title =
                    when (artwork) {
                        CgmAlarmArtwork.HIGH -> "Hoch"
                        CgmAlarmArtwork.LOW -> "Tief"
                        CgmAlarmArtwork.RAPID_RISE -> "Schnell steigend"
                        CgmAlarmArtwork.RAPID_FALL -> "Schnell fallend"
                        else -> "Bald Tief"
                    }
                val titleColor = if (high) CgmAlarmDesignTokens.HIGH else CgmAlarmDesignTokens.LOW
                if (longTitle) {
                    text(title, 70f, 369.4f, 310f, 50f, titleColor, true)
                } else {
                    text(title, 35f, 369.4f, 380f, 64f, titleColor, true)
                }
            }
            CgmAlarmArtwork.TECHNICAL_WARNING -> {
                val lines = (titleOverride ?: "Technisches Problem").split(" ", limit = 2)
                text(lines.first(), 20f, 247.2f, 410f, 60f, CgmAlarmDesignTokens.LOW, true)
                text(lines.getOrElse(1) { "" }, 40f, 307.2f, 370f, 60f, CgmAlarmDesignTokens.LOW, true)
            }
            CgmAlarmArtwork.SENSOR_ERROR -> {
                text(titleOverride ?: "Sensorfehler", 20f, 247.2f, 410f, 60f, CgmAlarmDesignTokens.LOW, true)
                if (titleOverride == null) description(listOf("Diese Phase kann", "bis zu 3h dauern."), 299f)
            }
            CgmAlarmArtwork.SIGNAL_LOSS -> {
                text("Signalverlust", 20f, 247.2f, 410f, 60f, Color.WHITE, true)
                description(listOf("Bleiben Sie in", "Reichweite des", "Sensors"), 301f)
            }
            CgmAlarmArtwork.BLUETOOTH -> {
                text("Bluetooth", 30f, 207f, 390f, 60f, Color.WHITE, true)
                text("deaktiviert!", 30f, 261f, 390f, 60f, Color.WHITE, true)
                description(listOf("Bitte aktivieren Sie", "Ihr Bluetooth in den", "Einstellungen"), 313f)
            }
            CgmAlarmArtwork.PAUSED -> {
                text("Datenempfang", 10f, 221.19f, 430f, 60f, Color.WHITE, true)
                text("pausiert", 40f, 271.19f, 370f, 60f, Color.WHITE, true)
                val button =
                    Button(context).apply {
                        setText(R.string.cgm_action_stop_app)
                        isAllCaps = false
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        setPadding(0, 0, 0, 0)
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        stateListAnimator = null
                        elevation = 0f
                        backgroundTintList = null
                        background =
                            GradientDrawable().apply {
                                setColor(CgmAlarmDesignTokens.BUTTON)
                                cornerRadius = 1000f
                            }
                        tag = "pause-exit-confirm"
                        setOnClickListener { onStop?.invoke() }
                    }
                place(button, 80.72f, 303.45f, 280f, 85f, 34f)
            }
        }
        if (mode == CgmAlarmContentMode.IN_GLUCOSE_TILE && onAcknowledge != null) {
            val button =
                Button(context).apply {
                    text = context.getString(android.R.string.ok)
                    applyCgmTileActionStyle()
                    tag = "alarm-acknowledge"
                    setOnClickListener { onAcknowledge() }
                }
            place(button, 85f, 405f, CgmAlarmDesignTokens.ACTION_WIDTH, CgmAlarmDesignTokens.ACTION_HEIGHT, CgmAlarmDesignTokens.ACTION_TEXT_SIZE)
        }
    }

    private fun description(lines: List<String>, firstBaseline: Float) = lines.forEachIndexed { i, line -> text(line, 40f, firstBaseline + i * 40f, 370f, 34f, CgmAlarmDesignTokens.SECONDARY, false) }

    private fun text(value: String, x: Float, baseline: Float, width: Float, size: Float, color: Int, bold: Boolean) {
        val view =
            TextView(context).apply {
                text = value
                setTextColor(color)
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
                includeFontPadding = false
                maxLines = 1
                setHorizontallyScrolling(false)
                setPadding(0, 0, 0, 0)
            }
        place(view, x, baseline, width, size * 1.5f, size, true)
    }

    private fun place(view: View, x: Float, y: Float, width: Float, height: Float, textSize: Float = 0f, baseline: Boolean = false) {
        placements += Placement(view, x, y, width, height, textSize, baseline)
        addView(view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (mode == CgmAlarmContentMode.IN_GLUCOSE_TILE) (w * tileArtboardHeight / CgmAlarmDesignTokens.SIZE).roundToInt() else MeasureSpec.getSize(heightMeasureSpec)
        val scale = if (mode == CgmAlarmContentMode.IN_GLUCOSE_TILE) w / CgmAlarmDesignTokens.SIZE else minOf(w, h) / CgmAlarmDesignTokens.SIZE
        placements.forEach {
            if (it.child is TextView) {
                val size = it.textSize * scale * resources.configuration.fontScale
                it.child.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
                // Honor system scaling while keeping a single-line reference inside its container.
                val measured = it.child.paint.measureText(it.child.text.toString())
                // Layout uses integer pixels; reserve two pixels so rounding cannot wrap the final glyph.
                val available = ((it.w * scale).roundToInt() - 2).coerceAtLeast(1)
                if (measured > available) it.child.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * available / measured)
            }
            it.child.measure(MeasureSpec.makeMeasureSpec((it.w * scale).roundToInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((it.h * scale).roundToInt(), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val scale = if (mode == CgmAlarmContentMode.IN_GLUCOSE_TILE) width / CgmAlarmDesignTokens.SIZE else minOf(width, height) / CgmAlarmDesignTokens.SIZE
        val ox = (width - CgmAlarmDesignTokens.SIZE * scale) / 2f
        val oy = if (mode == CgmAlarmContentMode.IN_GLUCOSE_TILE) 0f else (height - CgmAlarmDesignTokens.SIZE * scale) / 2f
        placements.forEach {
            val x = (ox + it.x * scale).roundToInt()
            val y = (oy + it.y * scale).roundToInt() - if (it.baseline) (it.child as TextView).baseline else 0
            it.child.layout(x, y, x + it.child.measuredWidth, y + it.child.measuredHeight)
        }
    }
}

/** System notifications accept drawable-backed bitmaps, never arrow glyphs in text. */
internal fun cgmAlarmTrendIcon(context: Context, reading: CgmReading?, type: SugarWearAlarmType, now: Long = System.currentTimeMillis()): android.graphics.Bitmap? {
    reading ?: return null
    if (type == SugarWearAlarmType.TECHNICAL_WARNING) return null
    if (reading.status != app.sugarlicious.sensor.protocol.CgmReadingStatus.VALID || reading.displayOnly || !reading.glucoseMgDl.isFinite() || now - reading.timestampEpochMs !in 0 until CGM_SIGNAL_LOSS_AFTER_MS) return null
    val rapid = type == SugarWearAlarmType.RAPID_RISE || type == SugarWearAlarmType.RAPID_FALL
    val trend =
        if (rapid) {
            when {
                type == SugarWearAlarmType.RAPID_RISE && reading.trend in setOf(app.sugarlicious.model.Trend.SINGLE_UP, app.sugarlicious.model.Trend.DOUBLE_UP) -> app.sugarlicious.model.Trend.SINGLE_UP
                type == SugarWearAlarmType.RAPID_FALL && reading.trend in setOf(app.sugarlicious.model.Trend.SINGLE_DOWN, app.sugarlicious.model.Trend.DOUBLE_DOWN) -> app.sugarlicious.model.Trend.SINGLE_DOWN
                else -> return null
            }
        } else {
            reading.trend
        }
    val spec =
        app.sugarlicious.model.TrendVisuals
            .spec(trend) ?: return null
    val style = CgmAppearanceStore(context).trendArrowStyle().renderSpec()
    val density = context.resources.displayMetrics.density
    val height = (48f * density * style.scale).roundToInt().coerceAtLeast(1)
    val width = (height * spec.aspectRatio).roundToInt().coerceAtLeast(1)
    val gap = (8f * density).roundToInt()
    val inset = (style.outlineThicknessDp * density).roundToInt() + 1
    val count = if (rapid) 2 else 1
    val bitmap = createBitmap(width * count + gap * (count - 1) + inset * 2, height + inset * 2)
    val canvas = android.graphics.Canvas(bitmap)
    val drawable =
        context
            .getDrawable(
                app.sugarlicious.uishared.TrendDrawableResources
                    .forAsset(spec.asset),
            )!!
            .mutate()
    repeat(count) { index ->
        fun draw(color: Int, dx: Float = 0f, dy: Float = 0f) {
            canvas.withTranslation(inset + index * (width + gap) + dx, inset + dy) {
                drawable.setBounds(0, 0, width, height)
                drawable.setTint(color)
                drawable.draw(this)
            }
        }
        val outline = style.outlineThicknessDp * density
        if (outline > 0f) listOf(-outline to 0f, outline to 0f, 0f to -outline, 0f to outline).forEach { (x, y) -> draw(style.outlineColor, x, y) }
        draw(style.fillColor)
    }
    return bitmap
}
