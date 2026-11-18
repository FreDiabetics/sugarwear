package app.sugarwear

import org.junit.Assert.*
import org.junit.Test

class CgmSensorRecoveryTest {
    @Test
    fun `returns bluetooth unavailable when radio is off`() {
        val state =
            CgmSensorRecoveryPlanner.determine(
                bluetoothAvailable = false,
                permissionGranted = true,
                recordedAddress = "AA:BB:CC:DD:EE:FF",
                candidates = emptyList(),
            )
        assertEquals(CgmRecoveryState.BluetoothUnavailable, state)
    }

    @Test
    fun `returns permission required when permission is missing`() {
        val state =
            CgmSensorRecoveryPlanner.determine(
                bluetoothAvailable = true,
                permissionGranted = false,
                recordedAddress = "AA:BB:CC:DD:EE:FF",
                candidates = emptyList(),
            )
        assertEquals(CgmRecoveryState.PermissionRequired, state)
    }

    @Test
    fun `detects existing bonded sensor candidate`() {
        val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
        val state =
            CgmSensorRecoveryPlanner.determine(
                bluetoothAvailable = true,
                permissionGranted = true,
                recordedAddress = "AA:BB:CC:DD:EE:FF",
                candidates = listOf(candidate),
            )
        assertTrue(state is CgmRecoveryState.ExistingBondDetected)
        assertEquals(candidate, (state as CgmRecoveryState.ExistingBondDetected).candidate)
    }

    @Test
    fun `requires candidate selection when multiple candidates exist`() {
        val c1 = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1", isBonded = true)
        val c2 = CgmBondCandidate("11:22:33:44:55:66", "DXCM2", isBonded = true)
        val state =
            CgmSensorRecoveryPlanner.determine(
                bluetoothAvailable = true,
                permissionGranted = true,
                recordedAddress = null,
                candidates = listOf(c1, c2),
            )
        assertTrue(state is CgmRecoveryState.CandidateSelectionRequired)
    }
}
