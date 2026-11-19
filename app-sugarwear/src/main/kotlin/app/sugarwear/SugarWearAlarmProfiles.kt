package app.sugarwear

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Serializable
enum class SugarWearAlarmProfile(
    val label: String,
) {
    DAY("Tag"),
    NIGHT("Nacht"),
}

@Serializable
data class ActiveSugarWearAlarmProfile(
    val profile: SugarWearAlarmProfile,
    val scheduledStartEpochMs: Long? = null,
)

@Serializable
data class SugarWearAlarmSchedule(
    val nightStartsMinute: Int = 22 * 60,
    val dayStartsMinute: Int = 6 * 60,
) {
    init {
        require(nightStartsMinute in 0 until MINUTES_IN_DAY && dayStartsMinute in 0 until MINUTES_IN_DAY)
        require(nightStartsMinute != dayStartsMinute)
    }

    internal fun activeAt(now: Long, zone: ZoneId): ActiveSugarWearAlarmProfile =
        boundaries(now, zone).last { it.first <= now }.let { (time, profile) ->
            ActiveSugarWearAlarmProfile(profile, time)
        }

    internal fun nextChangeAfter(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        boundaries(now, zone).first { it.first > now }.first

    private fun boundaries(now: Long, zone: ZoneId): List<Pair<Long, SugarWearAlarmProfile>> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // Resolve local clock times once per date so repeated DST hours cannot flip the profile.
        return listOf(today.minusDays(1), today, today.plusDays(1))
            .flatMap { date ->
                listOf(
                    boundary(date, nightStartsMinute, zone) to SugarWearAlarmProfile.NIGHT,
                    boundary(date, dayStartsMinute, zone) to SugarWearAlarmProfile.DAY,
                )
            }.sortedBy { it.first }
    }

    private fun boundary(date: LocalDate, minute: Int, zone: ZoneId): Long =
        date
            .atTime(LocalTime.ofSecondOfDay(minute * 60L))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    companion object {
        private const val MINUTES_IN_DAY = 24 * 60
    }
}

internal fun SugarWearAlarmSnapshot.withActiveProfile(
    selection: ActiveSugarWearAlarmProfile,
    now: Long,
    configurationChanged: Boolean = false,
): SugarWearAlarmSnapshot {
    if (activeProfile == selection) return this
    if (configurationChanged && activeProfile.profile == selection.profile) return copy(activeProfile = selection)
    return copy(
        activeProfile = selection,
        glucoseProfileStartedAtEpochMs = if (configurationChanged) now else selection.scheduledStartEpochMs,
        alarms = alarms.filterNot { it.type == SugarWearAlarmType.HIGH && it.state == SugarWearAlarmState.PENDING_DELAY },
    )
}

internal fun SugarWearAlarmSnapshot.acceptsGlucoseFrom(measuredAt: Long?): Boolean =
    glucoseProfileStartedAtEpochMs?.let { measuredAt != null && measuredAt >= it } ?: true
