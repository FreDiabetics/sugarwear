package app.sugarwear

internal object CgmAlarmArbitrator {
    fun priority(type: SugarWearAlarmType): Int = SugarWearAlarmDefaults.priority(type)

    fun select(
        alarms: Collection<SugarWearAlarmInstance>,
    ): SugarWearAlarmInstance? =
        alarms
            .asSequence()
            .filter { CgmSupportedAlarms.isSupported(it.type) }
            .filter { it.state == SugarWearAlarmState.ACTIVE }
            .sortedWith(
                compareByDescending<SugarWearAlarmInstance> { priority(it.type) }
                    .thenBy { it.triggeredAtEpochMs ?: 0L }
                    .thenBy { it.type.name },
            )
            .firstOrNull()
}
