package app.sugarwear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sugarlicious.sensor.protocol.CgmPersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

internal interface CgmPairingCommands {
    val unlinkWarning: String? get() = null

    suspend fun releaseOwnership()

    suspend fun connect(code: String)

    suspend fun unlink(): Boolean
}

internal enum class CgmPairingOperation { START, UNLINK }

internal enum class CgmPairingCommandError { RELEASE_FAILED, CONNECT_FAILED, UNLINK_FAILED }

/** Pending platform requests are state, not lossy events. They never contain a sensor code. */
internal sealed interface CgmPairingEffect {
    val token: Long

    data class Access(
        override val token: Long,
        val operation: CgmPairingOperation,
    ) : CgmPairingEffect

    data class VerifyBond(
        override val token: Long,
        val address: String,
    ) : CgmPairingEffect
}

internal data class CgmPairingUiState(
    val step: CgmPairingScreenStep? = null,
    val draft: String = "",
    val startInFlight: Boolean = false,
    val accessPending: Boolean = false,
    val bondInFlight: Boolean = false,
    val bondReady: Boolean = false,
    val bondFailure: String? = null,
    val startedAt: Long = Long.MAX_VALUE,
    val failureMessage: String? = null,
    val unlinkInFlight: Boolean = false,
    val unlinkFailed: Boolean = false,
    val unlinkFailureReason: String? = null,
    val effect: CgmPairingEffect? = null,
    val commandError: CgmPairingCommandError? = null,
    val now: Long = 0L,
)

/** Presentation/command sequencing only. The collector owns every BLE deadline and packet. */
@dagger.hilt.android.lifecycle.HiltViewModel
internal class SensorConnectionViewModel(
    private val commands: CgmPairingCommands,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
) : ViewModel() {
    @javax.inject.Inject
    constructor(commands: CgmPairingCommands) : this(commands, scope = null)

    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(CgmPairingUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var pendingCode: String? = null
    private var closeAfterUnlink = false
    private var latestSensor = CgmPersistedState()
    private var openRequested = false
    private var visible = false
    private var refreshJob: Job? = null
    private var completionJob: Job? = null

    fun open(step: CgmPairingScreenStep = CgmPairingScreenStep.ENTER_CODE, startedAt: Long = Long.MAX_VALUE) {
        if (state.value.step != null) return
        mutableState.update { it.copy(step = step, startedAt = startedAt) }
        if (step == CgmPairingScreenStep.CONNECTED) showSuccess()
        updateClock()
    }

    fun requestOpen() {
        openRequested = true
    }

    fun editCode(value: String) {
        mutableState.update { it.copy(draft = value) }
    }

    fun start(code: String): Boolean {
        if (!canStartCgmPairing(state.value.step, state.value.startInFlight, code)) return false
        pendingCode = code
        mutableState.update {
            it.copy(
                step = CgmPairingScreenStep.CONNECTING,
                startInFlight = true,
                accessPending = true,
                bondReady = false,
                bondFailure = null,
                failureMessage = null,
                startedAt = clock(),
                commandError = null,
            )
        }
        prepare(CgmPairingOperation.START)
        updateClock()
        return true
    }

    fun unlink(closePairing: Boolean = false) {
        if (state.value.unlinkInFlight) return
        closeAfterUnlink = closePairing
        pendingCode = null
        mutableState.update { it.copy(unlinkInFlight = true, unlinkFailureReason = null, commandError = null) }
        prepare(CgmPairingOperation.UNLINK)
    }

    private fun prepare(operation: CgmPairingOperation) {
        val token = ++generation
        mutableState.update { it.copy(effect = null) }
        workScope.launch {
            try {
                commands.releaseOwnership()
                if (token == generation) mutableState.update { it.copy(effect = CgmPairingEffect.Access(token, operation)) }
            } catch (error: Exception) {
                reportExpected(error)
                if (token == generation) {
                    mutableState.update { it.copy(commandError = CgmPairingCommandError.RELEASE_FAILED) }
                    failAccess(operation, "Sensorempfang konnte nicht beendet werden")
                }
            }
        }
    }

    fun accessReady(token: Long) {
        val effect = state.value.effect as? CgmPairingEffect.Access ?: return
        if (token != generation || token != effect.token) return
        val code = pendingCode
        mutableState.update { it.copy(effect = null, accessPending = false) }
        workScope.launch {
            try {
                when (effect.operation) {
                    CgmPairingOperation.START -> {
                        if (token != generation || code == null) return@launch
                        commands.connect(code)
                        if (token == generation) {
                            pendingCode = null
                            mutableState.update { it.copy(startInFlight = false, draft = "") }
                            sensorChanged(latestSensor)
                        }
                    }
                    CgmPairingOperation.UNLINK -> {
                        val detached = commands.unlink()
                        if (token == generation) finishUnlink(detached)
                    }
                }
            } catch (error: Exception) {
                reportExpected(error)
                if (token == generation) {
                    mutableState.update { it.copy(commandError = if (effect.operation == CgmPairingOperation.START) CgmPairingCommandError.CONNECT_FAILED else CgmPairingCommandError.UNLINK_FAILED) }
                    failAccess(effect.operation, "Verbindung konnte nicht gestartet werden. Bitte erneut versuchen.")
                }
            }
        }
    }

    fun accessFailed(token: Long, reason: String) {
        val effect = state.value.effect as? CgmPairingEffect.Access ?: return
        if (token != generation || token != effect.token) return
        failAccess(effect.operation, reason)
    }

    private fun failAccess(operation: CgmPairingOperation, reason: String) {
        pendingCode = null
        when (operation) {
            CgmPairingOperation.START -> mutableState.update { it.copy(effect = null, accessPending = false, startInFlight = false, step = CgmPairingScreenStep.ENTER_CODE, failureMessage = reason) }
            CgmPairingOperation.UNLINK -> {
                mutableState.update { it.copy(effect = null, unlinkFailureReason = reason) }
                finishUnlink(false)
            }
        }
        updateClock()
    }

    private fun finishUnlink(detached: Boolean) {
        mutableState.update { it.copy(unlinkInFlight = false, unlinkFailed = !detached, effect = null, unlinkFailureReason = if (detached) commands.unlinkWarning else it.unlinkFailureReason) }
        if (closeAfterUnlink && detached) close()
    }

    fun cancelPendingAccess() {
        ++generation
        pendingCode = null
        mutableState.update { it.copy(accessPending = false, startInFlight = false, effect = null, step = null) }
        updateClock()
    }

    fun retry() {
        if (state.value.bondFailure != null) {
            mutableState.update { it.copy(bondFailure = null, startInFlight = false) }
            sensorChanged(latestSensor)
        } else {
            mutableState.update { it.copy(step = CgmPairingScreenStep.ENTER_CODE, startInFlight = false) }
            updateClock()
        }
    }

    fun sensorChanged(sensor: CgmPersistedState) {
        latestSensor = sensor
        if (openRequested) {
            openRequested = false
            if (!hasCgmSensorAssociation(sensor)) open()
        }
        if (state.value.step == CgmPairingScreenStep.CONNECTING && state.value.startedAt == Long.MAX_VALUE) {
            mutableState.update { it.copy(startedAt = sensor.pairingStartedAtEpochMs ?: clock()) }
        }
        val current = state.value
        if (current.startInFlight || current.step != CgmPairingScreenStep.CONNECTING) return
        if (advanceCgmPairingScreen(current.step, sensor) != CgmPairingScreenStep.CONNECTED) return
        if (sdkInt >= 36 && !current.bondReady) {
            if (!current.bondInFlight && current.bondFailure == null) {
                val address = sensor.sensor?.deviceAddress?.takeIf(String::isNotBlank) ?: return
                mutableState.update { it.copy(bondInFlight = true, effect = CgmPairingEffect.VerifyBond(++generation, address)) }
            }
            return
        }
        showSuccess()
    }

    fun bondReady(token: Long) {
        if (state.value.effect !is CgmPairingEffect.VerifyBond || token != generation) return
        mutableState.update { it.copy(bondInFlight = false, bondReady = true, effect = null) }
        sensorChanged(latestSensor)
    }

    fun bondFailed(token: Long, reason: String) {
        if (state.value.effect !is CgmPairingEffect.VerifyBond || token != generation) return
        mutableState.update { it.copy(bondInFlight = false, bondFailure = reason, effect = null) }
    }

    private fun showSuccess() {
        mutableState.update { it.copy(step = CgmPairingScreenStep.CONNECTED, startInFlight = false) }
        updateClock()
        if (completionJob?.isActive == true) return
        completionJob =
            workScope.launch {
                delay(CGM_PAIRING_SUCCESS_DURATION_MS)
                close()
            }
    }

    fun close() {
        ++generation
        pendingCode = null
        completionJob?.cancel()
        completionJob = null
        mutableState.update { it.copy(step = null, draft = "", effect = null, startInFlight = false, accessPending = false, bondInFlight = false) }
        updateClock()
    }

    /** A replacement window can reclaim pending consent; callbacks from the old window expire. */
    fun platformDetached() {
        state.value.effect?.let { effect ->
            val token = ++generation
            mutableState.update {
                it.copy(
                    effect =
                        when (effect) {
                            is CgmPairingEffect.Access -> effect.copy(token = token)
                            is CgmPairingEffect.VerifyBond -> effect.copy(token = token)
                        },
                )
            }
        }
    }

    fun setVisible(value: Boolean) {
        visible = value
        updateClock()
    }

    private fun updateClock() {
        refreshJob?.cancel()
        refreshJob = null
        if (!visible || state.value.step != CgmPairingScreenStep.CONNECTING) return
        refreshJob =
            workScope.launch {
                while (true) {
                    delay(PRESENTATION_REFRESH_MS)
                    mutableState.update { it.copy(now = clock()) }
                }
            }
    }

    private fun reportExpected(error: Exception) {
        if (error is CancellationException) throw error
        if (error !is IOException && error !is SecurityException && error !is android.database.SQLException && error !is IllegalStateException) throw error
        java.util.logging.Logger
            .getLogger("G7Pairing")
            .warning("Pairing command failed: ${error.javaClass.simpleName}")
    }

    private companion object {
        const val PRESENTATION_REFRESH_MS = 1_000L
    }
}
