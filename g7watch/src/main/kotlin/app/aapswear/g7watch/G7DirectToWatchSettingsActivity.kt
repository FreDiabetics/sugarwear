package app.aapswear.g7watch

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import app.aapswear.model.AppearanceControlContract
import app.aapswear.model.AppearanceMode
import app.aapswear.model.AppearanceTerminology
import app.aapswear.model.GlucoseTrendSizing
import app.aapswear.model.GlucoseUnit
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.uishared.SharedColorEditor
import app.aapswear.uishared.SharedNumberEditor
import kotlin.math.roundToInt

class G7DirectToWatchSettingsActivity : Activity() {
    private lateinit var settings: G7DirectToWatchSettingsStore
    private lateinit var appearance: G7AppearanceStore
    private var mode = AppearanceMode.DARK
    private lateinit var scrollView: ScrollView
    private lateinit var pageRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = G7DirectToWatchSettingsStore(this)
        appearance = G7AppearanceStore(this)
        mode = settings.activeAppearanceMode(appearance.activeMode())
        settings.saveActiveAppearanceMode(mode)
        pageRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView =
            G7EdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    addView(pageRoot)
                }.applyG7EdgeFade()
        setContentView(scrollView)
        render()
    }

    private fun render() {
        val restoreScrollY = scrollView.scrollY
        val palette = appearance.load(mode)
        var root =
            pageRoot.apply {
                removeAllViews()
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp, 32.dp, 18.dp, 56.dp)
                setBackgroundColor(palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))
            }
        root.addView(topBar(palette))
        root.addView(modeSelector(palette), params(8))

        root = elementCard("Glukose", palette)
        val glucoseCard = root
        root.addView(glucoseUnitRow(settings.glucoseUnit(), palette), params(5))
        root.addView(
            toggle("Zuckerwert fett", settings.glucoseBold(), palette) {
                settings.saveGlucoseBold(it)
            },
            params(5),
        )
        root = elementCard("Delta und Einheit", palette)
        root.addView(
            colorRow(AppearanceTerminology.DELTA_UNIT, settings.deltaUnitColor(mode), palette, if (mode == AppearanceMode.LIGHT) 0xFF666666.toInt() else 0xFFA8A8BA.toInt()) {
                settings.saveDeltaUnitColor(mode, it)
            },
            params(5),
        )
        root = elementCard("Alter und Skala", palette)
        root.addView(
            slider("Größe", 75, 150, settings.statusSizePercent(), palette, { "Größe · $it %" }) {
                settings.saveStatusSizePercent(it)
            },
            params(5),
        )
        root.addView(toggle("Fett", settings.statusBold(), palette, settings::saveStatusBold), params(5))
        root = elementCard("Uhrzeit", palette)
        root.addView(
            slider("Größe", 75, 450, settings.clockSizePercent(), palette, { "Größe · $it %" }) {
                settings.saveClockSizePercent(it)
            },
            params(5),
        )
        root.addView(colorRow("Farbe", settings.clockColor(), palette, 0xFFA8A8BA.toInt(), settings::saveClockColor), params(5))
        root.addView(toggle("Fett", settings.clockBold(), palette, settings::saveClockBold), params(5))
        val thresholds = settings.thresholds()
        root = glucoseCard
        section(root, "Zielbereich", palette)
        root.addView(numericThresholdRow("Tief", thresholds.lowMgDl, isHigh = false, palette), params(5))
        root.addView(numericThresholdRow("Hoch", thresholds.highMgDl, isHigh = true, palette), params(5))

        root = elementCard("Graph", palette)
        section(root, "Zeitskala", palette)
        root.addView(choiceRow(settings.graphHours(), palette), params(5))
        root.addView(
            toggle("Vigil · Skalengröße anpassen", settings.scaleSizeEnabled(), palette) {
                settings.saveScaleSizeEnabled(it)
                render()
            },
            params(5),
        )
        if (settings.scaleSizeEnabled()) {
            root.addView(
                slider("Skalengröße", 50, 120, settings.scaleSizePercent(), palette, { "Skalengröße · $it %" }) {
                    settings.saveScaleSizePercent(it)
                },
                params(5),
            )
        }
        val graphStyle = settings.graphStyle()
        root.addView(
            toggle("Obere Skala · 400", graphStyle.maximumScaleEnabled, palette) {
                settings.saveGraphStyle(settings.graphStyle().copy(maximumScaleEnabled = it))
            },
            params(5),
        )
        section(root, "GRAPH · PUNKTE", palette)
        root.addView(
            slider("Punktgröße", 15, 60, (graphStyle.dotRadiusDp * 10).roundToInt(), palette, { "Punktgröße · ${it / 10f} dp" }) {
                settings.saveGraphStyle(settings.graphStyle().copy(dotRadiusDp = it / 10f))
            },
            params(5),
        )
        root.addView(
            toggle(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE, graphStyle.historicalDotOutlineEnabled, palette) {
                settings.saveGraphStyle(settings.graphStyle().copy(historicalDotOutlineEnabled = it))
            },
            params(5),
        )
        root.addView(
            slider(
                AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE_WIDTH,
                25,
                300,
                (graphStyle.historicalDotOutlineWidthDp * 100).roundToInt(),
                palette,
                { "Konturbreite · ${it / 100f} dp" },
            ) {
                settings.saveGraphStyle(
                    settings.graphStyle().copy(
                        historicalDotOutlineWidthDp =
                            AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    ),
                )
            },
            params(5),
        )
        root.addView(
            slider(AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE_WIDTH, 25, 300, (graphStyle.currentDotOutlineWidthDp * 100).roundToInt(), palette, { "Aktuell · ${it / 100f} dp" }) {
                settings.saveGraphStyle(
                    settings.graphStyle().copy(
                        currentDotOutlineWidthDp =
                            AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    ),
                )
            },
            params(5),
        )
        root.addView(
            slider("Eckenrundung", 0, 400, (graphStyle.cornerRadiusDp * 10).roundToInt(), palette, { "Eckenrundung · ${it / 10f} dp" }) {
                settings.saveGraphStyle(settings.graphStyle().copy(cornerRadiusDp = it / 10f))
            },
            params(5),
        )
        root.addView(
            toggle("Graphkontur", graphStyle.borderEnabled, palette) {
                settings.saveGraphStyle(settings.graphStyle().copy(borderEnabled = it))
            },
            params(5),
        )
        root.addView(
            toggle("Zeitachsenskala", graphStyle.timeAxisEnabled, palette) {
                settings.saveGraphStyle(settings.graphStyle().copy(timeAxisEnabled = it))
            },
            params(5),
        )
        root.addView(
            slider("Skalenbereich", 0, 100, graphStyle.scaleLaneOpacityPercent, palette, { "Skalenbereich · $it %" }) {
                settings.saveGraphStyle(settings.graphStyle().copy(scaleLaneOpacityPercent = it))
            },
            params(5),
        )
        val colors = settings.graphColors()
        section(root, "GRAPH · FARBEN", palette)
        graphColorRows(root, colors, palette)
        root.addView(
            button("GRAPH ZURÜCKSETZEN", palette) {
                settings.resetGraph()
                render()
            },
            params(8),
        )

        val trend = settings.trendStyle(mode)
        root = elementCard("Trendpfeil", palette)
        root.addView(
            slider("Größe", GlucoseTrendSizing.MIN_SCALE_PERCENT, GlucoseTrendSizing.MAX_SCALE_PERCENT, trend.sizePercent, palette, {
                "Größe · $it %"
            }) {
                settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(sizePercent = it))
            },
            params(5),
        )
        root.addView(
            colorRow(
                "Füllfarbe",
                trend.fillColor,
                palette,
                app.aapswear.model.TrendArrowStyle
                    .defaults(mode, 0xFFFFFFFF.toInt())
                    .fillColor,
            ) { value -> settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(fillColor = value)) },
            params(5),
        )
        root.addView(
            toggle("Kontur", trend.outlineEnabled, palette) {
                settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(outlineEnabled = it))
            },
            params(5),
        )
        root.addView(
            colorRow(
                "Konturfarbe",
                trend.outlineColor,
                palette,
                app.aapswear.model.TrendArrowStyle
                    .defaults(mode, 0xFFFFFFFF.toInt())
                    .outlineColor,
            ) { value -> settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(outlineColor = value)) },
            params(5),
        )
        root.addView(
            slider("Konturdicke", 25, 400, (trend.outlineThicknessDp * 100).roundToInt(), palette, { "Konturdicke · ${it / 100f} dp" }) {
                settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(outlineThicknessDp = it / 100f))
            },
            params(5),
        )
        root.addView(
            slider("Deckkraft", 0, 100, (trend.alpha * 100).roundToInt(), palette, { "Deckkraft · $it %" }) {
                settings.saveTrendStyle(mode, settings.trendStyle(mode).copy(alpha = it / 100f))
            },
            params(5),
        )
        root.addView(
            button("TREND-STIL ZURÜCKSETZEN", palette) {
                settings.resetTrendStyle(mode)
                render()
            },
            params(8),
        )

        restoreScrollPosition(restoreScrollY)
    }

    private fun elementCard(title: String, palette: G7AppearancePalette): LinearLayout =
        app.aapswear.uishared.WearSettingsUi.elementCard(this, title, palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY)).also {
            pageRoot.addView(it, params(10))
        }

    private fun graphColorRows(
        root: LinearLayout,
        c: WatchGraphColors,
        p: G7AppearancePalette,
    ) {
        val defaults =
            app.aapswear.protocol.DirectToWatchGraphColorDefaults
                .create()

        fun add(
            title: String,
            value: Int,
            defaultColor: Int,
            update: (WatchGraphColors, Int) -> WatchGraphColors,
        ) = root.addView(colorRow(title, value, p, defaultColor) { settings.saveGraphColors(update(settings.graphColors(), it)) }, params(5))
        add("Hintergrund", c.graphBackground, defaults.graphBackground) { x, v -> x.copy(graphBackground = v) }
        add("LOW-Bereich", c.rangeLow, defaults.rangeLow) { x, v -> x.copy(rangeLow = v) }
        add("Zielbereich", c.rangeInRange, defaults.rangeInRange) { x, v -> x.copy(rangeInRange = v) }
        add("HIGH-Bereich", c.rangeHigh, defaults.rangeHigh) { x, v -> x.copy(rangeHigh = v) }
        add("LOW-Punkte", c.cgmLow, defaults.cgmLow) { x, v -> x.copy(cgmLow = v) }
        add("IN-RANGE-Punkte", c.cgmInRange, defaults.cgmInRange) { x, v -> x.copy(cgmInRange = v) }
        add("HIGH-Punkte", c.cgmHigh, defaults.cgmHigh) { x, v -> x.copy(cgmHigh = v) }
        add("VERY-LOW-Punkte", c.cgmVeryLow, defaults.cgmVeryLow) { x, v -> x.copy(cgmVeryLow = v) }
        add("VERY-HIGH-Punkte", c.cgmVeryHigh, defaults.cgmVeryHigh) { x, v -> x.copy(cgmVeryHigh = v) }
        add("Punktkontur", c.outline, defaults.outline) { x, v -> x.copy(outline = v) }
        add("HIGH-Linie", c.highLine, defaults.highLine) { x, v -> x.copy(highLine = v) }
        add("LOW-Linie", c.lowLine, defaults.lowLine) { x, v -> x.copy(lowLine = v) }
        add("Achsentext", c.axisLabel, defaults.axisLabel) { x, v -> x.copy(axisLabel = v) }
        add("Achsenmarken", c.axisTick, defaults.axisTick) { x, v -> x.copy(axisTick = v) }
        add("Jetzt-Linie", c.nowLine, defaults.nowLine) { x, v -> x.copy(nowLine = v) }
        add("Graph-Trennlinie", c.divider, defaults.divider) { x, v -> x.copy(divider = v) }
        add("Graphkontur", c.border, defaults.border) { x, v -> x.copy(border = v) }
        add("Zielwert", c.targetValue, defaults.targetValue) { x, v -> x.copy(targetValue = v) }
        add("Signalverlust", c.signalLoss, defaults.signalLoss) { x, v -> x.copy(signalLoss = v) }
    }

    private fun topBar(p: G7AppearancePalette) = g7SettingsHeader("Watchface", p)

    private fun restoreScrollPosition(scrollY: Int) {
        scrollView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                    val maxScroll = (pageRoot.measuredHeight - scrollView.height).coerceAtLeast(0)
                    scrollView.scrollTo(0, scrollY.coerceAtMost(maxScroll))
                    return true
                }
            },
        )
    }

    private fun modeSelector(p: G7AppearancePalette) =
        LinearLayout(this).apply {
            listOf(AppearanceMode.LIGHT to "Hell", AppearanceMode.DARK to "Dunkel").forEach { (item, title) ->
                addView(
                    button(title, p, item == mode) {
                        mode = item
                        settings.saveActiveAppearanceMode(item)
                        render()
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5.dp },
                )
            }
        }

    private fun choiceRow(
        current: Int,
        p: G7AppearancePalette,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        G7DirectToWatchSettingsStore.HOUR_OPTIONS.chunked(3).forEach { hours ->
            val row = LinearLayout(this@G7DirectToWatchSettingsActivity)
            hours.forEach { hour ->
                row.addView(
                    button("${hour}h", p, hour == current) {
                        settings.saveGraphHours(hour)
                        restyleChoices(this, hour, p)
                    }.apply { tag = hour },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 3.dp },
                )
            }
            addView(row, params(3))
        }
    }

    private fun glucoseUnitRow(
        current: GlucoseUnit,
        p: G7AppearancePalette,
    ) = LinearLayout(this).apply {
        listOf(GlucoseUnit.MG_DL to "mg/dL", GlucoseUnit.MMOL_L to "mmol/L").forEach { (unit, title) ->
            addView(
                button(title, p, unit == current) {
                    settings.saveGlucoseUnit(unit)
                    restyleChoices(this, unit, p)
                }.apply { tag = unit },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 3.dp },
            )
        }
    }

    private fun restyleChoices(
        row: LinearLayout,
        selected: Any,
        p: G7AppearancePalette,
    ) {
        for (index in 0 until row.childCount) {
            (row.getChildAt(index) as? LinearLayout)?.let { restyleChoices(it, selected, p) }
            val choice = row.getChildAt(index) as? TextView ?: continue
            val active = choice.tag == selected
            choice.setTextColor(if (active) p.settingsArgb(G7AppearanceRole.MENU_BACKGROUND) else p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
            choice.background =
                rounded(
                    if (active) p.settingsArgb(G7AppearanceRole.MENU_PRIMARY) else p.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                    p.settingsArgb(G7AppearanceRole.MENU_BORDER),
                    999f,
                )
        }
    }

    private fun formatThreshold(
        valueMgDl: Int,
        unit: GlucoseUnit,
    ): String =
        if (unit == GlucoseUnit.MMOL_L) {
            String.format(java.util.Locale.GERMANY, "%.1f mmol/L", valueMgDl / 18.0)
        } else {
            "$valueMgDl mg/dL"
        }

    private fun numericThresholdRow(
        title: String,
        valueMgDl: Double,
        isHigh: Boolean,
        p: G7AppearancePalette,
    ) = button("$title · ${formatThreshold(valueMgDl.roundToInt(), settings.glucoseUnit())}", p) {
        showTargetThresholdEditor(title, valueMgDl, isHigh)
    }.apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(12.dp, 0, 12.dp, 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showTargetThresholdEditor(
        title: String,
        currentMgDl: Double,
        isHigh: Boolean,
    ) {
        val restoreScrollY = scrollView.scrollY
        val unit = settings.glucoseUnit()
        val shown =
            if (unit ==
                GlucoseUnit.MMOL_L
            ) {
                String.format(java.util.Locale.US, "%.1f", currentMgDl / 18.0)
            } else {
                currentMgDl.roundToInt().toString()
            }

        fun updated(text: String): app.aapswear.model.CgmThresholds? {
            val entered = text.trim().replace(',', '.').toDoubleOrNull()
            val mgDl = entered?.let { if (unit == GlucoseUnit.MMOL_L) it * 18.0 else it }
            val current = settings.thresholds()
            return mgDl
                ?.let { if (isHigh) current.copy(highMgDl = it) else current.copy(lowMgDl = it) }
                ?.takeIf { it.isValid && it.lowMgDl >= 40.0 && it.veryHighMgDl <= 400.0 }
        }
        SharedNumberEditor.showText(
            activity = this,
            title = "Zielbereich · $title",
            current = shown,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            validate = { text ->
                if (updated(text) == null) {
                    if (isHigh) "Muss über dem Tief-Wert liegen" else "Muss unter dem Hoch-Wert liegen"
                } else {
                    null
                }
            },
            onValue = { text ->
                updated(text)?.let {
                    settings.saveThresholds(it)
                    render()
                }
            },
            onDismiss = { restoreScrollPosition(restoreScrollY) },
        )
    }

    // This listener only arbitrates parent scrolling and returns false, so the
    // native SeekBar retains its own click and accessibility semantics.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun slider(
        title: String,
        min: Int,
        max: Int,
        initial: Int,
        p: G7AppearancePalette,
        format: (Int) -> String,
        save: (Int) -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        val value = label(format(initial), 11f, p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true)
        addView(value)
        value.setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        value.setOnClickListener {
            SharedNumberEditor.show(this@G7DirectToWatchSettingsActivity, title, initial.coerceIn(min, max), min, max) { selected ->
                save(selected)
                render()
            }
        }
        addView(
            app.aapswear.uishared.ScrollSafeSeekBar(this@G7DirectToWatchSettingsActivity).apply {
                app.aapswear.uishared.WearSettingsUi
                    .slider(this, p.settingsArgb(G7AppearanceRole.MENU_PRIMARY), p.settingsArgb(G7AppearanceRole.MENU_BORDER), title)
                this.max = max - min
                progress = initial.coerceIn(min, max) - min
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            bar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            val selected =
                                progress + min
                            value.text = format(selected)
                            if (fromUser) save(selected)
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                    },
                )
            },
        )
    }

    private fun toggle(
        title: String,
        checked: Boolean,
        p: G7AppearancePalette,
        save: (Boolean) -> Unit,
    ) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        addView(
            label(title, 11f, p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            app.aapswear.uishared.ScrollSafeSwitch(this@G7DirectToWatchSettingsActivity).apply {
                app.aapswear.uishared.WearSettingsUi
                    .toggle(this, p.settingsArgb(G7AppearanceRole.MENU_PRIMARY), p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                isChecked = checked
                setOnCheckedChangeListener {
                    _,
                    value,
                    ->
                    save(value)
                }
            },
        )
    }

    private fun colorRow(
        title: String,
        color: Int,
        p: G7AppearancePalette,
        defaultColor: Int,
        save: (Int) -> Unit,
    ) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        val swatch =
            View(this@G7DirectToWatchSettingsActivity).apply {
                background =
                    rounded(color, p.settingsArgb(G7AppearanceRole.MENU_BORDER), 10f)
            }
        addView(
            LinearLayout(this@G7DirectToWatchSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 11f, p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(swatch, LinearLayout.LayoutParams(32.dp, 32.dp))
        setOnClickListener {
            SharedColorEditor.show(
                this@G7DirectToWatchSettingsActivity,
                title,
                color,
                p.argb(
                    G7AppearanceRole.MENU_SURFACE,
                ),
                p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                p.settingsArgb(G7AppearanceRole.MENU_BORDER),
                defaultColor,
                onChange = { changed ->
                    save(changed)
                    swatch.background = rounded(changed, p.settingsArgb(G7AppearanceRole.MENU_BORDER), 10f)
                },
                onReset = {},
            )
        }
    }

    private fun section(
        root: LinearLayout,
        title: String,
        p: G7AppearancePalette,
    ) = root.addView(
        label(title, 9f, p.settingsArgb(G7AppearanceRole.MENU_PRIMARY), true).apply {
            letterSpacing = 0f
            setPadding(16.dp, 16.dp, 16.dp, 8.dp)
        },
    )

    private fun button(
        title: String,
        p: G7AppearancePalette,
        selected: Boolean = false,
        action: () -> Unit,
    ) = label(
        title,
        10f,
        if (selected) p.settingsArgb(G7AppearanceRole.MENU_BACKGROUND) else p.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
        true,
    ).apply {
        gravity =
            Gravity.CENTER
        textSize = 14f
        minimumHeight = 40.dp
        ; background =
            rounded(
                if (selected) p.settingsArgb(G7AppearanceRole.MENU_PRIMARY) else p.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                p.settingsArgb(G7AppearanceRole.MENU_BORDER),
                999f,
            )
        setOnClickListener { action() }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        includeFontPadding = false
        textSize =
            app.aapswear.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
        app.aapswear.uishared.SystemTypography
            .apply(this, bold)
    }

    private fun card(p: G7AppearancePalette) = rounded(p.settingsArgb(G7AppearanceRole.MENU_SURFACE), p.settingsArgb(G7AppearanceRole.MENU_BORDER), 18f)

    private fun rounded(
        fill: Int,
        stroke: Int,
        radius: Float,
    ) = GradientDrawable().apply {
        setColor(fill)
        setStroke(0, stroke)
        cornerRadius =
            maxOf(radius, 26f) * resources.displayMetrics.density
    }

    private fun params(top: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin =
                top.dp
        }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
