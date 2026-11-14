package app.sugarlicious.mobile

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors

/** User SVG foregrounds remain black; only their separate circular backgrounds are colored. */
internal fun settingsCategoryIcon(context: Context, key: String, title: String): ImageView? {
    val (asset, role) =
        when (key) {
            "general" -> R.drawable.settings_ic_general to SugarliciousColorRole.SETTINGS_GENERAL
            "glucose_ranges" -> R.drawable.settings_ic_glucose_units to SugarliciousColorRole.SETTINGS_GLUCOSE
            "overview_graphs" -> R.drawable.settings_ic_graph to SugarliciousColorRole.SETTINGS_GRAPH
            "appearance" -> R.drawable.settings_ic_colors to SugarliciousColorRole.SETTINGS_APPEARANCE
            "notifications" -> R.drawable.settings_ic_notification to SugarliciousColorRole.SETTINGS_NOTIFICATION
            "data" -> R.drawable.settings_ic_data to SugarliciousColorRole.SETTINGS_DATA
            "diagnostics" -> R.drawable.settings_ic_diagnosis to SugarliciousColorRole.SETTINGS_DIAGNOSIS
            "about" -> R.drawable.settings_ic_about to SugarliciousColorRole.SETTINGS_ABOUT
            else -> return null
        }
    val background =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(SugarliciousColors.argb(role))
        }
    val foreground = checkNotNull(ContextCompat.getDrawable(context, asset)).mutate()
    return ImageView(context).apply {
        contentDescription = title
        setImageDrawable(LayerDrawable(arrayOf(background, foreground)))
    }
}
