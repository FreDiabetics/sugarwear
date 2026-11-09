package app.sugarwear

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmBluetoothBondDiagnosticsTest {
    @Test
    fun `bond state is separate from any GATT connection state`() {
        assertEquals("Nicht gekoppelt (BOND_NONE)", cgmBondStateLabel(BluetoothDevice.BOND_NONE))
        assertEquals("Kopplung läuft (BOND_BONDING)", cgmBondStateLabel(BluetoothDevice.BOND_BONDING))
        assertEquals("Gekoppelt (BOND_BONDED)", cgmBondStateLabel(BluetoothDevice.BOND_BONDED))
        assertEquals("Nicht ermittelbar", cgmBondStateLabel(null))
    }

    @Test
    fun `unrecorded G7 bonds are shown as read only masked diagnostic entries`() {
        val inventory =
            cgmBondInventory(
                setOf("AA:BB:CC:DD:EE:01"),
                listOf(
                    "AA:BB:CC:DD:EE:01" to "DXCM1234",
                    "AA:BB:CC:DD:EE:02" to "DX011234",
                    "AA:BB:CC:DD:EE:02" to "DX011234",
                    "AA:BB:CC:DD:EE:03" to "Headphones",
                ),
                BluetoothDevice.BOND_BONDED,
            )
        assertEquals(1, inventory.knownSensorCount)
        assertEquals(1, inventory.otherG7BondCount)
        assertTrue(inventory.otherG7Devices.single().contains("EE:02"))
        assertFalse(inventory.otherG7Devices.single().contains("AA:BB:CC:DD"))
        assertEquals("Gekoppelt (BOND_BONDED)", inventory.activeBondState)
    }

    @Test
    fun `contradictory sensor identities are explicitly flagged`() {
        val inventory =
            cgmBondInventory(
                setOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
                emptyList(),
                null,
            )
        assertEquals("Widersprüchliche Sensoradressen", inventory.activeBondState)
        assertTrue(inventory.warning?.contains("keine automatische Entkopplung") == true)
    }

    @Test
    fun `malformed addresses are never exposed`() {
        assertEquals("Adresse nicht darstellbar", cgmMaskedBluetoothAddress("invalid"))
    }

    @Test
    fun `unavailable bond inventory is unknown and never reported as zero devices`() {
        val unavailable =
            cgmBondInventory(
                setOf("AA:BB:CC:DD:EE:01"),
                emptyList(),
                null,
            ).copy(inventoryAvailable = false)
        assertEquals("Nicht ermittelbar", unavailable.otherG7BondCountLabel())
        assertEquals("0", unavailable.copy(inventoryAvailable = true).otherG7BondCountLabel())
    }

    @Test
    fun `known bond inventory has a real zero when no other G7 is bonded`() {
        val inventory =
            cgmBondInventory(
                setOf("AA:BB:CC:DD:EE:01"),
                listOf("AA:BB:CC:DD:EE:01" to "DXCM1234"),
                BluetoothDevice.BOND_BONDED,
            )
        assertTrue(inventory.inventoryAvailable)
        assertEquals("0", inventory.otherG7BondCountLabel())
    }
}
