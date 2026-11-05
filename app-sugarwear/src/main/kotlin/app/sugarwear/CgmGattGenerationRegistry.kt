package app.sugarwear

internal data class CgmGattOwnership(
    val attemptId: Long,
    val generation: Long,
)

internal object CgmGattGenerationRegistry {
    private val nextGeneration =
        java.util.concurrent.atomic
            .AtomicLong(0L)

    @Volatile private var active: CgmGattOwnership? = null

    @Synchronized fun acquire(attemptId: Long): CgmGattOwnership =
        CgmGattOwnership(attemptId, nextGeneration.incrementAndGet()).also { active = it }

    fun isActive(ownership: CgmGattOwnership): Boolean = active == ownership

    @Synchronized fun invalidate(ownership: CgmGattOwnership) {
        if (active == ownership) active = null
    }

    @Synchronized internal fun resetForTest() {
        active = null
        nextGeneration.set(0L)
    }
}
