package app.sugarlicious.model

/** Shared progress semantics for Mobile and Wear therapy rings. */
object TherapyProgressSemantics {
    fun scaled(
        value: Double?,
        maximum: Double?,
    ): Float? {
        val safeValue = value?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val safeMaximum = maximum?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return (safeValue / safeMaximum).toFloat().coerceIn(0f, 1f)
    }

    fun basal(percent: Int?): Float? =
        percent?.let {
            if (it <= 100) {
                it.coerceAtLeast(0) / 200f
            } else {
                0.5f + (it.coerceAtMost(500) - 100) / 800f
            }
        }
}
