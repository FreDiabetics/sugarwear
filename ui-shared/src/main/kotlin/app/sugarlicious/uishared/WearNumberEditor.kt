package app.sugarlicious.uishared

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Wear-only value entry: natural text heights and a round-safe scrollable surface. */
internal object WearNumberEditor {
    fun show(
        activity: Activity,
        title: String,
        current: String,
        inputType: Int,
        validate: (String) -> String?,
        onValue: (String) -> Unit,
        onDismiss: () -> Unit,
    ): Dialog {
        fun dp(value: Int) = WearSettingsUi.dp(activity, value)
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                WearSettingsUi.pagePadding(this)
                setBackgroundColor(Color.BLACK)
            }
        root.addView(WearSettingsUi.header(activity, title, Color.WHITE) { dialog.dismiss() })
        val input =
            EditText(activity).apply {
                tag = "wear_number_input"
                this.inputType = inputType
                setSingleLine(true)
                setText(current)
                setSelectAllOnFocus(true)
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minHeight = dp(56)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = WearSettingsUi.background(activity, Color.rgb(32, 32, 34))
                SystemTypography.apply(this, false)
            }
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        val error =
            TextView(activity).apply {
                tag = "wear_number_error"
                textSize = 12f
                setTextColor(0xFFFFB4AB.toInt())
                setPadding(dp(8), dp(4), dp(8), dp(4))
                SystemTypography.apply(this, false)
                visibility = android.view.View.GONE
                accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
        root.addView(error)

        fun commit() {
            val raw = input.text.toString()
            val issue = validate(raw)
            if (issue != null) {
                error.text = issue
                error.visibility = android.view.View.VISIBLE
            } else {
                onValue(raw)
                dialog.dismiss()
            }
        }
        root.addView(
            TextView(activity).apply {
                tag = "wear_number_save"
                setText(R.string.shared_apply)
                textSize = 15f
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(10), dp(12), dp(10), dp(12))
                val accent = WearSettingsUi.accent(activity)
                setTextColor(if (Color.luminance(accent) > .45f) Color.BLACK else Color.WHITE)
                background = WearSettingsUi.background(activity, accent)
                SystemTypography.apply(this, true)
                setOnClickListener { commit() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) },
        )
        input.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commit()
                true
            } else {
                false
            }
        }
        val scroll =
            WearSettingsScrollView(activity).apply {
                tag = "wear_number_scroll"
                isFillViewport = true
                addView(root)
            }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        dialog.setContentView(scroll)
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.black)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        scroll.requestFocus()
        return dialog
    }
}
