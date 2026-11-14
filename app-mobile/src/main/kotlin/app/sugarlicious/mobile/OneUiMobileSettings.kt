package app.sugarlicious.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.Switch
import kotlin.math.roundToInt

/** Public device widgets retain DeviceVendor's installed theme and accessibility. */
internal fun mobileSettingsSwitch(context: Context, checked: Boolean, onChanged: (Boolean) -> Unit): Switch =
    Switch(mobileSettingsControlContext(context)).apply {
        showText = false
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        styleMobileSettingsSwitch(this)
        isChecked = checked
        setOnCheckedChangeListener { _, selected -> onChanged(selected) }
    }

/** Native interaction/accessibility, with the reference's 16dp knob inside a 20dp track. */
internal fun styleMobileSettingsSwitch(view: Switch) =
    with(view) {
        val density = resources.displayMetrics.density
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val accent =
            app.sugarlicious.mobile.ui.theme
                .mobileMenuAccentArgb(context, dark)
        val inactive = if (dark) Color.rgb(100, 100, 106) else Color.rgb(180, 180, 186)
        trackDrawable =
            object : GradientDrawable() {
                override fun getPadding(padding: android.graphics.Rect): Boolean {
                    // Match thumb optical insets so Switch keeps the full painted track height.
                    val inset = (2f * density).roundToInt()
                    padding.set(inset, inset, inset, inset)
                    return true
                }
            }.apply {
                cornerRadius = 10f * density
                setColor(inactive)
                setSize((32f * density).roundToInt(), (20f * density).roundToInt())
            }
        thumbDrawable =
            InsetDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setSize((16f * density).roundToInt(), (16f * density).roundToInt())
                },
                0,
                (2f * density).roundToInt(),
                0,
                (2f * density).roundToInt(),
            )
        switchMinWidth = (32f * density).roundToInt()
        thumbTextPadding = 0
        switchPadding = 0
        splitTrack = false
        trackTintList =
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    androidx.core.graphics.ColorUtils
                        .setAlphaComponent(inactive, 100),
                    accent,
                    inactive,
                ),
            )
        thumbTintList =
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(0xAAFFFFFF.toInt(), Color.WHITE),
            )
    }

internal fun mobileSettingsControlContext(context: Context, dark: Boolean = !app.sugarlicious.mobile.ui.theme.SugarliciousColors.palette.isLight): Context {
    val configuration =
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    val themeRes =
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.R.style.Theme_DeviceDefault_DayNight
        } else if (dark) {
            android.R.style.Theme_DeviceDefault
        } else {
            android.R.style.Theme_DeviceDefault_Light
        }
    return ContextThemeWrapper(context.createConfigurationContext(configuration), themeRes).apply {
        theme.applyStyle(R.style.MobileSettingsControls, true)
    }
}

/** Touch gestures batch writes; keyboard/accessibility changes have no touch-end callback. */
internal class SettingsSliderInteraction(
    private val onValue: (Int) -> Unit,
    private val onCommit: () -> Unit,
) {
    private var tracking = false

    fun started() {
        tracking = true
    }

    fun changed(value: Int, fromUser: Boolean) {
        if (!fromUser) return
        onValue(value)
        if (!tracking) onCommit()
    }

    fun stopped() {
        tracking = false
        onCommit()
    }
}

internal object MobileSettingsStyle {
    fun page(light: Boolean): Int = if (light) Color.rgb(242, 242, 242) else Color.BLACK

    fun card(light: Boolean): Int = if (light) Color.WHITE else Color.rgb(28, 28, 28)

    fun text(light: Boolean): Int = if (light) Color.rgb(37, 37, 37) else Color.WHITE

    fun secondary(light: Boolean): Int = if (light) Color.rgb(102, 102, 102) else Color.WHITE

    fun divider(light: Boolean): Int = if (light) Color.rgb(224, 224, 224) else Color.rgb(52, 52, 52)
}
