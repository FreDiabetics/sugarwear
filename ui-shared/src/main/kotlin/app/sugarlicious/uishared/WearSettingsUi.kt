package app.sugarlicious.uishared

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/** Shared round-screen settings vocabulary. Uses public Android widgets, not private DeviceVendor APIs. */
object WearSettingsUi {
    const val MENU_BACKGROUND: Int = 0xFF000000.toInt()
    const val MENU_SURFACE: Int = 0xFF202020.toInt()
    const val MENU_TEXT: Int = 0xFFFFFFFF.toInt()
    const val MENU_SECONDARY: Int = 0xFFB6B6BB.toInt()

    fun pageHeader(context: Context, title: String, textColor: Int, back: () -> Unit): LinearLayout =
        header(context, title, textColor, back).apply {
            val inset = dp(context, 11)
            setPadding(inset, 0, inset, 0)
        }

    /** Flat One UI Watch action surface, with no platform button inset or elevation shadow. */
    fun actionButton(view: android.widget.Button, cardSurface: Int, foreground: Int) {
        val density = view.resources.displayMetrics.density

        fun channel(shift: Int): Int = (((cardSurface ushr shift) and 255) * .90f + ((foreground ushr shift) and 255) * .10f).roundToInt()
        val fill = Color.rgb(channel(16), channel(8), channel(0))
        val shape =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = 26f * density
            }
        view.apply {
            backgroundTintList = null
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), shape, null)
            layoutParams =
                (layoutParams as? ViewGroup.MarginLayoutParams ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
                    topMargin = dp(context, 6)
                }
            stateListAnimator = null
            elevation = 0f
            translationZ = 0f
            minimumHeight = dp(context, 48)
            minWidth = 0
            minimumWidth = 0
            isAllCaps = false
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            setTextColor(foreground)
            SystemTypography.apply(this, true)
        }
    }

    fun accent(context: Context): Int =
        SugarliciousAccentPalette.primary(context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun textSize(legacySp: Float): Float =
        when {
            legacySp >= 16f -> 18f
            legacySp >= 10f -> 15f
            else -> 12f
        }

    fun background(context: Context, color: Int, radiusDp: Float = 26f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * context.resources.displayMetrics.density
        }

    fun swatch(context: Context, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(context, 1), Color.GRAY)
        }

    fun slider(view: SeekBar, accent: Int, muted: Int, label: String? = null) {
        val context = view.context
        val inactive = if (Color.alpha(muted) < 128 || Color.luminance(muted) < .025f) Color.rgb(70, 70, 73) else muted

        view.progressTintList = null
        view.progressBackgroundTintList = null
        view.secondaryProgressTintList = null

        fun track(color: Int) = background(context, color, 5f).apply { setSize(dp(context, 120), dp(context, 10)) }
        view.progressDrawable =
            LayerDrawable(arrayOf(track(inactive), ClipDrawable(track(accent), Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
                for (index in 0..1) {
                    setLayerHeight(index, dp(context, 10))
                    setLayerGravity(index, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)
                }
            }
        view.thumb =
            swatch(context, Color.WHITE).apply {
                setSize(dp(context, 20), dp(context, 20))
                setStroke(0, Color.TRANSPARENT)
            }
        view.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        view.minimumHeight = dp(context, 48)
        view.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        if (label != null) view.contentDescription = label
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.splitTrack = false
    }

    @Suppress("DEPRECATION")
    fun toggle(view: Switch, accent: Int, textColor: Int) {
        val context = view.context
        view.showText = false
        view.splitTrack = false
        view.switchPadding = dp(context, 12)
        view.switchMinWidth = dp(context, 40)
        view.minimumHeight = dp(context, 48)
        view.textSize = 15f
        view.includeFontPadding = false
        view.setTextColor(textColor)
        // Switch lays its thumb out at track height. Keep the actual disc at 20dp
        // inside that 24dp allocation instead of stretching an oval to fill it.
        view.thumbDrawable =
            LayerDrawable(arrayOf(swatch(context, Color.WHITE).apply { setStroke(0, Color.TRANSPARENT) })).apply {
                setLayerSize(0, dp(context, 20), dp(context, 20))
                setLayerGravity(0, Gravity.CENTER)
            }
        view.minimumWidth = dp(context, 54)
        view.background = null
        view.trackDrawable = background(context, Color.DKGRAY, 14f).apply { setSize(dp(context, 40), dp(context, 24)) }
        view.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, Color.DKGRAY))
        view.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        SystemTypography.apply(view, false)
    }

    /** A single visual element owns all of its controls, in one neutral settings surface. */
    fun elementCard(context: Context, title: String, surface: Int, textColor: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = background(context, surface)
            setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10))
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 15f
                    setTextColor(textColor)
                    setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 8))
                    SystemTypography.apply(this, true)
                    if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                },
            )
        }

    /** Circular page arcs need more vertical clearance than rectangular screens. */
    fun pagePadding(root: LinearLayout) {
        val config = root.resources.configuration
        val round = config.isScreenRound
        val horizontal = if (round) ((config.screenWidthDp.takeIf { it > 0 } ?: 192) * .085f).roundToInt().coerceIn(16, 28) else 12
        root.setPadding(dp(root.context, horizontal), dp(root.context, if (round) 32 else 16), dp(root.context, horizontal), dp(root.context, if (round) 56 else 24))
    }

    fun navigationRow(context: Context, title: String, summary: String?, textColor: Int, secondaryColor: Int, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(context, 48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))

            fun label(value: String, size: Float, color: Int) =
                TextView(context).apply {
                    text = value
                    textSize = size
                    includeFontPadding = false
                    setTextColor(color)
                    SystemTypography.apply(this, false)
                }
            addView(label(title, 15f, textColor))
            if (!summary.isNullOrBlank()) addView(label(summary, 12f, secondaryColor), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 4) })
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, background(context, Color.WHITE))
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun navigationGroup(context: Context, rows: List<View>, surface: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = background(context, surface)
            clipToOutline = true
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    addView(
                        View(context).apply { setBackgroundColor(0x225F6368) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)).apply {
                            marginStart = dp(context, 14)
                            marginEnd = dp(context, 14)
                        },
                    )
                }
                addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

    fun choice(context: Context, label: String, selected: Boolean, accent: Int, surface: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            includeFontPadding = false
            setTextColor(Color.WHITE)
            SystemTypography.apply(this, false)
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 48)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), background(context, surface), null)
            val ring =
                swatch(context, Color.TRANSPARENT).apply {
                    setStroke(dp(context, 2), if (selected) accent else Color.GRAY)
                }
            val indicator =
                LayerDrawable(arrayOf(ring, swatch(context, if (selected) accent else Color.TRANSPARENT).apply { setStroke(0, Color.TRANSPARENT) })).apply {
                    setLayerSize(1, dp(context, 10), dp(context, 10))
                    setLayerGravity(1, Gravity.CENTER)
                    setBounds(0, 0, dp(context, 20), dp(context, 20))
                }
            setCompoundDrawablesRelative(null, null, indicator, null)
            compoundDrawablePadding = dp(context, 10)
            isSelected = selected
            isFocusable = true
            accessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    @Suppress("DEPRECATION") // Wear OS API 33-35 still requires the Boolean checked setter.
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = android.widget.RadioButton::class.java.name
                        info.isCheckable = true
                        if (android.os.Build.VERSION.SDK_INT >= 36) {
                            info.setChecked(if (selected) AccessibilityNodeInfo.CHECKED_STATE_TRUE else AccessibilityNodeInfo.CHECKED_STATE_FALSE)
                        } else {
                            info.isChecked = selected
                        }
                    }
                }
            setOnClickListener { onClick() }
        }

    /** Only button steps persist here; native gesture callbacks remain owned by the caller. */
    fun inlineSlider(slider: SeekBar, onStep: (Int) -> Unit): LinearLayout =
        LinearLayout(slider.context).apply {
            orientation = LinearLayout.VERTICAL

            fun stepButton(label: String, description: String, delta: Int) =
                android.widget.Button(context).apply {
                    text = label
                    contentDescription = listOfNotNull(slider.contentDescription?.toString(), description).joinToString(": ")
                    actionButton(this, Color.rgb(36, 36, 39), Color.WHITE)
                    textSize = 20f
                    SystemTypography.apply(this, false)
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        if (!slider.isEnabled) return@setOnClickListener
                        val value = (slider.progress + delta).coerceIn(slider.min, slider.max)
                        if (value != slider.progress) {
                            slider.progress = value
                            onStep(value)
                        }
                    }
                }
            // Preserve the full gesture track even inside narrow, nested round-screen cards.
            addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(stepButton("−", "Verringern", -1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(context, 3) })
                    addView(stepButton("+", "Erhöhen", 1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 3) })
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }

    fun header(context: Context, title: String, textColor: Int, back: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 56)
            addView(
                android.widget.ImageButton(context).apply {
                    setImageDrawable(WearBackIcon(context, textColor))
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                    contentDescription = "Zurück"
                    tooltipText = "Zurück"
                    background = android.graphics.drawable.InsetDrawable(background(context, Color.rgb(42, 42, 45), 24f), dp(context, 2))
                    setOnClickListener { back() }
                },
                LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)),
            )
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 18f
                    includeFontPadding = false
                    setTextColor(textColor)
                    SystemTypography.apply(this, true)
                    if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                    setPadding(dp(context, 10), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
}

/** Navigation is an icon, so increasing the system font never clips a text glyph. */
private class WearBackIcon(
    context: Context,
    color: Int,
) : android.graphics.drawable.Drawable() {
    private val size = WearSettingsUi.dp(context, 20)
    private val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = WearSettingsUi.dp(context, 2).toFloat()
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

    override fun draw(canvas: android.graphics.Canvas) {
        val path =
            android.graphics.Path().apply {
                moveTo(bounds.left + bounds.width() * .65f, bounds.top + bounds.height() * .18f)
                lineTo(bounds.left + bounds.width() * .32f, bounds.top + bounds.height() * .5f)
                lineTo(bounds.left + bounds.width() * .65f, bounds.top + bounds.height() * .82f)
            }
        canvas.drawPath(path, paint)
    }

    override fun getIntrinsicWidth(): Int = size

    override fun getIntrinsicHeight(): Int = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
