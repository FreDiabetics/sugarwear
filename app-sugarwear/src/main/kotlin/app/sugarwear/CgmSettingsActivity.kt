package app.sugarwear

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels

/** The profile is the in-app navigation home, not a second set of settings. */
internal enum class CgmSettingsSection(
    val title: String,
    val summary: String,
) {
    ALARMS("Alarme", "Glukosealarme, Ruhemodi und Wiederholung"),
    IN_APP("In-App", "Glukose, Trend, Graph und Darstellung"),
    TILES("Tiles", "Glukose- und Graph-Tiles"),
    VIGIL("Vigil", "Watchface und dessen Unterkategorien"),
    PERMISSIONS("Berechtigungen", "Bluetooth, Benachrichtigungen und Systemzugriff"),
    ABOUT("Über", "SugarWear"),
}

@dagger.hilt.android.AndroidEntryPoint
class CgmSettingsActivity : CgmSettingsComposeActivity() {
    private val access by viewModels<CgmSettingsAccessViewModel>()
    private val navigation by viewModels<CgmSettingsNavigationViewModel>()
    private val aboutExpanded get() = navigation.state.value.aboutExpanded
    private var scrollView: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(navigation.state, render = ::render)
        bindSettingsState(access.state, render = ::render)
        render()
    }

    override fun onResume() {
        super.onResume()
        access.refresh()
        render()
    }

    private fun render() {
        if (!settingsReady(access.state.value, { access.refresh() })) return
        val palette = checkNotNull(access.state.value.snapshot).palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val restoreScrollY = scrollView?.scrollY ?: 0
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(background)
                addView(cgmSettingsHeader("Profil", palette), fullWidth())
            }

        val groups =
            listOf(
                "ALARME" to listOf(CgmSettingsSection.ALARMS),
                "DARSTELLUNG" to listOf(CgmSettingsSection.IN_APP, CgmSettingsSection.TILES, CgmSettingsSection.VIGIL),
                "SYSTEM" to listOf(CgmSettingsSection.PERMISSIONS, CgmSettingsSection.ABOUT),
            )
        groups.forEach { (title, sections) ->
            root.addView(
                text(title, 9f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true),
                cardParams(13),
            )
            val rows =
                sections.map { section ->
                    app.sugarlicious.uishared.WearSettingsUi
                        .navigationRow(
                            this,
                            section.title,
                            null,
                            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                        ) {
                            if (section == CgmSettingsSection.ABOUT) {
                                navigation.toggleAbout()
                            } else {
                                openSection(section)
                            }
                        }.apply {
                            tag = "settings-category-" + section.name.lowercase()
                        }
                }
            root.addView(
                app.sugarlicious.uishared.WearSettingsUi.navigationGroup(
                    this,
                    rows,
                    palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                ),
                cardParams(5),
            )
        }
        if (aboutExpanded) {
            root.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(13.dp, 12.dp, 13.dp, 12.dp)
                    background =
                        GradientDrawable().apply {
                            setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
                            cornerRadius = 22.dp.toFloat()
                        }
                    addView(text("SugarWear", 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                    addView(
                        text(
                            app.sugarlicious.uishared.AppReleaseInfo.summary(
                                this@CgmSettingsActivity,
                                app.sugarlicious.uishared.AppReleaseInfo.Product.COLLECTOR,
                            ),
                            9f,
                            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                        ),
                    )
                },
                cardParams(6),
            )
        }
        scrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    setBackgroundColor(background)
                    addView(root)
                    viewTreeObserver.addOnPreDrawListener(
                        object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                viewTreeObserver.removeOnPreDrawListener(this)
                                val maxScroll = (root.measuredHeight - height).coerceAtLeast(0)
                                scrollTo(0, restoreScrollY.coerceAtMost(maxScroll))
                                return true
                            }
                        },
                    )
                }.applyCgmEdgeFade()
        showSettingsView(checkNotNull(scrollView))
    }

    private fun openSection(section: CgmSettingsSection) {
        val intent =
            when (section) {
                CgmSettingsSection.ALARMS -> Intent(this, CgmAlarmSettingsActivity::class.java)
                CgmSettingsSection.IN_APP ->
                    Intent(this, CgmAppearanceActivity::class.java)
                        .putExtra(CgmAppearanceActivity.EXTRA_SCOPE, CgmAppearanceActivity.SCOPE_IN_APP)
                CgmSettingsSection.TILES ->
                    Intent(this, CgmAppearanceActivity::class.java)
                        .putExtra(CgmAppearanceActivity.EXTRA_SCOPE, CgmAppearanceActivity.SCOPE_TILES)
                CgmSettingsSection.VIGIL -> Intent(this, CgmDirectToWatchSettingsActivity::class.java)
                CgmSettingsSection.PERMISSIONS -> Intent(this, CgmPermissionsActivity::class.java)
                CgmSettingsSection.ABOUT -> return
            }
        startActivity(intent)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            includeFontPadding = false
            textSize =
                app.sugarlicious.uishared.WearSettingsUi
                    .textSize(size)
            setTextColor(color)
            app.sugarlicious.uishared.SystemTypography
                .apply(this, bold)
        }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardParams(top: Int) = fullWidth().apply { topMargin = top.dp }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
