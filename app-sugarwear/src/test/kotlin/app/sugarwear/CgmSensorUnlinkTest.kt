package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmSensorUnlinkTest {
    @Test
    @Config(sdk = [35, 36])
    fun `disabled Bluetooth cannot confirm removal or erase the retry session`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        setup(context)
        org.robolectric.Shadows
            .shadowOf(context)
            .grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        org.robolectric.Shadows
            .shadowOf(adapter)
            .setState(android.bluetooth.BluetoothAdapter.STATE_OFF)

        val result = unlinkCgmSensor(context)

        assertFalse(result.detached)
        assertEquals(CgmBondRemovalStatus.FAILED, result.bondRemovalStatus)
        assertEquals(target, CgmSensorStateStore(context).read().sensor?.deviceAddress)
        assertTrue(CgmCredentialStore(context).hasSetupMaterial())
    }

    private val target = "AA:BB:CC:DD:EE:11"
    private val unrelated = "AA:BB:CC:DD:EE:22"

    private fun preferences(context: Context, name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun setup(context: Context) {
        CgmSensorStateStore(context).save(
            CgmPersistedState(
                sensor = CgmSensor("sensor-11", sessionId = "session-11", deviceAddress = target),
                collectorEnabled = true,
            ),
        )
        val credentials = preferences(context, CGM_CREDENTIAL_PREFERENCES).edit()
        credentials.putString("shared_address", target)
        credentials.putString("pairing_code", "encrypted-placeholder")
        assertTrue(credentials.commit())
    }

    private fun addHistory(context: Context) {
        CgmReadingDatabase(context).use { database ->
            database.writableDatabase.execSQL(
                "INSERT INTO readings (id, sensor_id, session_id, glucose, measured_at, received_at, trend, status) VALUES ('historical', 'sensor-11', 'session-11', 123, 1000, 1100, 'UNKNOWN', 'VALID')",
            )
        }
    }

    @Test fun `confirmed explicit unlink removes only recorded sensor metadata and keeps glucose history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setup(context)
        addHistory(context)
        CgmRetiredSensors(context).retire(setOf(unrelated))
        preferences(context, "g7_collector_attempt_history").edit().apply { putString("attempts_v2", "old") }.commit()
        preferences(context, "g7_expected_window_ledger").edit().apply { putString("windows_v1", "old") }.commit()
        preferences(context, "g7_runtime_recovery").edit().apply { putLong("last_runtime_recycle_at", 5L) }.commit()
        val removed = mutableListOf<String?>()

        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, address ->
                    removed.add(address)
                    CgmBondRemovalStatus.REMOVED
                },
            )

        assertTrue(result.detached)
        assertEquals(listOf(target), removed)
        assertNull(CgmSensorStateStore(context).read().sensor)
        assertTrue(preferences(context, "g7_collector_state").all.isEmpty())
        assertFalse(CgmCredentialStore(context).hasSetupMaterial())
        assertTrue(preferences(context, "g7_collector_attempt_history").all.isEmpty())
        assertTrue(preferences(context, "g7_expected_window_ledger").all.isEmpty())
        assertTrue(preferences(context, "g7_runtime_recovery").all.isEmpty())
        assertTrue(preferences(context, "g7_retired_addresses").all.isEmpty())
        CgmReadingDatabase(context).use { database ->
            database.readableDatabase.rawQuery("SELECT COUNT(*) FROM readings", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
        val provider =
            Robolectric
                .buildContentProvider(CgmReadingProvider::class.java)
                .create()
                .get()
        val latest =
            CgmReadingProvider.CONTENT_URI
                .buildUpon()
                .appendPath("latest")
                .build()
        provider.query(latest, null, null, null, null).use { cursor ->
            assertEquals(0, cursor.count)
        }
        provider.query(CgmReadingProvider.CONTENT_URI, null, null, null, null).use { cursor ->
            assertEquals(1, cursor.count)
        }
    }

    @Test fun `unconfirmed bond keeps retry identity and never claims success`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setup(context)
        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, address ->
                    assertEquals(target, address)
                    CgmBondRemovalStatus.FAILED
                },
            )
        assertFalse(result.detached)
        assertEquals(CgmBondRemovalStatus.FAILED, result.bondRemovalStatus)
        assertEquals(target, CgmSensorStateStore(context).read().sensor?.deviceAddress)
        assertFalse(CgmSensorStateStore(context).read().collectorEnabled)
        assertTrue(CgmCredentialStore(context).hasSetupMaterial())
    }

    @Test fun `mismatched recorded addresses fail closed without touching another sensor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setup(context)
        val credentials = preferences(context, CGM_CREDENTIAL_PREFERENCES)
        assertTrue(credentials.edit().putString("shared_address", unrelated).commit())
        var removals = 0
        val result =
            unlinkCgmSensor(
                context,
                CgmBondRemover { _, _ ->
                    removals++
                    CgmBondRemovalStatus.REMOVED
                },
            )
        assertFalse(result.detached)
        assertEquals(0, removals)
        assertTrue(result.failureReason?.contains("Widersprüchliche") == true)
        assertEquals(target, CgmSensorStateStore(context).read().sensor?.deviceAddress)
    }

    @Test fun `new sensor setup must not silently erase previous session`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setup(context)
        val result =
            runCatching {
                moveCgmSensorToThisWatch(
                    context,
                    "1234",
                    saveSetup = { error("must not replace old code") },
                )
            }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(target, CgmSensorStateStore(context).read().sensor?.deviceAddress)
    }
}
