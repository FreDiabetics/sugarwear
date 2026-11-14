package app.sugarlicious.mobile

import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val diagnostics = DiagnosticsSnapshot(null, null, null, 0, 0, 0, 0, null, null)

    @Test fun `presentation consumer failure is not converted to a storage error`() =
        runTest {
            val expected = java.io.IOException("consumer")
            var reports = 0
            var caught: Throwable? = null
            try {
                kotlinx.coroutines.flow
                    .flowOf(DashboardRepositoryState(null, diagnostics, DashboardUiPreferences()))
                    .withDashboardStorageFailures { reports++ }
                    .collect { throw expected }
            } catch (failure: Throwable) {
                caught = failure
            }
            assertSame(expected, caught)
            assertEquals(0, reports)
        }

    @Test fun `visible subscription receives canonical updates and clock preserves measurement`() =
        runTest {
            val inputs = MutableStateFlow(DashboardRepositoryState(null, diagnostics, DashboardUiPreferences()))
            val clock = MutableStateFlow(1_000L)
            val model = DashboardViewModel(inputs, clock, backgroundScope)
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            val canonical = TherapyDisplayState(receivedAtEpochMs = 123L, canonicalRevision = 7L)
            inputs.value = inputs.value.copy(therapy = canonical)
            runCurrent()
            assertEquals(canonical, model.uiState.value.therapy)
            clock.value = 61_000L
            runCurrent()
            assertEquals(61_000L, model.uiState.value.nowEpochMs)
            assertEquals(canonical, model.uiState.value.therapy)
            observer.cancel()
        }

    @Test fun `legacy direct watch readings never enter mobile presentation`() =
        runTest {
            val watch =
                TherapyDisplayState(
                    source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH,
                    receivedAtEpochMs = 123L,
                    glucose = app.sugarlicious.model.GlucoseState(valueMgDl = 123.0, displayUnit = app.sugarlicious.model.GlucoseUnit.MG_DL, measuredAtEpochMs = 100L, source = app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH),
                )
            val model = DashboardViewModel(MutableStateFlow(DashboardRepositoryState(watch, diagnostics, DashboardUiPreferences())), MutableStateFlow(0L), backgroundScope)
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            assertNull(
                model.uiState.value.therapy
                    ?.glucose,
            )
            assertEquals(
                123L,
                model.uiState.value.therapy
                    ?.receivedAtEpochMs,
            )
            observer.cancel()
        }

    @Test fun `storage failure invalidates last measurement and visible resubscription recovers`() =
        runTest {
            val canonical =
                TherapyDisplayState(
                    receivedAtEpochMs = 500L,
                    glucose = app.sugarlicious.model.GlucoseState(valueMgDl = 123.0, displayUnit = app.sugarlicious.model.GlucoseUnit.MG_DL, measuredAtEpochMs = 400L),
                )
            var attempts = 0
            val upstream =
                kotlinx.coroutines.flow
                    .flow {
                        attempts++
                        emit(DashboardRepositoryState(canonical, diagnostics, DashboardUiPreferences()))
                        if (attempts == 1) throw java.io.IOException("storage")
                    }.withDashboardStorageFailures { }
            val model = DashboardViewModel(upstream, MutableStateFlow(1_000L), backgroundScope)
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            assertEquals(DashboardObservationError.STORAGE_UNAVAILABLE, model.uiState.value.observationError)
            assertEquals(
                app.sugarlicious.model.CgmQuality.INVALID,
                model.uiState.value.therapy
                    ?.glucose
                    ?.quality,
            )
            assertEquals(
                400L,
                model.uiState.value.therapy
                    ?.glucose
                    ?.measuredAtEpochMs,
            )
            assertEquals(app.sugarlicious.model.CgmQuality.VALID, canonical.glucose?.quality)
            observer.cancel()
            runCurrent()
            val resumed = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            assertNull(model.uiState.value.observationError)
            assertEquals(canonical, model.uiState.value.therapy)
            resumed.cancel()
        }

    @Test fun `observation stops immediately and resubscription loads latest state`() =
        runTest {
            var subscriptions = 0
            val canonical = MutableStateFlow(DashboardRepositoryState(null, diagnostics, DashboardUiPreferences()))
            val inputs =
                kotlinx.coroutines.flow.flow {
                    subscriptions++
                    try {
                        canonical.collect { emit(it) }
                    } finally {
                        subscriptions--
                    }
                }
            val model = DashboardViewModel(inputs, MutableStateFlow(0L), backgroundScope)
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            assertEquals(1, subscriptions)
            observer.cancel()
            runCurrent()
            assertEquals(0, subscriptions)
            canonical.value = canonical.value.copy(therapy = TherapyDisplayState(receivedAtEpochMs = 456L))
            val resumed = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            assertEquals(
                456L,
                model.uiState.value.therapy
                    ?.receivedAtEpochMs,
            )
            resumed.cancel()
        }
}
