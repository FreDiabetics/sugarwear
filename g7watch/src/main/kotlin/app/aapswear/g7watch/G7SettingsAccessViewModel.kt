package app.aapswear.g7watch

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.aapswear.g7.CgmAlarmSettings
import app.aapswear.g7.CgmAlarmType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class G7SettingsAccessState(
    val palette: G7AppearancePalette,
    val access: G7SystemAccessSnapshot,
    val alarmAccess: G7AlarmSystemSnapshot,
    val alarms: CgmAlarmSettings,
    val volumeOverride: Boolean,
    val volumePercent: Int,
    val sensor: app.aapswear.g7.G7PersistedState,
    val requestedPermissions: Set<String>,
)

/** Adapts canonical preference and system-access stores; never launches permission UI. */
internal class G7SettingsAccessRepository(
    private val app: Context,
) {
    fun read() =
        G7SettingsAccessState(
            G7AppearanceStore(app).load(),
            G7SystemAccess.snapshot(app),
            G7AlarmSystemAccess.snapshot(app),
            G7AlarmSettingsStore.read(app),
            G7AlarmVolume.enabled(app),
            G7AlarmVolume.percent(app),
            G7SensorStateStore(app).read(),
            requested(),
        )

    fun prepareChannels() = G7CgmAlarmNotifier.prepareCurrentChannels(app)

    fun edit(edit: G7AlarmSettingsEdit) {
        val settings = applyG7AlarmSettingsEdit(G7AlarmSettingsStore.read(app), edit)
        G7AlarmSettingsStore.write(app, settings)
        G7CgmAlarmNotifier.ensureAllChannels(app, settings)
    }

    fun test(type: CgmAlarmType) = G7CgmAlarmNotifier.showTest(app, type, G7AlarmSettingsStore.read(app))

    fun volume(enabled: Boolean, percent: Int?) = G7AlarmVolume.configure(app, enabled, percent ?: G7AlarmVolume.percent(app))

    fun adjustVolume(delta: Int) = G7AlarmVolume.configure(app, true, G7AlarmVolume.percent(app) + delta)

    fun completeSetup() = app.getSharedPreferences("g7_setup", Context.MODE_PRIVATE).edit { putBoolean("complete", true) }

    fun requested(): Set<String> =
        app
            .getSharedPreferences("g7_permission_requests", Context.MODE_PRIVATE)
            .all
            .filterValues { it == true }
            .keys

    fun recordRequests(missing: List<String>) = app.getSharedPreferences("g7_permission_requests", Context.MODE_PRIVATE).edit { missing.forEach { putBoolean(it, true) } }
}

internal data class G7AlarmNavigationState(
    val selectedAlarm: CgmAlarmType? = null,
    val alarmPage: String? = null,
)

internal class G7SettingsAccessViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repository by lazy { G7SettingsAccessRepository(app) }
    private val worker = G7SettingsWorker(viewModelScope, read = { repository.read() })
    val state = worker.state
    private val navigationState = MutableStateFlow(G7AlarmNavigationState())
    val navigation = navigationState.asStateFlow()
    private val completedState = MutableStateFlow(false)
    val setupCompleted = completedState.asStateFlow()
    private var setupCompletionPending = false

    fun refresh(prepare: Boolean = false) {
        worker.submit({ if (prepare) repository.prepareChannels() })
    }

    fun selectAlarm(type: CgmAlarmType?) {
        navigationState.value = navigation.value.copy(selectedAlarm = type)
    }

    fun selectPage(page: String?) {
        navigationState.value = navigation.value.copy(alarmPage = page)
    }

    fun edit(edit: G7AlarmSettingsEdit) {
        worker.submit({ repository.edit(edit) })
    }

    fun test(type: CgmAlarmType) {
        worker.submit({ repository.test(type) })
    }

    fun volume(enabled: Boolean, percent: Int? = null) {
        worker.submit({ repository.volume(enabled, percent) })
    }

    fun adjustVolume(delta: Int) {
        worker.submit({ repository.adjustVolume(delta) })
    }

    fun completeSetup() {
        if (setupCompletionPending || setupCompleted.value) return
        setupCompletionPending = true
        worker.submit({ repository.completeSetup() }, {
            setupCompletionPending = false
            completedState.value = true
        }, { setupCompletionPending = false })
    }

    fun requestedPermissions() = worker.snapshot().requestedPermissions

    fun recordRequests(missing: List<String>) {
        worker.submit({ repository.recordRequests(missing) })
    }
}
