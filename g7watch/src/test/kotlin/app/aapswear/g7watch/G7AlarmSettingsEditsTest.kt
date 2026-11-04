package app.aapswear.g7watch

import app.aapswear.g7.CgmAlarmSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class G7AlarmSettingsEditsTest {
    private fun baseline() = CgmAlarmSettings(250.0, 180.0, 70.0, 55.0, 2.0, 2.0, 16, soundEnabled = true)

    @Test fun `delayed queued toggles preserve both independent edits`() =
        runTest {
            var persisted = baseline()
            val worker = G7SettingsWorker(backgroundScope, StandardTestDispatcher(testScheduler)) { persisted }
            runCurrent()
            worker.submit({ persisted = applyG7AlarmSettingsEdit(persisted, G7AlarmSettingsEdit.Sound(false)) })
            worker.submit({ persisted = applyG7AlarmSettingsEdit(persisted, G7AlarmSettingsEdit.Vibration(false)) })
            assertTrue(persisted.soundEnabled)
            assertTrue(persisted.vibrationEnabled)
            runCurrent()
            assertFalse(persisted.soundEnabled)
            assertFalse(persisted.vibrationEnabled)
        }

    @Test fun `repeated interval taps accumulate before any worker write completes`() =
        runTest {
            var persisted = baseline().copy(repeatIntervalMinutes = 15)
            val worker = G7SettingsWorker(backgroundScope, StandardTestDispatcher(testScheduler)) { persisted }
            runCurrent()
            repeat(3) { worker.submit({ persisted = applyG7AlarmSettingsEdit(persisted, G7AlarmSettingsEdit.AdjustRepeat(5)) }) }
            assertEquals(15, persisted.repeatIntervalMinutes)
            runCurrent()
            assertEquals(30, persisted.repeatIntervalMinutes)
        }
}
