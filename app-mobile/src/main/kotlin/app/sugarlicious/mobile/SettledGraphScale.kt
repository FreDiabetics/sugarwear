package app.sugarlicious.mobile

/** Holds vertical geometry during navigation; interpolates only after navigation settles. */
internal class SettledGraphScale<T>(
    private val interpolate: (T, T, Float) -> T,
) {
    private var current: T? = null
    private var origin: T? = null
    private var target: T? = null
    private var started = 0L
    var animating = false
        private set

    fun resolve(proposed: T, navigating: Boolean, nowMs: Long): T {
        val previous =
            current ?: return proposed.also {
                current = it
                target = it
            }
        if (navigating) {
            origin = previous
            target = previous
            animating = false
            return previous
        }
        if (target != proposed) {
            origin = previous
            target = proposed
            started = nowMs
        }
        val fraction = ((nowMs - started) / 420f).coerceIn(0f, 1f)
        val eased = fraction * fraction * (3f - 2f * fraction)
        animating = fraction < 1f && origin != null && origin != target
        return (if (animating) interpolate(origin!!, proposed, eased) else proposed).also { current = it }
    }
}
