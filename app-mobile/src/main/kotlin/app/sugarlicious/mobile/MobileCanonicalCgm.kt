package app.sugarlicious.mobile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.sugarlicious.model.DataCapability
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendDiagnostics
import app.sugarlicious.model.withHistoryDerivedDelta
import app.sugarlicious.storage.CanonicalStateStore
import app.sugarlicious.storage.PhoneTherapyStateStore
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Legacy Mobile store kept only to delete data written by the short-lived Watch-backfill bridge.
 * SugarWear history is collector-local and must never be a Sugarlicious Mobile input.
 */
private val Context.mobileCgmHistoryDataStore by preferencesDataStore("mobile_g7_backfill")

private suspend fun clearLegacyMobileCgmHistory(context: Context) {
    context.mobileCgmHistoryDataStore.edit { it.clear() }
}

internal object MobileWatchCgmMigration {
    private const val PREFS = "mobile_watch_cgm_migration"
    private const val KEY_VERSION = "version"
    private const val VERSION = 2

    suspend fun runOnce(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, 0) >= VERSION) return false

        clearLegacyMobileCgmHistory(app)
        app
            .getSharedPreferences("mobile_canonical_cgm_resolver", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val phoneStore = PhoneTherapyStateStore(app)
        val phone = phoneStore.state.first()?.withoutDirectWatchCgm()
        val displayStore = TherapyStateStore(app)
        val current = displayStore.state.first()
        val replacement =
            when {
                phone != null -> phone
                current != null -> current.withoutDirectWatchCgm()
                else -> null
            }
        replacement?.let { CanonicalStateStore(app).commit(it) }

        prefs.edit().putInt(KEY_VERSION, VERSION).apply()
        app.recordMobileDiagnostic(
            module = "G7",
            code = "G7-MIGRATE-200",
            message = "Removed SugarWear CGM/history from Sugarlicious Mobile",
            metadata = mapOf("migrationVersion" to VERSION),
        )
        return true
    }
}

/** Sugarlicious Mobile canonical CGM is phone-source only. */
internal object MobileCanonicalCgmResolver {
    suspend fun resolve(
        context: Context,
        phoneState: TherapyDisplayState?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TherapyDisplayState? {
        MobileWatchCgmMigration.runOnce(context)
        return phoneState?.mobilePhoneSourcesOnly()
    }
}

internal object MobileCanonicalStateCoordinator {
    private val inputMutex = Mutex()

    suspend fun savePhoneInput(context: Context, incoming: TherapyDisplayState, nowEpochMs: Long): Pair<TherapyDisplayState, TherapyDisplayState> {
        val (committed, changed) =
            inputMutex.withLock {
                MobileWatchCgmMigration.runOnce(context)
                require(incoming.source in MOBILE_INPUT_SOURCES) { "Not a supported Mobile phone source" }
                val store = MobileSourceInputStore(context)
                seedLegacyTherapyBridge(context, store, nowEpochMs)
                commitComposition(context, store.merge(incoming, nowEpochMs), nowEpochMs)
            }
        if (changed) dispatchCanonicalDataChanged(context, committed)
        return committed to committed
    }

    suspend fun recompose(context: Context, nowEpochMs: Long = System.currentTimeMillis()): TherapyDisplayState {
        val (committed, changed) =
            inputMutex.withLock {
                MobileWatchCgmMigration.runOnce(context)
                val store = MobileSourceInputStore(context)
                seedLegacyTherapyBridge(context, store, nowEpochMs)
                commitComposition(context, store.read(), nowEpochMs)
            }
        if (changed) dispatchCanonicalDataChanged(context, committed)
        return committed
    }

    suspend fun activateShareAccount(context: Context, identity: String) = activateRemoteAccount(context, DataSourceId.DEXCOM_SHARE, identity)

    suspend fun activateRemoteAccount(context: Context, source: DataSourceId, identity: String) {
        val update =
            inputMutex.withLock {
                val inputs = MobileSourceInputStore(context)
                if (!inputs.activateRemoteIdentity(source, identity)) return@withLock null
                val canonical = CanonicalStateStore(context)
                canonical.reconcile()?.let { old ->
                    val removedCurrent = old.glucose?.source == source
                    val removedFields = old.fieldSources.filterValues { it == source }.keys
                    canonical.commit(
                        old.copy(
                            source = if (removedCurrent) DataSourceId.OTHER else old.source,
                            sourceContract = "PHONE_SOURCE_IDENTITY_CHANGED",
                            glucose = old.glucose.takeUnless { removedCurrent },
                            glucoseHistory = old.glucoseHistory.filter { it.source != source },
                            glucosePredictions = if (removedCurrent) emptyList() else old.glucosePredictions,
                            capabilities = if (removedCurrent) old.capabilities - setOf(DataCapability.GLUCOSE, DataCapability.TREND, DataCapability.DELTA, DataCapability.AVERAGE_DELTA) else old.capabilities,
                            fieldSources = old.fieldSources - removedFields,
                            fieldProvenance = old.fieldProvenance - removedFields,
                        ),
                    )
                }
                TherapyStateStore(context).removeSourceHistory(source)
                val committed = commitComposition(context, inputs.read(), System.currentTimeMillis()).first
                inputs.completeRemoteIdentity(source, identity)
                committed
            }
        if (update != null) dispatchCanonicalDataChanged(context, update)
    }

    private suspend fun seedLegacyTherapyBridge(context: Context, store: MobileSourceInputStore, now: Long) {
        if (DataSourceId.ANDROID_APS !in store.read()) {
            CanonicalStateStore(context).reconcile()?.takeIf { it.source == DataSourceId.ANDROID_APS }?.let { store.merge(it.withoutDirectWatchCgm(), now) }
        }
    }

    private suspend fun commitComposition(context: Context, inputs: Map<DataSourceId, TherapyDisplayState>, nowEpochMs: Long): Pair<TherapyDisplayState, Boolean> {
        var mergedPhone = MobileSourceComposition.compose(inputs, MobileSourceConfigurationStore.read(context), nowEpochMs)
        val glucose = mergedPhone.glucose
        if (mergedPhone.source == DataSourceId.ANDROID_APS && glucose != null && (glucose.deltaMgDl?.isFinite() == true || glucose.trend == Trend.UNKNOWN)) {
            val resolution =
                TrendArrowResolver.resolveWithRate(
                    glucose.trend,
                    glucose,
                    mergedPhone.glucoseHistory,
                )
            mergedPhone =
                mergedPhone.copy(
                    glucose =
                        glucose.copy(
                            trend = resolution.trend,
                            trendRateMgDlPerMinute = resolution.rateMgDlPerMinute,
                            trendOrigin = resolution.provenance,
                        ),
                )
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Log.d(
                    "CgmTrendPipeline",
                    TrendDiagnostics.format(
                        glucoseMgDl = glucose.valueMgDl,
                        deltaMgDl = glucose.deltaMgDl,
                        elapsedMinutes =
                            resolution.rateMgDlPerMinute
                                ?.takeIf { it != 0.0 }
                                ?.let { glucose.deltaMgDl?.div(it) },
                        rateMgDlPerMinute = resolution.rateMgDlPerMinute,
                        sourceTrend = glucose.trend,
                        canonicalTrend = resolution.trend,
                        source = glucose.source,
                    ),
                )
            }
        }

        if (MobileSourceConfigurationStore.read(context).therapyBridgeTherapyEnabled || CloudEndpointConfigurationStore.read(context).enabled) {
            val therapyBridgeEvents =
                mergedPhone.therapyEvents.mapNotNull { event ->
                    when (event.source) {
                        app.sugarlicious.model.TherapyEventSource.NIGHTSCOUT_ONLY -> null
                        app.sugarlicious.model.TherapyEventSource.AAPS_ENRICHED_BY_NIGHTSCOUT -> event.copy(source = app.sugarlicious.model.TherapyEventSource.AAPS_ONLY)
                        app.sugarlicious.model.TherapyEventSource.AAPS_ONLY -> event
                    }
                }
            val external = if (CloudEndpointConfigurationStore.read(context).enabled) CloudEndpointTreatmentStore.read(context, nowEpochMs) else emptyList()
            val events =
                app.sugarlicious.model.CanonicalTreatments
                    .merge(therapyBridgeEvents, external)
            val eventsField = app.sugarlicious.model.CanonicalDataField.THERAPY_EVENTS
            mergedPhone =
                mergedPhone.copy(
                    therapyEvents = events,
                    capabilities = if (events.isEmpty()) mergedPhone.capabilities - DataCapability.TREATMENTS else mergedPhone.capabilities + DataCapability.TREATMENTS,
                    fieldSources = if (events.isEmpty()) mergedPhone.fieldSources - eventsField else mergedPhone.fieldSources + (eventsField to if (therapyBridgeEvents.isEmpty()) DataSourceId.NIGHTSCOUT else DataSourceId.ANDROID_APS),
                    fieldProvenance = if (events.isEmpty()) mergedPhone.fieldProvenance - eventsField else mergedPhone.fieldProvenance + (eventsField to app.sugarlicious.model.ValueProvenance.SOURCE),
                )
        }
        val store = CanonicalStateStore(context)
        val previous = store.reconcile()
        mergedPhone = retainMobileDisplayHistory(restoreStoredMobileHistory(previous, inputs, nowEpochMs), mergedPhone, nowEpochMs).withHistoryDerivedDelta(app.sugarlicious.model.TrendRateProfile.SUGARLICIOUS)
        if (previous != null && mergedPhone.copy(canonicalRevision = previous.canonicalRevision) == previous) return previous to false
        return store.commit(mergedPhone) to true
    }
}

internal fun TherapyDisplayState.withoutDirectWatchCgm(): TherapyDisplayState {
    val filteredHistory = glucoseHistory.filter { sample -> sample.source != DataSourceId.DEXCOM_G7_WATCH }
    val currentIsWatch =
        source == DataSourceId.DEXCOM_G7_WATCH ||
            glucose?.source == DataSourceId.DEXCOM_G7_WATCH
    val safeGlucose =
        glucose?.takeUnless {
            it.source == DataSourceId.DEXCOM_G7_WATCH || source == DataSourceId.DEXCOM_G7_WATCH
        }
    val safeSource = if (source == DataSourceId.DEXCOM_G7_WATCH) DataSourceId.ANDROID_APS else source
    val safeCapabilities =
        if (safeGlucose == null && currentIsWatch) {
            capabilities -
                setOf(
                    DataCapability.GLUCOSE,
                    DataCapability.TREND,
                    DataCapability.DELTA,
                    DataCapability.AVERAGE_DELTA,
                )
        } else {
            capabilities
        }

    return copy(
        source = safeSource,
        sourceVersion = if (currentIsWatch) null else sourceVersion,
        sourceContract = if (currentIsWatch) "MOBILE_PHONE_ONLY:NO_WATCH_CGM" else sourceContract,
        glucose = safeGlucose,
        glucoseHistory = filteredHistory,
        capabilities = safeCapabilities,
    )
}

internal fun TherapyDisplayState.mobilePhoneSourcesOnly(): TherapyDisplayState =
    withoutDirectWatchCgm().copy(
        glucoseHistory = glucoseHistory.filter { it.source in MOBILE_INPUT_SOURCES }.sortedBy(GlucoseSample::measuredAtEpochMs),
    )
