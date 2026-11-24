package app.sugarwear

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.sugarlicious.uishared.SystemTypography
import app.sugarlicious.uishared.WearSettingsUi

/** Scrolls the title, content and actions together on small round screens and above the IME. */
internal fun Activity.showCgmSettingsDialog(
    title: String,
    content: View? = null,
    message: String? = null,
    cancel: String? = getString(android.R.string.cancel),
    confirm: String? = null,
    secure: Boolean = false,
    onConfirm: ((Dialog) -> Unit)? = null,
): Dialog {
    val palette = CgmAppearanceStore(this).load()
    val foreground = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
    val surface = palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
        WearSettingsUi.pagePadding(this)
        addView(WearSettingsUi.pageHeader(context, title, foreground))
        content?.let {
            it.background = WearSettingsUi.background(context, surface)
            addView(it)
        }
        message?.let {
            addView(TextView(context).apply {
                text = it
                textSize = 15f
                setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY))
                SystemTypography.apply(this, false)
            })
        }
    }
    fun action(label: String, color: Int, click: () -> Unit) {
        page.addView(Button(this).apply {
            text = label
            WearSettingsUi.actionButton(this, surface, color)
            setOnClickListener { click() }
        })
    }
    if (confirm != null && onConfirm != null) {
        action(confirm, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY)) { onConfirm(dialog) }
    }
    cancel?.let { action(it, foreground) { dialog.dismiss() } }
    dialog.setContentView(CgmEdgeFadeScrollView(this).apply {
        setBackgroundColor(Color.BLACK)
        isFillViewport = true
        addView(page)
    }.applyCgmEdgeFade())
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.BLACK))
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (secure) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    dialog.show()
    return dialog
}
