package app.sugarwear

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmDefaultGKey
import app.sugarlicious.sensor.protocol.CgmScanner
import app.sugarlicious.sensor.protocol.CgmSensor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CgmCollectorRadioRecoveryTest {
    @Test
    fun `bond reconnect releases the platform client before recovery handoff`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("AA:BB:CC:DD:EE:42")
            val rebond =
                Class
                    .forName("app.sugarwear.CgmBondReconnectRequired")
                    .getDeclaredConstructor(ByteArray::class.java)
                    .apply { isAccessible = true }
                    .newInstance(ByteArray(16)) as Exception
            val stop = IllegalStateException("end after observing recovery boundary")
            val result =
                runCatching {
                    CgmHardwareCollector(context).collect(
                        CgmSensor("sensor", deviceAddress = device.address),
                        StoredCgmCredentials("1234", null, null, CgmDefaultGKey.parts, null, null),
                        onState = { state ->
                            if (state == app.sugarlicious.sensor.protocol.CgmProtocolState.RECOVERING) {
                                assertTrue(shadowOf(device).bluetoothGatts.single().let { shadowOf(it).isClosed })
                                throw stop
                            }
                        },
                        onTelemetry = { event -> if (event is CgmDirectConnectStarted) throw rebond },
                    )
                }
            assertTrue(result.exceptionOrNull() === stop)
        }

    @Test
    fun `successful fallback connection does not hide its service failure behind an earlier 133`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("AA:BB:CC:DD:EE:41")
            val sensor = CgmSensor("sensor", deviceAddress = device.address)
            var scans = 0
            val scanner =
                object : CgmScanner {
                    override suspend fun findKnownSensor(sensor: CgmSensor?, timeoutMs: Long): CgmSensor {
                        scans++
                        return requireNotNull(sensor)
                    }
                }
            var connects = 0
            val result =
                runCatching {
                    CgmHardwareCollector(context, scanner).collect(
                        sensor,
                        StoredCgmCredentials("1234", null, null, CgmDefaultGKey.parts, null, null),
                        onState = {},
                        onTelemetry = { event ->
                            if (event is CgmDirectConnectStarted) {
                                connects++
                                val gatt = shadowOf(device).bluetoothGatts.last()
                                val shadow = shadowOf(gatt)
                                if (connects <= 2) {
                                    shadow.gattCallback.onConnectionStateChange(gatt, 133, BluetoothProfile.STATE_DISCONNECTED)
                                } else {
                                    shadow.addDiscoverableService(BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY))
                                    shadow.notifyConnection(device.address)
                                    shadow.gattCallback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
                                }
                            }
                        },
                    )
                }
            assertEquals(3, connects)
            assertEquals(1, scans)
            assertEquals("G7-GATT-204", (result.exceptionOrNull() as? CgmBleException)?.errorCode)
            assertTrue(shadowOf(device).bluetoothGatts.all { shadowOf(it).isClosed })
        }
}
