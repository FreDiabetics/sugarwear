package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmPersistedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CgmViewModelTest {
    @Test fun `visible age clock preserves the exact measured sample`() =
        runTest {
            val reading =
                app.sugarlicious.sensor.protocol.CgmReading(
                    id = "ui-clock",
                    source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor",
                    sessionId = "session",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = 100L,
                    receivedAtEpochMs = 100L,
                    status = app.sugarlicious.sensor.protocol.CgmReadingStatus.VALID,
                )
            val snapshot =
                CgmOverviewSnapshot(
                    CgmPersistedState(
                        sensor =
                            app.sugarlicious.sensor.protocol
                                .CgmSensor("sensor", "session"),
                        lastReading = reading,
                        collectorEnabled = true,
                    ),
                    false,
                    listOf(reading),
                    null,
                )
            val model = CollectorViewModel(CgmOverviewRepository { kotlinx.coroutines.flow.flowOf(snapshot) }, backgroundScope) { testScheduler.currentTime + 1_000L }
            backgroundScope.launch { model.state.collect {} }
            runCurrent()
            advanceTimeBy(60_000L)
            runCurrent()
            assertTrue(model.state.value.now >= 60_000L)
            assertEquals(snapshot, model.state.value.snapshot)
            assertEquals(
                100L,
                model.state.value.snapshot
                    ?.persisted
                    ?.lastReading
                    ?.timestampEpochMs,
            )
        }

    @Test fun `paused attempt clock stops after the exact expiry`() =
        runTest {
            val snapshot = CgmOverviewSnapshot(CgmPersistedState(collectorEnabled = false), false, emptyList(), CgmUiSensorTransition(CgmUiSensorAction.PAUSE, 0L))
            val model = CollectorViewModel(CgmOverviewRepository { kotlinx.coroutines.flow.flowOf(snapshot) }, backgroundScope) { testScheduler.currentTime + 119_500L }
            backgroundScope.launch { model.state.collect {} }
            runCurrent()
            advanceTimeBy(500L)
            runCurrent()
            assertEquals(120_000L, model.state.value.now)
            advanceTimeBy(24L * 60 * 60 * 1_000)
            runCurrent()
            assertEquals(120_000L, model.state.value.now)
        }

    @Test fun `explicit UI retry reloads failed storage without touching collector state`() =
        runTest {
            var loads = 0
            val expected = CgmOverviewSnapshot(CgmPersistedState(collectorEnabled = false), false, emptyList(), null)
            val model =
                CollectorViewModel(
                    CgmOverviewRepository {
                        flow {
                            loads++
                            if (loads == 1) throw java.io.IOException("unavailable")
                            emit(expected)
                        }
                    },
                    backgroundScope,
                ) { 123L }
            backgroundScope.launch { model.state.collect {} }
            runCurrent()
            assertTrue(model.state.value.failed)
            model.retry()
            runCurrent()
            assertEquals(2, loads)
            assertFalse(model.state.value.failed)
            assertEquals(expected, model.state.value.snapshot)
        }

    @Test fun `repository and clock stop without subscribers and resume loads latest`() =
        runTest {
            try {
                var active = 0
                var loads = 0
                val repository =
                    CgmOverviewRepository {
                        flow {
                            active++
                            try {
                                loads++
                                emit(CgmOverviewSnapshot(CgmPersistedState(collectorEnabled = loads > 1), false, emptyList(), null))
                                kotlinx.coroutines.awaitCancellation()
                            } finally {
                                active--
                            }
                        }
                    }
                val vm = CollectorViewModel(repository, backgroundScope) { testScheduler.currentTime }
                val first = backgroundScope.launch { vm.state.collect {} }
                runCurrent()
                assertEquals(1, active)
                assertNotNull(vm.state.value.snapshot)
                first.cancel()
                runCurrent()
                assertEquals(0, active)
                backgroundScope.launch { vm.state.collect {} }
                runCurrent()
                assertEquals(2, loads)
                assertTrue(
                    vm.state.value.snapshot!!
                        .persisted.collectorEnabled,
                )
            } finally {
                backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }

    @Test fun `failed IO is explicit and does not manufacture a reading`() =
        runTest {
            val vm = CollectorViewModel(CgmOverviewRepository { flow { throw java.io.IOException("unavailable") } }, backgroundScope) { 100L }
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertTrue(vm.state.value.failed)
            assertNull(vm.state.value.snapshot)
        }
}
