package app.aapswear.g7watch

import android.app.Activity
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

internal enum class G7SettingsSection(
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

class G7SettingsActivity : Activity() {
    private var aboutExpanded = false
    private lateinit var pageRoot: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::pageRoot.isInitialized) render()
    }

    private fun render() {
        val palette = G7AppearanceStore(this).load()
        val background = palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)
        applyG7SystemChrome(window, background)
        val state = G7SensorStateStore(this).read()

        val restoreScrollY = if (::scrollView.isInitialized) scrollView.scrollY else 0
        pageRoot =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(18.dp, 32.dp, 18.dp, 56.dp)
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

        G7SettingsSection.entries.forEach { section -> addSection(section, palette) }

        if (!::scrollView.isInitialized) {
            scrollView =
                G7EdgeFadeScrollView(this)
                    .apply {
                        isFillViewport = true
                    }.applyG7EdgeFade()
            setContentView(scrollView)
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

    private fun addSection(
        section: G7SettingsSection,
        palette: G7AppearancePalette,
    ) {
        val isAbout = section == G7SettingsSection.ABOUT
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (isAbout && aboutExpanded) View.VISIBLE else View.GONE
                if (isAbout) {
                    addView(
                        infoCard(
                            "SugarWear",
                            app.aapswear.uishared.AppReleaseInfo
                                .summary(this@G7SettingsActivity, app.aapswear.uishared.AppReleaseInfo.Product.COLLECTOR),
                            "Eigenständiger Sensorempfang auf der Watch.",
                            palette,
                        ),
                        cardParams(),
                    )
                }
            }
        val chevron =
            text(
                if (isAbout &&
                    aboutExpanded
                ) {
                    "⌄"
                } else {
                    "›"
                },
                20f,
                palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY),
                true,
            ).apply {
                gravity = Gravity.CENTER
            }
        val header =
            LinearLayout(this).apply {
                tag = "settings-category-${section.name.lowercase()}"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = 58.dp
                setPadding(13.dp, 8.dp, 8.dp, 8.dp)
                background = rounded(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 20f)
                isClickable = true
                isFocusable = true
                addView(
                    LinearLayout(this@G7SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(section.title, 11f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(chevron, LinearLayout.LayoutParams(28.dp, 38.dp))
                setOnClickListener {
                    if (isAbout) {
                        aboutExpanded = !aboutExpanded
                        content.visibility = if (aboutExpanded) View.VISIBLE else View.GONE
                        chevron.text = if (aboutExpanded) "⌄" else "›"
                    } else {
                        openSection(section)
                    }
                }
            }
        pageRoot.addView(header, cardParams(top = 6))
        pageRoot.addView(content, fullWidth())
    }

    private fun openSection(section: G7SettingsSection) {
        val intent =
            when (section) {
                G7SettingsSection.DISPLAY -> Intent(this, G7AppearanceActivity::class.java)
                G7SettingsSection.PERMISSIONS -> Intent(this, G7PermissionsActivity::class.java)
                G7SettingsSection.ALARMS -> Intent(this, G7AlarmSettingsActivity::class.java)
                G7SettingsSection.SUGARWEAR -> Intent(this, G7SystemStatusActivity::class.java)
                G7SettingsSection.VIGIL -> Intent(this, G7DirectToWatchSettingsActivity::class.java)
                G7SettingsSection.ABOUT -> return
            }
        startActivity(intent)
    }

    private fun topBar(palette: G7AppearancePalette) = g7SettingsHeader("Einstellungen", palette)

    private fun infoCard(
        title: String,
        value: String,
        detail: String,
        palette: G7AppearancePalette,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp, 10.dp, 13.dp, 10.dp)
            background = rounded(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 18f)
            addView(text(title, 8f, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), true).apply { letterSpacing = 0.08f })
            addView(text(value, 12f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true))
            addView(text(detail, 8.5f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_SECONDARY)))
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
            app.aapswear.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
        app.aapswear.uishared.SystemTypography
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
