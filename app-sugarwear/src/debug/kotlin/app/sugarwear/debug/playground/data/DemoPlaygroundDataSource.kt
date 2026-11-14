package app.sugarwear.debug.playground.data

import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarwear.debug.playground.demo.DemoPlaybackController
import app.sugarwear.debug.playground.demo.DemoScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Demo implementation of PlaygroundDataSource backed by DemoPlaybackController.
 */
class DemoPlaygroundDataSource(
    scope: CoroutineScope,
    val controller: DemoPlaybackController = DemoPlaybackController(scope),
) : PlaygroundDataSource {
    override val readings: StateFlow<List<CgmReading>> = controller.readings

    override val latestReading: StateFlow<CgmReading?> = readings.map { it.lastOrNull() }
        .stateIn(scope, SharingStarted.Lazily, null)

    override val isLive: Boolean = false

    fun setScenario(scenario: DemoScenario) {
        controller.loadScenario(scenario)
    }
}
