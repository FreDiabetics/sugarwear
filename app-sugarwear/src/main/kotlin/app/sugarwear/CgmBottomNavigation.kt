package app.sugarwear

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat

/**
 * The one shared SugarWear navigation surface. It is not shown on setup
 * or on alarm presentation: neither flow may be covered by ordinary tabs.
 */
internal enum class CgmNavTab(
    val title: String,
) {
    OVERVIEW("Übersicht"),
    CONNECTIONS("Verbindungen"),
    PROFILE("Profil"),
}

internal object CgmBottomNavigation {
    private val ACCENT = 0xFF22EB60.toInt()
    private val SURFACE = 0xFF0B0B0D.toInt()
    private val SELECTED_SURFACE = 0xFF183C25.toInt()
    private val INACTIVE = 0xFFB4B4BB.toInt()

    fun tabFor(activity: Activity): CgmNavTab? =
        when (activity) {
            is CgmSetupActivity, is CgmAlarmActivity -> null
            is CgmWatchActivity -> CgmNavTab.OVERVIEW
            is CgmConnectionsActivity, is CgmSystemStatusActivity, is CgmShareSettingsActivity -> CgmNavTab.CONNECTIONS
            is CgmSettingsComposeActivity -> CgmNavTab.PROFILE
            else -> null
        }

    fun footer(activity: Activity, selected: CgmNavTab): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            tag = "sugarwear-footer"
            setBackgroundColor(SURFACE)
            // Round-display lower corners need a narrower footer than the viewport.
            setPadding(22.dp(activity), 2.dp(activity), 22.dp(activity), 16.dp(activity))
            CgmNavTab.entries.forEach { tab ->
                val active = tab == selected
                val color = if (active) ACCENT else INACTIVE
                val button =
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        minimumHeight = 52.dp(activity)
                        tag = "nav-" + tab.name.lowercase()
                        isSelected = active
                        isClickable = true
                        isFocusable = true
                        contentDescription = tab.title + if (active) ", ausgewählt" else ", öffnen"
                        background =
                            GradientDrawable().apply {
                                setColor(if (active) SELECTED_SURFACE else SURFACE)
                                cornerRadius = 16.dp(activity).toFloat()
                            }
                        addView(
                            ImageView(activity).apply {
                                setImageResource(
                                    when (tab) {
                                        CgmNavTab.OVERVIEW -> R.drawable.ic_nav_overview
                                        CgmNavTab.CONNECTIONS -> R.drawable.ic_nav_connections
                                        CgmNavTab.PROFILE -> R.drawable.ic_nav_profile
                                    },
                                )
                                imageTintList = ColorStateList.valueOf(color)
                            },
                            LinearLayout.LayoutParams(17.dp(activity), 17.dp(activity)),
                        )
                        addView(
                            TextView(activity).apply {
                                text = tab.title
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                                setTextColor(color)
                                maxLines = 1
                                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                                    this,
                                    7,
                                    10,
                                    1,
                                    TypedValue.COMPLEX_UNIT_SP,
                                )
                                app.sugarlicious.uishared.SystemTypography
                                    .apply(this, active)
                            },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20.dp(activity)),
                        )
                        setOnClickListener {
                            val destination =
                                when (tab) {
                                    CgmNavTab.OVERVIEW -> CgmWatchActivity::class.java
                                    CgmNavTab.CONNECTIONS -> CgmConnectionsActivity::class.java
                                    CgmNavTab.PROFILE -> CgmSettingsActivity::class.java
                                }
                            if (activity.javaClass != destination) {
                                activity.startActivity(
                                    Intent(activity, destination).addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                    ),
                                )
                                if (activity !is CgmWatchActivity) activity.finish()
                            }
                        }
                    }
                addView(
                    button,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 1.dp(activity)
                        marginEnd = 1.dp(activity)
                    },
                )
            }
        }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
