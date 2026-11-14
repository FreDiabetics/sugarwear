package app.sugarlicious.mobile

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal data class MobileSettingsUiState(
    val category: String? = null,
    val title: String = "Einstellungen",
    val notificationGraphExpanded: Boolean = false,
    val expandedSources: Map<String, Boolean> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val resumeLiveNotificationSetup: Boolean = false,
    val promotionSettingsRequestedThisSession: Boolean = false,
)

internal interface MobileSettingsStore {
    fun write(key: String, value: Any)

    fun saveThresholds(value: CgmThresholds)

    suspend fun syncHealth(schedule: Boolean): HealthConnectSyncResult?

    suspend fun sync(therapy: TherapyDisplayState)

    suspend fun export(uri: Uri)

    suspend fun restore(uri: Uri): SettingsRestoreResult

    fun writePredictions(enabled: Boolean) {
        write("showPredictions", enabled)
        if (enabled) listOf("iob", "cob", "uam", "zeroTemp").forEach { write("cgm.prediction.$it", true) }
    }
}

/** Adapts the existing preference file; no second settings store or fan-out path. */
internal class MobileSettingsRepository(
    context: Context,
) : MobileSettingsStore {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
    private val diagnostics = app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    override fun write(key: String, value: Any) {
        preferences.edit {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                else -> error("Unsupported settings value")
            }
        }
    }

    override fun writePredictions(enabled: Boolean) {
        preferences.edit {
            putBoolean("showPredictions", enabled)
            if (enabled) listOf("iob", "cob", "uam", "zeroTemp").forEach { putBoolean("cgm.prediction.$it", true) }
        }
    }

    override suspend fun syncHealth(schedule: Boolean): HealthConnectSyncResult? {
        if (schedule) HealthConnectIntegration.schedule(app)
        val result = HealthConnectIntegration.sync(app)
        SugarliciousWidgets.update(app)
        return result
    }

    override suspend fun sync(therapy: TherapyDisplayState) {
        diagnostics.edit { putString("lastSyncStatus", "pending") }
        try {
            withTimeout(4.seconds) { publishState(app, therapy) }
            diagnostics.edit {
                putLong("lastSyncAt", System.currentTimeMillis())
                putString("lastSyncStatus", "ok")
                remove("lastSyncError")
            }
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            diagnostics.edit {
                putString("lastSyncStatus", "unavailable")
                putString("lastSyncError", failure.javaClass.simpleName)
            }
            throw failure
        }
    }

    override suspend fun export(uri: Uri) {
        app.contentResolver.openOutputStream(uri, "wt")?.use { SettingsBackup.write(app, it) }
            ?: error("Die Datei konnte nicht geï¿½ffnet werden")
    }

    override suspend fun restore(uri: Uri): SettingsRestoreResult {
        val restored =
            app.contentResolver.openInputStream(uri)?.use { SettingsBackup.restore(app, it) }
                ?: error("Die Datei konnte nicht geï¿½ffnet werden")
        // A local restore remains successful if the watch is unreachable; surface the partial result.
        var watchUnavailable = false
        try {
            publishWatchConfig(app)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            java.util.logging.Logger
                .getLogger("MobileSettings")
                .warning(failure.javaClass.simpleName)
            watchUnavailable = true
        }
        try {
            syncComplicationPreset(app, loadComplicationPreset(app))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            java.util.logging.Logger
                .getLogger("MobileSettings")
                .warning(failure.javaClass.simpleName)
            watchUnavailable = true
        }
        PersistentBridgeService.refresh(app)
        SugarliciousWidgets.update(app)
        if (watchUnavailable) {
            diagnostics.edit { putString("lastSyncStatus", "unavailable") }
        }
        return restored
    }

    override fun saveThresholds(value: CgmThresholds) {
        CgmThresholdPreferences.save(preferences, value)
    }
}

/** Retained feature controller. Views render state and dispatch actions only. */
@dagger.hilt.android.lifecycle.HiltViewModel
internal class MobileSettingsViewModel(
    private val repository: MobileSettingsStore,
    private val externalScope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    @javax.inject.Inject
    constructor(repository: MobileSettingsStore) : this(repository, null)

    private val operations get() = externalScope ?: viewModelScope
    private val mutableState = MutableStateFlow(MobileSettingsUiState())
    val uiState = mutableState.asStateFlow()

    fun open(category: String, title: String) {
        mutableState.update { it.copy(category = category, title = title, notificationGraphExpanded = false, expandedSources = if (category == "general") it.expandedSources else emptyMap()) }
    }

    fun back(): Boolean {
        if (uiState.value.category == null) return false
        mutableState.update { it.copy(category = null, title = "Einstellungen", notificationGraphExpanded = false, expandedSources = emptyMap()) }
        return true
    }

    fun collapse() {
        mutableState.update { it.copy(category = null, title = "Einstellungen", notificationGraphExpanded = false, expandedSources = emptyMap()) }
    }

    fun clearDataSourceExpansion() {
        mutableState.update { it.copy(expandedSources = emptyMap()) }
    }

    fun expandSource(key: String, expanded: Boolean) {
        mutableState.update { it.copy(expandedSources = it.expandedSources + (key to expanded)) }
    }

    fun toggleNotificationGraph() {
        mutableState.update { it.copy(notificationGraphExpanded = !it.notificationGraphExpanded) }
    }

    fun setString(key: String, value: String) = repository.write(key, value)

    fun setBoolean(key: String, value: Boolean) = repository.write(key, value)

    fun setFloat(key: String, value: Float) = repository.write(key, value)

    fun setGraphHours(hours: Int) = repository.write("graphHours", hours.takeIf { it in OVERVIEW_GRAPH_HOUR_OPTIONS } ?: 3)

    fun setNotificationGraphHours(hours: Int) = repository.write(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, hours.coerceIn(1, 3))

    fun setWatchFaceIndex(index: Int) = repository.write("watchFaceIndex", index.coerceIn(sugarliciousWatchFaceCards.indices))

    fun saveThresholds(value: CgmThresholds) {
        operations.launch(io) { repository.saveThresholds(value) }
    }

    fun setPredictions(enabled: Boolean) = repository.writePredictions(enabled)

    fun setResumeLiveNotificationSetup(value: Boolean) {
        mutableState.update { it.copy(resumeLiveNotificationSetup = value) }
    }

    fun setPromotionSettingsRequested(value: Boolean) {
        mutableState.update { it.copy(promotionSettingsRequestedThisSession = value) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun syncNow(therapy: TherapyDisplayState?) {
        if (therapy == null) {
            mutableState.update { it.copy(message = "Noch keine gï¿½ltigen AndroidAPS-Daten vorhanden") }
            return
        }
        operation(success = { "An Watch ï¿½bertragen" }, failure = { "Keine Watch erreichbar" }) { repository.sync(therapy) }
    }

    fun syncHealthConnect() =
        operation(
            success = { synced: HealthConnectSyncResult? ->
                if (synced == null) {
                    "Health-Connect-Zugriff fehlt"
                } else {
                    when (synced.glucoseExport.state) {
                        HealthConnectExportState.SUCCESS -> "Aktualisiert ï¿½ ${synced.glucoseExport.acceptedCount} BZ-Werte ï¿½bertragen"
                        HealthConnectExportState.NO_DATA -> "Aktualisiert ï¿½ kein neuer BZ-Wert"
                        HealthConnectExportState.PERMISSION_MISSING -> "Aktualisiert ï¿½ BZ-Schreibrecht fehlt"
                        HealthConnectExportState.FAILED -> "BZ-Export fehlgeschlagen ï¿½ ${synced.glucoseExport.errorCode}"
                        HealthConnectExportState.UNAVAILABLE -> "Health Connect nicht verfï¿½gbar"
                    }
                }
            },
            failure = { "Health-Connect-Zugriff fehlt" },
        ) { repository.syncHealth(false) }

    fun healthPermissionsResult(granted: Set<String>) {
        if (granted.none { it in HealthConnectIntegration.recordPermissions }) {
            mutableState.update { it.copy(message = "Keine Health-Connect-Berechtigung erteilt") }
            return
        }
        operation(
            success = { result: HealthConnectSyncResult? ->
                when {
                    HealthConnectIntegration.glucoseWritePermission !in granted -> "Verbunden ï¿½ BZ-Schreibrecht fehlt"
                    result?.glucoseExport?.state == HealthConnectExportState.SUCCESS -> "Verbunden ï¿½ ${result.glucoseExport.acceptedCount} BZ-Werte ï¿½bertragen"
                    result?.glucoseExport?.state == HealthConnectExportState.FAILED -> "Verbunden ï¿½ BZ-Export ${result.glucoseExport.errorCode}"
                    else -> "Health Connect verbunden"
                }
            },
            failure = { "Health-Connect-Zugriff fehlt" },
        ) { repository.syncHealth(true) }
    }

    fun exportSettings(uri: Uri) =
        operation(
            success = { "Einstellungen wurden gesichert" },
            failure = { "Sicherung fehlgeschlagen: ${it.message ?: "Dateifehler"}" },
        ) { repository.export(uri) }

    fun importSettings(uri: Uri) =
        operation(
            success = { "${it.valueCount} Einstellungen wurden wiederhergestellt" },
            failure = { "Import fehlgeschlagen: ${it.message ?: "ungï¿½ltige Datei"}" },
        ) { repository.restore(uri) }

    private fun <T> operation(success: (T) -> String, failure: (Exception) -> String, action: suspend () -> T) {
        if (uiState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        operations.launch {
            try {
                val result = withContext(io) { action() }
                mutableState.update { it.copy(busy = false, message = success(result)) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(busy = false) }
                if (cancelled is TimeoutCancellationException) {
                    mutableState.update { it.copy(message = failure(cancelled)) }
                } else {
                    throw cancelled
                }
            } catch (error: Exception) {
                mutableState.update { it.copy(busy = false, message = failure(error)) }
            }
        }
    }
}
