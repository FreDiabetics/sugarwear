package app.sugarwear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.uishared.SharedWearCgmGraphStyle

internal data class CgmAppearanceSettingsSnapshot(
    val activeMode: AppearanceMode,
    val glucoseScalePercent: Int,
    val trendScalePercent: Int,
    val tileGlucoseScalePercent: Int,
    val historicalDotOutlineEnabled: Boolean,
    val currentDotOutlineEnabled: Boolean,
    val graphHours: Int,
    val tileGraphHours: Int,
    val inAppGraphStyle: SharedWearCgmGraphStyle,
    val tileGraphStyle: SharedWearCgmGraphStyle,
    val load: Map<AppearanceMode, CgmAppearancePalette>,
    val trendArrowStyle: Map<AppearanceMode, TrendArrowStyle>,
    val tileTrendArrowStyle: Map<AppearanceMode, TrendArrowStyle>,
    val tileGlucosePalette: Map<AppearanceMode, CgmAppearancePalette>,
    val tileGraphPalette: Map<AppearanceMode, CgmAppearancePalette>,
)

@dagger.hilt.android.lifecycle.HiltViewModel
internal class CgmAppearanceSettingsViewModel(
    app: Application,
    private val createDependency: () -> CgmAppearanceStore = { CgmAppearanceStore(app) },
) : AndroidViewModel(app) {
    @javax.inject.Inject
    constructor(app: Application, dependency: dagger.Lazy<CgmAppearanceStore>) : this(app, { dependency.get() })

    private val store by lazy(createDependency)

    private fun snapshot() =
        CgmAppearanceSettingsSnapshot(
            activeMode = store.activeMode(),
            glucoseScalePercent = store.glucoseScalePercent(),
            trendScalePercent = store.trendScalePercent(),
            tileGlucoseScalePercent = store.tileGlucoseScalePercent(),
            historicalDotOutlineEnabled = store.historicalDotOutlineEnabled(),
            currentDotOutlineEnabled = store.currentDotOutlineEnabled(),
            graphHours = store.graphHours(),
            tileGraphHours = store.tileGraphHours(),
            inAppGraphStyle = store.inAppGraphStyle(),
            tileGraphStyle = store.tileGraphStyle(),
            load = AppearanceMode.entries.associateWith { store.load(it) },
            trendArrowStyle = AppearanceMode.entries.associateWith { store.trendArrowStyle(it) },
            tileTrendArrowStyle = AppearanceMode.entries.associateWith { store.tileTrendArrowStyle(it) },
            tileGlucosePalette = AppearanceMode.entries.associateWith { store.tileGlucosePalette(it) },
            tileGraphPalette = AppearanceMode.entries.associateWith { store.tileGraphPalette(it) },
        )

    private val worker = CgmSettingsWorker(viewModelScope, read = ::snapshot)
    val state = worker.state

    fun refresh() {
        worker.submit()
    }

    fun activeMode(): AppearanceMode = worker.snapshot().activeMode

    fun glucoseScalePercent(): Int = worker.snapshot().glucoseScalePercent

    fun trendScalePercent(): Int = worker.snapshot().trendScalePercent

    fun tileGlucoseScalePercent(): Int = worker.snapshot().tileGlucoseScalePercent

    fun historicalDotOutlineEnabled(): Boolean = worker.snapshot().historicalDotOutlineEnabled

    fun currentDotOutlineEnabled(): Boolean = worker.snapshot().currentDotOutlineEnabled

    fun graphHours(): Int = worker.snapshot().graphHours

    fun tileGraphHours(): Int = worker.snapshot().tileGraphHours

    fun inAppGraphStyle(): SharedWearCgmGraphStyle = worker.snapshot().inAppGraphStyle

    fun tileGraphStyle(): SharedWearCgmGraphStyle = worker.snapshot().tileGraphStyle

    fun load(mode: AppearanceMode = activeMode()): CgmAppearancePalette = worker.snapshot().load.getValue(mode)

    fun trendArrowStyle(mode: AppearanceMode = activeMode()): TrendArrowStyle = worker.snapshot().trendArrowStyle.getValue(mode)

    fun tileTrendArrowStyle(mode: AppearanceMode = activeMode()): TrendArrowStyle = worker.snapshot().tileTrendArrowStyle.getValue(mode)

    fun tileGlucosePalette(mode: AppearanceMode = activeMode()): CgmAppearancePalette = worker.snapshot().tileGlucosePalette.getValue(mode)

    fun tileGraphPalette(mode: AppearanceMode = activeMode()): CgmAppearancePalette = worker.snapshot().tileGraphPalette.getValue(mode)

    fun setActiveMode(mode: AppearanceMode) {
        worker.submit({ store.setActiveMode(mode) })
    }

    fun saveTrendArrowStyle(
        mode: AppearanceMode,
        style: TrendArrowStyle,
    ) {
        val baseline = trendArrowStyle(mode)
        worker.submit({ store.saveTrendArrowStyle(mode, mergeCgmTrendStyleEdit(baseline, style, store.trendArrowStyle(mode))) })
    }

    fun resetTrendArrowStyle(mode: AppearanceMode) {
        worker.submit({ store.resetTrendArrowStyle(mode) })
    }

    fun setGlucoseScalePercent(value: Int) {
        worker.submit({ store.setGlucoseScalePercent(value) })
    }

    fun setTrendScalePercent(value: Int) {
        worker.submit({ store.setTrendScalePercent(value) })
    }

    fun setTileGlucoseScalePercent(value: Int) {
        worker.submit({ store.setTileGlucoseScalePercent(value) })
    }

    fun saveTileTrendArrowStyle(
        mode: AppearanceMode,
        style: TrendArrowStyle,
    ) {
        val baseline = tileTrendArrowStyle(mode)
        worker.submit({ store.saveTileTrendArrowStyle(mode, mergeCgmTrendStyleEdit(baseline, style, store.tileTrendArrowStyle(mode))) })
    }

    fun resetTileTrendArrowStyle(mode: AppearanceMode) {
        worker.submit({ store.resetTileTrendArrowStyle(mode) })
    }

    fun saveTileGlucoseColor(
        mode: AppearanceMode,
        role: CgmAppearanceRole,
        argb: Int,
    ) {
        worker.submit({ store.saveTileGlucoseColor(mode, role, argb) })
    }

    fun resetTileGlucoseAppearance(mode: AppearanceMode) {
        worker.submit({ store.resetTileGlucoseAppearance(mode) })
    }

    fun setHistoricalDotOutlineEnabled(value: Boolean) {
        worker.submit({ store.setHistoricalDotOutlineEnabled(value) })
    }

    fun setCurrentDotOutlineEnabled(value: Boolean) {
        worker.submit({ store.setCurrentDotOutlineEnabled(value) })
    }

    fun setInAppDotOutlineWidths(historical: Float, current: Float) {
        val baseline = inAppGraphStyle()
        worker.submit({
            val latest = store.inAppGraphStyle()
            store.setInAppDotOutlineWidths(
                if (historical != baseline.historicalDotOutlineWidthDp) historical else latest.historicalDotOutlineWidthDp,
                if (current != baseline.currentDotOutlineWidthDp) current else latest.currentDotOutlineWidthDp,
            )
        })
    }

    fun save(
        role: CgmAppearanceRole,
        argb: Int,
    ) {
        worker.submit({ store.save(role, argb) })
    }

    fun save(
        mode: AppearanceMode,
        role: CgmAppearanceRole,
        argb: Int,
    ) {
        worker.submit({ store.save(mode, role, argb) })
    }

    fun reset(role: CgmAppearanceRole) {
        worker.submit({ store.reset(role) })
    }

    fun reset(
        mode: AppearanceMode,
        role: CgmAppearanceRole,
    ) {
        worker.submit({ store.reset(mode, role) })
    }

    fun resetAll() {
        worker.submit({ store.resetAll() })
    }

    fun setGraphHours(hours: Int) {
        worker.submit({ store.setGraphHours(hours) })
    }

    fun setTileGraphHours(hours: Int) {
        worker.submit({ store.setTileGraphHours(hours) })
    }

    fun setInAppGraphTimeAxisEnabled(value: Boolean) {
        worker.submit({ store.setInAppGraphTimeAxisEnabled(value) })
    }

    fun setInAppGraphBorderEnabled(value: Boolean) {
        worker.submit({ store.setInAppGraphBorderEnabled(value) })
    }

    fun saveTileGraphStyle(style: SharedWearCgmGraphStyle) {
        val baseline = tileGraphStyle()
        worker.submit({ store.saveTileGraphStyle(mergeCgmGraphStyleEdit(baseline, style, store.tileGraphStyle())) })
    }

    fun saveTileGraphColor(
        role: CgmAppearanceRole,
        argb: Int,
        mode: AppearanceMode = activeMode(),
    ) {
        worker.submit({ store.saveTileGraphColor(role, argb, mode) })
    }

    fun resetTileGraph() {
        worker.submit({ store.resetTileGraph() })
    }
}
