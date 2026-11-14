package app.sugarlicious.storage

import android.content.Context
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

interface CanonicalStateBackend : RevisionedStateBackend<TherapyDisplayState> {
    suspend fun readPhone(): TherapyDisplayState?

    suspend fun readDisplay(): TherapyDisplayState?

    suspend fun writePhone(value: TherapyDisplayState)

    suspend fun writeDisplay(value: TherapyDisplayState)

    override suspend fun readPrimary(): TherapyDisplayState? = readPhone()

    override suspend fun readSecondary(): TherapyDisplayState? = readDisplay()

    override suspend fun writePrimary(value: TherapyDisplayState) = writePhone(value)

    override suspend fun writeSecondary(value: TherapyDisplayState) = writeDisplay(value)
}

/** Restart-safe two-copy commit protocol for Mobile's raw and canonical state. */
class CanonicalStateStore(
    backend: CanonicalStateBackend,
) {
    private val repository =
        RevisionedStateRepository(
            backend = backend,
            revisionOf = TherapyDisplayState::canonicalRevision,
            withRevision = { value, revision -> value.copy(canonicalRevision = revision) },
            mutex = commitMutex,
        )

    constructor(context: Context) : this(AndroidCanonicalStateBackend(context.applicationContext))

    suspend fun reconcile(): TherapyDisplayState? = repository.read()

    suspend fun commit(value: TherapyDisplayState): TherapyDisplayState = repository.write(value)

    private companion object {
        val commitMutex = Mutex()
    }
}

private class AndroidCanonicalStateBackend(
    context: Context,
) : CanonicalStateBackend {
    private val phone = PhoneTherapyStateStore(context)
    private val display = TherapyStateStore(context)

    override suspend fun readPhone(): TherapyDisplayState? = phone.state.first()

    override suspend fun readDisplay(): TherapyDisplayState? = display.state.first()

    override suspend fun writePhone(value: TherapyDisplayState) = phone.save(value)

    override suspend fun writeDisplay(value: TherapyDisplayState) = display.save(value)
}
