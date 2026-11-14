package app.sugarlicious.wear

import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WearOverviewViewModelTest {
    @Test fun `resolution failure stays explicit until retry and never kills observation`() =
        runTest {
            val repository = FakeRepository().apply { resolutionFailure = IOException("storage unavailable") }
            val vm = WearOverviewViewModel(repository, clock = { testScheduler.currentTime }, scope = backgroundScope)
            var observation = backgroundScope.launch { vm.uiState.collect() }
            runCurrent()
            assertEquals(WearOverviewError.STORAGE_UNAVAILABLE, vm.uiState.value.snapshot.error)
            val attempts = repository.resolvedTimes.size
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(attempts, repository.resolvedTimes.size)
            repository.resolutionFailure = null
            vm.refresh()
            runCurrent()
            assertEquals(null, vm.uiState.value.snapshot.error)
            assertTrue(repository.resolvedTimes.size > attempts)
            repository.resolutionFailure = IOException("storage unavailable again")
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(WearOverviewError.STORAGE_UNAVAILABLE, vm.uiState.value.snapshot.error)
            observation.cancel()
            runCurrent()
            repository.resolutionFailure = null
            observation = backgroundScope.launch { vm.uiState.collect() }
            runCurrent()
            assertEquals(null, vm.uiState.value.snapshot.error)
            observation.cancel()
        }

    @Test fun `storage failure retains provenance and explicit retry reloads latest snapshot`() =
        runTest {
            val retry = MutableStateFlow(0L)
            val old =
                WearOverviewSnapshot(
                    therapy =
                        TherapyDisplayState(
                            receivedAtEpochMs = 1L,
                            glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = 1L),
                        ),
                )
            val fresh = old.copy(therapy = old.therapy?.copy(receivedAtEpochMs = 2L))
            var attempts = 0
            val values = mutableListOf<WearOverviewSnapshot>()
            backgroundScope.launch {
                recoverWearOverviewSnapshots(retry) {
                    flow {
                        attempts++
                        if (attempts == 1) {
                            emit(old)
                            throw IOException("unavailable")
                        }
                        emit(fresh)
                        awaitCancellation()
                    }
                }.collect { values += it }
            }
            runCurrent()
            assertEquals(1, attempts)
            assertEquals(old.therapy, values.last().therapy)
            assertEquals(WearOverviewError.STORAGE_UNAVAILABLE, values.last().error)
            advanceTimeBy(90_000)
            runCurrent()
            assertEquals(1, attempts)
            retry.value++
            runCurrent()
            assertEquals(2, attempts)
            assertEquals(fresh, values.last())
        }

    @Test fun `storage recovery preserves cancellation and programming failures`() =
        runTest {
            listOf(CancellationException("cancelled"), IllegalArgumentException("programming error")).forEach { expected ->
                var caught: Throwable? = null
                try {
                    recoverWearOverviewSnapshots(MutableStateFlow(0L)) { flow { throw expected } }.collect()
                } catch (error: Throwable) {
                    caught = error
                }
                assertTrue(caught === expected)
            }
        }

    @Test fun `canonical observation stops offscreen and reloads on return`() =
        runTest {
            try {
                val repository = FakeRepository()
                val vm = WearOverviewViewModel(repository, clock = { testScheduler.currentTime }, scope = backgroundScope)
                assertEquals(0, repository.starts)
                val first = backgroundScope.launch { vm.uiState.collect() }
                runCurrent()
                assertTrue(repository.active)
                assertEquals(1, repository.starts)
                first.cancel()
                runCurrent()
                assertFalse(repository.active)
                val stoppedAt = vm.uiState.value.nowEpochMs
                advanceTimeBy(90_000)
                runCurrent()
                assertEquals(stoppedAt, vm.uiState.value.nowEpochMs)
                backgroundScope.launch { vm.uiState.collect() }
                runCurrent()
                assertEquals(2, repository.starts)
                assertEquals(90_000L, vm.uiState.value.nowEpochMs)
            } finally {
                backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }

    @Test fun `clock advances visible state and actions delegate without changing measurements`() =
        runTest {
            try {
                val repository = FakeRepository()
                val vm = WearOverviewViewModel(repository, clock = { testScheduler.currentTime }, scope = backgroundScope)
                backgroundScope.launch { vm.uiState.collect() }
                runCurrent()
                vm.refresh()
                vm.advanceGraphPeriod()
                runCurrent()
                assertEquals(1, repository.refreshes)
                assertEquals(1, repository.graphActions)
                assertEquals(2, vm.uiState.value.connectedNodes)
                assertFalse(vm.uiState.value.refreshing)
                val snapshot = vm.uiState.value.snapshot
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(30_000L, vm.uiState.value.nowEpochMs)
                assertEquals(30_000L, repository.resolvedTimes.last())
                assertEquals(snapshot, vm.uiState.value.snapshot)
            } finally {
                backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }

    private class FakeRepository : WearOverviewRepository {
        var starts = 0
        var active = false
        var refreshes = 0
        var graphActions = 0
        val resolvedTimes = mutableListOf<Long>()
        var resolutionFailure: IOException? = null

        override fun observe(): Flow<WearOverviewSnapshot> =
            flow {
                starts++
                active = true
                try {
                    emit(WearOverviewSnapshot(preferences = WearDisplayPreferences()))
                    awaitCancellation()
                } finally {
                    active = false
                }
            }

        override suspend fun resolve(snapshot: WearOverviewSnapshot, now: Long): WearOverviewSnapshot {
            resolvedTimes += now
            resolutionFailure?.let { throw it }
            return snapshot
        }

        override suspend fun refresh(): Int {
            refreshes++
            return 2
        }

        override suspend fun advanceGraphPeriod() {
            graphActions++
        }

        override fun retry() = Unit
    }
}
