package app.sugarwear.debug.playground.demo

import app.sugarlicious.sensor.protocol.CgmReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages playback/stepping of synthetic glucose readings over time.
 * Controlled coroutine loop; pure test harness with zero production side effects.
 */
class DemoPlaybackController(
    private val scope: CoroutineScope,
) {
    private val _readings = MutableStateFlow<List<CgmReading>>(emptyList())
    val readings: StateFlow<List<CgmReading>> = _readings.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var fullDataset: List<CgmReading> = emptyList()
    private var playbackJob: Job? = null
    private var speedMultiplier: Float = 1.0f

    fun loadScenario(scenario: DemoScenario, nowEpochMs: Long = System.currentTimeMillis()) {
        pause()
        fullDataset = DemoGlucoseFactory.createReadingsForScenario(scenario, nowEpochMs = nowEpochMs)
        _currentIndex.value = fullDataset.size
        _readings.value = fullDataset
    }

    fun play() {
        if (_isPlaying.value || fullDataset.isEmpty()) return
        _isPlaying.value = true
        playbackJob = scope.launch {
            while (_isPlaying.value) {
                val delayMs = (5000L / speedMultiplier.coerceAtLeast(0.1f)).toLong()
                delay(delayMs.milliseconds)
                stepForward()
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun stepForward() {
        if (fullDataset.isEmpty()) return
        val nextIdx = (_currentIndex.value + 1) % (fullDataset.size + 1)
        _currentIndex.value = nextIdx
        _readings.value = fullDataset.take(nextIdx)
    }

    fun reset() {
        pause()
        _currentIndex.value = fullDataset.size
        _readings.value = fullDataset
    }

    fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier
    }
}
