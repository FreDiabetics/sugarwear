package app.aapswear.g7watch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aapswear.model.AppearanceMode
import app.aapswear.model.CgmThresholds
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TrendArrowStyle
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.uishared.SharedWearCgmGraphStyle

internal data class G7VigilSettingsSnapshot(
    val graphHours: Int,
    val glucoseUnit: GlucoseUnit,
    val glucoseBold: Boolean,
    val scaleSizeEnabled: Boolean,
    val scaleSizePercent: Int,
    val statusSizePercent: Int,
    val statusColor: Int,
    val statusBold: Boolean,
    val clockSizePercent: Int,
    val clockColor: Int,
    val clockBold: Boolean,
    val thresholds: CgmThresholds,
    val graphStyle: SharedWearCgmGraphStyle,
    val graphColors: WatchGraphColors,
    val deltaUnitColor: Map<AppearanceMode, Int>,
    val trendStyle: Map<AppearanceMode, TrendArrowStyle>,
    val activeMode: AppearanceMode,
)

internal class G7VigilSettingsViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val appContext = app
    private val store by lazy { G7DirectToWatchSettingsStore(app) }

    private var initialized = false

    private fun snapshot(): G7VigilSettingsSnapshot {
        if (!initialized) {
            store.saveActiveAppearanceMode(store.activeAppearanceMode(G7AppearanceStore(appContext).activeMode()))
            initialized = true
        }
        return G7VigilSettingsSnapshot(
            graphHours = store.graphHours(),
            glucoseUnit = store.glucoseUnit(),
            glucoseBold = store.glucoseBold(),
            scaleSizeEnabled = store.scaleSizeEnabled(),
            scaleSizePercent = store.scaleSizePercent(),
            statusSizePercent = store.statusSizePercent(),
            statusColor = store.statusColor(),
            statusBold = store.statusBold(),
            clockSizePercent = store.clockSizePercent(),
            clockColor = store.clockColor(),
            clockBold = store.clockBold(),
            thresholds = store.thresholds(),
            graphStyle = store.graphStyle(),
            graphColors = store.graphColors(),
            deltaUnitColor = AppearanceMode.entries.associateWith { store.deltaUnitColor(it) },
            trendStyle = AppearanceMode.entries.associateWith { store.trendStyle(it) },
            activeMode = store.activeAppearanceMode(G7AppearanceStore(getApplication()).activeMode()),
        )
    }

    private val worker = G7SettingsWorker(viewModelScope, read = ::snapshot)
    val state = worker.state

    fun refresh() {
        worker.submit()
    }

    fun graphHours(): Int = worker.snapshot().graphHours

    fun glucoseUnit(): GlucoseUnit = worker.snapshot().glucoseUnit

    fun glucoseBold(): Boolean = worker.snapshot().glucoseBold

    fun scaleSizeEnabled(): Boolean = worker.snapshot().scaleSizeEnabled

    fun scaleSizePercent(): Int = worker.snapshot().scaleSizePercent

    fun statusSizePercent(): Int = worker.snapshot().statusSizePercent

    fun statusColor(): Int = worker.snapshot().statusColor

    fun statusBold(): Boolean = worker.snapshot().statusBold

    fun clockSizePercent(): Int = worker.snapshot().clockSizePercent

    fun clockColor(): Int = worker.snapshot().clockColor

    fun clockBold(): Boolean = worker.snapshot().clockBold

    fun thresholds(): CgmThresholds = worker.snapshot().thresholds

    fun graphStyle(): SharedWearCgmGraphStyle = worker.snapshot().graphStyle

    fun graphColors(): WatchGraphColors = worker.snapshot().graphColors

    fun deltaUnitColor(mode: AppearanceMode = worker.snapshot().activeMode): Int = worker.snapshot().deltaUnitColor.getValue(mode)

    fun trendStyle(mode: AppearanceMode = worker.snapshot().activeMode): TrendArrowStyle = worker.snapshot().trendStyle.getValue(mode)

    fun saveGraphHours(value: Int) {
        worker.submit({ store.saveGraphHours(value) })
    }

    fun saveGlucoseUnit(value: GlucoseUnit) {
        worker.submit({ store.saveGlucoseUnit(value) })
    }

    fun saveGlucoseBold(value: Boolean) {
        worker.submit({ store.saveGlucoseBold(value) })
    }

    fun saveDeltaUnitColor(
        mode: AppearanceMode,
        value: Int,
    ) {
        worker.submit({ store.saveDeltaUnitColor(mode, value) })
    }

    fun saveScaleSizeEnabled(value: Boolean) {
        worker.submit({ store.saveScaleSizeEnabled(value) })
    }

    fun saveScaleSizePercent(value: Int) {
        worker.submit({ store.saveScaleSizePercent(value) })
    }

    fun saveStatusSizePercent(value: Int) {
        worker.submit({ store.saveStatusSizePercent(value) })
    }

    fun saveStatusColor(value: Int) {
        worker.submit({ store.saveStatusColor(value) })
    }

    fun saveStatusBold(value: Boolean) {
        worker.submit({ store.saveStatusBold(value) })
    }

    fun saveClockSizePercent(value: Int) {
        worker.submit({ store.saveClockSizePercent(value) })
    }

    fun saveClockColor(value: Int) {
        worker.submit({ store.saveClockColor(value) })
    }

    fun saveClockBold(value: Boolean) {
        worker.submit({ store.saveClockBold(value) })
    }

    fun saveActiveAppearanceMode(value: AppearanceMode) {
        worker.submit({ store.saveActiveAppearanceMode(value) })
    }

    fun saveThresholds(value: CgmThresholds): Boolean {
        if (!value.isValid) return false
        worker.submit({ store.saveThresholds(value) })
        return true
    }

    fun saveGraphStyle(value: SharedWearCgmGraphStyle) {
        val baseline = graphStyle()
        worker.submit({ store.saveGraphStyle(mergeG7GraphStyleEdit(baseline, value, store.graphStyle())) })
    }

    fun saveGraphColors(value: WatchGraphColors) {
        val baseline = graphColors()
        worker.submit({ store.saveGraphColors(mergeG7GraphColorEdit(baseline, value, store.graphColors())) })
    }

    fun saveTrendStyle(
        mode: AppearanceMode,
        value: TrendArrowStyle,
    ) {
        val baseline = trendStyle(mode)
        worker.submit({ store.saveTrendStyle(mode, mergeG7TrendStyleEdit(baseline, value, store.trendStyle(mode))) })
    }

    fun resetTrendStyle(mode: AppearanceMode) {
        worker.submit({ store.resetTrendStyle(mode) })
    }

    fun resetGraph() {
        worker.submit({ store.resetGraph() })
    }
}
