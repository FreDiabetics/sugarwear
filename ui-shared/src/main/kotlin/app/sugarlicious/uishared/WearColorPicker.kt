package app.sugarlicious.uishared

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.sugarlicious.model.ArgbColor
import kotlin.math.roundToInt

/** Full-screen, round-safe palette and HSV editor shared by both Wear apps. */
internal object WearColorPicker {
    fun show(activity: Activity, title: String, initial: Int, default: Int, changed: (Int) -> Unit, reset: () -> Unit, dismissed: () -> Unit): Dialog {
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var alpha = Color.alpha(initial)
        var syncing = false
        val accent = WearSettingsUi.accent(activity)
        val surface = Color.rgb(36, 36, 39)

        fun dp(value: Int) = WearSettingsUi.dp(activity, value)

        fun color() = Color.HSVToColor(alpha, hsv)

        fun label(value: String, size: Float = 15f) =
            TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                SystemTypography.apply(this, false)
            }

        fun params() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                WearSettingsUi.pagePadding(this)
                setBackgroundColor(Color.BLACK)
            }
        val header = WearSettingsUi.header(activity, title, Color.WHITE) { dialog.dismiss() }
        val preview =
            View(activity).apply {
                tag = "wear_color_preview"
                background = WearSettingsUi.swatch(activity, initial)
            }
        header.addView(
            preview,
            LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                marginStart = dp(4)
            },
        )
        root.addView(header, params())
        val tabs = LinearLayout(activity).apply { gravity = Gravity.CENTER }
        root.addView(tabs, params())
        val palette = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val spectrum =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
        root.addView(palette, params())
        root.addView(spectrum, params())
        val hex =
            EditText(activity).apply {
                tag = "wear_color_hex"
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(9))
                setTextColor(Color.WHITE)
                textSize = 15f
                SystemTypography.apply(this, false)
                minHeight = dp(48)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = WearSettingsUi.background(activity, surface)
            }
        val sliders = mutableListOf<SeekBar>()
        val values = mutableListOf<TextView>()

        fun refresh(persist: Boolean) {
            syncing = true
            preview.background = WearSettingsUi.swatch(activity, color())
            hex.setText(ArgbColor.format(color()))
            listOf(hsv[0].roundToInt(), (hsv[1] * 100).roundToInt(), (hsv[2] * 100).roundToInt(), (alpha / 255f * 100).roundToInt()).forEachIndexed { index, progress ->
                sliders.getOrNull(index)?.progress = progress
                values.getOrNull(index)?.text = String.format(java.util.Locale.getDefault(), "%d", progress)
            }
            syncing = false
            if (persist) changed(color())
        }

        fun choose(value: Int) {
            Color.colorToHSV(value, hsv)
            alpha = Color.alpha(value)
            refresh(true)
        }

        fun commitHex(): Boolean {
            val value = ArgbColor.parse(hex.text.toString())
            if (value == null) {
                hex.error = "#RRGGBB oder #AARRGGBB"
                return false
            }
            if (value != color()) choose(value)
            return true
        }
        listOf("Palette", "Spektrum").forEachIndexed { index, text ->
            tabs.addView(
                label(text, 14f).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    background = WearSettingsUi.background(activity, surface)
                    isSelected = index == 0
                    setTextColor(if (index == 0) accent else Color.WHITE)
                    setOnClickListener {
                        repeat(tabs.childCount) { tabIndex ->
                            (tabs.getChildAt(tabIndex) as TextView).apply {
                                isSelected = tabIndex == index
                                setTextColor(if (isSelected) accent else Color.WHITE)
                            }
                        }
                        palette.visibility = if (index == 0) View.VISIBLE else View.GONE
                        spectrum.visibility = if (index == 1) View.VISIBLE else View.GONE
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                },
            )
        }
        val presets = intArrayOf(Color.WHITE, Color.LTGRAY, Color.BLACK, 0xFFFF4747.toInt(), 0xFFFF914D.toInt(), 0xFFFFD84D.toInt(), 0xFFA9F545.toInt(), 0xFF37E88C.toInt(), 0xFF22DCE5.toInt(), 0xFF4D9DFF.toInt(), 0xFF6C64FF.toInt(), 0xFFC46BFF.toInt(), 0xFFFF62C4.toInt(), 0xFFF3C7B4.toInt(), 0xFF9DBBC5.toInt())
        presets.toList().chunked(3).forEach { colors ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER }
            colors.forEach { preset ->
                row.addView(
                    View(activity).apply {
                        contentDescription = ArgbColor.format(preset)
                        background = WearSettingsUi.swatch(activity, preset)
                        setOnClickListener { choose(preset) }
                    },
                    LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) },
                )
            }
            palette.addView(row, params())
        }
        listOf("Farbton", "Sättigung", "Helligkeit", "Deckkraft").forEachIndexed { index, titleText ->
            val card =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = WearSettingsUi.background(activity, surface)
                    setPadding(dp(12), dp(10), dp(12), dp(4))
                }
            val value =
                label("", 14f).apply {
                    setTextColor(accent)
                    gravity = Gravity.END
                }
            values += value
            card.addView(
                LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(titleText, 15f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(value)
                },
            )

            fun updateChannel(value: Int) {
                when (index) {
                    0 -> hsv[0] = value.toFloat()
                    1 -> hsv[1] = value / 100f
                    2 -> hsv[2] = value / 100f
                    3 -> alpha = (value * 2.55f).roundToInt()
                }
                refresh(true)
            }
            val slider =
                ScrollSafeSeekBar(activity).apply {
                    tag = "wear_color_slider_$index"
                    max = if (index == 0) 360 else 100
                    WearSettingsUi.slider(this, accent, Color.DKGRAY, titleText)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                                if (fromUser && !syncing) updateChannel(value)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                        },
                    )
                }
            sliders += slider
            card.addView(WearSettingsUi.inlineSlider(slider, ::updateChannel))
            spectrum.addView(card, params())
        }
        root.addView(hex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        hex.setOnEditorActionListener { _, _, _ ->
            commitHex()
            true
        }
        val actions = LinearLayout(activity)
        actions.addView(
            label("Kopieren", 14f).apply {
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = WearSettingsUi.background(activity, surface)
                setOnClickListener { if (commitHex()) (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Farbe", ArgbColor.format(color()))) }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            label("↺", 24f).apply {
                tag = "wear_color_reset"
                contentDescription = "Farbe zurücksetzen"
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = WearSettingsUi.background(activity, surface)
                setOnClickListener {
                    choose(default)
                    reset()
                }
            },
            LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) },
        )
        root.addView(actions, params())
        root.addView(
            label("Fertig").apply {
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setTextColor(if (Color.luminance(accent) > .45f) Color.BLACK else Color.WHITE)
                background = WearSettingsUi.background(activity, accent)
                setOnClickListener { if (commitHex()) dialog.dismiss() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) },
        )
        val scroll =
            WearSettingsScrollView(activity).apply {
                isFillViewport = true
                addView(root)
            }
        dialog.setContentView(scroll)
        dialog.setOnDismissListener { dismissed() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.black)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        refresh(false)
        scroll.requestFocus()
        scroll.scrollTo(0, 0)
        return dialog
    }
}
