package app.sugarwear

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CgmSensorDetachPolicyTest {
    @Test fun `explicit connect starts only after successful sensor preparation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<String>()
        val sensor = CgmSensor("new")
        val result =
            connectCgmSensorToThisWatch(
                context,
                "0123",
                prepare = { _, code ->
                    assertEquals("0123", code)
                    events += "prepare"
                    sensor
                },
                start = { events += "start" },
            )
        assertEquals(sensor, result)
        assertEquals(listOf("prepare", "start"), events)
    }

    @Test fun `failed explicit preparation cannot start collection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var starts = 0
        val result =
            runCatching {
                connectCgmSensorToThisWatch(context, "0123", prepare = { _, _ -> error("Old bond remains") }, start = { starts++ })
            }
        assertTrue(result.isFailure)
        assertEquals(0, starts)
    }

    @Test fun `new sensor preparation clears old association and waits for explicit reception start`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val stateStore = CgmSensorStateStore(context)
        val oldAddress = "AA:BB:CC:DD:EE:41"
        stateStore.save(
            CgmPersistedState(
                sensor = CgmSensor("old-sensor", "old-session", deviceAddress = oldAddress),
                collectorEnabled = true,
            ),
        )
        val credentials = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        credentials
            .edit()
            .putString("shared_address", oldAddress)
            .putString("shared_key", "old-session-key")
            .commit()
        context.deleteDatabase("g7_readings.db")
        val now = System.currentTimeMillis()
        val reading =
            CgmReading(
                id = "history-before-preparation",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "old-sensor",
                sessionId = "old-session",
                glucoseMgDl = 123.0,
                timestampEpochMs = now - 60_000L,
                receivedAtEpochMs = now,
                status = CgmReadingStatus.VALID,
            )
        CgmReadingDatabase(context).use { database -> assertTrue(runBlocking { database.insert(reading) }) }
        val removed = mutableListOf<String?>()
        var savedCode: String? = null
        val application = shadowOf(context)
        assertNull(application.nextStartedService)

        val sensor =
            moveCgmSensorToThisWatch(
                context,
                "1234",
                CgmBondRemover { _, address ->
                    removed.add(address)
                    CgmBondRemovalStatus.REMOVED
                },
                saveSetup = { savedCode = it.pairingCode },
            )

        assertTrue(oldAddress in removed)
        assertEquals("1234", savedCode)
        assertEquals(sensor, stateStore.read().sensor)
        assertNull(sensor.deviceAddress)
        assertFalse(sensor.sensorId == "old-sensor")
        assertFalse(stateStore.read().collectorEnabled)
        assertEquals(CgmAuthenticationState.REQUIRED, stateStore.read().authenticationState)
        assertNull(credentials.getString("shared_address", null))
        assertNull(credentials.getString("shared_key", null))
        assertFalse(CgmRetiredSensors(context).contains(oldAddress))
        assertNull(application.nextStartedService)
        CgmReadingDatabase(context).use { database ->
            assertTrue(database.query(limit = 300).any { it.id == reading.id })
        }
    }

    @Test fun `takeover cleanup removes existing bond without permanently excluding its target`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = "AA:BB:CC:DD:EE:11"
        val alreadyRetired = "AA:BB:CC:DD:EE:12"
        CgmRetiredSensors(context).retire(setOf(alreadyRetired))
        CgmSensorStateStore(context).save(CgmPersistedState(sensor = CgmSensor("transfer", "transfer", deviceAddress = target)))
        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, _ -> CgmBondRemovalStatus.REMOVED },
                bondedAddresses = { setOf(target) },
                permanentlyRetire = false,
            )
        assertTrue(result.detached)
        assertFalse(CgmRetiredSensors(context).contains(target))
        assertTrue(CgmRetiredSensors(context).contains(alreadyRetired))
    }

    @Test fun `fully detached sensor stays excluded across store recreation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "AA:BB:CC:DD:EE:01"
        CgmSensorStateStore(context).save(CgmPersistedState(sensor = CgmSensor("old", "old", deviceAddress = address), collectorEnabled = true))
        CgmCollectorDiagnosticStore(context).stageScheduledCycle(
            app.sugarlicious.sensor.protocol
                .CollectorCycleTiming(expectedWindowId = "old-window"),
        )
        val removed = mutableListOf<String?>()
        val legacy = "AA:BB:CC:DD:EE:03"
        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, candidate ->
                    removed.add(candidate)
                    CgmBondRemovalStatus.REMOVED
                },
                bondedAddresses = { setOf(legacy) },
            )
        assertTrue(result.detached)
        assertTrue(address in removed)
        assertTrue(legacy in removed)
        assertTrue(CgmRetiredSensors(context).contains(address.lowercase()))
        assertTrue(CgmRetiredSensors(context).contains(legacy))
        assertFalse(CgmRetiredSensors(context).contains("AA:BB:CC:DD:EE:02"))
        assertNull(CgmSensorStateStore(context).read().sensor)
        assertNull(CgmCollectorDiagnosticStore(context).pendingScheduledCycle())
    }

    @Test fun `detach stops only local collector and preserves history without ending sensor`() {
        val semantics = cgmDetachSemantics()

        assertTrue(semantics.stopsCollector)
        assertTrue(semantics.clearsLocalSession)
        assertTrue(semantics.preservesHistory)
        assertFalse(semantics.sendsSensorEndCommand)
    }

    @Test fun `unlink removes local sensor and credentials but retains cgm history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("g7_readings.db")
        val stateStore = CgmSensorStateStore(context)
        stateStore.save(CgmPersistedState(sensor = CgmSensor("sensor-a", "session-a"), collectorEnabled = true))
        val now = System.currentTimeMillis()
        val reading =
            CgmReading(
                id = "retained",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor-a",
                sessionId = "session-a",
                glucoseMgDl = 123.0,
                timestampEpochMs = now - 60_000L,
                receivedAtEpochMs = now,
                status = CgmReadingStatus.VALID,
            )
        CgmReadingDatabase(context).use { database -> assertTrue(runBlocking { database.insert(reading) }) }

        val result = unlinkCgmSensor(context, CgmBondRemover { _, _ -> CgmBondRemovalStatus.REMOVED })

        assertEquals(CgmBondRemovalStatus.REMOVED, result.bondRemovalStatus)
        assertNull(stateStore.read().sensor)
        assertFalse(stateStore.read().collectorEnabled)
        assertNull(CgmCredentialStore(context).read())
        CgmReadingDatabase(context).use { database ->
            assertTrue(database.query(limit = 300).any { it.id == "retained" })
        }
    }

    @Test fun `explicit disconnect only succeeds after confirmed OS bond removal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = CgmSensorStateStore(context)
        val address = "AA:BB:CC:DD:EE:FF"
        stateStore.save(CgmPersistedState(sensor = CgmSensor("sensor-retry", "session-retry", deviceAddress = address), collectorEnabled = true))
        val credentials = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        credentials
            .edit()
            .putString("shared_address", address)
            .putString("shared_key", "existing-key")
            .commit()

        val result = unlinkCgmSensor(context, CgmBondRemover { _, _ -> CgmBondRemovalStatus.FAILED })

        assertEquals(CgmBondRemovalStatus.FAILED, result.bondRemovalStatus)
        assertFalse(result.detached)
        assertEquals("sensor-retry", stateStore.read().sensor?.sensorId)
        assertEquals("existing-key", credentials.getString("shared_key", null))
        assertFalse(stateStore.read().collectorEnabled)
        assertFalse(CgmRetiredSensors(context).contains(address))
    }

    @Test fun `denied Bluetooth enumeration cannot silently clear sensor or credentials`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "AA:BB:CC:DD:EE:88"
        CgmSensorStateStore(context).save(CgmPersistedState(sensor = CgmSensor("permission-old", "permission-session", deviceAddress = address), collectorEnabled = true))
        val credentials = context.getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
        credentials
            .edit()
            .putString("shared_address", address)
            .putString("shared_key", "existing-key")
            .commit()

        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, _ -> CgmBondRemovalStatus.FAILED },
                bondedAddresses = { throw SecurityException("Bluetooth permission revoked") },
            )

        assertFalse(result.detached)
        assertEquals(CgmBondRemovalStatus.FAILED, result.bondRemovalStatus)
        assertEquals("permission-old", CgmSensorStateStore(context).read().sensor?.sensorId)
        assertFalse(CgmSensorStateStore(context).read().collectorEnabled)
        assertEquals("existing-key", credentials.getString("shared_key", null))
    }

    @Test fun `new sensor setup never tries to remove unrelated OS bonded G7 peers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CgmSensorStateStore(context).save(CgmPersistedState())
        context
            .getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val removed = mutableListOf<String?>()

        val sensor =
            moveCgmSensorToThisWatch(
                context,
                "1234",
                CgmBondRemover { _, address ->
                    removed += address
                    if (address == null) CgmBondRemovalStatus.NOT_REQUIRED else CgmBondRemovalStatus.FAILED
                },
                saveSetup = { },
            )

        assertEquals(listOf(null), removed)
        assertEquals(sensor, CgmSensorStateStore(context).read().sensor)
        assertFalse(CgmSensorStateStore(context).read().collectorEnabled)
    }

    @Test fun `retired system bond cannot block preparing a different sensor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val address = "AA:BB:CC:DD:EE:99"
        CgmRetiredSensors(context).retire(setOf(address))
        CgmSensorStateStore(context).save(CgmPersistedState())
        var removedAddress: String? = null
        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, candidate ->
                    removedAddress = candidate
                    if (candidate == null) CgmBondRemovalStatus.NOT_REQUIRED else CgmBondRemovalStatus.FAILED
                },
                bondedAddresses = { setOf(address) },
                permanentlyRetire = false,
            )
        assertTrue(result.detached)
        assertNull(removedAddress)
    }

    @Test fun `bond removal wait confirms transition and times out without it`() {
        val states = ArrayDeque(listOf(android.bluetooth.BluetoothDevice.BOND_BONDED, android.bluetooth.BluetoothDevice.BOND_NONE))

        assertTrue(awaitCgmBondRemoval(timeoutMs = 100L, pollMs = 10L, readBondState = { states.removeFirst() }, sleep = {}))
        assertFalse(
            awaitCgmBondRemoval(
                timeoutMs = 20L,
                pollMs = 10L,
                readBondState = { android.bluetooth.BluetoothDevice.BOND_BONDED },
                sleep = {},
            ),
        )
    }

    @Test fun `new sensor cannot replace association while old bond removal failed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = CgmSensorStateStore(context)
        stateStore.save(
            CgmPersistedState(
                sensor = CgmSensor("sensor-old", "session-old", deviceAddress = "AA:BB:CC:DD:EE:FF"),
                collectorEnabled = true,
            ),
        )

        try {
            moveCgmSensorToThisWatch(context, "1234", CgmBondRemover { _, _ -> CgmBondRemovalStatus.FAILED })
            fail("replacement must stop when the previous Bluetooth bond still exists")
        } catch (_: IllegalStateException) {
            assertEquals("sensor-old", stateStore.read().sensor?.sensorId)
        }
    }
}
