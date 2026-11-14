package app.sugarlicious.mobile

import app.sugarlicious.model.CgmThresholds
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

class MobileSettingsViewModelTest {
    private open class Store : MobileSettingsStore {
        val values = mutableMapOf<String, Any>()

        override fun write(key: String, value: Any) {
            values[key] = value
        }

        override fun saveThresholds(value: CgmThresholds) = Unit

        override suspend fun syncHealth(schedule: Boolean): HealthConnectSyncResult? = null

        override suspend fun sync(therapy: app.sugarlicious.model.TherapyDisplayState) = Unit

        override suspend fun export(uri: android.net.Uri) = Unit

        override suspend fun restore(uri: android.net.Uri) = SettingsRestoreResult(0, 0)
    }

    @Test fun `back consumes only a nested page and resets page expansions`() {
        val model = MobileSettingsViewModel(Store())
        assertFalse(model.back())
        model.open("notifications", "Benachrichtigungen und Alarme")
        model.toggleNotificationGraph()
        assertTrue(model.uiState.value.notificationGraphExpanded)
        assertTrue(model.back())
        assertNull(model.uiState.value.category)
        assertEquals("Einstellungen", model.uiState.value.title)
        assertFalse(model.uiState.value.notificationGraphExpanded)
        assertFalse(model.back())
    }

    @Test fun `graph hours and watchface selection preserve existing valid ranges`() {
        val store = Store()
        val model = MobileSettingsViewModel(store)
        model.setGraphHours(-1)
        model.setNotificationGraphHours(90)
        model.setWatchFaceIndex(-4)
        assertEquals(3, store.values["graphHours"])
        assertEquals(3, store.values[PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS])
        assertEquals(0, store.values["watchFaceIndex"])
        model.setGraphHours(OVERVIEW_GRAPH_HOUR_OPTIONS.last())
        assertEquals(OVERVIEW_GRAPH_HOUR_OPTIONS.last(), store.values["graphHours"])
    }

    @Test fun `navigation does not write settings and mutations use the existing store`() {
        val store = Store()
        val model = MobileSettingsViewModel(store)
        model.open("appearance", "Farben & Darstellung")
        assertTrue(store.values.isEmpty())
        model.setBoolean("showCgmGraph", false)
        model.setFloat(DashboardUiPreferences.IOB_GRAPH_MAXIMUM_KEY, 12f)
        assertEquals(false, store.values["showCgmGraph"])
        assertEquals(12f, store.values[DashboardUiPreferences.IOB_GRAPH_MAXIMUM_KEY])
    }

    @Test fun `sync without a canonical value does not call publisher`() {
        val model = MobileSettingsViewModel(Store())
        model.syncNow(null)
        assertEquals("Noch keine gï¿½ltigen AndroidAPS-Daten vorhanden", model.uiState.value.message)
        assertFalse(model.uiState.value.busy)
        model.clearMessage()
        assertNull(model.uiState.value.message)
    }

    @Test fun `enabling predictions selects all streams but disabling preserves stream choices`() {
        val store = Store()
        val model = MobileSettingsViewModel(store)
        model.setPredictions(true)
        assertEquals(true, store.values["showPredictions"])
        listOf("iob", "cob", "uam", "zeroTemp").forEach { assertEquals(true, store.values["cgm.prediction.$it"]) }
        model.setPredictions(false)
        assertEquals(false, store.values["showPredictions"])
        assertEquals(true, store.values["cgm.prediction.iob"])
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `repeated sync taps publish once and operation survives category back`() =
        kotlinx.coroutines.test.runTest {
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            var published = 0
            val store =
                object : Store() {
                    override suspend fun sync(therapy: app.sugarlicious.model.TherapyDisplayState) {
                        published++
                        release.await()
                    }
                }
            val model = MobileSettingsViewModel(store, backgroundScope, kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            val therapy = app.sugarlicious.model.TherapyDisplayState(receivedAtEpochMs = 123L)
            model.open("data", "Datenverwaltung")
            model.syncNow(therapy)
            model.syncNow(therapy)
            runCurrent()
            assertEquals(1, published)
            assertTrue(model.uiState.value.busy)
            model.back()
            assertTrue(model.uiState.value.busy)
            release.complete(Unit)
            runCurrent()
            assertFalse(model.uiState.value.busy)
            assertEquals("An Watch ï¿½bertragen", model.uiState.value.message)
        }
}
