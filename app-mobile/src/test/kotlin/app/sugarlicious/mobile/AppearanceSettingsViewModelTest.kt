package app.sugarlicious.mobile

import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.model.AppearanceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelTest {
    @Test fun `slider draft updates immediately and persistence waits for gesture completion`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            vm.setFloat(AppearanceFloat.CGM_RADIUS, 4f)
            assertEquals(
                4f,
                vm.uiState.value.snapshot!!
                    .floats
                    .getValue(AppearanceFloat.CGM_RADIUS),
            )
            runCurrent()
            assertTrue(repository.commands.isEmpty())
            vm.saveFloat(AppearanceFloat.CGM_RADIUS)
            runCurrent()
            assertEquals(AppearanceWrite.FloatValue(AppearanceFloat.CGM_RADIUS, 4f), repository.commands.single().second)
        }

    @Test fun `palette command captures original mode and retains edited role`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            vm.editColor(SugarliciousColorRole.CGM_DOT_LOW)
            vm.saveColor(SugarliciousColorRole.CGM_DOT_LOW, 123)
            vm.selectMode(AppearanceMode.LIGHT)
            runCurrent()
            assertEquals(AppearanceMode.DARK, repository.commands.first().first)
            assertEquals(
                AppearanceMode.LIGHT,
                vm.uiState.value.snapshot!!
                    .mode,
            )
            assertEquals(SugarliciousColorRole.CGM_DOT_LOW, vm.uiState.value.editingRole)
        }

    @Test fun `save failure is explicit and retry repeats original command without losing draft`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            repository.fail = true
            vm.setFloat(AppearanceFloat.CGM_RADIUS, 4.5f)
            vm.saveFloat(AppearanceFloat.CGM_RADIUS)
            runCurrent()
            assertEquals(AppearanceSettingsError.STORAGE, vm.uiState.value.error)
            assertEquals(
                4.5f,
                vm.uiState.value.snapshot!!
                    .floats
                    .getValue(AppearanceFloat.CGM_RADIUS),
            )
            repository.fail = false
            vm.retry()
            runCurrent()
            assertEquals(null, vm.uiState.value.error)
            assertEquals(1, repository.commands.size)
        }

    @Test fun `watch sync rejects duplicate taps and cancellation stays cancellation`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            repository.cancelWatch = true
            vm.sendWatchColors()
            vm.sendWatchColors()
            runCurrent()
            assertEquals(1, repository.watchCalls)
            assertEquals(null, vm.uiState.value.error)
            assertFalse(vm.uiState.value.syncingWatch)
            vm.setFloat(AppearanceFloat.CGM_RADIUS, 4f)
            vm.saveFloat(AppearanceFloat.CGM_RADIUS)
            runCurrent()
            assertEquals("Cancelled publication must not stop later settings writes", 1, repository.commands.size)
        }

    @Test fun `notification dot commit mirrors legacy outline fields while retaining separate current outline`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            vm.setDots(NotificationGraphProfile.EXPANDED, NotificationGraphDotStyle(3f, true, .95f, false, .5f, 1.5f))
            vm.saveDots(NotificationGraphProfile.EXPANDED)
            runCurrent()
            val saved = (repository.commands.single().second as AppearanceWrite.Dots).value
            assertFalse(saved.cgmOutlineEnabled)
            assertEquals(.5f, saved.cgmOutlineWidthDp)
            assertEquals(1.5f, saved.currentOutlineWidthDp)
        }

    @Test fun `send waits for earlier color write and publishes its committed value`() =
        runTest {
            val repository = FakeRepository()
            val vm = AppearanceSettingsViewModel(repository, backgroundScope)
            runCurrent()
            val writeGate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.writeGate = writeGate
            vm.saveColor(SugarliciousColorRole.CGM_DOT_LOW, 0xff123456.toInt())
            vm.sendWatchColors()
            vm.sendWatchColors()
            runCurrent()
            assertEquals("Publication must wait for the color write", 0, repository.watchCalls)
            assertTrue(vm.uiState.value.syncingWatch)
            writeGate.complete(Unit)
            runCurrent()
            assertEquals(listOf(0xff123456.toInt()), repository.publishedColors)
            assertEquals(1, repository.watchCalls)
            assertFalse(vm.uiState.value.syncingWatch)
            assertEquals("Graphfarben wurden an die Watch gesendet.", vm.uiState.value.watchSyncStatus)
        }

    private class FakeRepository : AppearanceSettingsRepository {
        var fail = false
        var writeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        private var committedColor = 0
        val publishedColors = mutableListOf<Int>()
        var watchCalls = 0
        var cancelWatch = false
        val commands = mutableListOf<Pair<AppearanceMode, AppearanceWrite>>()

        override suspend fun load(mode: AppearanceMode?) = AppearanceSettingsSnapshot(mode = mode ?: AppearanceMode.DARK)

        override suspend fun execute(mode: AppearanceMode, action: AppearanceWrite) {
            writeGate?.await()
            if (fail) throw java.io.IOException("storage unavailable")
            if (action is AppearanceWrite.Color) committedColor = action.argb
            commands += mode to action
        }

        override suspend fun sendWatchColors() {
            watchCalls++
            publishedColors += committedColor
            if (cancelWatch) throw kotlinx.coroutines.CancellationException("cancelled")
        }
    }
}
