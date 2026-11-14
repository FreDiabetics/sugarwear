package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileSourceConfigurationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    @Before fun prepare() {
        prefs.edit().clear().commit()
    }

    @Test fun `changing therapy after migration keeps the forecast switch independent`() {
        assertTrue(MobileSourceConfigurationStore.read(context).therapyBridgePredictionsEnabled)
        MobileSourceConfigurationStore.setTherapy(context, false)
        assertTrue(MobileSourceConfigurationStore.read(context).therapyBridgePredictionsEnabled)
        MobileSourceConfigurationStore.setPredictions(context, false)
        MobileSourceConfigurationStore.setTherapy(context, true)
        assertFalse(MobileSourceConfigurationStore.read(context).therapyBridgePredictionsEnabled)
    }

    @Test fun `forecast-only AndroidAPS master restore never enables AndroidAPS dots`() {
        prefs
            .edit()
            .putStringSet("source.glucose.enabled", setOf("DEXCOM_BROADCAST"))
            .putBoolean("source.aaps.therapy", false)
            .putBoolean("source.aaps.predictions", true)
            .commit()
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, false)
        assertFalse(MobileSourceConfigurationStore.read(context).therapyBridgePredictionsEnabled)
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, true)
        val restored = MobileSourceConfigurationStore.read(context)
        assertTrue(restored.therapyBridgePredictionsEnabled)
        assertFalse(restored.therapyBridgeTherapyEnabled)
        assertEquals(setOf(MobileGlucoseSource.DEXCOM_BROADCAST), restored.enabledGlucoseSources)
    }

    @Test fun `CloudEndpoint treatment-only source remains active without enabling glucose after master restore`() {
        context
            .getSharedPreferences("nightscout_treatment_config", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", true)
            .putString("url", "https://example.org")
            .commit()
        MobileSourceConfigurationStore.setCloudEndpointEnabled(context, false)
        assertFalse(CloudEndpointConfigurationStore.read(context).enabled)
        MobileSourceConfigurationStore.setCloudEndpointEnabled(context, true)
        assertTrue(CloudEndpointConfigurationStore.read(context).enabled)
        assertFalse(MobileGlucoseSource.NIGHTSCOUT in MobileSourceConfigurationStore.read(context).enabledGlucoseSources)
    }

    @Test fun `old configuration enabling both Manufacturer streams resolves to the displayed Share choice`() {
        prefs.edit().putStringSet("source.glucose.enabled", setOf("ANDROID_APS", "DEXCOM_SHARE", "DEXCOM_BROADCAST")).commit()
        assertEquals(setOf(MobileGlucoseSource.ANDROID_APS, MobileGlucoseSource.DEXCOM_SHARE), MobileSourceConfigurationStore.read(context).enabledGlucoseSources)
    }

    @Test fun `Manufacturer selector is exclusive and master restores choice without disabling therapy`() {
        MobileSourceConfigurationStore.setManufacturer(context, MobileGlucoseSource.DEXCOM_SHARE, true)
        MobileSourceConfigurationStore.setManufacturer(context, MobileGlucoseSource.DEXCOM_BROADCAST, true)
        assertEquals(setOf(MobileGlucoseSource.ANDROID_APS, MobileGlucoseSource.DEXCOM_BROADCAST), MobileSourceConfigurationStore.read(context).enabledGlucoseSources)
        MobileSourceConfigurationStore.setManufacturer(context, MobileGlucoseSource.DEXCOM_BROADCAST, false)
        assertEquals(MobileGlucoseSource.DEXCOM_BROADCAST, MobileSourceConfigurationStore.preferredManufacturer(context))
        assertTrue(MobileSourceConfigurationStore.read(context).therapyBridgeTherapyEnabled)
    }

    @Test fun `source master preserves other sources and restores its field choices`() {
        prefs
            .edit()
            .putStringSet("source.glucose.enabled", setOf("ANDROID_APS", "DEXCOM_SHARE"))
            .putBoolean("source.aaps.therapy", true)
            .commit()
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, false)
        val disabled = MobileSourceConfigurationStore.read(context)
        assertEquals(setOf(MobileGlucoseSource.DEXCOM_SHARE), disabled.enabledGlucoseSources)
        assertFalse(disabled.therapyBridgeTherapyEnabled)
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, true)
        val restored = MobileSourceConfigurationStore.read(context)
        assertEquals(setOf(MobileGlucoseSource.ANDROID_APS, MobileGlucoseSource.DEXCOM_SHARE), restored.enabledGlucoseSources)
        assertTrue(restored.therapyBridgeTherapyEnabled)
    }

    @Test fun `therapy-only AndroidAPS selection remains therapy-only after master toggle`() {
        prefs
            .edit()
            .putStringSet("source.glucose.enabled", setOf("DEXCOM_SHARE"))
            .putBoolean("source.aaps.therapy", true)
            .commit()
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, false)
        MobileSourceConfigurationStore.setTherapyBridgeEnabled(context, true)
        val restored = MobileSourceConfigurationStore.read(context)
        assertFalse(MobileGlucoseSource.ANDROID_APS in restored.enabledGlucoseSources)
        assertTrue(restored.therapyBridgeTherapyEnabled)
    }
}
