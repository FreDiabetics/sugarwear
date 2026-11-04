package app.sugarwear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSessionManager
import app.sugarlicious.sensor.protocol.CgmSetupPayload

class CgmSetupReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_CONFIGURE) return
        val pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: return
        val serial = intent.getStringExtra(EXTRA_SENSOR_SERIAL)
        val gtin = intent.getStringExtra(EXTRA_GTIN)
        val payload = runCatching { CgmSetupPayload(pairingCode, serial, gtin) }.getOrNull() ?: return
        CgmCredentialStore(context).saveSetup(payload)
        val sensorId =
            serial?.takeIf(String::isNotBlank)
                ?: "G7-${java.util.UUID.randomUUID().toString().take(8)}"
        val prepared =
            CgmSessionManager(CgmSensorStateStore(context).read()).prepareInitialSetup(
                CgmSensor(
                    sensorId = sensorId,
                    sessionId = serial ?: sensorId,
                    deviceName = "Dexcom G7",
                ),
            )
        CgmSensorStateStore(context).save(prepared)
    }

    companion object {
        const val ACTION_CONFIGURE = "app.aapswear.g7watch.CONFIGURE"
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val EXTRA_SENSOR_SERIAL = "sensor_serial"
        const val EXTRA_GTIN = "gtin"
    }
}
