package app.sugarlicious.uishared

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import app.sugarlicious.model.ArgbColor
import java.util.Locale
import kotlin.math.roundToInt

/** One functional color editor for classic Mobile/Wear/Collector surfaces. Layout stays responsive. */
object SharedColorEditor {
    private val recentColors = ArrayDeque<Int>()
    private val presets =
        intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF6DE892.toInt(), 0xFFFF5C69.toInt(), 0xFFFFD040.toInt(), 0xFF64BFFF.toInt())

    fun show(
        activity: Activity,
        title: String,
        initialArgb: Int,
        surfaceArgb: Int,
        textArgb: Int,
        borderArgb: Int,
        defaultArgb: Int,
        onChange: (Int) -> Unit,
        onReset: () -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        if (activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)) {
            WearColorPicker.show(activity, title, initialArgb, defaultArgb, onChange, onReset, onDismiss)
            return
        }
        val density = activity.resources.displayMetrics.density

        fun dp(value: Int) = (value * density).roundToInt()

        fun background(
            fill: Int,
            radius: Float = 14f,
        ) = GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), borderArgb)
            cornerRadius = radius * density
        }
        val hsv = FloatArray(3).also { Color.colorToHSV(initialArgb, it) }
        var hue = hsv[0]
        var saturation = hsv[1]
        var brightness = hsv[2]
        var alpha = Color.alpha(initialArgb) / 255f
        var updating = false
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundColor(surfaceArgb)
            }
        val preview = android.view.View(activity).apply { background = background(initialArgb) }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        val hex =
            EditText(activity).apply {
                setText(ArgbColor.format(initialArgb))
                inputType = InputType.TYPE_CLASS_TEXT
                filters = arrayOf(InputFilter.LengthFilter(9))
                setTextColor(textArgb)
            }
        root.addView(hex)
        val rgb =
            TextView(activity).apply {
                setTextColor(textArgb)
                textSize = 10f
            }
        root.addView(rgb)

        fun color() = Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, saturation, brightness))

        fun refresh(persist: Boolean = true) {
            val value = color()
            preview.background = background(value)
            rgb.text =
                activity.getString(
                    R.string.shared_color_rgba,
                    Color.red(value),
                    Color.green(value),
                    Color.blue(value),
                    Color.alpha(value),
                )
            if (!updating) {
                updating = true
                hex.setText(ArgbColor.format(value))
                hex.setSelection(hex.length())
                updating = false
            }
            if (persist) {
                remember(value)
                onChange(value)
            }
        }

        // The listener returns false and only arbitrates parent scrolling;
        // SeekBar remains responsible for click and accessibility semantics.
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        fun slider(
            label: String,
            max: Int,
            progress: Int,
            update: (Int) -> Unit,
        ) {
            val valueInput =
                EditText(activity).apply {
                    setText(String.format(Locale.ROOT, "%d", progress))
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setTextColor(textArgb)
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(4), 0, dp(4), 0)
                    background = background(surfaceArgb, 10f)
                }
            root.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = label
                            textSize = 10f
                            setTextColor(textArgb)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(valueInput, LinearLayout.LayoutParams(dp(64), dp(38)))
                },
            )
            val seek =
                ScrollSafeSeekBar(activity).apply {
                    // Rotary input scrolls the picker; it must never silently alter hue/brightness.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.max = max
                    this.progress = progress
                    progressTintList =
                        android.content.res.ColorStateList
                            .valueOf(initialArgb)
                    progressBackgroundTintList =
                        android.content.res.ColorStateList
                            .valueOf(borderArgb)
                    thumbTintList =
                        android.content.res.ColorStateList
                            .valueOf(initialArgb)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                view: SeekBar?,
                                value: Int,
                                fromUser: Boolean,
                            ) {
                                if (fromUser) {
                                    valueInput.setText(String.format(Locale.ROOT, "%d", value))
                                    update(value)
                                    refresh()
                                }
                            }

                            override fun onStartTrackingTouch(view: SeekBar?) = Unit

                            override fun onStopTrackingTouch(view: SeekBar?) = Unit
                        },
                    )
                }
            valueInput.setOnEditorActionListener { _, _, _ ->
                valueInput.text.toString().toIntOrNull()?.coerceIn(0, max)?.let { value ->
                    seek.progress = value
                    update(value)
                    refresh()
                }
                activity.currentFocus?.let { focus ->
                    (
                        activity.getSystemService(
                            Activity.INPUT_METHOD_SERVICE,
                        ) as? android.view.inputmethod.InputMethodManager
                    )?.hideSoftInputFromWindow(focus.windowToken, 0)
                }
                true
            }
            root.addView(seek)
        }
        hex.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (updating) return
                    ArgbColor.parse(s?.toString())?.let { value ->
                        Color.colorToHSV(value, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                        alpha = Color.alpha(value) / 255f
                        preview.background = background(value)
                        rgb.text =
                            activity.getString(
                                R.string.shared_color_rgba,
                                Color.red(value),
                                Color.green(value),
                                Color.blue(value),
                                Color.alpha(value),
                            )
                        onChange(value)
                    }
                }
            },
        )
        slider("Farbton", 360, hue.roundToInt()) { hue = it.toFloat() }
        slider("Sättigung", 100, (saturation * 100).roundToInt()) { saturation = it / 100f }
        slider("Helligkeit", 100, (brightness * 100).roundToInt()) { brightness = it / 100f }
        slider("Deckkraft / Alpha", 100, (alpha * 100).roundToInt()) { alpha = it / 100f }
        root.addView(
            TextView(activity).apply {
                setText(R.string.shared_color_presets_recent)
                textSize = 10f
                setTextColor(textArgb)
            },
        )
        root.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                (recentColors.toList() + presets.toList()).distinct().take(8).forEach { preset ->
                    addView(
                        android.view.View(activity).apply {
                            background = background(preset, 999f)
                            setOnClickListener {
                                Color.colorToHSV(preset, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2]
                                alpha =
                                    Color.alpha(preset) / 255f
                                refresh()
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(3) },
                    )
                }
            },
        )
        refresh(false)
        val scroll =
            ColorEditorRotaryScrollView(activity).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                scrollBarDefaultDelayBeforeFade = 250
                scrollBarFadeDuration = 250
                isVerticalFadingEdgeEnabled = false
                addView(root)
            }
        AlertDialog
            .Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setNeutralButton("Standard") { _, _ -> onReset() }
            .setNegativeButton("Fertig", null)
            .create()
            .apply {
                setOnShowListener { window?.setBackgroundDrawable(background(surfaceArgb, 24f)) }
                setOnDismissListener { onDismiss() }
                show()
            }
    }

    private fun remember(color: Int) {
        recentColors.remove(color)
        recentColors.addFirst(color)
        while (recentColors.size > 8) recentColors.removeLast()
    }

    private class ColorEditorRotaryScrollView(
        context: Activity,
    ) : ScrollView(context) {
        private val rotaryScrollFactor = ViewConfiguration.get(context).scaledVerticalScrollFactor
        private val rotaryFeedback = WearRotaryFeedback(this)

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            defaultFocusHighlightEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            requestFocus(View.FOCUS_DOWN)
            post { requestFocus(View.FOCUS_DOWN) }
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                val delta = (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * rotaryScrollFactor * 0.55f).roundToInt()
                val before = scrollY
                if (delta != 0) scrollBy(0, delta)
                rotaryFeedback.onScroll(event, scrollY - before)
                awakenScrollBars()
                return true
            }
            return super.dispatchGenericMotionEvent(event)
        }
    }
}
