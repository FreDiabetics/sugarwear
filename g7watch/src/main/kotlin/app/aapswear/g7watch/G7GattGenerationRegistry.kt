package app.aapswear.g7watch

internal data class G7GattOwnership(
    val attemptId: Long,
    val generation: Long,
)

internal object G7GattGenerationRegistry {
    private val nextGeneration =
        java.util.concurrent.atomic
            .AtomicLong(0L)

    @Volatile private var active: G7GattOwnership? = null

    @Synchronized fun acquire(attemptId: Long): G7GattOwnership =
        G7GattOwnership(attemptId, nextGeneration.incrementAndGet()).also { active = it }

    fun isActive(ownership: G7GattOwnership): Boolean = active == ownership

    @Synchronized fun invalidate(ownership: G7GattOwnership) {
        if (active == ownership) active = null
    }

    @Synchronized internal fun resetForTest() {
        active = null
        nextGeneration.set(0L)
    }
}
