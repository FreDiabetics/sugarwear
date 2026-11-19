package app.sugarwear

internal object CgmSupportedAlarms {
    val types = SugarWearAlarmType.entries

    fun isSupported(type: SugarWearAlarmType): Boolean = type in types
}
