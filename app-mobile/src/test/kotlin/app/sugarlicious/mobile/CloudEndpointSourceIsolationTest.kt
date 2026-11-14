package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudEndpointSourceIsolationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L

    @Test fun `new CloudEndpoint server is fetched immediately despite previous account throttle`() =
        runBlocking {
            context
                .getSharedPreferences("nightscout_treatment_secret", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            val configuration = CloudEndpointConfiguration(true, "https://first.example.org", CloudEndpointAuthMode.API_SECRET)
            CloudEndpointConfigurationStore.save(context, configuration, null)
            // Missing credentials exit before any network call, but reserve the same account throttle.
            assertNotNull(CloudEndpointTreatmentSync.syncIfDue(context, now))
            assertNull(CloudEndpointTreatmentSync.syncIfDue(context, now + 1))
            CloudEndpointConfigurationStore.save(context, configuration.copy(baseUrl = "https://second.example.org"), null)
            assertNotNull(CloudEndpointTreatmentSync.syncIfDue(context, now + 2))
        }

    @Test fun `treatment histories remain scoped to their captured server account`() {
        val first = CloudEndpointConfiguration(true, "https://first.example.org", CloudEndpointAuthMode.API_SECRET)
        val second = first.copy(baseUrl = "https://second.example.org")
        val a = CloudEndpointTreatmentStore.accountKey(first, "first-key")
        val b = CloudEndpointTreatmentStore.accountKey(second, "second-key")
        val event = TherapyEvent("dose", TherapyEventKind.MANUAL_CORRECTION, now - 60_000, 1.0, source = TherapyEventSource.NIGHTSCOUT_ONLY)
        CloudEndpointTreatmentStore.merge(context, listOf(event), now, a)
        assertEquals(listOf(event), CloudEndpointTreatmentStore.read(context, now, a))
        assertTrue(CloudEndpointTreatmentStore.read(context, now, b).isEmpty())
    }

    @Test fun `CloudEndpoint treatments work with AndroidAPS therapy disabled`() =
        runBlocking {
            context
                .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("source.aaps.therapy", false)
                .putStringSet("source.glucose.enabled", setOf("ANDROID_APS"))
                .commit()
            CloudEndpointConfigurationStore.save(context, CloudEndpointConfiguration(true, "https://example.org", CloudEndpointAuthMode.API_SECRET), null)
            val event = TherapyEvent("ns-dose", TherapyEventKind.MANUAL_CORRECTION, now - 60_000, 1.0, source = TherapyEventSource.NIGHTSCOUT_ONLY)
            CloudEndpointTreatmentStore.merge(context, listOf(event), now)
            val state = TherapyDisplayState(source = DataSourceId.ANDROID_APS, receivedAtEpochMs = now, glucose = GlucoseState(120.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now), insulin = InsulinState(totalIob = 2.0))
            val result = MobileCanonicalStateCoordinator.savePhoneInput(context, state, now).first
            assertTrue(result.therapyEvents.any { it.id == "ns-dose" })
            assertNull(result.insulin)
            assertEquals(DataSourceId.NIGHTSCOUT, result.fieldSources[CanonicalDataField.THERAPY_EVENTS])
            assertTrue(DataCapability.TREATMENTS in result.capabilities)
        }
}
