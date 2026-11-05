package app.sugarwear

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.sensor.protocol.CgmAlarmSettings
import app.sugarlicious.sensor.protocol.CgmAlarmType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CgmSettingsAccessState(
    val palette: CgmAppearancePalette,
    val access: CgmSystemAccessSnapshot,
    val alarmAccess: CgmAlarmSystemSnapshot,
    val alarms: CgmAlarmSettings,
    val volumeOverride: Boolean,
    val volumePercent: Int,
    val sensor: app.sugarlicious.sensor.protocol.CgmPersistedState,
    val requestedPermissions: Set<String>,
)

/** Adapts canonical preference and system-access stores; never launches permission UI. */
internal class CgmSettingsAccessRepository(
    private val app: Context,
) {
    fun read() =
        CgmSettingsAccessState(
            CgmAppearanceStore(app).load(),
            CgmSystemAccess.snapshot(app),
            CgmAlarmSystemAccess.snapshot(app),
            CgmAlarmSettingsStore.read(app),
            CgmAlarmVolume.enabled(app),
            CgmAlarmVolume.percent(app),
            CgmSensorStateStore(app).read(),
            requested(),
        )

    fun prepareChannels() = CgmAlarmNotifier.prepareCurrentChannels(app)

    fun edit(edit: CgmAlarmSettingsEdit) {
        val settings = applyCgmAlarmSettingsEdit(CgmAlarmSettingsStore.read(app), edit)
        CgmAlarmSettingsStore.write(app, settings)
        CgmAlarmNotifier.ensureAllChannels(app, settings)
    }

    fun test(type: CgmAlarmType) = CgmAlarmNotifier.showTest(app, type, CgmAlarmSettingsStore.read(app))

    fun volume(enabled: Boolean, percent: Int?) = CgmAlarmVolume.configure(app, enabled, percent ?: CgmAlarmVolume.percent(app))

    fun adjustVolume(delta: Int) = CgmAlarmVolume.configure(app, true, CgmAlarmVolume.percent(app) + delta)

    fun completeSetup() = app.getSharedPreferences("g7_setup", Context.MODE_PRIVATE).edit { putBoolean("complete", true) }

    fun requested(): Set<String> =
        app
            .getSharedPreferences("g7_permission_requests", Context.MODE_PRIVATE)
            .all
            .filterValues { it == true }
            .keys

    fun recordRequests(missing: List<String>) = app.getSharedPreferences("g7_permission_requests", Context.MODE_PRIVATE).edit { missing.forEach { putBoolean(it, true) } }
}

internal data class CgmAlarmNavigationState(
    val selectedAlarm: CgmAlarmType? = null,
    val alarmPage: String? = null,
)

@dagger.hilt.android.lifecycle.HiltViewModel
internal class CgmSettingsAccessViewModel(
    app: Application,
    private val createDependency: () -> CgmSettingsAccessRepository = { CgmSettingsAccessRepository(app) },
) : AndroidViewModel(app) {
    @javax.inject.Inject
    constructor(app: Application, dependency: dagger.Lazy<CgmSettingsAccessRepository>) : this(app, { dependency.get() })

    private val repository by lazy(createDependency)
    private val worker = CgmSettingsWorker(viewModelScope, read = { repository.read() })
    val state = worker.state
    private val navigationState = MutableStateFlow(CgmAlarmNavigationState())
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

    fun edit(edit: CgmAlarmSettingsEdit) {
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
