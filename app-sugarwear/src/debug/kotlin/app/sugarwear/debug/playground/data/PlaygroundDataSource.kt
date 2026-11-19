package app.sugarwear.debug.playground.data

import app.sugarlicious.sensor.protocol.CgmReading
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface providing glucose data streams for the Playground UI.
 */
interface PlaygroundDataSource {
    val readings: StateFlow<List<CgmReading>>
    val latestReading: StateFlow<CgmReading?>
    val isLive: Boolean
}
