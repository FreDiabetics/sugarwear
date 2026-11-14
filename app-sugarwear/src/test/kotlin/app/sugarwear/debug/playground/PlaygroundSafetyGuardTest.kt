package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.testing.PlaygroundSafetyGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaygroundSafetyGuardTest {

    @Test
    fun `isActionAllowedInPlayground blocks forbidden commands`() {
        assertTrue(PlaygroundSafetyGuard.isActionAllowedInPlayground("select_scenario"))
        assertFalse(PlaygroundSafetyGuard.isActionAllowedInPlayground("connect_ble_device"))
        assertFalse(PlaygroundSafetyGuard.isActionAllowedInPlayground("trigger_real_alarm_sound"))
    }
}
