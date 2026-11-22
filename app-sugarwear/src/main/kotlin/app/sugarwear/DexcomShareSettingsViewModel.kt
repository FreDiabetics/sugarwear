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
import kotlinx.coroutines.withContext

internal data class DexcomShareSettingsUiState(
    val status: DexcomShareUploadStatus,
    val connecting: Boolean = false,
    val updating: Boolean = false,
    val setupError: Boolean = false,
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
            mutableState.value = mutableState.value.copy(updating = true, connecting = true, setupError = false)
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
                if (candidate != null || config.receiverSerial.isEmpty()) {
                    val confirmed = if (candidate == null) client.checkReceiver(config) else client.checkReceiver(config, candidate)
                    store.saveReceiver(confirmed, config.revision)
                }
            } catch (problem: DexcomShareProtocolException) {
                store.markError(config, problem.issue)
            }
        }

        fun clearAccount() {
            if (mutableState.value.updating || mutableState.value.connecting) return
            mutableState.value = mutableState.value.copy(updating = true)
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
            const val TAG = "SugarWearShare"
        }
    }
