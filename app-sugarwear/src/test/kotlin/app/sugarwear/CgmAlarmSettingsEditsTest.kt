package app.sugarwear

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CgmAlarmSettingsEditsTest {
    private fun baseline() = SugarWearAlarmDefaults.defaultSettings()

    @Test fun `delayed queued toggles preserve both independent edits`() =
        runTest {
            var persisted = baseline()
            val worker = CgmSettingsWorker(backgroundScope, StandardTestDispatcher(testScheduler)) { persisted }
            runCurrent()
            worker.submit({ persisted = applyCgmAlarmSettingsEdit(persisted, CgmAlarmSettingsEdit.GlobalMuteAll(true)) })
            worker.submit({ persisted = applyCgmAlarmSettingsEdit(persisted, CgmAlarmSettingsEdit.GlobalVibrateOnly(true)) })
            assertFalse(persisted.globalMuteAll)
            assertFalse(persisted.globalVibrateOnly)
            runCurrent()
            assertTrue(persisted.globalMuteAll)
            assertTrue(persisted.globalVibrateOnly)
        }

    @Test fun `high alert delay range supports 5 minutes to 240 minutes`() =
        runTest {
            var persisted = baseline()
            val worker = CgmSettingsWorker(backgroundScope, StandardTestDispatcher(testScheduler)) { persisted }
            runCurrent()
            worker.submit({ persisted = applyCgmAlarmSettingsEdit(persisted, CgmAlarmSettingsEdit.HighDelay(SugarWearAlarmType.HIGH, 240)) })
            runCurrent()
            assertEquals(240, persisted.ruleFor(SugarWearAlarmType.HIGH).highDelayMinutes)

            worker.submit({ persisted = applyCgmAlarmSettingsEdit(persisted, CgmAlarmSettingsEdit.HighDelay(SugarWearAlarmType.HIGH, 300)) })
            runCurrent()
            assertEquals(240, persisted.ruleFor(SugarWearAlarmType.HIGH).highDelayMinutes)
        }
}
