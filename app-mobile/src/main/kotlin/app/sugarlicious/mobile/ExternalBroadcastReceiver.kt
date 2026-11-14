package app.sugarlicious.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.datasource.external.ExternalCgmAdapters
import kotlinx.coroutines.*

class ExternalBroadcastReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val source =
            when (intent.action) {
                ExternalCgmAdapters.XDRIP_ACTION -> MobileGlucoseSource.XDRIP_PLUS
                ExternalCgmAdapters.LIBRE_SCAN_ACTION -> MobileGlucoseSource.LIBRE_PATCHED
                else -> return
            }
        if (source !in MobileSourceConfigurationStore.read(context).enabledGlucoseSources) return
        val now = System.currentTimeMillis()
        val values =
            runCatching {
                val extras = intent.extras ?: return
                if (source == MobileGlucoseSource.XDRIP_PLUS) {
                    listOf("BgEstimate", "Time", "BgSlope", "BgSlopeName", "SensorStartedAt").associate { name ->
                        val key = "com.eveningoutpost.dexdrip.Extras.$name"
                        key to extras.get(key)
                    }
                } else {
                    val manager = extras.getBundle("bleManager")
                    val sas = extras.getBundle("sas")
                    val readings = mutableListOf<Map<String, Any?>>()
                    listOf("realTimeGlucoseReadings", "historicGlucoseReadings").forEach { key ->
                        sas?.getBundle(key)?.let { group ->
                            require(group.size() <= 1000)
                            group.keySet().forEach { name ->
                                group.getBundle(name)?.let { item ->
                                    readings += mapOf("glucoseValue" to item.get("glucoseValue"), "timestamp" to item.get("timestamp"))
                                }
                            }
                        }
                    }
                    mapOf("sensorSerial" to manager?.getString("sensorSerial"), "sensorStartTime" to sas?.getBundle("currentSensor")?.getLong("sensorStartTime")?.takeIf { it > 0 }, "readings" to readings)
                }
            }.getOrNull() ?: return
        val state = ExternalCgmAdapters.local(intent.action, values, now) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    MobileCanonicalStateCoordinator.savePhoneInput(app, state, now)
                    PersistentBridgeService.start(app)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                app.recordMobileDiagnostic("SOURCE", "SRC-LOCAL-500", "Local glucose broadcast could not be committed")
            } finally {
                pending.finish()
            }
        }
    }
}
