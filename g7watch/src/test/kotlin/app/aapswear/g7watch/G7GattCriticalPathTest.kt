package app.aapswear.g7watch

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7AuthenticationSession
import app.aapswear.g7.G7DefaultGKey
import app.aapswear.g7.G7GlucosePacketParser
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class G7GattCriticalPathTest {
    @Test fun `scan callback releases its result before diagnostics are flushed on collector`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
            val device = adapter.getRemoteDevice("AA:BB:CC:DD:EE:02")
            val sensor = G7Sensor("sensor", deviceAddress = device.address)
            val scanner = AndroidG7Scanner(context)
            var persisted = 0
            scanner.telemetryListener = { persisted++ }
            val result = async(start = CoroutineStart.UNDISPATCHED) { scanner.findKnownSensor(sensor, 60_000L) }
            val platformScanner = adapter.bluetoothLeScanner
            val callback = shadowOf(platformScanner).scanCallbacks.single()

            @Suppress("DEPRECATION")
            val advertisement = ScanResult(device, null, -50, 0L)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, advertisement)
            assertEquals(0, persisted)
            assertEquals(device.address, result.await()?.deviceAddress)
            assertTrue(shadowOf(platformScanner).scanCallbacks.isEmpty())
            scanner.flushTelemetry(G7StaleGattCallbackTelemetry(0L, 1L, 1L, "CONNECTION_STATE"))
            assertEquals(0, persisted)
            scanner.flushTelemetry(G7DirectConnectStarted(0L, 0L, 1L, 2L))
            scanner.flushTelemetry()
            assertEquals(1, persisted)
        }

    @Test fun `radio starts before diagnostic persistence and callback returns without persistence`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
            val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("AA:BB:CC:DD:EE:01")
            var insideCallback = false
            var completed = false
            val connection =
                G7GattConnection(context, G7Sensor("sensor", deviceAddress = device.address), { event ->
                    when (event) {
                        is G7DirectConnectStarted -> assertTrue(shadowOf(device).bluetoothGatts.isNotEmpty())
                        is G7DirectConnectCompleted -> {
                            assertFalse(insideCallback)
                            completed = true
                        }
                        is G7GattCleanupTelemetry -> assertTrue(shadowOf(shadowOf(device).bluetoothGatts.single()).isClosed)
                        else -> Unit
                    }
                }, G7GattGenerationRegistry.acquire(900))
            val result =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching {
                        connection.collect(G7AuthenticationSession("1234", G7DefaultGKey.parts, null), G7GlucosePacketParser(), {}, {}, null, {})
                    }
                }
            try {
                val gatt = shadowOf(device).bluetoothGatts.single()
                insideCallback = true
                shadowOf(gatt).gattCallback.onConnectionStateChange(gatt, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED)
                insideCallback = false
                assertTrue(result.await().isFailure)
                assertTrue(completed)
            } finally {
                insideCallback = false
                connection.close()
            }
        }
}
