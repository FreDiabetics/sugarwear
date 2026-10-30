package app.aapswear.g7watch

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import app.aapswear.model.ArgbColor
import app.aapswear.model.GlucoseTrendSizing
import app.aapswear.model.TrendArrowStyle
import app.aapswear.uishared.SharedColorEditor
import app.aapswear.uishared.SharedNumberEditor
import kotlin.math.roundToInt

class G7AppearanceActivity : Activity() {
    private lateinit var store: G7AppearanceStore
    private var selectedMode: AppearanceMode = AppearanceMode.DARK
    private lateinit var scrollView: ScrollView
    private lateinit var pageRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = G7AppearanceStore(this)
        selectedMode = store.activeMode()
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
        val palette = store.load(selectedMode)
        applyG7SystemChrome(window, palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))

        val restoreScrollY = scrollView.scrollY
        var content =
            pageRoot.apply {
                removeAllViews()
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp, 32.dp, 18.dp, 56.dp)
                setBackgroundColor(palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))
            }
        content.addView(topBar(palette))
        content.addView(themeSelector(palette), params(top = 8))

        fun card(title: String): LinearLayout =
            app.aapswear.uishared.WearSettingsUi.elementCard(this, title, palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY)).also {
                pageRoot.addView(it, params(top = 10))
            }
        val glucoseCard = card("Glukosewert")
        content = glucoseCard
        content.addView(scaleRow("Glukosewert", store.glucoseScalePercent(), palette, store::setGlucoseScalePercent), params(top = 5))
        content = card("Trendpfeil")
        content.addView(scaleRow("Trendpfeil", store.trendScalePercent(), palette, store::setTrendScalePercent), params(top = 5))
        val trendStyle = store.trendArrowStyle(selectedMode)
        content.addView(
            simpleColorRow("Trendpfeil · Füllfarbe", trendStyle.fillColor, palette) {
                SharedColorEditor.show(
                    this,
                    "Trendpfeil · Füllfarbe",
                    trendStyle.fillColor,
                    palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                    palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                    palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
                    TrendArrowStyle.defaults(selectedMode, palette.argb(G7AppearanceRole.GLUCOSE_TREND)).fillColor,
                    onChange = { store.saveTrendArrowStyle(selectedMode, store.trendArrowStyle(selectedMode).copy(fillColor = it)) },
                    onReset = {
                        store.resetTrendArrowStyle(selectedMode)
                        render()
                    },
                    onDismiss = { render() },
                )
            },
            params(top = 5),
        )
        content.addView(
            toggleRow("Trendpfeil-Kontur", trendStyle.outlineEnabled, palette) {
                store.saveTrendArrowStyle(selectedMode, trendStyle.copy(outlineEnabled = it))
                render()
            },
            params(top = 5),
        )
        if (trendStyle.outlineEnabled) {
            content.addView(
                simpleColorRow("Trendpfeil · Konturfarbe", trendStyle.outlineColor, palette) {
                    SharedColorEditor.show(
                        this,
                        "Trendpfeil · Konturfarbe",
                        trendStyle.outlineColor,
                        palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                        palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                        palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
                        TrendArrowStyle.defaults(selectedMode, trendStyle.fillColor).outlineColor,
                        onChange = { store.saveTrendArrowStyle(selectedMode, store.trendArrowStyle(selectedMode).copy(outlineColor = it)) },
                        onReset = {
                            store.saveTrendArrowStyle(
                                selectedMode,
                                trendStyle.copy(outlineColor = TrendArrowStyle.defaults(selectedMode, trendStyle.fillColor).outlineColor),
                            )
                            render()
                        },
                        onDismiss = { render() },
                    )
                },
                params(top = 5),
            )
            content.addView(
                scaleRow("Konturdicke", (trendStyle.outlineThicknessDp * 100).roundToInt(), palette, {
                    store.saveTrendArrowStyle(selectedMode, trendStyle.copy(outlineThicknessDp = it / 100f))
                }, min = 25, max = 400, format = { "Konturdicke · ${it / 100f} dp" }),
                params(top = 5),
            )
        }
        content.addView(
            scaleRow("Deckkraft", (trendStyle.alpha * 100).roundToInt(), palette, {
                store.saveTrendArrowStyle(selectedMode, trendStyle.copy(alpha = it / 100f))
            }, min = 0, max = 100, format = { "Deckkraft · $it %" }),
            params(top = 5),
        )
        content.addView(
            pill("TREND-STIL RESET", palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY)) {
                store.resetTrendArrowStyle(selectedMode)
                render()
            },
            params(top = 5),
        )

        content = card("WearOS-Tile · Glukosewert")
        val tileGlucoseCard = content
        content.addView(
            scaleRow("Glukosewert", store.tileGlucoseScalePercent(), palette, store::setTileGlucoseScalePercent),
            params(top = 5),
        )
        content = card("WearOS-Tile · Trendpfeil")
        val tileTrendStyle = store.tileTrendArrowStyle(selectedMode)
        content.addView(
            scaleRow("Trendpfeil", tileTrendStyle.sizePercent, palette, {
                store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(sizePercent = it))
            }),
            params(top = 5),
        )
        content.addView(
            simpleColorRow("Trendpfeil · Füllfarbe", tileTrendStyle.fillColor, palette) {
                SharedColorEditor.show(
                    this,
                    "Tile-Trendpfeil · Füllfarbe",
                    tileTrendStyle.fillColor,
                    palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                    palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                    palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
                    TrendArrowStyle.defaults(selectedMode, store.tileGlucosePalette(selectedMode).argb(G7AppearanceRole.GLUCOSE_TREND)).fillColor,
                    onChange = { store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(fillColor = it)) },
                    onReset = {
                        store.resetTileTrendArrowStyle(selectedMode)
                        render()
                    },
                    onDismiss = { render() },
                )
            },
            params(top = 5),
        )
        content.addView(
            toggleRow("Trendpfeil-Kontur", tileTrendStyle.outlineEnabled, palette) {
                store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(outlineEnabled = it))
                render()
            },
            params(top = 5),
        )
        if (tileTrendStyle.outlineEnabled) {
            content.addView(
                simpleColorRow("Trendpfeil · Konturfarbe", tileTrendStyle.outlineColor, palette) {
                    SharedColorEditor.show(
                        this,
                        "Tile-Trendpfeil · Konturfarbe",
                        tileTrendStyle.outlineColor,
                        palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                        palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                        palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
                        TrendArrowStyle.defaults(selectedMode, tileTrendStyle.fillColor).outlineColor,
                        onChange = { store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(outlineColor = it)) },
                        onReset = {
                            store.saveTileTrendArrowStyle(
                                selectedMode,
                                tileTrendStyle.copy(outlineColor = TrendArrowStyle.defaults(selectedMode, tileTrendStyle.fillColor).outlineColor),
                            )
                            render()
                        },
                        onDismiss = { render() },
                    )
                },
                params(top = 5),
            )
            content.addView(
                scaleRow("Konturdicke", (tileTrendStyle.outlineThicknessDp * 100).roundToInt(), palette, {
                    store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(outlineThicknessDp = it / 100f))
                }, min = 25, max = 400, format = { "Konturdicke · ${it / 100f} dp" }),
                params(top = 5),
            )
        }
        content.addView(
            scaleRow("Deckkraft", (tileTrendStyle.alpha * 100).roundToInt(), palette, {
                store.saveTileTrendArrowStyle(selectedMode, tileTrendStyle.copy(alpha = it / 100f))
            }, min = 0, max = 100, format = { "Deckkraft · $it %" }),
            params(top = 5),
        )
        content = tileGlucoseCard
        G7AppearanceRole.entries
            .filter { it.section == G7AppearanceSection.GLUCOSE && it != G7AppearanceRole.GLUCOSE_TREND }
            .forEach { role ->
                content.addView(
                    simpleColorRow(role.label, store.tileGlucosePalette(selectedMode).argb(role), palette) {
                        val current = store.tileGlucosePalette(selectedMode).argb(role)
                        SharedColorEditor.show(
                            this,
                            "Tile · ${role.label}",
                            current,
                            palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                            palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                            palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
                            store.load(selectedMode).argb(role),
                            onChange = { store.saveTileGlucoseColor(selectedMode, role, it) },
                            onReset = {
                                store.saveTileGlucoseColor(selectedMode, role, store.load(selectedMode).argb(role))
                                render()
                            },
                            onDismiss = { render() },
                        )
                    },
                    params(top = 5),
                )
            }
        content.addView(
            pill("TILE-GLUKOSE RESET", palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY)) {
                store.resetTileGlucoseAppearance(selectedMode)
                render()
            },
            params(top = 5),
        )

        val graphCard = card("Graph")
        content = graphCard
        content.addView(
            toggleRow(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE, store.historicalDotOutlineEnabled(), palette) {
                store.setHistoricalDotOutlineEnabled(it)
            },
            params(top = 5),
        )
        content.addView(
            scaleRow(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE_WIDTH, (store.inAppGraphStyle().historicalDotOutlineWidthDp * 100).roundToInt(), palette, {
                val style =
                    store.inAppGraphStyle().copy(
                        historicalDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    )
                store.setInAppDotOutlineWidths(style.historicalDotOutlineWidthDp, style.currentDotOutlineWidthDp)
            }, min = 25, max = 300, format = { "Historisch · ${it / 100f} dp" }),
            params(top = 5),
        )
        content.addView(
            scaleRow(AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE_WIDTH, (store.inAppGraphStyle().currentDotOutlineWidthDp * 100).roundToInt(), palette, {
                val style =
                    store.inAppGraphStyle().copy(
                        currentDotOutlineWidthDp = AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    )
                store.setInAppDotOutlineWidths(style.historicalDotOutlineWidthDp, style.currentDotOutlineWidthDp)
            }, min = 25, max = 300, format = { "Aktuell · ${it / 100f} dp" }),
            params(top = 5),
        )
        content.addView(
            toggleRow("Zeitachse · In-App-Graph", store.inAppGraphStyle().timeAxisEnabled, palette) {
                store.setInAppGraphTimeAxisEnabled(it)
            },
            params(top = 5),
        )
        content.addView(
            toggleRow("Graphkontur · In-App-Graph", store.inAppGraphStyle().borderEnabled, palette) {
                store.setInAppGraphBorderEnabled(it)
            },
            params(top = 5),
        )

        val tileStyle = store.tileGraphStyle()
        content = card("WearOS-Tile · Graph")
        content.addView(tileHoursRow(palette), params(top = 5))
        content.addView(
            scaleRow("Punktgröße", (tileStyle.dotRadiusDp * 100).roundToInt(), palette, {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(dotRadiusDp = it / 100f))
            }, min = 150, max = 600, format = { "Punktgröße · ${it / 100f} dp" }),
            params(top = 5),
        )
        content.addView(
            toggleRow(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE, tileStyle.historicalDotOutlineEnabled, palette) {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(historicalDotOutlineEnabled = it))
            },
            params(top = 5),
        )
        content.addView(
            scaleRow(AppearanceTerminology.GRAPH_HISTORICAL_DOT_OUTLINE_WIDTH, (tileStyle.historicalDotOutlineWidthDp * 100).roundToInt(), palette, {
                store.saveTileGraphStyle(
                    store.tileGraphStyle().copy(
                        historicalDotOutlineWidthDp =
                            AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    ),
                )
            }, min = 25, max = 300, format = { "Historisch · ${it / 100f} dp" }),
            params(top = 5),
        )
        content.addView(
            scaleRow(AppearanceTerminology.GRAPH_CURRENT_DOT_OUTLINE_WIDTH, (tileStyle.currentDotOutlineWidthDp * 100).roundToInt(), palette, {
                store.saveTileGraphStyle(
                    store.tileGraphStyle().copy(
                        currentDotOutlineWidthDp =
                            AppearanceControlContract.quantizeDotOutlineWidth(it / 100f),
                    ),
                )
            }, min = 25, max = 300, format = { "Aktuell · ${it / 100f} dp" }),
            params(top = 5),
        )
        content.addView(
            toggleRow("Zeitachse", tileStyle.timeAxisEnabled, palette) {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(timeAxisEnabled = it))
            },
            params(top = 5),
        )
        content.addView(
            toggleRow("Graphkontur", tileStyle.borderEnabled, palette) {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(borderEnabled = it))
            },
            params(top = 5),
        )
        content.addView(
            scaleRow("Eckenradius", (tileStyle.cornerRadiusDp * 10).roundToInt(), palette, {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(cornerRadiusDp = it / 10f))
            }, min = 0, max = 400, format = { "Eckenradius · ${it / 10f} dp" }),
            params(top = 5),
        )
        content.addView(
            scaleRow("Skalenbereich-Deckkraft", tileStyle.scaleLaneOpacityPercent, palette, {
                store.saveTileGraphStyle(store.tileGraphStyle().copy(scaleLaneOpacityPercent = it))
            }, min = 0, max = 100, format = { "Skalenbereich-Deckkraft · $it %" }),
            params(top = 5),
        )
        G7AppearanceRole.entries.filter { it.section == G7AppearanceSection.GRAPH }.forEach { role ->
            val tilePalette = store.tileGraphPalette(selectedMode)
            content.addView(
                simpleColorRow(role.label, tilePalette.argb(role), palette) {
                    openTileColorEditor(role)
                },
                params(top = 5),
            )
        }
        content.addView(
            pill("TILE-GRAPH RESET", palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY)) {
                store.resetTileGraph()
                render()
            },
            params(top = 5),
        )

        G7AppearanceSection.entries.forEach { section ->
            val roles = G7AppearanceRole.entries.filter { it.section == section && it != G7AppearanceRole.GLUCOSE_TREND && it != G7AppearanceRole.MENU_SURFACE }
            when (section) {
                G7AppearanceSection.GLUCOSE -> {
                    roles.filter { it != G7AppearanceRole.GLUCOSE_DELTA }.forEach { role ->
                        glucoseCard.addView(colorRow(role, palette), params(top = 5))
                    }
                    card("Delta und Einheit").addView(colorRow(G7AppearanceRole.GLUCOSE_DELTA, palette), params(top = 5))
                }
                G7AppearanceSection.GRAPH -> roles.forEach { role -> graphCard.addView(colorRow(role, palette), params(top = 5)) }
                else -> {
                    val sectionCard = card(section.label)
                    roles.forEach { role -> sectionCard.addView(colorRow(role, palette), params(top = 5)) }
                }
            }
        }
        content = pageRoot

        content.addView(
            pill("ALLES RESET", palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)) {
                store.resetAll()
                render()
            },
            params(top = 14),
        )

        scrollView.setBackgroundColor(palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND))
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

    private fun topBar(palette: G7AppearancePalette) = g7SettingsHeader("Farben & Darstellung", palette)

    private fun tileHoursRow(palette: G7AppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            G7AppearanceStore.ALLOWED_GRAPH_HOURS.chunked(3).forEach { group ->
                val row = LinearLayout(this@G7AppearanceActivity)
                group.forEach { hours ->
                    val active = store.tileGraphHours() == hours
                    row.addView(
                        pill(
                            "${hours}h",
                            if (active) palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY) else palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
                            if (active) palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND) else palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
                        ) {
                            store.setTileGraphHours(hours)
                            render()
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 3.dp },
                    )
                }
                addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 3.dp })
            }
        }

    private fun colorRow(
        role: G7AppearanceRole,
        palette: G7AppearancePalette,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp, 8.dp, 8.dp, 8.dp)
            background = rounded(palette.settingsArgb(G7AppearanceRole.MENU_SURFACE), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 18f)

            val swatch =
                View(this@G7AppearanceActivity).apply {
                    background = rounded(palette.argb(role), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 12f)
                }
            addView(swatch, LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 9.dp })

            addView(
                LinearLayout(this@G7AppearanceActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        label(role.label, 12f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true).apply { gravity = Gravity.START },
                    )
                    setOnClickListener { openColorEditor(role) }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            setOnClickListener { openColorEditor(role) }
        }

    // This listener only arbitrates parent scrolling and returns false, so the
    // native SeekBar retains its own click and accessibility semantics.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun scaleRow(
        title: String,
        initial: Int,
        palette: G7AppearancePalette,
        save: (Int) -> Unit,
        min: Int = GlucoseTrendSizing.MIN_SCALE_PERCENT,
        max: Int = GlucoseTrendSizing.MAX_SCALE_PERCENT,
        format: (Int) -> String = { "$title · $it %" },
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        var selectedValue = initial.coerceIn(min, max)
        val valueLabel = label(format(selectedValue), 11f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true)
        valueLabel.setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        valueLabel.setOnClickListener {
            SharedNumberEditor.show(this@G7AppearanceActivity, title, selectedValue, min, max) { value ->
                save(value)
                render()
            }
        }
        addView(valueLabel)
        addView(
            app.aapswear.uishared.ScrollSafeSeekBar(this@G7AppearanceActivity).apply {
                app.aapswear.uishared.WearSettingsUi
                    .slider(this, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), palette.settingsArgb(G7AppearanceRole.MENU_BORDER), title)
                this.max = max - min
                progress = initial.coerceIn(min, max) - min
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            val value = progress + min
                            selectedValue = value
                            valueLabel.text = format(value)
                            if (fromUser) save(value)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            },
        )
    }

    private fun toggleRow(
        title: String,
        checked: Boolean,
        palette: G7AppearancePalette,
        save: (Boolean) -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        addView(
            label(title, 11f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            app.aapswear.uishared.ScrollSafeSwitch(this@G7AppearanceActivity).apply {
                app.aapswear.uishared.WearSettingsUi
                    .toggle(this, palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY), palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY))
                isChecked = checked
                setOnCheckedChangeListener { _, value -> save(value) }
            },
        )
    }

    private fun simpleColorRow(
        title: String,
        color: Int,
        palette: G7AppearancePalette,
        edit: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        addView(
            label(title, 11f, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            View(this@G7AppearanceActivity).apply {
                background = rounded(color, palette.settingsArgb(G7AppearanceRole.MENU_BORDER), 12f)
                setOnClickListener { edit() }
            },
            LinearLayout.LayoutParams(32.dp, 32.dp),
        )
        setOnClickListener { edit() }
    }

    private fun openColorEditor(role: G7AppearanceRole) {
        val initial = store.load(selectedMode).argb(role)
        val palette = store.load(selectedMode)
        SharedColorEditor.show(
            this,
            role.label,
            initial,
            palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
            palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
            palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
            if (selectedMode == AppearanceMode.LIGHT) role.lightArgb else role.defaultArgb,
            onChange = { store.save(selectedMode, role, it) },
            onReset = {
                store.reset(selectedMode, role)
                render()
            },
            onDismiss = { render() },
        )
    }

    private fun openTileColorEditor(role: G7AppearanceRole) {
        val palette = store.load(selectedMode)
        val initial = store.tileGraphPalette(selectedMode).argb(role)
        SharedColorEditor.show(
            this,
            "WearOS-Tile · ${role.label}",
            initial,
            palette.settingsArgb(G7AppearanceRole.MENU_SURFACE),
            palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY),
            palette.settingsArgb(G7AppearanceRole.MENU_BORDER),
            palette.argb(role),
            onChange = { store.saveTileGraphColor(role, it, selectedMode) },
            onReset = {
                store.saveTileGraphColor(role, palette.argb(role), selectedMode)
                render()
            },
            onDismiss = { render() },
        )
    }

    private fun pill(
        textValue: String,
        fill: Int,
        textColor: Int,
        action: () -> Unit,
    ) = TextView(this).apply {
        text = textValue
        textSize = 14f
        minimumHeight = 40.dp
        gravity = Gravity.CENTER
        app.aapswear.uishared.SystemTypography
            .apply(this, true)
        setTextColor(textColor)
        setPadding(8.dp, 7.dp, 8.dp, 7.dp)
        background = rounded(fill, textColor, 999f)
        setOnClickListener { action() }
    }

    private fun themeSelector(palette: G7AppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(AppearanceMode.LIGHT to "Hell", AppearanceMode.DARK to "Dunkel").forEach { (mode, title) ->
                addView(
                    pill(
                        title,
                        if (selectedMode ==
                            mode
                        ) {
                            palette.settingsArgb(G7AppearanceRole.MENU_PRIMARY)
                        } else {
                            palette.settingsArgb(G7AppearanceRole.MENU_SURFACE)
                        },
                        if (selectedMode ==
                            mode
                        ) {
                            palette.settingsArgb(G7AppearanceRole.MENU_BACKGROUND)
                        } else {
                            palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY)
                        },
                    ) {
                        selectedMode = mode
                        store.setActiveMode(mode)
                        render()
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6.dp },
                )
            }
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

    private fun params(top: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top.dp
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

    private fun toHex(argb: Int): String = ArgbColor.format(argb)

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
