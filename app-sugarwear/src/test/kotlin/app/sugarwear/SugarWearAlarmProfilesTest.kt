package app.sugarwear

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SugarWearAlarmProfilesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val schedule = SugarWearAlarmSchedule()
    private val high = SugarWearAlarmType.HIGH
    private val legacy = SugarWearAlarmDefaults.defaultSettings()

    @Test fun `legacy settings retain their rules and do not activate a schedule`() {
        val settings = Json.decodeFromString<SugarWearAlarmSettings>("""{"globalMuteAll":true}""")
        assertEquals(SugarWearAlarmProfile.DAY, settings.activeProfile(epoch("2026-11-18T23:00:00Z"), zone).profile)
        assertFalse(settings.scheduleEnabled)
        assertTrue(settings.globalMuteAll)
        assertEquals(legacy.ruleFor(high), settings.ruleFor(high))
    }

    @Test fun `night starts and ends at the configured local times across midnight`() {
        val settings = legacy.copy(schedule = schedule, scheduleEnabled = true)
        assertEquals(SugarWearAlarmProfile.DAY, settings.activeProfile(epoch("2026-11-18T20:59:59Z"), zone).profile)
        assertEquals(SugarWearAlarmProfile.NIGHT, settings.activeProfile(epoch("2026-11-18T21:00:00Z"), zone).profile)
        assertEquals(SugarWearAlarmProfile.NIGHT, settings.activeProfile(epoch("2026-11-19T04:59:59Z"), zone).profile)
        assertEquals(SugarWearAlarmProfile.DAY, settings.activeProfile(epoch("2026-11-19T05:00:00Z"), zone).profile)
        assertEquals(epoch("2026-11-19T05:00:00Z"), schedule.nextChangeAfter(epoch("2026-11-18T21:00:00Z"), zone))
    }

    @Test fun `clock going back keeps the same night profile during the repeated hour`() {
        val settings = legacy.copy(schedule = SugarWearAlarmSchedule(nightStartsMinute = 150), scheduleEnabled = true)
        val first = settings.activeProfile(epoch("2026-10-25T00:40:00Z"), zone)
        assertEquals(SugarWearAlarmProfile.NIGHT, first.profile)
        assertEquals(first, settings.activeProfile(epoch("2026-10-25T01:10:00Z"), zone))
    }

    @Test fun `night rules are initialized from current day rules and edits remain independent`() {
        val day = applyCgmAlarmSettingsEdit(legacy, CgmAlarmSettingsEdit.Threshold(high, 170.0))
        val switched = applyCgmAlarmSettingsEdit(day, CgmAlarmSettingsEdit.ManualProfile(SugarWearAlarmProfile.NIGHT))
        assertEquals(170.0, switched.forProfile(SugarWearAlarmProfile.NIGHT).ruleFor(high).thresholdMgDl, 0.0)
        val edited = applyCgmAlarmSettingsEdit(switched, CgmAlarmSettingsEdit.Threshold(high, 190.0), SugarWearAlarmProfile.NIGHT)
        assertEquals(170.0, edited.ruleFor(high).thresholdMgDl, 0.0)
        assertEquals(190.0, edited.forProfile(SugarWearAlarmProfile.NIGHT).ruleFor(high).thresholdMgDl, 0.0)
        assertEquals(edited, Json.decodeFromString<SugarWearAlarmSettings>(Json.encodeToString(SugarWearAlarmSettings.serializer(), edited)))
    }

    @Test fun `switch at 22 cancels the day countdown and keeps triggered notices independent`() {
        val tenToTen = epoch("2026-11-18T20:50:00Z")
        val ten = epoch("2026-11-18T21:00:00Z")
        val settings = legacy.copy(schedule = schedule, scheduleEnabled = true)
        val low = SugarWearAlarmInstance(SugarWearAlarmType.LOW, SugarWearAlarmState.ACTIVE)
        val pendingHigh = SugarWearAlarmInstance(high, SugarWearAlarmState.PENDING_DELAY, conditionFirstMetAtEpochMs = tenToTen)
        val snapshot = SugarWearAlarmSnapshot(alarms = listOf(low, pendingHigh), activeProfile = settings.activeProfile(tenToTen, zone))

        val transitioned = snapshot.withActiveProfile(settings.activeProfile(ten, zone), ten + 1_000L)

        assertEquals(listOf(low), transitioned.alarms)
        assertFalse(transitioned.acceptsGlucoseFrom(ten - 1L))
        assertTrue(transitioned.acceptsGlucoseFrom(ten))
        assertEquals(transitioned, transitioned.withActiveProfile(settings.activeProfile(ten + 60_000L, zone), ten + 60_000L))
    }

    @Test fun `returning to the same scheduled profile a day later cannot inherit a countdown`() {
        val evening = epoch("2026-11-18T21:10:00Z")
        val tomorrow = epoch("2026-11-19T21:10:00Z")
        val settings = legacy.copy(schedule = schedule, scheduleEnabled = true)
        val snapshot =
            SugarWearAlarmSnapshot(
                alarms = listOf(SugarWearAlarmInstance(high, SugarWearAlarmState.PENDING_DELAY)),
                activeProfile = settings.activeProfile(evening, zone),
            )
        assertTrue(snapshot.withActiveProfile(settings.activeProfile(tomorrow, zone), tomorrow).alarms.isEmpty())
    }

    @Test fun `manual override pauses the schedule without discarding its times`() {
        val configured = SugarWearAlarmSchedule(nightStartsMinute = 1_380, dayStartsMinute = 420)
        val automatic = applyCgmAlarmSettingsEdit(legacy, CgmAlarmSettingsEdit.Schedule(configured))
        val manual = applyCgmAlarmSettingsEdit(automatic, CgmAlarmSettingsEdit.ManualProfile(SugarWearAlarmProfile.DAY))
        assertFalse(manual.scheduleEnabled)
        assertEquals(configured, manual.schedule)
        assertEquals(SugarWearAlarmProfile.DAY, manual.activeProfile(epoch("2026-11-18T23:00:00Z"), zone).profile)
    }

    private fun epoch(iso: String) = Instant.parse(iso).toEpochMilli()
}
