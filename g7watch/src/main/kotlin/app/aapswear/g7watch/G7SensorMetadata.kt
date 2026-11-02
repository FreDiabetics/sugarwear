package app.aapswear.g7watch

import android.content.Context
import androidx.core.content.edit
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Identity from the original G7 application's 0x32 metadata response; not a local session ID. */
internal fun g7SensorSerialFromMetadata(packet: ByteArray): String? {
    if (packet.size != 20 || packet[0] != 0x32.toByte() || packet[1] != 0.toByte()) return null
    val identity = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getLong(12)
    return identity.takeIf { it in 100_000_000_000L..999_999_999_999L }?.toString()
}

internal suspend fun readG7SensorMetadata(
    send: suspend () -> Unit,
    receive: suspend () -> ByteArray?,
    timeoutMs: Long = 1_200L,
): String? =
    try {
        withTimeoutOrNull(timeoutMs) {
            send()
            var serial: String? = null
            while (serial == null) {
                currentCoroutineContext().ensureActive()
                serial = receive()?.let(::g7SensorSerialFromMetadata)
            }
            serial
        }
    } catch (_: G7BleException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (_: ClosedReceiveChannelException) {
        currentCoroutineContext().ensureActive()
        null
    }

/** Separate metadata keys in the credential file: fully removed with credentials on sensor unlink. */
internal class G7SensorMetadataStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(G7_CREDENTIAL_PREFERENCES, Context.MODE_PRIVATE)

    fun serialCandidate(sensor: G7Sensor): String? =
        preferences
            .getString(SENSOR, null)
            .takeIf { it == sensor.sensorId }
            ?.let { preferences.getString(ADDRESS, null) }
            ?.takeIf { it.equals(sensor.deviceAddress, ignoreCase = true) }
            ?.let { preferences.getString(SERIAL, null) }
            ?.takeIf { it.matches(Regex("[0-9]{12}")) }

    fun beginRead(sensor: G7Sensor, now: Long): Boolean {
        val address = sensor.deviceAddress ?: return false
        if (serialCandidate(sensor) != null) return false
        val sameAttempt = preferences.getString(ATTEMPT_SENSOR, null) == sensor.sensorId && preferences.getString(ATTEMPT_ADDRESS, null).equals(address, ignoreCase = true)
        val previous = preferences.getLong(ATTEMPT_TIME, 0L)
        if (sameAttempt && now >= previous && now - previous < RETRY_MS) return false
        preferences.edit {
            putString(ATTEMPT_SENSOR, sensor.sensorId)
            putString(ATTEMPT_ADDRESS, address)
            putLong(ATTEMPT_TIME, now)
        }
        return true
    }

    fun save(sensor: G7Sensor, serial: String) {
        val address = sensor.deviceAddress ?: return
        require(serial.matches(Regex("[0-9]{12}")))
        preferences.edit {
            putString(SENSOR, sensor.sensorId)
            putString(ADDRESS, address)
            putString(SERIAL, serial)
        }
    }

    companion object {
        private const val SENSOR = "metadata_sensor"
        private const val ADDRESS = "metadata_address"
        private const val SERIAL = "metadata_serial_candidate"
        private const val ATTEMPT_SENSOR = "metadata_attempt_sensor"
        private const val ATTEMPT_ADDRESS = "metadata_attempt_address"
        private const val ATTEMPT_TIME = "metadata_attempt_time"
        private const val RETRY_MS = 24 * 60 * 60_000L
    }
}
