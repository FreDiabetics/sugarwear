package app.sugarwear

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
import androidx.activity.viewModels
import app.sugarlicious.model.AppearanceControlContract
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.AppearanceTerminology
import app.sugarlicious.model.ArgbColor
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.uishared.SharedColorEditor
import app.sugarlicious.uishared.SharedNumberEditor
import kotlin.math.roundToInt

@dagger.hilt.android.AndroidEntryPoint
class CgmAppearanceActivity : CgmSettingsComposeActivity() {
    private val navigation by viewModels<CgmSettingsNavigationViewModel>()
    private val store by viewModels<CgmAppearanceSettingsViewModel>()
    private val selectedMode get() = checkNotNull(store.state.value.snapshot).activeMode
    private lateinit var scrollView: ScrollView
    private lateinit var pageRoot: LinearLayout
    private var selectedCategory: String?
        get() = navigation.state.value.category
        set(value) = navigation.selectCategory(value)
    private val categoryTitles = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(navigation.state, render = ::render)
        bindSettingsState(store.state, render = ::render)

        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        selectedCategory = savedInstanceState?.getString("settings-category")
        pageRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    addView(pageRoot)
                }.applyCgmEdgeFade()
        showSettingsView(scrollView)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("settings-category", selectedCategory)
    }

    private fun render() {
        if (!settingsReady(store.state.value, store::refresh)) return
        showSettingsView(scrollView)
        val palette = store.load(selectedMode)
        applyCgmSystemChrome(window, palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND))

        val restoreScrollY = scrollView.scrollY
        categoryTitles.clear()
        var content =
            pageRoot.apply {
                removeAllViews()
                orientation = LinearLayout.VERTICAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND))
            }
        content.addView(topBar(palette))
        if (selectedCategory != null) content.addView(themeSelector(palette), params(top = 8))

        fun card(title: String): LinearLayout =
            app.sugarlicious.uishared.WearSettingsUi.elementCard(this, title, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)).also {
                categoryTitles += title
                if (selectedCategory == title) pageRoot.addView(it, params(top = 10))
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
                    palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
                    TrendArrowStyle.defaults(selectedMode, palette.argb(CgmAppearanceRole.GLUCOSE_TREND)).fillColor,
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
                        palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
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
            pill("TREND-STIL RESET", palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY)) {
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
                    palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
                    TrendArrowStyle.defaults(selectedMode, store.tileGlucosePalette(selectedMode).argb(CgmAppearanceRole.GLUCOSE_TREND)).fillColor,
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
                        palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
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
        CgmAppearanceRole.entries
            .filter { it.section == CgmAppearanceSection.GLUCOSE && it !in setOf(CgmAppearanceRole.GLUCOSE_TREND, CgmAppearanceRole.METADATA_PILL_BACKGROUND, CgmAppearanceRole.METADATA_PILL_VALUE, CgmAppearanceRole.METADATA_PILL_DIVIDER) }
            .forEach { role ->
                content.addView(
                    simpleColorRow(role.label, store.tileGlucosePalette(selectedMode).argb(role), palette) {
                        val current = store.tileGlucosePalette(selectedMode).argb(role)
                        SharedColorEditor.show(
                            this,
                            "Tile · ${role.label}",
                            current,
                            palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
                            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                            palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
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
            pill("Tile-Darstellung zurücksetzen", palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY)) {
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
        CgmAppearanceRole.entries.filter { it.section == CgmAppearanceSection.GRAPH }.forEach { role ->
            val tilePalette = store.tileGraphPalette(selectedMode)
            content.addView(
                simpleColorRow(role.label, tilePalette.argb(role), palette) {
                    openTileColorEditor(role)
                },
                params(top = 5),
            )
        }
        content.addView(
            pill("TILE-GRAPH RESET", palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY)) {
                store.resetTileGraph()
                render()
            },
            params(top = 5),
        )

        CgmAppearanceSection.entries.forEach { section ->
            val roles = CgmAppearanceRole.entries.filter { it.section == section && it != CgmAppearanceRole.GLUCOSE_TREND && it != CgmAppearanceRole.MENU_SURFACE }
            when (section) {
                CgmAppearanceSection.GLUCOSE -> {
                    val metadataRoles = setOf(CgmAppearanceRole.GLUCOSE_DELTA, CgmAppearanceRole.METADATA_PILL_BACKGROUND, CgmAppearanceRole.METADATA_PILL_VALUE, CgmAppearanceRole.METADATA_PILL_DIVIDER)
                    roles.filter { it !in metadataRoles }.forEach { role ->
                        glucoseCard.addView(colorRow(role, palette), params(top = 5))
                    }
                    val metadataCard = card("Delta und Alter")
                    metadataRoles.forEach { role -> metadataCard.addView(colorRow(role, palette), params(top = 5)) }
                }
                CgmAppearanceSection.GRAPH -> roles.forEach { role -> graphCard.addView(colorRow(role, palette), params(top = 5)) }
                else -> {
                    val sectionCard = card(section.label)
                    roles.forEach { role -> sectionCard.addView(colorRow(role, palette), params(top = 5)) }
                }
            }
        }
        content = pageRoot
        addCategoryNavigation(palette)

        if (selectedCategory == null) {
            content.addView(
                pill("Alle Darstellungen zurücksetzen", palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)) {
                    store.resetAll()
                    render()
                },
                params(top = 14),
            )
        }

        scrollView.setBackgroundColor(palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND))
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

    private fun addCategoryNavigation(palette: CgmAppearancePalette) {
        if (selectedCategory != null) return
        val rows =
            categoryTitles.map { title ->
                app.sugarlicious.uishared.WearSettingsUi
                    .navigationRow(
                        this,
                        title,
                        null,
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                    ) {
                        selectedCategory = title
                        scrollView.scrollTo(0, 0)
                        render()
                    }.apply { tag = "settings-element-$title" }
            }
        rows.chunked(4).forEach { group ->
            pageRoot.addView(
                app.sugarlicious.uishared.WearSettingsUi
                    .navigationGroup(this, group, palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12.dp },
            )
        }
    }

    private fun navigateBack() {
        if (selectedCategory == null) {
            finish()
        } else {
            selectedCategory = null
            scrollView.scrollTo(0, 0)
            render()
        }
    }

    private fun topBar(palette: CgmAppearancePalette) = cgmSettingsHeader(selectedCategory ?: "Anzeige", palette) { navigateBack() }

    private fun tileHoursRow(palette: CgmAppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            CgmAppearanceStore.ALLOWED_GRAPH_HOURS.forEach { hours ->
                addView(
                    app.sugarlicious.uishared.WearSettingsUi.choice(this@CgmAppearanceActivity, "${hours}h", store.tileGraphHours() == hours, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)) {
                        store.setTileGraphHours(hours)
                        render()
                    },
                )
            }
        }

    private fun colorRow(
        role: CgmAppearanceRole,
        palette: CgmAppearancePalette,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp, 8.dp, 8.dp, 8.dp)
            background = rounded(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE), palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), 18f)

            val swatch =
                View(this@CgmAppearanceActivity).apply {
                    background = rounded(palette.argb(role), palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), 12f)
                }
            addView(swatch, LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 9.dp })

            addView(
                LinearLayout(this@CgmAppearanceActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        label(role.label, 12f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true).apply { gravity = Gravity.START },
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
        palette: CgmAppearancePalette,
        save: (Int) -> Unit,
        min: Int = GlucoseTrendSizing.MIN_SCALE_PERCENT,
        max: Int = GlucoseTrendSizing.MAX_SCALE_PERCENT,
        format: (Int) -> String = { "$title · $it %" },
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        var selectedValue = initial.coerceIn(min, max)
        val valueLabel = label(format(selectedValue), 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true)
        valueLabel.setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        valueLabel.setOnClickListener {
            SharedNumberEditor.show(this@CgmAppearanceActivity, title, selectedValue, min, max) { value ->
                save(value)
                render()
            }
        }
        addView(valueLabel)
        addView(
            app.sugarlicious.uishared
                .ScrollSafeSeekBar(this@CgmAppearanceActivity)
                .apply {
                    app.sugarlicious.uishared.WearSettingsUi
                        .slider(this, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), title)
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
                }.let { slider ->
                    app.sugarlicious.uishared.WearSettingsUi.inlineSlider(slider) { raw ->
                        selectedValue = raw + min
                        valueLabel.text = format(selectedValue)
                        save(selectedValue)
                    }
                },
        )
    }

    private fun toggleRow(
        title: String,
        checked: Boolean,
        palette: CgmAppearancePalette,
        save: (Boolean) -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        addView(
            label(title, 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            app.sugarlicious.uishared.ScrollSafeSwitch(this@CgmAppearanceActivity).apply {
                app.sugarlicious.uishared.WearSettingsUi
                    .toggle(this, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                isChecked = checked
                setOnCheckedChangeListener { _, value -> save(value) }
            },
        )
    }

    private fun simpleColorRow(
        title: String,
        color: Int,
        palette: CgmAppearancePalette,
        edit: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        addView(
            label(title, 11f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            View(this@CgmAppearanceActivity).apply {
                background = rounded(color, palette.settingsArgb(CgmAppearanceRole.MENU_BORDER), 12f)
                setOnClickListener { edit() }
            },
            LinearLayout.LayoutParams(32.dp, 32.dp),
        )
        setOnClickListener { edit() }
    }

    private fun openColorEditor(role: CgmAppearanceRole) {
        val initial = store.load(selectedMode).argb(role)
        val palette = store.load(selectedMode)
        SharedColorEditor.show(
            this,
            role.label,
            initial,
            palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
            palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
            if (selectedMode == AppearanceMode.LIGHT) role.lightArgb else role.defaultArgb,
            onChange = { store.save(selectedMode, role, it) },
            onReset = {
                store.reset(selectedMode, role)
                render()
            },
            onDismiss = { render() },
        )
    }

    private fun openTileColorEditor(role: CgmAppearanceRole) {
        val palette = store.load(selectedMode)
        val initial = store.tileGraphPalette(selectedMode).argb(role)
        SharedColorEditor.show(
            this,
            "WearOS-Tile · ${role.label}",
            initial,
            palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE),
            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
            palette.settingsArgb(CgmAppearanceRole.MENU_BORDER),
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
        textSize = 15f
        minimumHeight = 48.dp
        gravity = Gravity.CENTER
        app.sugarlicious.uishared.SystemTypography
            .apply(this, true)
        setTextColor(textColor)
        setPadding(8.dp, 7.dp, 8.dp, 7.dp)
        background = rounded(fill, textColor, 999f)
        setOnClickListener { action() }
    }

    private fun themeSelector(palette: CgmAppearancePalette) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(AppearanceMode.LIGHT to "Hell", AppearanceMode.DARK to "Dunkel").forEach { (mode, title) ->
                addView(
                    app.sugarlicious.uishared.WearSettingsUi.choice(this@CgmAppearanceActivity, title, selectedMode == mode, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)) {
                        store.setActiveMode(mode)
                        render()
                    },
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
            app.sugarlicious.uishared.WearSettingsUi
                .textSize(size)
        setTextColor(color)
        app.sugarlicious.uishared.SystemTypography
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
