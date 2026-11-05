package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmAlertPolicyTest {
    @Test fun `signal loss starts only at sixteen minutes`() {
        val last = 1_000_000L
        assertFalse(isCgmSignalLoss(last, last + CGM_SIGNAL_LOSS_AFTER_MS - 1L))
        assertTrue(isCgmSignalLoss(last, last + CGM_SIGNAL_LOSS_AFTER_MS))
    }

    @Test fun `future signal loss deadline is preserved`() {
        val now = 1_000_000L
        val deadline = now + 120_000L

        assertEquals(deadline, nextCgmSignalLossMonitorAt(deadline, now))
    }

    @Test fun `past signal loss deadline advances to stable fifteen minute recovery boundary`() {
        val deadline = 1_000_000L
        val now = deadline + 31L * 60_000L
        val expected = deadline + 45L * 60_000L

        assertEquals(expected, nextCgmSignalLossMonitorAt(deadline, now))
        assertEquals(expected, nextCgmSignalLossMonitorAt(deadline, now + 30_000L))
    }

    @Test fun `recoverable scan miss does not immediately alert`() {
        val error = CgmCollectorError("G7-BLE-107", true, 1_000L, "Kein sendender Dexcom-G7-Sensor gefunden")
        assertFalse(shouldPostImmediateCollectorAlert(true, error, CgmSessionState.RECOVERING))
    }

    @Test fun `collector errors are suppressed while canonical Watch alarms are disabled`() {
        val error = CgmCollectorError("G7-PERM-401", false, 1_000L, "Bluetooth-Berechtigung fehlt")
        assertFalse(shouldPostImmediateCollectorAlert(false, error, CgmSessionState.USER_INTERVENTION_REQUIRED))
    }

    @Test fun `hard user action alerts immediately while Watch Direct is canonical`() {
        val error = CgmCollectorError("G7-PERM-401", false, 1_000L, "Bluetooth-Berechtigung fehlt")
        assertTrue(shouldPostImmediateCollectorAlert(true, error, CgmSessionState.USER_INTERVENTION_REQUIRED))
    }
}
