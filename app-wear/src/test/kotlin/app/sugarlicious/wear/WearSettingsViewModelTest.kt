package app.sugarlicious.wear

import app.sugarlicious.model.AppearanceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearSettingsViewModelTest {
    @Test fun `navigation retains category list scroll and back returns to it`() =
        runTest {
            val repository = FakeRepository()
            val vm = WearSettingsViewModel(repository, backgroundScope)
            runCurrent()
            vm.openCategory("graph", 217)
            assertEquals("graph", vm.uiState.value.category)
            assertTrue(vm.navigateBack())
            assertEquals(null, vm.uiState.value.category)
            assertEquals(217, vm.uiState.value.scrollY)
            assertFalse(vm.navigateBack())
        }

    @Test fun `queued edits keep their selected palette and fanout occurs through repository`() =
        runTest {
            val repository = FakeRepository()
            val vm = WearSettingsViewModel(repository, backgroundScope)
            runCurrent()
            vm.save(
                vm.uiState.value.snapshot!!
                    .preferences
                    .copy(graphHours = 6),
                rebuild = false,
            )
            vm.selectAppearance(AppearanceMode.LIGHT)
            runCurrent()
            assertEquals(listOf(AppearanceMode.DARK to 6), repository.saved)
            assertEquals(
                AppearanceMode.LIGHT,
                vm.uiState.value.snapshot!!
                    .mode,
            )
        }

    @Test fun `storage failure remains visible and retry repeats the failed write`() =
        runTest {
            val repository = FakeRepository()
            val vm = WearSettingsViewModel(repository, backgroundScope)
            runCurrent()
            repository.failWrites = true
            vm.save(
                vm.uiState.value.snapshot!!
                    .preferences
                    .copy(graphHours = 12),
            )
            runCurrent()
            assertTrue(vm.uiState.value.storageError)
            assertTrue(repository.saved.isEmpty())
            repository.failWrites = false
            vm.retry()
            runCurrent()
            assertFalse(vm.uiState.value.storageError)
            assertEquals(listOf(AppearanceMode.DARK to 12), repository.saved)
        }

    @Test fun `color edits persist without replacing active editor and dismissal renders latest state`() =
        runTest {
            val repository = FakeRepository()
            val vm = WearSettingsViewModel(repository, backgroundScope)
            runCurrent()
            val revision = vm.uiState.value.renderRevision
            vm.setColorEditorOpen(true)
            vm.save(
                vm.uiState.value.snapshot!!
                    .preferences
                    .copy(glucoseScalePercent = 130),
            )
            runCurrent()
            assertEquals(revision, vm.uiState.value.renderRevision)
            assertTrue(vm.uiState.value.colorEditorOpen)
            vm.setColorEditorOpen(false)
            assertTrue(vm.uiState.value.renderRevision > revision)
            assertFalse(vm.uiState.value.colorEditorOpen)
        }

    private class FakeRepository : WearSettingsRepository {
        var failWrites = false
        val saved = mutableListOf<Pair<AppearanceMode, Int>>()

        override suspend fun load(mode: AppearanceMode?) = WearSettingsSnapshot(mode = mode ?: AppearanceMode.DARK)

        override suspend fun execute(mode: AppearanceMode, action: WearSettingsWrite) {
            if (failWrites) throw java.io.IOException("storage unavailable")
            if (action is WearSettingsWrite.Display) saved += mode to action.value.graphHours
        }
    }
}
