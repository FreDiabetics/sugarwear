package app.sugarwear

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Shared SugarWear navigation: one pill at the end of each scrollable menu. */
internal enum class CgmNavTab(
    val title: String,
) {
    OVERVIEW("Übersicht"),
    CONNECTIONS("Verbindungen"),
    PROFILE("Profil"),
}

internal object CgmBottomNavigation {
    private val ACCENT = 0xFF22EB60.toInt()
    private val INACTIVE = 0xFFB4B4BB.toInt()

    fun tabFor(activity: Activity): CgmNavTab? =
        when (activity) {
            is CgmSetupActivity, is CgmAlarmActivity -> null
            is CgmWatchActivity -> CgmNavTab.OVERVIEW
            is CgmConnectionsActivity, is CgmSystemStatusActivity, is CgmShareSettingsActivity -> CgmNavTab.CONNECTIONS
            is CgmSettingsComposeActivity -> CgmNavTab.PROFILE
            else -> null
        }

    /** Appends the same three destinations after all content, never over the viewport. */
    fun appendAtEnd(
        activity: Activity,
        page: View,
        selected: CgmNavTab,
        palette: CgmAppearancePalette = CgmAppearanceStore(activity).load(),
    ) {
        val root =
            when (page) {
                is ScrollView -> page.getChildAt(0) as? LinearLayout
                is LinearLayout -> page
                else -> null
            } ?: return
        if (root.orientation != LinearLayout.VERTICAL) return

        val existing = root.findViewWithTag<View>("sugarwear-nav-pill")
        if (existing?.parent === root) {
            applyPillPalette(activity, existing, palette)
            if (root.getChildAt(root.childCount - 1) === existing) return
            root.removeView(existing)
        }
        root.addView(
            existing ?: pill(activity, selected, palette),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 14.dp(activity)
                bottomMargin = 28.dp(activity)
            },
        )
    }

    fun pill(
        activity: Activity,
        selected: CgmNavTab,
        palette: CgmAppearancePalette = CgmAppearanceStore(activity).load(),
    ): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            tag = "sugarwear-nav-pill"
            setPadding(4.dp(activity), 4.dp(activity), 4.dp(activity), 4.dp(activity))
            applyPillPalette(activity, this, palette)
            CgmNavTab.entries.forEach { tab ->
                val active = tab == selected
                val color = if (active) ACCENT else INACTIVE
                val button =
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        minimumHeight = 48.dp(activity)
                        minimumWidth = 48.dp(activity)
                        tag = "nav-" + tab.name.lowercase()
                        isSelected = active
                        isClickable = true
                        isFocusable = true
                        contentDescription = tab.title + if (active) ", ausgewählt" else ", öffnen"
                        setBackgroundColor(Color.TRANSPARENT)
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
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
                            LinearLayout.LayoutParams(22.dp(activity), 22.dp(activity)),
                        )
                        // Names are only shown when the user deliberately holds an icon.
                        setOnLongClickListener {
                            Toast.makeText(activity, tab.title, Toast.LENGTH_SHORT).show()
                            true
                        }
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
                addView(button, LinearLayout.LayoutParams(48.dp(activity), 48.dp(activity)))
            }
        }

    private fun applyPillPalette(activity: Activity, pill: View, palette: CgmAppearancePalette) {
        pill.background =
            GradientDrawable().apply {
                setColor(palette.argb(CgmAppearanceRole.MENU_SURFACE))
                navigationPillBorderArgb(palette)?.let { setStroke(1.dp(activity), it) }
                cornerRadius = 999f
            }
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}

/** Uses the same configurable contour as the glucose/graph card, not menu chrome. */
internal fun navigationPillBorderArgb(palette: CgmAppearancePalette): Int? =
    palette.argb(CgmAppearanceRole.MENU_BORDER).takeIf { Color.alpha(it) != 0 }
