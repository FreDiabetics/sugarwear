package app.sugarlicious.model

/** Single injectable time source for all time-dependent domain decisions. */
fun interface AppClock {
    fun nowEpochMs(): Long
}

object SystemAppClock : AppClock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
