package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import app.sugarlicious.model.DataSourceId

internal enum class MobileGlucoseSource(
    val sourceId: DataSourceId?,
    val label: String,
) {
    NONE(null, "Keine"),
    ANDROID_APS(DataSourceId.ANDROID_APS, "AndroidAPS"),
    DEXCOM_BROADCAST(DataSourceId.DEXCOM_G7_APP, "BYODA"),
    DEXCOM_SHARE(DataSourceId.DEXCOM_SHARE, "BYODA Share"),
    NIGHTSCOUT(DataSourceId.NIGHTSCOUT, "Nightscout"),
    XDRIP_PLUS(DataSourceId.XDRIP_PLUS, "xDrip+"),
    LIBRE_PATCHED(DataSourceId.LIBRE_PATCHED, "Libre patched app"),
}

internal data class MobileSourceConfiguration(
    val glucoseSource: MobileGlucoseSource = MobileGlucoseSource.ANDROID_APS,
    val therapyBridgeTherapyEnabled: Boolean = true,
    val shareRegion: String = "ous",
    val credentialsRevision: Long = 0L,
    val enabledGlucoseSources: Set<MobileGlucoseSource> = setOf(glucoseSource).filter { it != MobileGlucoseSource.NONE }.toSet(),
    val cloudEndpointRevision: Long = 0L,
    val therapyBridgePredictionsEnabled: Boolean = therapyBridgeTherapyEnabled,
)

internal object MobileSourceConfigurationStore {
    const val PREFIX = "source."

    private fun preferences(context: Context) = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    fun read(context: Context): MobileSourceConfiguration {
        val prefs = preferences(context)
        return MobileSourceConfiguration(
            runCatching { MobileGlucoseSource.valueOf(prefs.getString("source.glucose", "ANDROID_APS")!!) }.getOrDefault(MobileGlucoseSource.ANDROID_APS),
            prefs.getBoolean("source.aaps.therapy", true),
            prefs.getString("source.share.region", "ous").takeIf { it in setOf("us", "ous") } ?: "ous",
            prefs.getLong("source.share.credentials_revision", 0L),
            (
                prefs.getStringSet("source.glucose.enabled", null)?.mapNotNull { name -> MobileGlucoseSource.entries.find { it.name == name && it != MobileGlucoseSource.NONE } }?.toSet()
                    ?: setOf(runCatching { MobileGlucoseSource.valueOf(prefs.getString("source.glucose", "ANDROID_APS")!!) }.getOrDefault(MobileGlucoseSource.ANDROID_APS)).filter { it != MobileGlucoseSource.NONE }.toSet()
            ).let { sources ->
                if (sources.containsAll(DEXCOM_SOURCES)) sources - MobileGlucoseSource.DEXCOM_BROADCAST else sources
            },
            cloudEndpointRevision = prefs.getLong("source.nightscout.revision", 0L),
            therapyBridgePredictionsEnabled = prefs.getBoolean("source.aaps.predictions", prefs.getBoolean("source.aaps.therapy", true)),
        )
    }

    fun setGlucoseEnabled(context: Context, source: MobileGlucoseSource, enabled: Boolean) {
        require(source != MobileGlucoseSource.NONE)
        val current = read(context).enabledGlucoseSources
        val next = if (enabled) current + source else current - source
        preferences(context).edit { putStringSet("source.glucose.enabled", next.map { it.name }.toSet()) }
        PersistentBridgeService.start(context)
    }

    fun preferredManufacturer(context: Context): MobileGlucoseSource {
        val active = read(context).enabledGlucoseSources
        return when {
            MobileGlucoseSource.DEXCOM_SHARE in active -> MobileGlucoseSource.DEXCOM_SHARE
            MobileGlucoseSource.DEXCOM_BROADCAST in active -> MobileGlucoseSource.DEXCOM_BROADCAST
            else -> runCatching { MobileGlucoseSource.valueOf(preferences(context).getString("source.dexcom.previous", "DEXCOM_BROADCAST")!!) }.getOrDefault(MobileGlucoseSource.DEXCOM_BROADCAST).takeIf { it in DEXCOM_SOURCES } ?: MobileGlucoseSource.DEXCOM_BROADCAST
        }
    }

    fun setManufacturer(context: Context, source: MobileGlucoseSource, enabled: Boolean) {
        require(source in DEXCOM_SOURCES)
        val next = read(context).enabledGlucoseSources - DEXCOM_SOURCES + if (enabled) setOf(source) else emptySet()
        preferences(context).edit {
            putString("source.dexcom.previous", source.name)
            putStringSet("source.glucose.enabled", next.map { it.name }.toSet())
        }
        PersistentBridgeService.start(context)
    }

    private val DEXCOM_SOURCES = setOf(MobileGlucoseSource.DEXCOM_SHARE, MobileGlucoseSource.DEXCOM_BROADCAST)

    fun setTherapyBridgeEnabled(context: Context, enabled: Boolean) {
        val prefs = preferences(context)
        val current = read(context)
        val next = current.enabledGlucoseSources.toMutableSet()
        prefs.edit {
            if (!enabled) {
                putBoolean("source.aaps.previous_glucose", MobileGlucoseSource.ANDROID_APS in next)
                putBoolean("source.aaps.previous_therapy", current.therapyBridgeTherapyEnabled)
                putBoolean("source.aaps.previous_predictions", current.therapyBridgePredictionsEnabled)
                next.remove(MobileGlucoseSource.ANDROID_APS)
                putBoolean("source.aaps.therapy", false)
                putBoolean("source.aaps.predictions", false)
            } else {
                val restoreGlucose = prefs.getBoolean("source.aaps.previous_glucose", true)
                val restoreTherapy = prefs.getBoolean("source.aaps.previous_therapy", true)
                val restorePredictions = prefs.getBoolean("source.aaps.previous_predictions", restoreTherapy)
                putBoolean("source.aaps.predictions", restorePredictions)
                if (restoreGlucose || !restoreTherapy && !restorePredictions) next.add(MobileGlucoseSource.ANDROID_APS)
                putBoolean("source.aaps.therapy", restoreTherapy)
            }
            putStringSet("source.glucose.enabled", next.map { it.name }.toSet())
        }
        PersistentBridgeService.start(context)
    }

    fun setCloudEndpointEnabled(context: Context, enabled: Boolean) {
        val prefs = preferences(context)
        val current = read(context)
        val cloudEndpoint = CloudEndpointConfigurationStore.read(context)
        val currentGlucose = MobileGlucoseSource.NIGHTSCOUT in current.enabledGlucoseSources
        val restoreGlucose = prefs.getBoolean("source.nightscout.previous_glucose", true)
        val restoreTreatments = prefs.getBoolean("source.nightscout.previous_treatments", false) && cloudEndpoint.isValid()
        val next = current.enabledGlucoseSources.toMutableSet()
        if (!enabled || !restoreGlucose && restoreTreatments) next.remove(MobileGlucoseSource.NIGHTSCOUT) else next.add(MobileGlucoseSource.NIGHTSCOUT)
        prefs.edit {
            if (!enabled) {
                putBoolean("source.nightscout.previous_glucose", currentGlucose)
                putBoolean("source.nightscout.previous_treatments", cloudEndpoint.enabled)
            }
            putStringSet("source.glucose.enabled", next.map { it.name }.toSet())
        }
        CloudEndpointConfigurationStore.save(context, cloudEndpoint.copy(enabled = enabled && restoreTreatments), null)
    }

    fun setPredictions(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean("source.aaps.predictions", enabled) }
        PersistentBridgeService.start(context)
    }

    fun setTherapy(context: Context, enabled: Boolean) {
        val predictions = read(context).therapyBridgePredictionsEnabled
        preferences(context).edit {
            putBoolean("source.aaps.predictions", predictions)
            putBoolean("source.aaps.therapy", enabled)
        }
        PersistentBridgeService.start(context)
    }
}
