package app.sugarwear

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels

internal enum class CgmSettingsSection(
    val title: String,
    val summary: String,
) {
    DISPLAY("Anzeige", "Farben und Darstellung"),
    PERMISSIONS("Berechtigungen", "Bluetooth, Benachrichtigungen und Systemzugriff"),
    ALARMS("Alarme", "Glukosealarme und notwendige Systemrechte"),
    SUGARWEAR("Sensor", "Verbinden, trennen und Datenempfang pausieren"),
    VIGIL("Vigil", "Watchface"),
    ABOUT("Über", "SugarWear"),
}

@dagger.hilt.android.AndroidEntryPoint
class CgmSettingsActivity : CgmSettingsComposeActivity() {
    private val access by viewModels<CgmSettingsAccessViewModel>()
    private val navigation by viewModels<CgmSettingsNavigationViewModel>()
    private val aboutExpanded get() = navigation.state.value.aboutExpanded
    private lateinit var pageRoot: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(navigation.state, render = ::render)
        bindSettingsState(access.state, render = ::render)

        render()
    }

    override fun onResume() {
        super.onResume()
        access.refresh()
        if (::pageRoot.isInitialized) render()
    }

    private fun render() {
        if (!settingsReady(access.state.value, { access.refresh() })) return
        val palette = checkNotNull(access.state.value.snapshot).palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val state = checkNotNull(access.state.value.snapshot).sensor

        val restoreScrollY = if (::scrollView.isInitialized) scrollView.scrollY else 0
        pageRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(background)
                addView(topBar(palette), fullWidth())
                addView(
                    infoCard(
                        "Sensorempfang",
                        if (state.collectorEnabled) "Aktiv" else "Inaktiv",
                        state.lastReading?.let { "${it.glucoseMgDl.toInt()} · ${relativeAge(it.timestampEpochMs)}" }
                            ?: state.lastError?.safeMessage
                            ?: "Noch kein gültiger Sensorwert",
                        palette,
                    ),
                    cardParams(top = 5),
                )
            }

        CgmSettingsSection.entries.toList().chunked(3).forEach { sections ->
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
                                render()
                            } else {
                                openSection(section)
                            }
                        }.apply { tag = "settings-category-${section.name.lowercase()}" }
                }
            pageRoot.addView(
                app.sugarlicious.uishared.WearSettingsUi
                    .navigationGroup(this, rows, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                cardParams(top = 10),
            )
        }
        if (aboutExpanded) {
            pageRoot.addView(
                infoCard(
                    "SugarWear",
                    app.sugarlicious.uishared.AppReleaseInfo
                        .summary(this, app.sugarlicious.uishared.AppReleaseInfo.Product.COLLECTOR),
                    "Eigenständiger Sensorempfang auf der Watch.",
                    palette,
                ),
                cardParams(),
            )
        }

        if (!::scrollView.isInitialized) {
            scrollView =
                CgmEdgeFadeScrollView(this)
                    .apply {
                        isFillViewport = true
                    }.applyCgmEdgeFade()
            showSettingsView(scrollView)
        } else {
            scrollView.removeAllViews()
        }
        scrollView.setBackgroundColor(background)
        scrollView.addView(pageRoot)
        scrollView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                    val maxScroll = (pageRoot.measuredHeight - scrollView.height).coerceAtLeast(0)
                    scrollView.scrollTo(0, restoreScrollY.coerceAtMost(maxScroll))
                    return true
                }
            },
        )
    }

    private fun openSection(section: CgmSettingsSection) {
        val intent =
            when (section) {
                CgmSettingsSection.DISPLAY -> Intent(this, CgmAppearanceActivity::class.java)
                CgmSettingsSection.PERMISSIONS -> Intent(this, CgmPermissionsActivity::class.java)
                CgmSettingsSection.ALARMS -> Intent(this, CgmAlarmSettingsActivity::class.java)
                CgmSettingsSection.SUGARWEAR -> Intent(this, CgmSystemStatusActivity::class.java)
                CgmSettingsSection.VIGIL -> Intent(this, CgmDirectToWatchSettingsActivity::class.java)
                CgmSettingsSection.ABOUT -> return
            }
        startActivity(intent)
    }

    private fun topBar(palette: CgmAppearancePalette) = cgmSettingsHeader("Einstellungen", palette)

    private fun infoCard(
        title: String,
        value: String,
        detail: String,
        palette: CgmAppearancePalette,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp, 10.dp, 13.dp, 10.dp)
            background = rounded(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), 18f)
            addView(text(title, 8f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true).apply { letterSpacing = 0.08f })
            addView(text(value, 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
            addView(text(detail, 8.5f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
        }

    private fun relativeAge(timestamp: Long): String {
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
        return if (minutes == 0L) "jetzt" else "vor $minutes min"
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        includeFontPadding = false
        text = value
        textSize =
            app.sugarlicious.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
        app.sugarlicious.uishared.SystemTypography
            .apply(this, bold)
    }

    private fun rounded(
        fill: Int,
        stroke: Int,
        radiusDp: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(0, stroke)
        cornerRadius = maxOf(radiusDp, 26f) * resources.displayMetrics.density
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardParams(top: Int = 3) =
        fullWidth().apply {
            topMargin = top.dp
            bottomMargin = 3.dp
        }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
