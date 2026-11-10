package app.sugarwear

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import org.junit.Assert.*
import org.junit.Test

class CgmBondAvailabilityTest {
    @Test fun `unavailable adapter cannot report a confirmed absent bond`() {
        for (state in listOf(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON)) {
            var reads = 0
            assertNull(
                readableCgmBondState({ state }, {
                    reads++
                    BluetoothDevice.BOND_NONE
                }),
            )
            assertEquals(0, reads)
        }
    }

    @Test fun `adapter switching off during the bond read is unknown`() {
        var reads = 0
        assertNull(readableCgmBondState({ if (reads++ == 0) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF }, { BluetoothDevice.BOND_NONE }))
    }

    @Test fun `radio loss while waiting fails without treating it as removed`() {
        var reads = 0
        assertFalse(awaitCgmBondRemoval(readBondState = { if (reads++ == 0) BluetoothDevice.BOND_BONDED else null }, sleep = {}))
        assertEquals(2, reads)
    }

    @Test fun `already-gone companion path still requires observable absence`() {
        var disassociated = false
        val result = removeConfirmedCgmCompanionBond("sensor", listOf(CgmOwnedCompanionAssociation(1, "sensor")), { error("already absent") }, { false }, { disassociated = true }, alreadyGone = true)
        assertEquals(CgmBondRemovalStatus.FAILED, result)
        assertFalse(disassociated)
    }
}
