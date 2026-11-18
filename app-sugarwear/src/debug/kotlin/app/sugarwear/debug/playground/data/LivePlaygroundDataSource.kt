package app.sugarwear.debug.playground.data

import android.content.Context
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarwear.CgmReadingDatabase
import app.sugarwear.CgmSensorStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Passive, read-only data source consuming live stored readings from the app's local database.
 * Does not initialize BLE connections, trigger sensor scans, or issue commands.
 */
class LivePlaygroundDataSource(
    private val context: Context,
    private val scope: CoroutineScope,
) : PlaygroundDataSource {
    private val _readings = MutableStateFlow<List<CgmReading>>(emptyList())
    override val readings: StateFlow<List<CgmReading>> = _readings.asStateFlow()

    private val _latestReading = MutableStateFlow<CgmReading?>(null)
    override val latestReading: StateFlow<CgmReading?> = _latestReading.asStateFlow()

    override val isLive: Boolean = true

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = CgmReadingDatabase(context)
                val now = System.currentTimeMillis()
                val liveReadings = db.getGraphHistory(now)
                val latest = CgmSensorStateStore(context).read().lastReading

                _readings.value = liveReadings
                _latestReading.value = latest
            }
        }
    }
}
