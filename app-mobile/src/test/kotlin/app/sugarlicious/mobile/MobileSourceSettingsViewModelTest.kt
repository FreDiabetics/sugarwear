package app.sugarlicious.mobile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSourceSettingsViewModelTest {
    @Test fun `new cloudEndpoint edit uses current configuration and preserves an active draft`() =
        runTest {
            val store = Store()
            val original = CloudEndpointConfiguration(false, "https://old.example", CloudEndpointAuthMode.API_SECRET)
            store.state.value = MobileSourceSettingsSnapshot(cloudEndpoint = original)
            val model = MobileSourceSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            model.setVisible(true)
            runCurrent()
            assertFalse(model.cloudEndpoint.value.editing)
            val updated = original.copy(baseUrl = "https://new.example", authMode = CloudEndpointAuthMode.ACCESS_TOKEN)
            store.state.value = store.state.value.copy(cloudEndpoint = updated)
            runCurrent()
            model.editCloudEndpoint()
            assertEquals(updated.baseUrl, model.cloudEndpoint.value.url)
            assertEquals(updated.authMode, model.cloudEndpoint.value.authMode)
            model.updateCloudEndpoint(url = "https://draft.example", secret = "private")
            store.state.value = store.state.value.copy(cloudEndpoint = original)
            runCurrent()
            model.editCloudEndpoint()
            assertEquals("https://draft.example", model.cloudEndpoint.value.url)
            assertEquals("private", model.cloudEndpoint.value.secret)
        }

    private class Store : MobileSourceSettingsStore {
        override val state = MutableStateFlow(MobileSourceSettingsSnapshot())
        var savedAccount = ""
        var savedPassword = ""
        var cloudEndpointSecret: String? = null

        override fun setSource(source: MobileGlucoseSource, enabled: Boolean) = Unit

        override fun setTherapyBridge(enabled: Boolean) = Unit

        override fun setManufacturer(source: MobileGlucoseSource, enabled: Boolean) = Unit

        override fun setPredictions(enabled: Boolean) = Unit

        override fun setTherapy(enabled: Boolean) = Unit

        override fun setCloudEndpoint(enabled: Boolean) = Unit

        override fun setCloudEndpointTherapy(enabled: Boolean) = Unit

        override fun saveShare(account: String, password: String, region: String) {
            savedAccount = account
            savedPassword = password
        }

        override fun saveCloudEndpoint(configuration: CloudEndpointConfiguration, secret: String?) {
            cloudEndpointSecret = secret
        }

        override fun hasCloudEndpointSecret() = false
    }

    @Test fun `credential validation refuses incomplete drafts and successful save clears password`() =
        runTest {
            val store = Store()
            val model = MobileSourceSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            model.saveShare()
            runCurrent()
            assertEquals("", store.savedAccount)
            assertTrue(model.share.value.editing)
            model.updateShare(account = "alice", password = "private")
            model.saveShare()
            runCurrent()
            assertEquals("alice", store.savedAccount)
            assertEquals("private", store.savedPassword)
            assertEquals("", model.share.value.password)
            assertFalse(model.share.value.editing)
        }

    @Test fun `cancel clears in memory secrets without writing repository`() =
        runTest {
            val store = Store()
            val model = MobileSourceSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            model.updateShare(password = "private")
            model.cancelShare()
            model.updateCloudEndpoint(secret = "private")
            model.cancelCloudEndpoint()
            assertEquals("", model.share.value.password)
            assertEquals("", model.cloudEndpoint.value.secret)
            assertEquals("", store.savedPassword)
            assertNull(store.cloudEndpointSecret)
        }

    @Test fun `invalid cloudEndpoint URL never reaches persistence`() =
        runTest {
            val store = Store()
            val model = MobileSourceSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            model.updateCloudEndpoint(url = "http://unsafe", secret = "private")
            model.saveCloudEndpoint()
            runCurrent()
            assertNull(store.cloudEndpointSecret)
            assertTrue(model.cloudEndpoint.value.editing)
            assertTrue(
                model.cloudEndpoint.value.feedback
                    .contains("HTTPS"),
            )
        }

    @Test fun `source observation stops when the settings panel is not visible`() =
        runTest {
            val store = Store()
            val model = MobileSourceSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            assertEquals(0, store.state.subscriptionCount.value)
            model.setVisible(true)
            runCurrent()
            assertEquals(1, store.state.subscriptionCount.value)
            model.updateShare(password = "private")
            model.setVisible(false)
            runCurrent()
            assertEquals(0, store.state.subscriptionCount.value)
            model.setVisible(true)
            runCurrent()
            assertEquals("private", model.share.value.password)
        }
}
