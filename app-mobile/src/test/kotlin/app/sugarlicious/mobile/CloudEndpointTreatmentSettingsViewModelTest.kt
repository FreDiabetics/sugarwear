package app.sugarlicious.mobile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudEndpointTreatmentSettingsViewModelTest {
    private class Store : CloudEndpointTreatmentSettingsStore {
        var saves = 0
        var tests = 0
        var needsPermission = true

        override suspend fun load() = CloudEndpointTreatmentSettingsState(loaded = true, url = "https://example.com")

        override suspend fun needsPermission(configuration: CloudEndpointConfiguration, sdk: Int, granted: Boolean) = needsPermission && !granted

        override suspend fun save(configuration: CloudEndpointConfiguration, secret: String?) {
            saves++
        }

        override suspend fun test() = "connected".also { tests++ }
    }

    @Test fun `repeated taps request consent once and denial never writes settings`() =
        runTest {
            val store = Store()
            val model = CloudEndpointTreatmentSettingsViewModel(store, backgroundScope, UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            model.submit(test = true, sdk = 37, granted = false)
            model.submit(test = true, sdk = 37, granted = false)
            runCurrent()
            assertTrue(model.uiState.value.requestPermission)
            model.consumePermissionRequest()
            assertFalse(model.uiState.value.requestPermission)
            model.permissionResult(false)
            runCurrent()
            assertEquals(0, store.saves)
            assertEquals(0, store.tests)
            assertFalse(model.uiState.value.busy)
        }

    @Test fun `consent resumes retained test command and clears secret after persistence`() =
        runTest {
            val store = Store()
            val model = CloudEndpointTreatmentSettingsViewModel(store, backgroundScope, UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            model.update(secret = "private")
            model.submit(test = true, sdk = 37, granted = false)
            runCurrent()
            model.consumePermissionRequest()
            model.permissionResult(true)
            runCurrent()
            assertEquals(1, store.saves)
            assertEquals(1, store.tests)
            assertEquals("", model.uiState.value.secret)
            assertEquals("connected", model.uiState.value.status)
            assertFalse(model.uiState.value.busy)
        }
}
