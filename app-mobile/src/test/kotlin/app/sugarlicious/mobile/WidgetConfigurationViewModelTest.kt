package app.sugarlicious.mobile

import app.sugarlicious.model.AppearanceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigurationViewModelTest {
    @Test fun `drafts stay local until one atomic save and duplicate taps do not publish twice`() =
        runTest {
            val repository = Store()
            val vm = WidgetConfigurationViewModel(repository, backgroundScope)
            runCurrent()
            vm.edit { it.copy(trendScalePercent = 140) }
            assertEquals(0, repository.saves)
            vm.save()
            vm.save()
            runCurrent()
            assertEquals(1, repository.saves)
            assertEquals(140, repository.saved?.trendScalePercent)
            assertTrue(vm.state.value.saved)
        }

    @Test fun `failed save retains draft and explicit retry can publish`() =
        runTest {
            val repository = Store().apply { fail = true }
            val vm = WidgetConfigurationViewModel(repository, backgroundScope)
            runCurrent()
            vm.edit { it.copy(trendScalePercent = 145) }
            vm.save()
            runCurrent()
            assertEquals(WidgetConfigurationError.SAVE_FAILED, vm.state.value.error)
            assertEquals(
                145,
                vm.state.value.snapshot
                    ?.value
                    ?.trendScalePercent,
            )
            assertFalse(vm.state.value.saving)
            repository.fail = false
            vm.save()
            runCurrent()
            assertTrue(vm.state.value.saved)
        }

    private class Store : WidgetConfigurationRepository {
        var saves = 0
        var saved: WidgetInstanceConfiguration? = null
        var fail = false

        override suspend fun load() = WidgetConfigurationSnapshot(WidgetInstanceConfiguration(), selectedMode = AppearanceMode.DARK)

        override suspend fun save(initial: WidgetInstanceConfiguration, value: WidgetInstanceConfiguration) {
            saves++
            if (fail) throw IOException("unavailable")
            saved = value
        }
    }
}
