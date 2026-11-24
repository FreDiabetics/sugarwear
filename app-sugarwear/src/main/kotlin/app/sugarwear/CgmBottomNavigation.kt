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

/** Shared SugarWear navigation: one pill at the end of each top-level page. */
internal enum class CgmNavTab(
    val title: String,
) {
    OVERVIEW("Übersicht"),
    CONNECTIONS("Verbindungen"),
    PROFILE("Profil"),
}

internal object CgmBottomNavigation {
    fun tabFor(activity: Activity): CgmNavTab? =
        when (activity) {
            is CgmWatchActivity -> CgmNavTab.OVERVIEW
            is CgmConnectionsActivity -> CgmNavTab.CONNECTIONS
            is CgmSettingsActivity -> CgmNavTab.PROFILE
            else -> null
        }

    /** Detail pages have no footer, including its margins and touch targets. */
    fun appendAtEnd(
        activity: Activity,
        page: View,
        palette: CgmAppearancePalette = CgmAppearanceStore(activity).load(),
        detailOpen: Boolean = false,
    ) {
        val selected = tabFor(activity) ?: return
        val root =
            when (page) {
                is ScrollView -> page.getChildAt(0) as? LinearLayout
                is LinearLayout -> page
                else -> null
            } ?: return
        if (root.orientation != LinearLayout.VERTICAL) return

        val existing = root.findViewWithTag<View>("sugarwear-nav-pill")
        if (detailOpen) {
            if (existing?.parent === root) root.removeView(existing)
            return
        }
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
                val color = palette.settingsArgb(if (active) CgmAppearanceRole.MENU_TEXT_PRIMARY else CgmAppearanceRole.MENU_TEXT_SECONDARY)
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
            if (selected == CgmNavTab.OVERVIEW) {
                addView(ImageView(activity).apply {
                    tag = "nav-fingerstick"
                    contentDescription = activity.getString(R.string.fingerstick_log)
                    setImageResource(R.drawable.ic_add)
                    val inset = 13.dp(activity)
                    setPadding(inset, inset, inset, inset)
                    imageTintList = ColorStateList.valueOf(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY))
                    isFocusable = true
                    setOnClickListener { activity.startActivity(Intent(activity, FingerstickActivity::class.java)) }
                }, LinearLayout.LayoutParams(48.dp(activity), 48.dp(activity)))
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
