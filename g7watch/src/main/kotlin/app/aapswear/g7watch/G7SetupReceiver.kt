package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SetupPayload

class G7SetupReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_CONFIGURE) return
        val pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: return
        val serial = intent.getStringExtra(EXTRA_SENSOR_SERIAL)
        val gtin = intent.getStringExtra(EXTRA_GTIN)
        val payload = runCatching { G7SetupPayload(pairingCode, serial, gtin) }.getOrNull() ?: return
        G7CredentialStore(context).saveSetup(payload)
        val sensorId =
            serial?.takeIf(String::isNotBlank)
                ?: "G7-${java.util.UUID.randomUUID().toString().take(8)}"
        val prepared =
            G7SessionManager(G7SensorStateStore(context).read()).prepareInitialSetup(
                G7Sensor(
                    sensorId = sensorId,
                    sessionId = serial ?: sensorId,
                    deviceName = "Dexcom G7",
                ),
            )
        G7SensorStateStore(context).save(prepared)
    }

    companion object {
        const val ACTION_CONFIGURE = "app.aapswear.g7watch.CONFIGURE"
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val EXTRA_SENSOR_SERIAL = "sensor_serial"
        const val EXTRA_GTIN = "gtin"
    }
}
