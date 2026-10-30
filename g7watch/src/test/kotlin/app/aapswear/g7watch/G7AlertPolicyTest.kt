package app.aapswear.g7watch

import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7AlertPolicyTest {
    @Test fun `signal loss starts only at sixteen minutes`() {
        val last = 1_000_000L
        assertFalse(isG7SignalLoss(last, last + G7_SIGNAL_LOSS_AFTER_MS - 1L))
        assertTrue(isG7SignalLoss(last, last + G7_SIGNAL_LOSS_AFTER_MS))
    }

    @Test fun `future signal loss deadline is preserved`() {
        val now = 1_000_000L
        val deadline = now + 120_000L

        assertEquals(deadline, nextG7SignalLossMonitorAt(deadline, now))
    }

    @Test fun `past signal loss deadline advances to stable fifteen minute recovery boundary`() {
        val deadline = 1_000_000L
        val now = deadline + 31L * 60_000L
        val expected = deadline + 45L * 60_000L

        assertEquals(expected, nextG7SignalLossMonitorAt(deadline, now))
        assertEquals(expected, nextG7SignalLossMonitorAt(deadline, now + 30_000L))
    }

    @Test fun `recoverable scan miss does not immediately alert`() {
        val error = G7CollectorError("G7-BLE-107", true, 1_000L, "Kein sendender Dexcom-G7-Sensor gefunden")
        assertFalse(shouldPostImmediateCollectorAlert(true, error, G7SessionState.RECOVERING))
    }

    @Test fun `collector errors are suppressed while canonical Watch alarms are disabled`() {
        val error = G7CollectorError("G7-PERM-401", false, 1_000L, "Bluetooth-Berechtigung fehlt")
        assertFalse(shouldPostImmediateCollectorAlert(false, error, G7SessionState.USER_INTERVENTION_REQUIRED))
    }

    @Test fun `hard user action alerts immediately while Watch Direct is canonical`() {
        val error = G7CollectorError("G7-PERM-401", false, 1_000L, "Bluetooth-Berechtigung fehlt")
        assertTrue(shouldPostImmediateCollectorAlert(true, error, G7SessionState.USER_INTERVENTION_REQUIRED))
    }
}
