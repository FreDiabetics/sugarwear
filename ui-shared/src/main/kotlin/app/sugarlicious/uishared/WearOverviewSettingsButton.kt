package app.sugarlicious.uishared

import android.content.res.ColorStateList
import android.widget.Button

/** Identical native overview action on both watch apps, using the shared settings surface. */
object WearOverviewSettingsButton {
    fun apply(button: Button, iconResource: Int, surface: Int, foreground: Int) {
        WearSettingsUi.actionButton(button, surface, foreground)
        button.background =
            android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF),
                WearSettingsUi.background(button.context, surface),
                null,
            )
        (button.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = WearSettingsUi.dp(button.context, 12)
        button.setText(R.string.shared_settings)
        button.includeFontPadding = false
        button.compoundDrawablePadding = WearSettingsUi.dp(button.context, 8)
        val icon = button.context.getDrawable(iconResource)?.mutate()
        val size = WearSettingsUi.dp(button.context, 20)
        icon?.setBounds(0, 0, size, size)
        button.setCompoundDrawablesRelative(icon, null, null, null)
        button.compoundDrawableTintList = ColorStateList.valueOf(foreground)
    }
}
