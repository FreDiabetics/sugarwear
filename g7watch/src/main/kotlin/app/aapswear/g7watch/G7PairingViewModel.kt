package app.aapswear.g7watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aapswear.g7.G7PersistedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

internal interface G7PairingCommands {
    val unlinkWarning: String? get() = null

    suspend fun releaseOwnership()

    suspend fun connect(code: String)

    suspend fun unlink(): Boolean
}

internal enum class G7PairingOperation { START, UNLINK }

internal enum class G7PairingCommandError { RELEASE_FAILED, CONNECT_FAILED, UNLINK_FAILED }

/** Pending platform requests are state, not lossy events. They never contain a sensor code. */
internal sealed interface G7PairingEffect {
    val token: Long

    data class Access(
        override val token: Long,
        val operation: G7PairingOperation,
    ) : G7PairingEffect

    data class VerifyBond(
        override val token: Long,
        val address: String,
    ) : G7PairingEffect
}

internal data class G7PairingUiState(
    val step: G7PairingScreenStep? = null,
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
    val effect: G7PairingEffect? = null,
    val commandError: G7PairingCommandError? = null,
    val now: Long = 0L,
)

/** Presentation/command sequencing only. The collector owns every BLE deadline and packet. */
internal class G7PairingViewModel(
    private val commands: G7PairingCommands,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(G7PairingUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var pendingCode: String? = null
    private var closeAfterUnlink = false
    private var latestSensor = G7PersistedState()
    private var openRequested = false
    private var visible = false
    private var refreshJob: Job? = null
    private var completionJob: Job? = null

    fun open(step: G7PairingScreenStep = G7PairingScreenStep.ENTER_CODE, startedAt: Long = Long.MAX_VALUE) {
        if (state.value.step != null) return
        mutableState.update { it.copy(step = step, startedAt = startedAt) }
        if (step == G7PairingScreenStep.CONNECTED) showSuccess()
        updateClock()
    }

    fun requestOpen() {
        openRequested = true
    }

    fun editCode(value: String) {
        mutableState.update { it.copy(draft = value) }
    }

    fun start(code: String): Boolean {
        if (!canStartG7Pairing(state.value.step, state.value.startInFlight, code)) return false
        pendingCode = code
        mutableState.update {
            it.copy(
                step = G7PairingScreenStep.CONNECTING,
                startInFlight = true,
                accessPending = true,
                bondReady = false,
                bondFailure = null,
                failureMessage = null,
                startedAt = clock(),
                commandError = null,
            )
        }
        prepare(G7PairingOperation.START)
        updateClock()
        return true
    }

    fun unlink(closePairing: Boolean = false) {
        if (state.value.unlinkInFlight) return
        closeAfterUnlink = closePairing
        pendingCode = null
        mutableState.update { it.copy(unlinkInFlight = true, unlinkFailureReason = null, commandError = null) }
        prepare(G7PairingOperation.UNLINK)
    }

    private fun prepare(operation: G7PairingOperation) {
        val token = ++generation
        mutableState.update { it.copy(effect = null) }
        workScope.launch {
            try {
                commands.releaseOwnership()
                if (token == generation) mutableState.update { it.copy(effect = G7PairingEffect.Access(token, operation)) }
            } catch (error: Exception) {
                reportExpected(error)
                if (token == generation) {
                    mutableState.update { it.copy(commandError = G7PairingCommandError.RELEASE_FAILED) }
                    failAccess(operation, "Sensorempfang konnte nicht beendet werden")
                }
            }
        }
    }

    fun accessReady(token: Long) {
        val effect = state.value.effect as? G7PairingEffect.Access ?: return
        if (token != generation || token != effect.token) return
        val code = pendingCode
        mutableState.update { it.copy(effect = null, accessPending = false) }
        workScope.launch {
            try {
                when (effect.operation) {
                    G7PairingOperation.START -> {
                        if (token != generation || code == null) return@launch
                        commands.connect(code)
                        if (token == generation) {
                            pendingCode = null
                            mutableState.update { it.copy(startInFlight = false, draft = "") }
                            sensorChanged(latestSensor)
                        }
                    }
                    G7PairingOperation.UNLINK -> {
                        val detached = commands.unlink()
                        if (token == generation) finishUnlink(detached)
                    }
                }
            } catch (error: Exception) {
                reportExpected(error)
                if (token == generation) {
                    mutableState.update { it.copy(commandError = if (effect.operation == G7PairingOperation.START) G7PairingCommandError.CONNECT_FAILED else G7PairingCommandError.UNLINK_FAILED) }
                    failAccess(effect.operation, "Verbindung konnte nicht gestartet werden. Bitte erneut versuchen.")
                }
            }
        }
    }

    fun accessFailed(token: Long, reason: String) {
        val effect = state.value.effect as? G7PairingEffect.Access ?: return
        if (token != generation || token != effect.token) return
        failAccess(effect.operation, reason)
    }

    private fun failAccess(operation: G7PairingOperation, reason: String) {
        pendingCode = null
        when (operation) {
            G7PairingOperation.START -> mutableState.update { it.copy(effect = null, accessPending = false, startInFlight = false, step = G7PairingScreenStep.ENTER_CODE, failureMessage = reason) }
            G7PairingOperation.UNLINK -> {
                mutableState.update { it.copy(effect = null, unlinkFailureReason = reason) }
                finishUnlink(false)
            }
        }
        updateClock()
    }

    private fun finishUnlink(detached: Boolean) {
        mutableState.update { it.copy(unlinkInFlight = false, unlinkFailed = !detached, effect = null, unlinkFailureReason = if (detached) commands.unlinkWarning else it.unlinkFailureReason) }
        if (closeAfterUnlink) close()
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
            mutableState.update { it.copy(step = G7PairingScreenStep.ENTER_CODE, startInFlight = false) }
            updateClock()
        }
    }

    fun sensorChanged(sensor: G7PersistedState) {
        latestSensor = sensor
        if (openRequested) {
            openRequested = false
            if (!hasG7SensorAssociation(sensor)) open()
        }
        if (state.value.step == G7PairingScreenStep.CONNECTING && state.value.startedAt == Long.MAX_VALUE) {
            mutableState.update { it.copy(startedAt = sensor.pairingStartedAtEpochMs ?: clock()) }
        }
        val current = state.value
        if (current.startInFlight || current.step != G7PairingScreenStep.CONNECTING) return
        if (advanceG7PairingScreen(current.step, sensor) != G7PairingScreenStep.CONNECTED) return
        if (sdkInt >= 36 && !current.bondReady) {
            if (!current.bondInFlight && current.bondFailure == null) {
                val address = sensor.sensor?.deviceAddress?.takeIf(String::isNotBlank) ?: return
                mutableState.update { it.copy(bondInFlight = true, effect = G7PairingEffect.VerifyBond(++generation, address)) }
            }
            return
        }
        showSuccess()
    }

    fun bondReady(token: Long) {
        if (state.value.effect !is G7PairingEffect.VerifyBond || token != generation) return
        mutableState.update { it.copy(bondInFlight = false, bondReady = true, effect = null) }
        sensorChanged(latestSensor)
    }

    fun bondFailed(token: Long, reason: String) {
        if (state.value.effect !is G7PairingEffect.VerifyBond || token != generation) return
        mutableState.update { it.copy(bondInFlight = false, bondFailure = reason, effect = null) }
    }

    private fun showSuccess() {
        mutableState.update { it.copy(step = G7PairingScreenStep.CONNECTED, startInFlight = false) }
        updateClock()
        if (completionJob?.isActive == true) return
        completionJob =
            workScope.launch {
                delay(G7_PAIRING_SUCCESS_DURATION_MS)
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
                            is G7PairingEffect.Access -> effect.copy(token = token)
                            is G7PairingEffect.VerifyBond -> effect.copy(token = token)
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
        if (!visible || state.value.step != G7PairingScreenStep.CONNECTING) return
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
