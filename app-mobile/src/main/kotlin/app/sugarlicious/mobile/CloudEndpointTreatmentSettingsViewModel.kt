package app.sugarlicious.mobile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CloudEndpointTreatmentSettingsState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val url: String = "",
    val authMode: CloudEndpointAuthMode = CloudEndpointAuthMode.API_SECRET,
    val secret: String = "",
    val hasSecret: Boolean = false,
    val status: String = "",
    val busy: Boolean = false,
    val requestPermission: Boolean = false,
    val message: String? = null,
    val finish: Boolean = false,
)

internal interface CloudEndpointTreatmentSettingsStore {
    suspend fun load(): CloudEndpointTreatmentSettingsState

    suspend fun needsPermission(configuration: CloudEndpointConfiguration, sdk: Int, granted: Boolean): Boolean

    suspend fun save(configuration: CloudEndpointConfiguration, secret: String?)

    suspend fun test(): String
}

internal class CloudEndpointTreatmentSettingsRepository(
    context: Context,
) : CloudEndpointTreatmentSettingsStore {
    private val app = context.applicationContext

    override suspend fun load(): CloudEndpointTreatmentSettingsState {
        val configuration = CloudEndpointConfigurationStore.read(app)
        val diagnostics = app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val last = diagnostics.getLong("nightscoutTreatmentLastSuccess", 0L)
        val status = diagnostics.getString("nightscoutTreatmentStatus", "Noch nicht synchronisiert").orEmpty()
        return CloudEndpointTreatmentSettingsState(
            loaded = true,
            enabled = configuration.enabled,
            url = configuration.baseUrl,
            authMode = configuration.authMode,
            hasSecret = !CloudEndpointSecretStore.read(app).isNullOrBlank(),
            status = if (last > 0L) "$status \u00b7 zuletzt ${java.time.Instant.ofEpochMilli(last)}" else status,
        )
    }

    override suspend fun needsPermission(configuration: CloudEndpointConfiguration, sdk: Int, granted: Boolean) =
        LocalNetworkAccessPolicy.needsPermissionAfterResolution(sdk, configuration.enabled, configuration.baseUrl, granted)

    override suspend fun save(configuration: CloudEndpointConfiguration, secret: String?) {
        CloudEndpointConfigurationStore.save(app, configuration, secret)
        CloudEndpointTreatmentSync.applyConfigurationState(app)
    }

    override suspend fun test() = CloudEndpointTreatmentSync.sync(app).message
}

/** Retained command/draft owner. Neither credentials nor pending commands enter saved state. */
@dagger.hilt.android.lifecycle.HiltViewModel
internal class CloudEndpointTreatmentSettingsViewModel(
    private val repository: CloudEndpointTreatmentSettingsStore,
    externalScope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    @javax.inject.Inject
    constructor(repository: CloudEndpointTreatmentSettingsStore) : this(repository, null)

    private val scope = externalScope ?: viewModelScope
    private val mutableState = MutableStateFlow(CloudEndpointTreatmentSettingsState())
    val uiState = mutableState.asStateFlow()

    private data class Pending(
        val configuration: CloudEndpointConfiguration,
        val secret: String?,
        val test: Boolean,
    )

    private var pending: Pending? = null

    init {
        scope.launch {
            try {
                mutableState.value = withContext(io) { repository.load() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                java.util.logging.Logger
                    .getLogger("NightscoutSettings")
                    .warning(failure.javaClass.simpleName)
                mutableState.update { it.copy(message = "Einstellungen konnten nicht gelesen werden") }
            }
        }
    }

    fun update(enabled: Boolean = uiState.value.enabled, url: String = uiState.value.url, authMode: CloudEndpointAuthMode = uiState.value.authMode, secret: String = uiState.value.secret) {
        if (!uiState.value.loaded || uiState.value.busy) return
        mutableState.update { it.copy(enabled = enabled, url = url, authMode = authMode, secret = secret) }
    }

    fun consumePermissionRequest() {
        mutableState.update { it.copy(requestPermission = false) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun consumeFinish() {
        mutableState.update { it.copy(finish = false) }
    }

    fun submit(test: Boolean, sdk: Int, granted: Boolean) {
        val state = uiState.value
        if (!state.loaded || state.busy) return
        val command = Pending(CloudEndpointConfiguration(state.enabled, state.url, state.authMode), state.secret.takeIf(String::isNotBlank), test)
        pending = command
        mutableState.update { it.copy(busy = true, message = null) }
        scope.launch {
            try {
                if (withContext(io) { repository.needsPermission(command.configuration, sdk, granted) }) {
                    mutableState.update { it.copy(requestPermission = true) }
                } else {
                    execute(command)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failed(failure)
            }
        }
    }

    fun permissionResult(granted: Boolean) {
        val command = pending ?: return
        mutableState.update { it.copy(requestPermission = false) }
        if (!granted) {
            pending = null
            mutableState.update { it.copy(busy = false, message = "Lokaler Netzwerkzugriff wurde nicht freigegeben") }
        } else {
            scope.launch { execute(command) }
        }
    }

    private suspend fun execute(command: Pending) {
        // Consume before suspension, so duplicate platform results cannot start another operation.
        if (pending !== command) return
        pending = null
        try {
            withContext(io) { repository.save(command.configuration, command.secret) }
            mutableState.update { it.copy(secret = "", hasSecret = it.hasSecret || command.secret != null) }
            if (command.test) {
                mutableState.update { it.copy(status = "Verbindung wird gepr\u00fcft \u2026") }
                val status = withContext(io) { repository.test() }
                mutableState.update { it.copy(status = status, busy = false) }
            } else {
                mutableState.update { it.copy(busy = false, message = "Nightscout-Einstellungen gespeichert", finish = true) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failed(failure)
        }
    }

    private fun failed(failure: Exception) {
        pending = null
        mutableState.update { it.copy(busy = false, requestPermission = false, message = failure.message ?: "Einstellungen ung\u00fcltig") }
    }
}
