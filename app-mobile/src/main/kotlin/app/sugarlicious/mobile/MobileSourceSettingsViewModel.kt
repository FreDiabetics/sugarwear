package app.sugarlicious.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.datasource.manufacturer.ShareCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class MobileSourceSettingsSnapshot(
    val configuration: MobileSourceConfiguration = MobileSourceConfiguration(),
    val preferredManufacturer: MobileGlucoseSource = MobileGlucoseSource.DEXCOM_BROADCAST,
    val share: ShareCredentials? = null,
    val cloudEndpoint: CloudEndpointConfiguration = CloudEndpointConfiguration(false, "", CloudEndpointAuthMode.API_SECRET),
    val shareStatus: String = "",
    val cloudEndpointStatus: String = "",
    val loaded: Boolean = true,
    val observationError: String = "",
)

internal interface MobileSourceSettingsStore {
    val state: StateFlow<MobileSourceSettingsSnapshot>

    fun setSource(source: MobileGlucoseSource, enabled: Boolean)

    fun setTherapyBridge(enabled: Boolean)

    fun setManufacturer(source: MobileGlucoseSource, enabled: Boolean)

    fun setPredictions(enabled: Boolean)

    fun setTherapy(enabled: Boolean)

    fun setCloudEndpoint(enabled: Boolean)

    fun setCloudEndpointTherapy(enabled: Boolean)

    fun saveShare(account: String, password: String, region: String)

    fun saveCloudEndpoint(configuration: CloudEndpointConfiguration, secret: String?)

    fun hasCloudEndpointSecret(): Boolean
}

/** Existing stores and service entry points remain authoritative. Listener lifetime follows the feature. */
internal class MobileSourceSettingsRepository(
    context: Context,
    scope: CoroutineScope,
) : MobileSourceSettingsStore {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
    private val diagnostics = app.getSharedPreferences("source_diagnostics", Context.MODE_PRIVATE)
    private val cloudEndpointPrefs = app.getSharedPreferences("nightscout_treatment_config", Context.MODE_PRIVATE)
    override val state =
        callbackFlow {
            fun snapshot() =
                MobileSourceSettingsSnapshot(
                    configuration = MobileSourceConfigurationStore.read(app),
                    preferredManufacturer = MobileSourceConfigurationStore.preferredManufacturer(app),
                    share = CloudShareCredentialsStore.read(app),
                    cloudEndpoint = CloudEndpointConfigurationStore.read(app),
                    shareStatus = diagnostics.getString("dexcom_share", "").orEmpty(),
                    cloudEndpointStatus = diagnostics.getString("nightscout_cgm", "").orEmpty(),
                )
            // Preference callbacks may arrive on Main; read encrypted stores only on this IO collector.
            val changes = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes.trySend(Unit) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            diagnostics.registerOnSharedPreferenceChangeListener(listener)
            cloudEndpointPrefs.registerOnSharedPreferenceChangeListener(listener)
            val reader = launch { for (ignored in changes) send(snapshot()) }
            changes.trySend(Unit)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                diagnostics.unregisterOnSharedPreferenceChangeListener(listener)
                cloudEndpointPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                changes.close()
                reader.cancel()
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(MobileSourceSettingsSnapshot(loaded = false, observationError = "Datenquellen-Einstellungen konnten nicht gelesen werden"))
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(0), MobileSourceSettingsSnapshot(loaded = false))

    override fun setSource(source: MobileGlucoseSource, enabled: Boolean) = MobileSourceConfigurationStore.setGlucoseEnabled(app, source, enabled)

    override fun setTherapyBridge(enabled: Boolean) = MobileSourceConfigurationStore.setTherapyBridgeEnabled(app, enabled)

    override fun setManufacturer(source: MobileGlucoseSource, enabled: Boolean) = MobileSourceConfigurationStore.setManufacturer(app, source, enabled)

    override fun setPredictions(enabled: Boolean) = MobileSourceConfigurationStore.setPredictions(app, enabled)

    override fun setTherapy(enabled: Boolean) = MobileSourceConfigurationStore.setTherapy(app, enabled)

    override fun setCloudEndpoint(enabled: Boolean) = MobileSourceConfigurationStore.setCloudEndpointEnabled(app, enabled)

    override fun setCloudEndpointTherapy(enabled: Boolean) = CloudEndpointConfigurationStore.save(app, CloudEndpointConfigurationStore.read(app).copy(enabled = enabled), null)

    override fun saveShare(account: String, password: String, region: String) = CloudShareCredentialsStore.save(app, account, password, region)

    override fun saveCloudEndpoint(configuration: CloudEndpointConfiguration, secret: String?) = CloudEndpointConfigurationStore.save(app, configuration, secret)

    override fun hasCloudEndpointSecret() = !CloudEndpointSecretStore.read(app).isNullOrBlank()
}

internal data class ShareSettingsDraft(
    val account: String = "",
    val password: String = "",
    val region: String = "ous",
    val editing: Boolean = true,
    val feedback: String = "",
    val saving: Boolean = false,
)

internal data class CloudEndpointSettingsDraft(
    val url: String = "",
    val secret: String = "",
    val authMode: CloudEndpointAuthMode = CloudEndpointAuthMode.API_SECRET,
    val editing: Boolean = true,
    val feedback: String = "",
    val saving: Boolean = false,
)

/** Drafts live only in memory; no SavedStateHandle, Bundle or logs contain access credentials. */
@dagger.hilt.android.lifecycle.HiltViewModel
internal class MobileSourceSettingsViewModel(
    private val repository: MobileSourceSettingsStore,
    externalScope: CoroutineScope? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    @javax.inject.Inject
    constructor(dependencies: MobileSourceSettingsDependencies) : this(dependencies.repository, dependencies.scope) {
        repositoryScope = dependencies.scope
    }

    private val scope = externalScope ?: viewModelScope
    val state = repository.state
    private val mutableShare = MutableStateFlow(ShareSettingsDraft())
    val share = mutableShare.asStateFlow()
    private val mutableCloudEndpoint = MutableStateFlow(CloudEndpointSettingsDraft())
    val cloudEndpoint = mutableCloudEndpoint.asStateFlow()
    private val visible = MutableStateFlow(false)
    private val commands = Mutex()
    private val mutableOperationError = MutableStateFlow("")
    val operationError = mutableOperationError.asStateFlow()

    fun setVisible(value: Boolean) {
        visible.value = value
    }

    private var shareInitialized = false
    private var cloudEndpointInitialized = false

    init {
        scope.launch {
            visible.collectLatest { active ->
                if (!active) return@collectLatest
                state.collect { snapshot ->
                    if (snapshot.loaded) {
                        if (!shareInitialized) {
                            shareInitialized = true
                            mutableShare.value = ShareSettingsDraft(account = snapshot.share?.account.orEmpty(), region = snapshot.share?.region ?: "ous", editing = snapshot.share == null)
                        }
                        if (!cloudEndpointInitialized) {
                            cloudEndpointInitialized = true
                            mutableCloudEndpoint.value = CloudEndpointSettingsDraft(url = snapshot.cloudEndpoint.baseUrl, authMode = snapshot.cloudEndpoint.authMode, editing = !snapshot.cloudEndpoint.isValid())
                        }
                    }
                }
            }
        }
    }

    private fun command(action: () -> Unit) {
        scope.launch {
            try {
                commands.withLock { withContext(io) { action() } }
                mutableOperationError.value = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                java.util.logging.Logger
                    .getLogger("MobileSourceSettings")
                    .warning(failure.javaClass.simpleName)
                mutableOperationError.value = "Einstellung konnte nicht gespeichert werden"
            }
        }
    }

    fun setSource(source: MobileGlucoseSource, enabled: Boolean) = command { repository.setSource(source, enabled) }

    fun setTherapyBridge(enabled: Boolean) = command { repository.setTherapyBridge(enabled) }

    fun setManufacturer(source: MobileGlucoseSource, enabled: Boolean) = command { repository.setManufacturer(source, enabled) }

    fun setPredictions(enabled: Boolean) = command { repository.setPredictions(enabled) }

    fun setTherapy(enabled: Boolean) = command { repository.setTherapy(enabled) }

    fun setCloudEndpoint(enabled: Boolean) = command { repository.setCloudEndpoint(enabled) }

    fun setCloudEndpointTherapy(enabled: Boolean) = command { repository.setCloudEndpointTherapy(enabled) }

    fun updateShare(account: String = share.value.account, password: String = share.value.password, region: String = share.value.region) {
        if (share.value.saving) return
        shareInitialized = true
        mutableShare.update { it.copy(account = account, password = password, region = region) }
    }

    fun editShare() {
        mutableShare.update { it.copy(editing = true) }
    }

    fun cancelShare() {
        if (share.value.saving) return
        val saved = state.value.share
        mutableShare.value = ShareSettingsDraft(account = saved?.account.orEmpty(), region = saved?.region ?: "ous", editing = false)
    }

    fun saveShare() {
        val draft = share.value
        if (draft.saving) return
        val secret =
            draft.password.ifBlank {
                state.value.share
                    ?.takeIf { it.account == draft.account.trim() }
                    ?.password
                    .orEmpty()
            }
        if (draft.account.isBlank() || secret.isBlank()) {
            mutableShare.update { it.copy(feedback = "Benutzername und Passwort fehlen") }
            return
        }
        mutableShare.update { it.copy(saving = true) }
        scope.launch {
            try {
                withContext(io) { repository.saveShare(draft.account, secret, draft.region) }
                mutableShare.update { it.copy(password = "", editing = false, feedback = "Zugang gespeichert", saving = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                java.util.logging.Logger
                    .getLogger("MobileSourceSettings")
                    .warning(failure.javaClass.simpleName)
                mutableShare.update { it.copy(feedback = "Zugang konnte nicht sicher gespeichert werden", saving = false) }
            }
        }
    }

    fun updateCloudEndpoint(url: String = cloudEndpoint.value.url, secret: String = cloudEndpoint.value.secret, authMode: CloudEndpointAuthMode = cloudEndpoint.value.authMode) {
        if (cloudEndpoint.value.saving) return
        cloudEndpointInitialized = true
        mutableCloudEndpoint.update { it.copy(url = url, secret = secret, authMode = authMode) }
    }

    fun editCloudEndpoint() {
        if (cloudEndpoint.value.editing || cloudEndpoint.value.saving) return
        val saved = state.value.cloudEndpoint
        mutableCloudEndpoint.value = CloudEndpointSettingsDraft(url = saved.baseUrl, authMode = saved.authMode)
    }

    fun cancelCloudEndpoint() {
        if (cloudEndpoint.value.saving) return
        val saved = state.value.cloudEndpoint
        mutableCloudEndpoint.value = CloudEndpointSettingsDraft(url = saved.baseUrl, authMode = saved.authMode, editing = false)
    }

    fun saveCloudEndpoint() {
        val draft = cloudEndpoint.value
        if (draft.saving) return
        val saved = state.value.cloudEndpoint
        val candidate = saved.copy(baseUrl = draft.url, authMode = draft.authMode)
        if (!candidate.isValid()) {
            mutableCloudEndpoint.update { it.copy(feedback = "G\u00fcltige HTTPS-Adresse ohne Zugangsdaten in der URL erforderlich") }
            return
        }
        mutableCloudEndpoint.update { it.copy(saving = true) }
        scope.launch {
            try {
                withContext(io) {
                    require((draft.authMode == saved.authMode && candidate.normalizedBaseUrl == saved.normalizedBaseUrl) || draft.secret.isNotBlank() || !repository.hasCloudEndpointSecret())
                    repository.saveCloudEndpoint(candidate, draft.secret.takeIf(String::isNotBlank))
                }
                mutableCloudEndpoint.update { it.copy(secret = "", editing = false, feedback = "Zugang gespeichert", saving = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                java.util.logging.Logger
                    .getLogger("MobileSourceSettings")
                    .warning(failure.javaClass.simpleName)
                mutableCloudEndpoint.update { it.copy(feedback = "Zugang konnte nicht sicher gespeichert werden", saving = false) }
            }
        }
    }

    private var repositoryScope: CoroutineScope? = null

    override fun onCleared() {
        repositoryScope?.let { it.cancel() }
    }
}
