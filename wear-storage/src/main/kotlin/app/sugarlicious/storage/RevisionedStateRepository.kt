package app.sugarlicious.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RevisionedStateBackend<T> {
    suspend fun readPrimary(): T?

    suspend fun readSecondary(): T?

    suspend fun writePrimary(value: T)

    suspend fun writeSecondary(value: T)
}

/** Restart-safe two-copy persistence with deterministic reconciliation. */
class RevisionedStateRepository<T>(
    private val backend: RevisionedStateBackend<T>,
    private val revisionOf: (T) -> Long,
    private val withRevision: (T, Long) -> T,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun read(): T? = mutex.withLock { reconcileUnlocked() }

    suspend fun write(value: T): T =
        mutex.withLock {
            val current = reconcileUnlocked()
            val nextRevision = maxOf(current?.let(revisionOf) ?: 0L, revisionOf(value)) + 1L
            val committed = withRevision(value, nextRevision)
            backend.writePrimary(committed)
            backend.writeSecondary(committed)
            committed
        }

    private suspend fun reconcileUnlocked(): T? {
        val primary = backend.readPrimary()
        val secondary = backend.readSecondary()
        val winner =
            when {
                primary == null -> secondary
                secondary == null -> primary
                revisionOf(primary) >= revisionOf(secondary) -> primary
                else -> secondary
            } ?: return null
        if (primary != winner) backend.writePrimary(winner)
        if (secondary != winner) backend.writeSecondary(winner)
        return winner
    }
}
