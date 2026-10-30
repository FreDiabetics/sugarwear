package app.aapswear.g7watch

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7DefaultGKey
import app.aapswear.g7.G7Scanner
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class G7CollectorRadioRecoveryTest {
    @Test
    fun `bond reconnect releases the platform client before recovery handoff`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("AA:BB:CC:DD:EE:42")
            val rebond =
                Class
                    .forName("app.aapswear.g7watch.G7BondReconnectRequired")
                    .getDeclaredConstructor(ByteArray::class.java)
                    .apply { isAccessible = true }
                    .newInstance(ByteArray(16)) as Exception
            val stop = IllegalStateException("end after observing recovery boundary")
            val result =
                runCatching {
                    AndroidG7Collector(context).collect(
                        G7Sensor("sensor", deviceAddress = device.address),
                        StoredG7Credentials("1234", null, null, G7DefaultGKey.parts, null, null),
                        onState = { state ->
                            if (state == app.aapswear.g7.G7ProtocolState.RECOVERING) {
                                assertTrue(shadowOf(device).bluetoothGatts.single().let { shadowOf(it).isClosed })
                                throw stop
                            }
                        },
                        onTelemetry = { event -> if (event is G7DirectConnectStarted) throw rebond },
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
            val sensor = G7Sensor("sensor", deviceAddress = device.address)
            var scans = 0
            val scanner =
                object : G7Scanner {
                    override suspend fun findKnownSensor(sensor: G7Sensor?, timeoutMs: Long): G7Sensor {
                        scans++
                        return requireNotNull(sensor)
                    }
                }
            var connects = 0
            val result =
                runCatching {
                    AndroidG7Collector(context, scanner).collect(
                        sensor,
                        StoredG7Credentials("1234", null, null, G7DefaultGKey.parts, null, null),
                        onState = {},
                        onTelemetry = { event ->
                            if (event is G7DirectConnectStarted) {
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
            assertEquals("G7-GATT-204", (result.exceptionOrNull() as? G7BleException)?.errorCode)
            assertTrue(shadowOf(device).bluetoothGatts.all { shadowOf(it).isClosed })
        }
}
