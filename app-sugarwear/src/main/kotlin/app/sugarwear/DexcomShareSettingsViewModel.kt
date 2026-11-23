package app.sugarwear

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class DexcomShareSettingsUiState(
    val status: DexcomShareUploadStatus,
    val connecting: Boolean = false,
    val updating: Boolean = false,
    val setupError: Boolean = false,
    val invitationMessage: String? = null,
)

/** Owns the Share setting lifecycle; the Activity only renders state and sends UI actions. */
@dagger.hilt.android.lifecycle.HiltViewModel
internal class DexcomShareSettingsViewModel
    @javax.inject.Inject
    constructor(
        app: Application,
    ) : AndroidViewModel(app) {
        private val context = app.applicationContext
        private val store = DexcomShareUploadStore(context)
        private val mutableState = MutableStateFlow(DexcomShareSettingsUiState(store.status()))
        val state = mutableState.asStateFlow()

        fun refresh() {
            mutableState.value = mutableState.value.copy(status = store.status())
        }

        fun saveAccount(account: String, password: String, region: DexcomShareRegion) {
            if (mutableState.value.updating || mutableState.value.connecting) return
            mutableState.value = mutableState.value.copy(updating = true, connecting = true, setupError = false, invitationMessage = null)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        store.saveAccount(account, password, region)
                        DexcomShareUploadScheduler.stop(context)
                        verifyAccountAndReceiver()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Share account save failed: ${error.javaClass.simpleName}")
                    mutableState.value = mutableState.value.copy(setupError = true)
                } finally {
                    mutableState.value = mutableState.value.copy(updating = false, connecting = false, status = store.status())
                }
            }
        }

        fun saveReceiver(serial: String) {
            testConnection(serial)
        }

        fun setupWatch(revision: Long) {
            val current = mutableState.value
            if (current.connecting || current.updating || current.status.enabled || current.status.revision != revision) return
            mutableState.value = current.copy(updating = true, connecting = true, setupError = false)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val config = checkNotNull(store.readConfig())
                        check(config.revision == revision && !store.enabled())
                        try {
                            val confirmed = DexcomShareUploadClient().setupWatchReceiver(config)
                            store.enableConfirmedReceiver(config, confirmed)
                            DexcomShareUploadScheduler.start(context)
                        } catch (problem: DexcomShareProtocolException) {
                            store.markError(config, problem.issue, problem.httpStatus)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Share setup failed: ${error.javaClass.simpleName}")
                    mutableState.value = mutableState.value.copy(setupError = true)
                } finally {
                    mutableState.value = mutableState.value.copy(updating = false, connecting = false, status = store.status())
                }
            }
        }

        fun toggleUpload() {
            if (mutableState.value.updating || mutableState.value.connecting) return
            mutableState.value = mutableState.value.copy(updating = true, setupError = false)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val enable = !store.enabled()
                        store.setEnabled(enable)
                        if (enable) DexcomShareUploadScheduler.start(context) else DexcomShareUploadScheduler.stop(context)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Share enable state could not be changed: ${error.javaClass.simpleName}")
                    mutableState.value = mutableState.value.copy(setupError = true)
                } finally {
                    mutableState.value = mutableState.value.copy(updating = false, status = store.status())
                }
            }
        }

        fun testConnection(candidate: String? = null) {
            if (mutableState.value.connecting || mutableState.value.updating) return
            mutableState.value = mutableState.value.copy(connecting = true, setupError = false)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { verifyAccountAndReceiver(candidate) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Share account test failed: ${error.javaClass.simpleName}")
                    mutableState.value = mutableState.value.copy(setupError = true)
                } finally {
                    mutableState.value = mutableState.value.copy(connecting = false, status = store.status())
                }
            }
        }

        private suspend fun verifyAccountAndReceiver(candidate: String? = null) {
            val config = checkNotNull(store.readConfig())
            val client = DexcomShareUploadClient()
            try {
                client.testAccount(config)
                store.markVerified(config, System.currentTimeMillis())
                if (!store.enabled()) {
                    val serial = candidate ?: config.receiverSerial.takeIf { it.isNotEmpty() }
                    val confirmed = if (serial == null) client.checkReceiver(config) else client.checkReceiver(config, serial)
                    store.saveReceiver(confirmed, config.revision)
                }
            } catch (problem: DexcomShareProtocolException) {
                store.markError(config, problem.issue, problem.httpStatus)
            }
        }

        fun inviteFollower(invitation: DexcomShareInvitation) {
            val current = mutableState.value
            if (current.updating || current.connecting || !invitation.valid() || !current.status.configured) return
            mutableState.value = current.copy(updating = true, invitationMessage = "Einladung wird angefordert …")
            viewModelScope.launch {
                val message =
                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                invitationLock.withLock {
                                    val config = checkNotNull(store.readConfig())
                                    val pending = store.pendingContact(config, invitation.email)
                                    val outcome =
                                        DexcomShareUploadClient().inviteFollower(config, invitation, pending) { contactId ->
                                            store.rememberContact(config, invitation.email, contactId)
                                        }
                                    if (outcome != DexcomShareInviteResult.CONTACT_EXISTS) store.rememberContact(config, invitation.email, null)
                                    outcome
                                }
                            }
                        when (result) {
                            DexcomShareInviteResult.REQUESTED -> "Dexcom hat die Einladung bestätigt. Dein Follower kann sie per E-Mail annehmen."
                            DexcomShareInviteResult.ALREADY_INVITED -> "Bei Dexcom besteht bereits eine Einladung oder Freigabe für diesen Kontakt."
                            DexcomShareInviteResult.CONTACT_EXISTS -> "Dieser Kontaktname existiert bei Dexcom bereits. Bitte prüfe die bestehende Freigabe."
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (problem: DexcomShareProtocolException) {
                        when (problem.issue) {
                            DexcomShareUploadError.AUTHENTICATION -> "Anmeldung fehlgeschlagen. Bitte prüfe das Dexcom-Konto."
                            DexcomShareUploadError.LOGIN_LIMIT, DexcomShareUploadError.RATE_LIMIT -> "Dexcom begrenzt die Anfragen. Bitte später erneut versuchen."
                            else -> "Einladung unbestätigt. Prüfe das Postfach vor einem erneuten Versuch." + problem.httpStatus?.let { " (HTTP $it)" }.orEmpty()
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Share invitation unavailable: ${error.javaClass.simpleName}")
                        "Einladung unbestätigt. Bitte prüfe dein Share-Konto und das Postfach des Followers."
                    }
                mutableState.value = mutableState.value.copy(updating = false, invitationMessage = message)
            }
        }

        fun clearAccount() {
            if (mutableState.value.updating || mutableState.value.connecting) return
            mutableState.value = mutableState.value.copy(updating = true, invitationMessage = null)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        DexcomShareUploadScheduler.stop(context)
                        store.clear()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Share account could not be removed: ${error.javaClass.simpleName}")
                    mutableState.value = mutableState.value.copy(setupError = true)
                } finally {
                    mutableState.value = mutableState.value.copy(updating = false, status = store.status())
                }
            }
        }

        private companion object {
            val invitationLock = Mutex()
            const val TAG = "SugarWearShare"
        }
    }
